package com.yagay.YNFC

/** Pure formatting and bounded-list helpers shared by the UI and diagnostics. */
internal object RuntimeText {
    fun statusSummary(status: RuntimeStatus, expectedHookBuild: Int, readModeEnabled: Boolean): String = buildString {
        appendLine("=== CURRENT STATUS ===")
        appendLine("BUILD: app=${status.appBuild} hook=${status.hookBuild} expectedHook=$expectedHookBuild")
        appendLine("PID: current=${status.currentPid} runtime=${status.runtimePid} scope=${status.scopePid} hook=${status.hookPid} command=${status.commandPid} rf=${status.rfPid}")
        appendLine("SCOPE: ${if (status.scopeOk) "SUCCESS" else "NOT DETECTED/STALE"}")
        appendLine("HOOK: ${if (status.hookInstalled) "SUCCESS" else "NOT INSTALLED/STALE"}")
        appendLine("DIAGNOSIS: ${hookDiagnosis(status, expectedHookBuild)}")
        appendLine("CONFIG: ${if (status.simulationEnabled) "ENABLED" else "IDLE"} uid=${status.selectedUid}")
        appendLine("SEMANTIC: operation=${status.operationState} effective=${status.effectiveState} confidence=${status.verificationConfidence} accepted=${status.rfAccepted}")
        appendLine("COMMAND: generation=${status.commandGeneration} consumed=${status.consumedGeneration} completed=${status.handledGeneration} action=${status.commandAction} status=${status.commandStatus} detail=${status.commandDetail}")
        appendLine("READ_MODE: ${if (readModeEnabled) "ENABLED" else "IDLE"}")
        appendLine("NFC_POLICY: desired=${if (status.pollingDisabled) "HCE_ONLY" else "NORMAL"} status=${status.pollingGuardStatus} pid=${status.pollingGuardPid} detail=${status.pollingGuardDetail}")
        appendLine("NFC_CAPABILITIES: ${status.pollingCapabilitySummary ?: "UNKNOWN"}")
        appendLine("RF: generation=${status.rfGeneration} ${status.rfStatus} uid=${status.rfUid} source=${status.rfSource} result=${status.rfResult} raw=${status.rfNativeResult}/${status.rfNativeResultType} verification=${status.rfVerification} error=${status.rfError}")
        append("FINAL: stage=${status.fullDiagStage} summary=${status.fullDiagSummary}")
    }

    /**
     * Distinguishes an absent module callback from an old NFC process and a
     * discovered-but-not-installed hook. Root access alone cannot inject LSPosed.
     */
    fun hookDiagnosis(status: RuntimeStatus, expectedHookBuild: Int): String = when {
        status.currentPid <= 0 -> "NFC_PROCESS_NOT_FOUND: verify com.android.nfc is running."
        status.scopePid <= 0 && status.hookPid <= 0 ->
            "HOOK_REPORT_MISSING: no current NFC Hook callback was observed. Check LSPosed target loading, process-specific Hook logs and status-provider delivery; Root access does not confirm Hook installation."
        (status.scopePid > 0 && status.scopePid != status.currentPid) ||
            (status.hookPid > 0 && status.hookPid != status.currentPid) ->
            "STALE_PROCESS: the NFC process restarted; the installed hook has not reported from the current PID. Verify LSPosed scope and reboot."
        !status.scopeOk ->
            "SCOPE_NOT_CONFIRMED: check YSuite's com.android.nfc scope and current-process callback."
        !status.hookInstalled ->
            "HOOK_NOT_INSTALLED: module reached the NFC process but did not install its hooks; inspect focused YSuite/YNFC logs."
        status.hookBuild != expectedHookBuild ->
            "HOOK_BUILD_MISMATCH: installed NFC hook is older than this APK; restart com.android.nfc or reboot after updating YSuite."
        else -> "HOOK_READY"
    }

    fun boundedLines(text: String, maxLines: Int = 1800): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = text.lineSequence().toList()
        return if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
    }

    /** Applies only the changed prefix/suffix range to avoid replacing the whole Compose list. */
    fun updateWindow(target: MutableList<String>, incoming: List<String>) {
        var prefix = 0
        val commonLimit = minOf(target.size, incoming.size)
        while (prefix < commonLimit && target[prefix] == incoming[prefix]) prefix++

        var suffix = 0
        while (suffix < commonLimit - prefix &&
            target[target.size - 1 - suffix] == incoming[incoming.size - 1 - suffix]) {
            suffix++
        }

        val removeUntil = target.size - suffix
        for (i in removeUntil - 1 downTo prefix) target.removeAt(i)
        val addUntil = incoming.size - suffix
        if (prefix < addUntil) target.addAll(prefix, incoming.subList(prefix, addUntil))
    }
}
