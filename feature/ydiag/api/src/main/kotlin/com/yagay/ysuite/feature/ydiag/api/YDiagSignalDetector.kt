package com.yagay.ysuite.feature.ydiag.api

/** Recognize the legacy YDiag crash, Binder, WebView, SELinux and Hook signals. */
data class YDiagIssueSignal(
    val category: String,
    val severity: YDiagSeverity,
    val title: String,
    val evidence: String,
)

object YDiagSignalDetector {
    fun detect(line: String): YDiagIssueSignal? {
        val lower = line.lowercase()
        val rule = when {
            "fatal exception" in lower -> Triple("crash", YDiagSeverity.Fatal, "Fatal exception")
            "fatal signal 11" in lower || "sigsegv" in lower ->
                Triple("native-segv", YDiagSeverity.Fatal, "Native SIGSEGV")
            "fatal signal 6" in lower || "sigabrt" in lower ->
                Triple("native-abrt", YDiagSeverity.Fatal, "Native SIGABRT")
            "anr in " in lower || "not responding" in lower ->
                Triple("anr", YDiagSeverity.Error, "Application not responding")
            "outofmemoryerror" in lower ->
                Triple("memory", YDiagSeverity.Fatal, "Out of memory")
            "transactiontoolargeexception" in lower ->
                Triple("binder-size", YDiagSeverity.Error, "Binder transaction too large")
            "deadobjectexception" in lower ->
                Triple("binder-dead", YDiagSeverity.Error, "Binder remote object died")
            "securityexception" in lower ->
                Triple("permission", YDiagSeverity.Error, "SecurityException")
            "avc: denied" in lower ->
                Triple("selinux", YDiagSeverity.Warning, "SELinux denial")
            "renderer process" in lower && ("gone" in lower || "crash" in lower) ->
                Triple("webview", YDiagSeverity.Error, "WebView renderer died")
            "nosuchmethod" in lower || "no such method" in lower ->
                Triple("hook-method", YDiagSeverity.Error, "Missing method / incompatible Hook")
            "classnotfoundexception" in lower ->
                Triple("hook-class", YDiagSeverity.Error, "Missing class / incompatible Hook")
            "ydiag.hook" in lower && ("hook_failed" in lower || "error=" in lower) ->
                Triple("hook", YDiagSeverity.Error, "YDiag Hook failure")
            else -> null
        } ?: return null
        return YDiagIssueSignal(rule.first, rule.second, rule.third, line.take(2000))
    }

    fun find(lines: Sequence<String>, limit: Int = 64): List<YDiagIssueSignal> {
        val found = linkedMapOf<String, YDiagIssueSignal>()
        for (line in lines) {
            val issue = detect(line) ?: continue
            found.putIfAbsent(issue.category, issue)
            if (found.size >= limit.coerceAtLeast(1)) break
        }
        return found.values.toList()
    }
}
