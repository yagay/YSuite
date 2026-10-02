package com.yagay.YSuite.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.yagay.suite.api.RuntimeHandoffGate
import java.io.File

/**
 * Records proof that an embedded YSuite hook is alive inside a specific target process.
 *
 * The record is keyed by feature + target process and bound to both PID and /proc start token, so a
 * stale acknowledgement cannot survive a target-process restart or later PID reuse. The provider
 * intentionally exposes only this tiny acknowledgement protocol; no feature configuration is stored
 * here.
 */
class SuiteRuntimeHandoffProvider : ContentProvider() {
    private val prefs by lazy {
        requireNotNull(context)
            .createDeviceProtectedStorageContext()
            .getSharedPreferences("runtime_handoff", 0)
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val feature = extras?.getString(RuntimeHandoffGate.EXTRA_FEATURE).orEmpty()
        val target = extras?.getString(RuntimeHandoffGate.EXTRA_TARGET).orEmpty()
        if (feature.isBlank() || target.isBlank() || !isAllowedCaller(feature, target)) {
            return Bundle().apply { putBoolean(RuntimeHandoffGate.RESULT_ACTIVE, false) }
        }

        val callerPid = Binder.getCallingPid()
        val claimedPid = extras?.getInt("pid", 0) ?: 0
        if (callerPid <= 0 || claimedPid != callerPid) {
            return Bundle().apply { putBoolean(RuntimeHandoffGate.RESULT_ACTIVE, false) }
        }
        val startToken = processStartToken(callerPid)
        if (startToken.isBlank()) {
            return Bundle().apply { putBoolean(RuntimeHandoffGate.RESULT_ACTIVE, false) }
        }

        val prefix = keyPrefix(feature, target)
        return when (method) {
            RuntimeHandoffGate.METHOD_MARK_ACTIVE -> {
                prefs.edit()
                    .putInt("${prefix}pid", callerPid)
                    .putString("${prefix}start", startToken)
                    .putLong("${prefix}marked_at", System.currentTimeMillis())
                    .apply()
                Bundle().apply { putBoolean(RuntimeHandoffGate.RESULT_ACTIVE, true) }
            }
            RuntimeHandoffGate.METHOD_IS_ACTIVE -> {
                val active = prefs.getInt("${prefix}pid", -1) == callerPid &&
                    prefs.getString("${prefix}start", null) == startToken
                Bundle().apply { putBoolean(RuntimeHandoffGate.RESULT_ACTIVE, active) }
            }
            else -> super.call(method, arg, extras) ?: Bundle()
        }
    }

    private fun isAllowedCaller(feature: String, target: String): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.SYSTEM_UID || uid == 1027) return true
        val packages = context?.packageManager?.getPackagesForUid(uid)?.toSet().orEmpty()
        return when (feature) {
            "ynfc" -> target == "com.android.nfc" && "com.android.nfc" in packages
            "yfloat" -> target.startsWith("com.google.android.googlequicksearchbox") &&
                "com.google.android.googlequicksearchbox" in packages
            else -> false
        }
    }

    private fun keyPrefix(feature: String, target: String): String =
        feature.replace(Regex("[^a-z0-9_]"), "_") + "." +
            target.replace(Regex("[^A-Za-z0-9_.:-]"), "_") + "."

    private fun processStartToken(pid: Int): String {
        return runCatching {
            // /proc/<pid>/stat field 22 is process start time. The process name is enclosed in
            // parentheses and can contain spaces, so split only after the final ')'.
            val stat = File("/proc/$pid/stat").readText()
            val end = stat.lastIndexOf(')')
            if (end < 0 || end + 2 >= stat.length) return@runCatching ""
            val fields = stat.substring(end + 2).trim().split(Regex("\\s+"))
            // After removing pid/comm, field 3 ('state') is index 0; field 22 is index 19.
            fields.getOrNull(19).orEmpty()
        }.getOrDefault("")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
