package com.yagay.suite.api

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional *observed* feature Hook proof, published by app-side readers of
 * existing cross-process status providers. A scoped or connected framework
 * alone is not proof. No fabricated "installed" state when a feature has not
 * reported, and no destructive active probes into system processes.
 */
object FeatureHookEvidence {
    enum class State { INSTALLED, NOT_INSTALLED, STALE, UNCONFIRMED }

    data class Report(
        val featureId: String,
        val process: String,
        val pid: Int,
        val build: Long,
        val state: State,
        val detail: String,
        val observedElapsedMs: Long,
        val validForMs: Long,
    )

    private val reports = ConcurrentHashMap<String, Report>()

    @JvmStatic
    @JvmOverloads
    fun publish(
        featureId: String,
        process: String,
        pid: Int,
        build: Long,
        installed: Boolean,
        detail: String,
        validForMs: Long = 30_000L,
    ) {
        if (featureId.isBlank()) return
        reports[featureId] = Report(
            featureId,
            process,
            pid,
            build,
            if (installed && pid > 0 && build > 0L) State.INSTALLED else State.UNCONFIRMED,
            detail.take(400),
            SystemClock.elapsedRealtime(),
            validForMs.coerceAtLeast(1L),
        )
    }

    @JvmStatic
    fun read(featureId: String): Report? {
        val saved = reports[featureId] ?: return null
        val elapsed = SystemClock.elapsedRealtime() - saved.observedElapsedMs
        return if (elapsed < 0 || elapsed > saved.validForMs) {
            // A missing/unconfirmed Hook cannot become "stale installed" simply because
            // the UI has been open for 30 seconds. Only positive evidence may expire.
            if (saved.state == State.INSTALLED) saved.copy(state = State.STALE)
            else saved.copy(state = State.UNCONFIRMED)
        } else saved
    }

    @JvmStatic
    fun invalidate(featureId: String) {
        reports.remove(featureId)
    }

    @JvmStatic
    fun diagnosticSnapshot(): String =
        if (reports.isEmpty()) "No feature process evidence sampled yet (not proof of failure)\n"
        else reports.keys.sorted().joinToString("\n", postfix = "\n") { id ->
            val report = read(id)!!
            "$id\t${report.state}\tprocess=${report.process}\tpid=${report.pid}\tbuild=${report.build}\t${report.detail}"
        }
}
