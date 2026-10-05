package com.yagay.ysuite.feature.yfiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class YFilesAutomationSettings(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                "yfiles_automation",
                Context.MODE_PRIVATE,
            )

    fun enabled(): Boolean =
        prefs.getBoolean(
            KEY_ENABLED,
            false,
        )

    fun setEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(
                KEY_ENABLED,
                enabled,
            )
            .apply()
    }

    companion object {
        private const val KEY_ENABLED =
            "enabled"
    }
}

class YFilesAutomationReceiver :
    BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val appContext =
            context.applicationContext
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO,
        ).launch {
            try {
                if (
                    !YFilesAutomationSettings(
                        appContext,
                    ).enabled()
                ) {
                    sendResult(
                        appContext,
                        intent,
                        success = false,
                        message =
                            "YFiles automation is disabled",
                    )
                    return@launch
                }
                val environment =
                    YFilesEnvironmentFactory
                        .createWithoutRoot(
                            appContext,
                        )
                when (intent.action) {
                    ACTION_COPY,
                    ACTION_MOVE,
                    ACTION_NETWORK_UPLOAD -> {
                        val sources =
                            decodeRefs(
                                intent.getStringExtra(
                                    EXTRA_SOURCES,
                                ),
                            )
                        val destination =
                            decodeRef(
                                intent.getStringExtra(
                                    EXTRA_DESTINATION,
                                ),
                            )
                        require(
                            sources.isNotEmpty()
                        ) {
                            "No sources supplied"
                        }
                        requireNotNull(destination) {
                            "No destination supplied"
                        }
                        val taskId =
                            environment.transfers
                                .enqueue(
                                    sources =
                                        sources,
                                    destination =
                                        destination,
                                    move =
                                        intent.action ==
                                            ACTION_MOVE,
                                )
                        sendResult(
                            appContext,
                            intent,
                            success = true,
                            message =
                                "Transfer queued",
                            taskId = taskId,
                        )
                    }
                    ACTION_ARCHIVE -> {
                        val sources =
                            decodeRefs(
                                intent.getStringExtra(
                                    EXTRA_SOURCES,
                                ),
                            )
                        val destination =
                            decodeRef(
                                intent.getStringExtra(
                                    EXTRA_DESTINATION,
                                ),
                            )
                        val name =
                            intent.getStringExtra(
                                EXTRA_NAME,
                            ).orEmpty()
                                .ifBlank {
                                    "archive.zip"
                                }
                        requireNotNull(destination) {
                            "No destination supplied"
                        }
                        val result =
                            environment.tools
                                .createZip(
                                    sources =
                                        sources,
                                    destination =
                                        destination,
                                    archiveName =
                                        name,
                                )
                        when (result) {
                            is com.yagay.ysuite
                                .common.Outcome
                                .Success ->
                                sendResult(
                                    appContext,
                                    intent,
                                    success = true,
                                    message =
                                        "Archive created",
                                )
                            is com.yagay.ysuite
                                .common.Outcome
                                .Failure ->
                                sendResult(
                                    appContext,
                                    intent,
                                    success = false,
                                    message =
                                        result.message,
                                )
                        }
                    }
                    else ->
                        sendResult(
                            appContext,
                            intent,
                            success = false,
                            message =
                                "Unsupported YFiles automation action",
                        )
                }
            } catch (error: Throwable) {
                sendResult(
                    appContext,
                    intent,
                    success = false,
                    message =
                        error.message
                            ?: "YFiles automation failed",
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun sendResult(
        context: Context,
        request: Intent,
        success: Boolean,
        message: String,
        taskId: String? = null,
    ) {
        val callback =
            request.getStringExtra(
                EXTRA_CALLBACK_ACTION,
            )?.takeIf {
                it.isNotBlank()
            } ?: ACTION_RESULT
        val result =
            Intent(callback)
                .setPackage(
                    request.getStringExtra(
                        EXTRA_CALLBACK_PACKAGE,
                    ),
                )
                .putExtra(
                    EXTRA_REQUEST_ID,
                    request.getStringExtra(
                        EXTRA_REQUEST_ID,
                    ),
                )
                .putExtra(
                    EXTRA_SUCCESS,
                    success,
                )
                .putExtra(
                    EXTRA_MESSAGE,
                    message,
                )
        taskId?.let {
            result.putExtra(
                EXTRA_TASK_ID,
                it,
            )
        }
        context.sendBroadcast(result)
    }

    private fun decodeRef(
        raw: String?,
    ): YFileRef? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val json = JSONObject(raw)
        return YFileRef(
            providerId =
                json.getString(
                    "provider",
                ),
            path =
                json.getString("path"),
        )
    }

    private fun decodeRefs(
        raw: String?,
    ): List<YFileRef> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        val array = JSONArray(raw)
        return buildList {
            for (
                index in 0 until
                    array.length()
            ) {
                val item =
                    array.getJSONObject(index)
                add(
                    YFileRef(
                        providerId =
                            item.getString(
                                "provider",
                            ),
                        path =
                            item.getString(
                                "path",
                            ),
                    ),
                )
            }
        }
    }

    companion object {
        const val ACTION_COPY =
            "com.yagay.ysuite.yfiles.COPY"
        const val ACTION_MOVE =
            "com.yagay.ysuite.yfiles.MOVE"
        const val ACTION_ARCHIVE =
            "com.yagay.ysuite.yfiles.ARCHIVE"
        const val ACTION_NETWORK_UPLOAD =
            "com.yagay.ysuite.yfiles.NETWORK_UPLOAD"
        const val ACTION_RESULT =
            "com.yagay.ysuite.yfiles.RESULT"

        const val EXTRA_SOURCES =
            "sources_json"
        const val EXTRA_DESTINATION =
            "destination_json"
        const val EXTRA_NAME = "name"
        const val EXTRA_REQUEST_ID =
            "request_id"
        const val EXTRA_CALLBACK_ACTION =
            "callback_action"
        const val EXTRA_CALLBACK_PACKAGE =
            "callback_package"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TASK_ID = "task_id"
    }
}
