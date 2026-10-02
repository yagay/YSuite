package com.yagay.ydiag.diagnostics

import com.yagay.ydiag.model.Issue
import com.yagay.ydiag.model.Severity
import java.util.concurrent.atomic.AtomicLong

data class Detection(val issue: Issue, val title: String)

object IssueDetector {
    private val counter = AtomicLong()

    fun detect(line: String, timestamp: Long, relatedEventId: String?): Detection? {
        val lower = line.lowercase()
        val spec = when {
            "fatal exception" in lower -> Triple(Severity.FATAL, "crash", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_d807863f118b))
            "fatal signal 11" in lower || "sigsegv" in lower -> Triple(Severity.FATAL, "native", "Native SIGSEGV")
            "fatal signal 6" in lower || "sigabrt" in lower -> Triple(Severity.FATAL, "native", "Native SIGABRT")
            "anr in " in lower -> Triple(Severity.ERROR, "anr", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_20d043082048))
            "outofmemoryerror" in lower -> Triple(Severity.FATAL, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_d7d812116480))
            "transactiontoolargeexception" in lower -> Triple(Severity.ERROR, "binder", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_2e141aae5a13))
            "deadobjectexception" in lower -> Triple(Severity.ERROR, "binder", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_2f9d0e5b5b93))
            "securityexception" in lower -> Triple(Severity.ERROR, "permission", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_3c0018a1f3a0))
            "avc: denied" in lower -> Triple(Severity.WARNING, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_01246ded0fcd))
            "renderer process" in lower && ("gone" in lower || "crash" in lower) ->
                Triple(Severity.ERROR, "webview", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_2a022a101a28))
            "nosuchmethod" in lower || "no such method" in lower ->
                Triple(Severity.ERROR, "hook", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_a9cf84ce0ef3))
            "classnotfoundexception" in lower ->
                Triple(Severity.ERROR, "hook", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_71544cb78165))
            "ydiag.hook" in lower && ("hook_failed" in lower || "error=" in lower) ->
                Triple(Severity.ERROR, "hook", com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_cbc0f32ca62b))
            else -> null
        } ?: return null
        val issueId = "I${counter.incrementAndGet().toString().padStart(6, '0')}"
        val issue = Issue(
            id = issueId,
            timestamp = timestamp,
            severity = spec.first.name,
            category = spec.second,
            title = spec.third,
            detail = line.take(4000),
            relatedEventIds = listOfNotNull(relatedEventId),
        )
        return Detection(issue, spec.third)
    }
}
