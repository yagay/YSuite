package com.yagay.ysuite.feature.yfiles

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class YInstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val enabled: Boolean,
    val system: Boolean,
    val apkPath: String,
)

data class YApkAnalysis(
    val fileName: String,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val permissions: List<String>,
    val signingSha256: List<String>,
    val dexMethodCount: Long,
    val archiveBreakdown:
        Map<String, Long>,
)

class YFilesAppsService(
    context: Context,
    private val engine: YFilesEngine,
    private val cacheDirectory: File,
) {
    private val appContext =
        context.applicationContext
    private val packageManager =
        appContext.packageManager

    suspend fun installedApps():
        List<YInstalledApp> =
        withContext(Dispatchers.IO) {
            val flags =
                PackageManager
                    .MATCH_DISABLED_COMPONENTS or
                    PackageManager
                        .MATCH_UNINSTALLED_PACKAGES
            packageManager
                .getInstalledApplications(flags)
                .map { info ->
                    val packageInfo =
                        runCatching {
                            packageManager
                                .getPackageInfo(
                                    info.packageName,
                                    0,
                                )
                        }.getOrNull()
                    YInstalledApp(
                        packageName =
                            info.packageName,
                        label =
                            packageManager
                                .getApplicationLabel(
                                    info,
                                ).toString(),
                        versionName =
                            packageInfo
                                ?.versionName,
                        versionCode =
                            packageInfo
                                ?.longVersionCode
                                ?: 0L,
                        enabled = info.enabled,
                        system =
                            info.flags and
                                ApplicationInfo
                                    .FLAG_SYSTEM != 0,
                        apkPath =
                            info.sourceDir.orEmpty(),
                    )
                }
                .sortedWith(
                    compareBy<YInstalledApp> {
                        !it.enabled
                    }.thenBy {
                        it.label.lowercase()
                    },
                )
        }

    suspend fun analyzeApk(
        ref: YFileRef,
    ): Outcome<YApkAnalysis> =
        withContext(Dispatchers.IO) {
            try {
                val file =
                    materialize(ref)
                val flags =
                    PackageManager
                        .GET_PERMISSIONS or
                        PackageManager
                            .GET_SIGNING_CERTIFICATES
                val info =
                    packageManager
                        .getPackageArchiveInfo(
                            file.absolutePath,
                            flags,
                        )
                val signatures =
                    if (
                        Build.VERSION.SDK_INT >= 28
                    ) {
                        info?.signingInfo
                            ?.apkContentsSigners
                            ?.map {
                                signature ->
                                MessageDigest
                                    .getInstance(
                                        "SHA-256",
                                    )
                                    .digest(
                                        signature
                                            .toByteArray(),
                                    )
                                    .joinToString(
                                        ":",
                                    ) {
                                        "%02X"
                                            .format(it)
                                    }
                            }.orEmpty()
                    } else {
                        emptyList()
                    }

                var dexMethods = 0L
                val breakdown =
                    linkedMapOf<String, Long>()
                ZipFile(file).use { zip ->
                    val entries =
                        zip.entries()
                    while (
                        entries.hasMoreElements()
                    ) {
                        val entry =
                            entries.nextElement()
                        if (entry.isDirectory) {
                            continue
                        }
                        val group =
                            when {
                                entry.name
                                    .endsWith(
                                        ".dex",
                                        true,
                                    ) -> "DEX"
                                entry.name
                                    .startsWith(
                                        "lib/",
                                    ) -> "Native"
                                entry.name
                                    .startsWith(
                                        "res/",
                                    ) -> "Resources"
                                entry.name ==
                                    "AndroidManifest.xml" ->
                                    "Manifest"
                                entry.name
                                    .startsWith(
                                        "assets/",
                                    ) -> "Assets"
                                else -> "Other"
                            }
                        breakdown[group] =
                            (
                                breakdown[group]
                                    ?: 0L
                                ) +
                                entry.size
                                    .coerceAtLeast(
                                        0L,
                                    )
                        if (
                            entry.name.matches(
                                Regex(
                                    "classes(\\d*)\\.dex",
                                ),
                            )
                        ) {
                            zip.getInputStream(
                                entry,
                            ).use {
                                dexMethods +=
                                    dexMethodCount(
                                        it.readNBytes(
                                            DEX_HEADER_BYTES,
                                        ),
                                    )
                            }
                        }
                    }
                }
                val appInfo =
                    info?.applicationInfo
                Outcome.Success(
                    YApkAnalysis(
                        fileName = file.name,
                        packageName =
                            info?.packageName,
                        versionName =
                            info?.versionName,
                        versionCode =
                            info?.longVersionCode,
                        minSdk =
                            if (
                                Build.VERSION.SDK_INT >=
                                24
                            ) {
                                appInfo?.minSdkVersion
                            } else {
                                null
                            },
                        targetSdk =
                            appInfo?.targetSdkVersion,
                        permissions =
                            info?.requestedPermissions
                                ?.toList()
                                .orEmpty()
                                .sorted(),
                        signingSha256 =
                            signatures.sorted(),
                        dexMethodCount =
                            dexMethods,
                        archiveBreakdown =
                            breakdown,
                    ),
                )
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "apk_analysis_failed",
                    message =
                        error.message
                            ?: "Unable to analyze APK",
                    cause = error,
                )
            }
        }

    private suspend fun materialize(
        ref: YFileRef,
    ): File {
        if (ref.providerId == "local") {
            return File(ref.path)
        }
        require(
            cacheDirectory.mkdirs() ||
                cacheDirectory.isDirectory,
        )
        val target =
            File(
                cacheDirectory,
                "apk-" +
                    ref.path.hashCode()
                        .toUInt()
                        .toString(16) +
                    ".apk",
            )
        FileOutputStream(target)
            .use { output ->
                var offset = 0L
                while (true) {
                    when (
                        val chunk =
                            engine.read(
                                ref,
                                offset,
                                128 * 1024,
                            )
                    ) {
                        is Outcome.Success -> {
                            output.write(
                                chunk.value.data,
                            )
                            offset +=
                                chunk.value
                                    .data.size
                            if (
                                chunk.value.eof
                            ) {
                                break
                            }
                        }
                        is Outcome.Failure ->
                            error(
                                chunk.message,
                            )
                    }
                }
            }
        return target
    }

    private fun dexMethodCount(
        header: ByteArray,
    ): Long {
        if (
            header.size <
            DEX_METHOD_IDS_SIZE_OFFSET + 4
        ) {
            return 0L
        }
        if (
            !header.copyOfRange(0, 4)
                .contentEquals(
                    byteArrayOf(
                        'd'.code.toByte(),
                        'e'.code.toByte(),
                        'x'.code.toByte(),
                        '\n'.code.toByte(),
                    ),
                )
        ) {
            return 0L
        }
        return ByteBuffer.wrap(
            header,
            DEX_METHOD_IDS_SIZE_OFFSET,
            4,
        )
            .order(
                ByteOrder.LITTLE_ENDIAN,
            )
            .int
            .toLong()
            .and(0xffffffffL)
    }

    companion object {
        private const val DEX_METHOD_IDS_SIZE_OFFSET =
            88
        private const val DEX_HEADER_BYTES =
            112
    }
}
