package com.yagay.ysuite.feature.ynfc

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.yagay.YNFC.CardModel
import com.yagay.YNFC.ConfigProvider
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

data class YNfcRuntimeSnapshot(
    val currentPid: Int = 0,
    val hookBuild: Int = 0,
    val hookInstalled: Boolean = false,
    val scopeOk: Boolean = false,
    val simulationEnabled: Boolean = false,
    val selectedUid: String? = null,
    val commandGeneration: Long = 0L,
    val handledGeneration: Long = Long.MIN_VALUE,
    val commandStatus: String = "IDLE",
    val effectiveState: String = "UNKNOWN",
    val verification: String = "NONE",
    val rfAccepted: Boolean = false,
    val rfStatus: String = "IDLE",
    val rfUid: String? = null,
    val rfError: String? = null,
)

internal class YNfcRepository(
    private val context: Context,
    private val root: RootGateway,
    private val hooks: HookGateway,
) {
    private val cardPrefs = context.getSharedPreferences("ysuite_ynfc_cards", Context.MODE_PRIVATE)

    fun cards(): List<CardModel> {
        val raw = cardPrefs.getString("cards", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(CardModel(o.optString("name"), o.optString("uid"), o.optString("sak", "08"), o.optString("atqa", "0400")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveCards(cards: List<CardModel>) {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(JSONObject().apply {
                put("name", card.name)
                put("uid", card.uid)
                put("sak", card.sak)
                put("atqa", card.atqa)
            })
        }
        check(cardPrefs.edit().putString("cards", array.toString()).commit())
    }

    suspend fun rootStatus(): CapabilityStatus = root.status()
    suspend fun hookStatus(): CapabilityStatus = hooks.status()

    suspend fun runtime(): YNfcRuntimeSnapshot {
        val pid = execute("pidof com.android.nfc 2>/dev/null | awk '{print \$1}'", 5_000L)
            .stdout.trim().lineSequence().firstOrNull()?.toIntOrNull() ?: 0
        val map = linkedMapOf<String, String>()
        runCatching {
            context.contentResolver.query(ConfigProvider.URI, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) map[cursor.getString(0)] = cursor.getString(1)
            }
        }
        return YNfcRuntimeSnapshot(
            currentPid = pid,
            hookBuild = map[ConfigProvider.KEY_HOOK_BUILD]?.toIntOrNull() ?: 0,
            hookInstalled = map[ConfigProvider.KEY_HOOK_INSTALLED].toBoolean() &&
                map[ConfigProvider.KEY_HOOK_PID]?.toIntOrNull() == pid,
            scopeOk = map[ConfigProvider.KEY_SCOPE_OK].toBoolean(),
            simulationEnabled = map[ConfigProvider.KEY_SIMULATION_ENABLED].toBoolean(),
            selectedUid = map[ConfigProvider.KEY_UID]?.takeIf { it.isNotBlank() },
            commandGeneration = map[ConfigProvider.KEY_COMMAND_GENERATION]?.toLongOrNull() ?: 0L,
            handledGeneration = map[ConfigProvider.KEY_COMMAND_HANDLED_GENERATION]?.toLongOrNull() ?: Long.MIN_VALUE,
            commandStatus = map[ConfigProvider.KEY_COMMAND_STATUS] ?: "IDLE",
            effectiveState = map[ConfigProvider.KEY_EFFECTIVE_STATE] ?: "UNKNOWN",
            verification = map[ConfigProvider.KEY_VERIFICATION_CONFIDENCE] ?: "NONE",
            rfAccepted = map[ConfigProvider.KEY_RF_ACCEPTED].toBoolean(),
            rfStatus = map[ConfigProvider.KEY_RF_STATUS] ?: "IDLE",
            rfUid = map[ConfigProvider.KEY_RF_UID]?.takeIf { it.isNotBlank() },
            rfError = map[ConfigProvider.KEY_RF_ERROR]?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun apply(card: CardModel): Pair<YNfcRuntimeSnapshot, String> {
        val scope = hooks.reload(setOf("com.android.nfc"))
        if (scope is Outcome.Failure) return runtime() to "scope_failed"
        val generation = publish(true, card)
        restartNfc("apply:" + generation)
        val state = waitFor(generation, 12_000L)
        val ok = state.commandStatus == "SUCCESS" &&
            state.handledGeneration == generation &&
            state.effectiveState == "ACTIVE" &&
            state.verification == "VERIFIED" &&
            state.rfAccepted &&
            state.rfUid.equals(card.uid, true)
        return state to when {
            ok -> "apply_success"
            !state.hookInstalled -> "hook_not_ready"
            state.commandStatus == "FAILED" -> "apply_failed"
            else -> "waiting_rf"
        }
    }

    suspend fun stop(): Pair<YNfcRuntimeSnapshot, String> {
        val generation = publish(false, null)
        restartNfc("stop:" + generation)
        val pid = execute("pidof com.android.nfc 2>/dev/null | awk '{print \$1}'", 5_000L)
            .stdout.trim().lineSequence().firstOrNull()?.toIntOrNull() ?: 0
        context.contentResolver.call(
            ConfigProvider.URI,
            ConfigProvider.METHOD_CONFIRM_STOCK_RESTART,
            null,
            Bundle().apply {
                putLong(ConfigProvider.EXTRA_GENERATION, generation)
                putInt(ConfigProvider.EXTRA_PID, pid)
            },
        )
        val state = waitFor(generation, 4_000L)
        val ok = state.commandStatus == "SUCCESS" &&
            state.effectiveState == "STOCK" &&
            state.verification == "VERIFIED"
        return state to if (ok) "stop_success" else "stop_unconfirmed"
    }

    suspend fun diagnostics(): String {
        val runtime = runtime()
        val rootStatus = root.status()
        val hookStatus = hooks.status()
        val details = if (rootStatus == CapabilityStatus.Available) {
            val result = execute(
                "echo '--- NFC PID ---'; pidof com.android.nfc; " +
                    "echo '--- NFC STATE ---'; dumpsys nfc 2>/dev/null | head -n 250; " +
                    "echo '--- NFC LOGS ---'; logcat -d -v threadtime -t 800 2>/dev/null | " +
                    "grep -E 'NfcUIDSim|NfcService|stnfc|libnfc|NFC' | tail -n 500",
                15_000L,
            )
            result.stdout + if (result.stderr.isBlank()) "" else "\n[stderr]\n" + result.stderr
        } else {
            "Root unavailable"
        }
        return buildString {
            appendLine("Root=" + rootStatus + " Hook=" + hookStatus)
            appendLine("PID=" + runtime.currentPid + " HookBuild=" + runtime.hookBuild + "/40 installed=" + runtime.hookInstalled)
            appendLine("scope=" + runtime.scopeOk + " simulation=" + runtime.simulationEnabled + " uid=" + runtime.selectedUid)
            appendLine("command=" + runtime.commandStatus + " generation=" + runtime.commandGeneration)
            appendLine("effective=" + runtime.effectiveState + " verification=" + runtime.verification)
            appendLine("rf=" + runtime.rfStatus + " accepted=" + runtime.rfAccepted + " uid=" + runtime.rfUid)
            runtime.rfError?.let { appendLine("rfError=" + it) }
            appendLine()
            append(details)
        }
    }

    fun export(text: String): String {
        val resolver = context.contentResolver
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "YNFC-" + System.currentTimeMillis() + ".txt")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YSuite")
                },
            ),
        )
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: error("Unable to export diagnostics")
        return uri.toString()
    }

    private fun publish(enabled: Boolean, card: CardModel?): Long =
        context.contentResolver.call(
            ConfigProvider.URI,
            ConfigProvider.METHOD_PUBLISH_COMMAND,
            null,
            Bundle().apply {
                putBoolean(ConfigProvider.EXTRA_ENABLED, enabled)
                card?.let {
                    putString(ConfigProvider.EXTRA_UID, it.uid)
                    putString(ConfigProvider.EXTRA_SAK, it.sak)
                    putString(ConfigProvider.EXTRA_ATQA, it.atqa)
                }
            },
        )?.getLong(ConfigProvider.RESULT_GENERATION, 0L) ?: 0L

    private suspend fun waitFor(generation: Long, timeout: Long): YNfcRuntimeSnapshot {
        val end = System.currentTimeMillis() + timeout
        var last = runtime()
        while (System.currentTimeMillis() < end) {
            last = runtime()
            if (last.commandGeneration == generation &&
                last.handledGeneration == generation &&
                last.commandStatus in setOf("SUCCESS", "FAILED")) return last
            delay(150L)
        }
        return last
    }

    private suspend fun restartNfc(reason: String) {
        execute(
            "old=$(pidof com.android.nfc 2>/dev/null | awk '{print \$1}'); " +
                "echo reason=" + reason + "; " +
                "if [ -n \"$old\" ]; then kill -TERM \"$old\" 2>/dev/null || true; sleep 0.5; " +
                "kill -0 \"$old\" 2>/dev/null && kill -KILL \"$old\" 2>/dev/null || true; fi; " +
                "i=0; while [ \$i -lt 60 ]; do new=$(pidof com.android.nfc 2>/dev/null | awk '{print \$1}'); " +
                "if [ -n \"$new\" ] && [ \"$new\" != \"$old\" ]; then break; fi; " +
                "sleep 0.2; i=\$((i+1)); done; svc nfc enable 2>/dev/null || true",
            30_000L,
        )
    }

    private suspend fun execute(command: String, timeout: Long) =
        when (val result = root.execute(RootRequest(command = command, timeoutMillis = timeout))) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> error(result.error.message)
        }
}
