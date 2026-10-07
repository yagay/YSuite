package com.yagay.ysuite.feature.ynfc.runtime

import android.content.Context
import android.os.Bundle
import com.yagay.YNFC.CardModel
import com.yagay.YNFC.ConfigProvider

data class YNfcCard(
    val name: String,
    val uid: String,
    val sak: String,
    val atqa: String,
)

object YNfcRuntimeBridge {
    @JvmStatic
    fun queryState(context: Context): Map<String, String> {
        val result = linkedMapOf<String, String>()
        runCatching {
            context.contentResolver.query(
                ConfigProvider.uri(context),
                null,
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result[cursor.getString(0)] =
                        cursor.getString(1)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun publish(
        context: Context,
        enabled: Boolean,
        card: YNfcCard?,
    ): Long =
        context.contentResolver.call(
            ConfigProvider.uri(context),
            ConfigProvider.METHOD_PUBLISH_COMMAND,
            null,
            Bundle().apply {
                putBoolean(
                    ConfigProvider.EXTRA_ENABLED,
                    enabled,
                )
                card?.let {
                    putString(
                        ConfigProvider.EXTRA_UID,
                        it.uid,
                    )
                    putString(
                        ConfigProvider.EXTRA_SAK,
                        it.sak,
                    )
                    putString(
                        ConfigProvider.EXTRA_ATQA,
                        it.atqa,
                    )
                }
            },
        )?.getLong(
            ConfigProvider.RESULT_GENERATION,
            0L,
        ) ?: 0L

    @JvmStatic
    fun confirmStockRestart(
        context: Context,
        generation: Long,
        pid: Int,
    ) {
        context.contentResolver.call(
            ConfigProvider.uri(context),
            ConfigProvider.METHOD_CONFIRM_STOCK_RESTART,
            null,
            Bundle().apply {
                putLong(
                    ConfigProvider.EXTRA_GENERATION,
                    generation,
                )
                putInt(
                    ConfigProvider.EXTRA_PID,
                    pid,
                )
            },
        )
    }

    @JvmStatic
    fun toLegacy(card: YNfcCard): CardModel =
        CardModel(
            card.name,
            card.uid,
            card.sak,
            card.atqa,
        )

    const val KEY_HOOK_BUILD = ConfigProvider.KEY_HOOK_BUILD
    const val KEY_HOOK_INSTALLED = ConfigProvider.KEY_HOOK_INSTALLED
    const val KEY_HOOK_PID = ConfigProvider.KEY_HOOK_PID
    const val KEY_SCOPE_OK = ConfigProvider.KEY_SCOPE_OK
    const val KEY_SIMULATION_ENABLED = ConfigProvider.KEY_SIMULATION_ENABLED
    const val KEY_UID = ConfigProvider.KEY_UID
    const val KEY_COMMAND_GENERATION = ConfigProvider.KEY_COMMAND_GENERATION
    const val KEY_COMMAND_HANDLED_GENERATION = ConfigProvider.KEY_COMMAND_HANDLED_GENERATION
    const val KEY_COMMAND_STATUS = ConfigProvider.KEY_COMMAND_STATUS
    const val KEY_COMMAND_PID = ConfigProvider.KEY_COMMAND_PID
    const val KEY_COMMAND_CONSUMED_GENERATION = "command_consumed_generation"
    const val KEY_OPERATION_STATE = ConfigProvider.KEY_OPERATION_STATE
    const val KEY_EFFECTIVE_STATE = ConfigProvider.KEY_EFFECTIVE_STATE
    const val KEY_VERIFICATION_CONFIDENCE = ConfigProvider.KEY_VERIFICATION_CONFIDENCE
    const val KEY_RF_ACCEPTED = ConfigProvider.KEY_RF_ACCEPTED
    const val KEY_RF_STATUS = ConfigProvider.KEY_RF_STATUS
    const val KEY_RF_UID = ConfigProvider.KEY_RF_UID
    const val KEY_RF_ERROR = ConfigProvider.KEY_RF_ERROR
    const val KEY_RF_PID = ConfigProvider.KEY_RF_PID
    const val KEY_RF_GENERATION = ConfigProvider.KEY_RF_GENERATION
    const val KEY_CONTROLLER_EPOCH = ConfigProvider.KEY_CONTROLLER_EPOCH
    const val KEY_RF_CONTROLLER_EPOCH = ConfigProvider.KEY_RF_CONTROLLER_EPOCH
    const val KEY_REFRESH_TRIGGER_STATUS = "refresh_trigger_status"
    const val KEY_PROFILE_STATUS = "profile_status"
    const val KEY_FULL_DIAG_STAGE = "full_diag_stage"
}
