package com.yagay.ysuite.feature.yfiles.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.yagay.ysuite.common.Outcome
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

data class YFilesPluginDescriptor(
    val packageName: String,
    val serviceName: String,
    val label: String,
    val certificateSha256: String,
    val approved: Boolean,
    val protocolVersion: Int?,
    val capabilities: List<String>,
)

class YFilesPluginRegistry(
    context: Context,
) {
    private val appContext =
        context.applicationContext
    private val packageManager =
        appContext.packageManager
    private val prefs =
        appContext.getSharedPreferences(
            "yfiles_plugin_trust",
            Context.MODE_PRIVATE,
        )

    fun discover():
        List<YFilesPluginDescriptor> {
        val intent =
            Intent(ACTION_PLUGIN)
        val services =
            packageManager
                .queryIntentServices(
                    intent,
                    PackageManager
                        .MATCH_ALL,
                )
        return services.mapNotNull {
            resolveInfo ->
            val service =
                resolveInfo.serviceInfo
                    ?: return@mapNotNull null
            val certificate =
                certificateSha256(
                    service.packageName,
                )
                    ?: return@mapNotNull null
            val key =
                trustKey(
                    service.packageName,
                    certificate,
                )
            YFilesPluginDescriptor(
                packageName =
                    service.packageName,
                serviceName = service.name,
                label =
                    resolveInfo.loadLabel(
                        packageManager,
                    ).toString(),
                certificateSha256 =
                    certificate,
                approved =
                    prefs.getBoolean(
                        key,
                        false,
                    ),
                protocolVersion = null,
                capabilities =
                    emptyList(),
            )
        }.sortedBy {
            it.label.lowercase()
        }
    }

    fun approve(
        descriptor: YFilesPluginDescriptor,
        approved: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                trustKey(
                    descriptor.packageName,
                    descriptor
                        .certificateSha256,
                ),
                approved,
            )
            .apply()
    }

    suspend fun inspect(
        descriptor: YFilesPluginDescriptor,
    ): Outcome<YFilesPluginDescriptor> =
        withPlugin(
            descriptor,
            requireApproval = false,
        ) { plugin ->
            val version =
                plugin.protocolVersion()
            require(
                version in
                    MIN_PROTOCOL..
                    MAX_PROTOCOL,
            ) {
                "Unsupported YFiles plugin protocol: $version"
            }
            val json =
                runCatching {
                    JSONObject(
                        plugin.descriptorJson(),
                    )
                }.getOrDefault(
                    JSONObject(),
                )
            val caps =
                json.optJSONArray(
                    "capabilities",
                )
            val capabilities =
                buildList {
                    if (caps != null) {
                        for (
                            index in 0 until
                                caps.length()
                        ) {
                            caps.optString(
                                index,
                            ).takeIf {
                                it.isNotBlank()
                            }?.let(::add)
                        }
                    }
                }
            descriptor.copy(
                protocolVersion =
                    version,
                capabilities =
                    capabilities,
            )
        }

    suspend fun call(
        descriptor: YFilesPluginDescriptor,
        request: JSONObject,
    ): Outcome<JSONObject> =
        withPlugin(
            descriptor,
            requireApproval = true,
        ) { plugin ->
            val version =
                plugin.protocolVersion()
            require(
                version in
                    MIN_PROTOCOL..
                    MAX_PROTOCOL,
            ) {
                "Unsupported plugin protocol"
            }
            val safeRequest =
                JSONObject(
                    request.toString(),
                )
                    .put(
                        "protocolVersion",
                        PROTOCOL_VERSION,
                    )
            JSONObject(
                plugin.call(
                    safeRequest.toString(),
                ),
            )
        }

    private suspend fun <T> withPlugin(
        descriptor: YFilesPluginDescriptor,
        requireApproval: Boolean,
        block: (IYFilesPlugin) -> T,
    ): Outcome<T> =
        withContext(Dispatchers.IO) {
            try {
                val currentCert =
                    certificateSha256(
                        descriptor.packageName,
                    )
                        ?: error(
                            "Plugin package is unavailable",
                        )
                require(
                    currentCert.equals(
                        descriptor
                            .certificateSha256,
                        ignoreCase = true,
                    ),
                ) {
                    "Plugin signing certificate changed"
                }
                if (requireApproval) {
                    require(
                        prefs.getBoolean(
                            trustKey(
                                descriptor
                                    .packageName,
                                currentCert,
                            ),
                            false,
                        ),
                    ) {
                        "Plugin is not approved"
                    }
                }
                val plugin =
                    bind(descriptor)
                        ?: error(
                            "Unable to bind plugin",
                        )
                try {
                    Outcome.Success(
                        block(plugin),
                    )
                } finally {
                    unbindLast()
                }
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "plugin_call_failed",
                    message =
                        error.message
                            ?: "YFiles plugin failed",
                    cause = error,
                )
            }
        }

    @Volatile
    private var lastConnection:
        ServiceConnection? = null

    private suspend fun bind(
        descriptor: YFilesPluginDescriptor,
    ): IYFilesPlugin? =
        suspendCancellableCoroutine {
            continuation ->
            val intent =
                Intent(ACTION_PLUGIN)
                    .setComponent(
                        ComponentName(
                            descriptor
                                .packageName,
                            descriptor
                                .serviceName,
                        ),
                    )
            val connection =
                object :
                    ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName?,
                        service: IBinder?,
                    ) {
                        if (
                            continuation
                                .isActive
                        ) {
                            continuation.resume(
                                IYFilesPlugin
                                    .Stub
                                    .asInterface(
                                        service,
                                    ),
                            )
                        }
                    }

                    override fun onServiceDisconnected(
                        name: ComponentName?,
                    ) = Unit

                    override fun onNullBinding(
                        name: ComponentName?,
                    ) {
                        if (
                            continuation
                                .isActive
                        ) {
                            continuation
                                .resume(null)
                        }
                    }
                }
            lastConnection = connection
            val bound =
                appContext.bindService(
                    intent,
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            if (!bound) {
                lastConnection = null
                continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                runCatching {
                    appContext
                        .unbindService(
                            connection,
                        )
                }
                if (
                    lastConnection ===
                    connection
                ) {
                    lastConnection = null
                }
            }
        }

    private fun unbindLast() {
        val connection =
            lastConnection ?: return
        lastConnection = null
        runCatching {
            appContext.unbindService(
                connection,
            )
        }
    }

    private fun certificateSha256(
        packageName: String,
    ): String? =
        runCatching {
            val info =
                packageManager
                    .getPackageInfo(
                        packageName,
                        PackageManager
                            .GET_SIGNING_CERTIFICATES,
                    )
            val signature =
                info.signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
                    ?: return@runCatching null
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    signature.toByteArray(),
                )
                .joinToString(":") {
                    "%02X".format(it)
                }
        }.getOrNull()

    private fun trustKey(
        packageName: String,
        certificate: String,
    ): String =
        packageName +
            "|" +
            certificate.lowercase()

    companion object {
        const val ACTION_PLUGIN =
            "com.yagay.ysuite.yfiles.PLUGIN"
        const val PROTOCOL_VERSION = 1
        private const val MIN_PROTOCOL = 1
        private const val MAX_PROTOCOL = 1
    }
}
