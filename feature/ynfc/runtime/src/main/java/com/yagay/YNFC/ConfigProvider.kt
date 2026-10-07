package com.yagay.YNFC

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class ConfigProvider : ContentProvider() {
    private val prefs by lazy {
        val base = requireNotNull(context)
        val device = base.createDeviceProtectedStorageContext()
        device.getSharedPreferences("ynfc_config", 0)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (!trusted()) return MatrixCursor(arrayOf("key", "value"))
        return MatrixCursor(arrayOf("key", "value")).apply {
            val values =
                prefs.all.toMutableMap()
            values.putIfAbsent(
                KEY_HOOK_BUILD,
                BuildConfig.HOOK_BUILD,
            )
            values.forEach { (key, value) ->
                addRow(
                    arrayOf(
                        key,
                        value?.toString().orEmpty(),
                    ),
                )
            }
        }
    }

    @Synchronized
    override fun insert(uri: Uri, values: ContentValues?): Uri {
        if (!trusted() || values == null) return uri
        val editor = prefs.edit()
        values.keySet().forEach { key ->
            when (val value = values.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                null -> editor.remove(key)
                else -> editor.putString(key, value.toString())
            }
        }
        editor.commit()
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (!trusted()) return Bundle().apply { putBoolean(RESULT_SUCCESS, false) }
        return when (method) {
            METHOD_PUBLISH_COMMAND -> {
                val enabled = extras?.getBoolean(EXTRA_ENABLED, false) == true
                val generation = maxOf(
                    prefs.getLong(KEY_COMMAND_GENERATION, 0L) + 1L,
                    System.currentTimeMillis(),
                )
                insert(currentUri(), ContentValues().apply {
                    put(KEY_SIMULATION_ENABLED, enabled)
                    put(KEY_UID, if (enabled) extras?.getString(EXTRA_UID).orEmpty() else "")
                    put(KEY_SAK, if (enabled) extras?.getString(EXTRA_SAK).orEmpty() else "")
                    put(KEY_ATQA, if (enabled) extras?.getString(EXTRA_ATQA).orEmpty() else "")
                    put(KEY_COMMAND_GENERATION, generation)
                    put(KEY_COMMAND_ACTION, if (enabled) "APPLY" else "STOP")
                    put(KEY_COMMAND_STATUS, "PENDING")
                    put(KEY_COMMAND_DETAIL, "Waiting for NFC Hook runtime")
                    put(KEY_COMMAND_PID, 0)
                    put(KEY_OPERATION_STATE, if (enabled) "APPLYING" else "STOPPING")
                    put(KEY_EFFECTIVE_STATE, "UNKNOWN")
                    put(KEY_VERIFICATION_CONFIDENCE, "PENDING")
                    put(KEY_RF_ACCEPTED, false)
                    put(KEY_RF_STATUS, if (enabled) "WAITING" else "STOPPING")
                    put(KEY_RF_UID, "")
                    put(KEY_RF_PID, 0)
                    put(KEY_RF_GENERATION, generation)
                })
                Bundle().apply {
                    putBoolean(RESULT_SUCCESS, true)
                    putLong(RESULT_GENERATION, generation)
                }
            }
            METHOD_CONFIRM_STOCK_RESTART -> {
                val generation = extras?.getLong(EXTRA_GENERATION, 0L) ?: 0L
                val pid = extras?.getInt(EXTRA_PID, 0) ?: 0
                val epoch = System.currentTimeMillis()
                val ok = generation > 0L &&
                    generation == prefs.getLong(KEY_COMMAND_GENERATION, 0L) &&
                    pid > 0
                if (ok) {
                    insert(currentUri(), ContentValues().apply {
                        put(KEY_COMMAND_HANDLED_GENERATION, generation)
                        put(KEY_COMMAND_STATUS, "SUCCESS")
                        put(KEY_COMMAND_DETAIL, "Stock RF restored by NFC process restart")
                        put(KEY_COMMAND_PID, pid)
                        put(KEY_OPERATION_STATE, "IDLE")
                        put(KEY_EFFECTIVE_STATE, "STOCK")
                        put(KEY_VERIFICATION_CONFIDENCE, "VERIFIED")
                        put(KEY_RF_ACCEPTED, true)
                        put(KEY_RF_STATUS, "RF_STOCK_RESTORED_BY_RESTART")
                        put(KEY_RF_UID, "")
                        put(KEY_RF_PID, pid)
                        put(KEY_RF_GENERATION, generation)
                        put(KEY_CONTROLLER_EPOCH, epoch)
                        put(KEY_RF_CONTROLLER_EPOCH, epoch)
                    })
                }
                Bundle().apply { putBoolean(RESULT_SUCCESS, ok) }
            }
            else -> super.call(method, arg, extras) ?: Bundle()
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        insert(uri, values)
        return 1
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (!trusted()) return 0
        prefs.edit().clear().commit()
        context?.contentResolver?.notifyChange(uri, null)
        return 1
    }
    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.ynfc.settings"

    private fun currentUri(): Uri =
        uri(requireNotNull(context))

    private fun trusted(): Boolean {
        val uid = Binder.getCallingUid()
        return uid == Process.myUid() || uid == 1027 || uid == Process.SYSTEM_UID || uid == 0
    }

    companion object {
        const val METHOD_PUBLISH_COMMAND = "publish_command"
        const val METHOD_CONFIRM_STOCK_RESTART = "confirm_stock_restart"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_UID = "uid"
        const val EXTRA_SAK = "sak"
        const val EXTRA_ATQA = "atqa"
        const val EXTRA_GENERATION = "generation"
        const val EXTRA_PID = "pid"
        const val RESULT_GENERATION = "generation"
        const val RESULT_SUCCESS = "success"

        const val KEY_HOOK_BUILD = "hook_build"
        const val KEY_SIMULATION_ENABLED = "simulation_enabled"
        const val KEY_UID = "uid"
        const val KEY_SAK = "sak"
        const val KEY_ATQA = "atqa"
        const val KEY_COMMAND_GENERATION = "command_generation"
        const val KEY_COMMAND_HANDLED_GENERATION = "command_handled_generation"
        const val KEY_COMMAND_ACTION = "command_action"
        const val KEY_COMMAND_STATUS = "command_status"
        const val KEY_COMMAND_DETAIL = "command_detail"
        const val KEY_COMMAND_PID = "command_pid"
        const val KEY_OPERATION_STATE = "operation_state"
        const val KEY_EFFECTIVE_STATE = "effective_state"
        const val KEY_VERIFICATION_CONFIDENCE = "verification_confidence"
        const val KEY_RF_ACCEPTED = "rf_accepted"
        const val KEY_RF_STATUS = "rf_status"
        const val KEY_RF_UID = "rf_uid"
        const val KEY_RF_SOURCE = "rf_source"
        const val KEY_RF_ERROR = "rf_error"
        const val KEY_RF_PID = "rf_pid"
        const val KEY_RF_GENERATION = "rf_generation"
        const val KEY_CONTROLLER_EPOCH = "controller_epoch"
        const val KEY_RF_CONTROLLER_EPOCH = "rf_controller_epoch"
        const val KEY_HOOK_INSTALLED = "hook_installed"
        const val KEY_HOOK_PID = "hook_pid"
        const val KEY_SCOPE_OK = "scope_ok"
        const val KEY_SCOPE_PID = "scope_pid"
        const val KEY_RUNTIME_PID = "runtime_pid"

        @JvmStatic
        fun uri(context: Context): Uri =
            Uri.parse(
                "content://" +
                    context.packageName +
                    ".ynfc.config/settings",
            )
    }
}
