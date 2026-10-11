package com.yagay.YNFC.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.FeatureRootCommands
import com.yagay.suite.api.HostRootProbe
import com.yagay.suite.api.HostCommandResult

class RootShell(context: Context) {
    companion object {
        private val services = FeatureServices.of("ynfc", "YNFC")
    }

    private val appContext = context.applicationContext
    @Volatile private var lastRootToastAt: Long = 0L

    fun run(command: String, timeoutSeconds: Long = 20, maxChars: Int = 1_000_000, showToast: Boolean = true): String {
        val result = hostRun(command, timeoutSeconds)
        if (!result.success && showToast && result.code == -1 &&
            HostRootProbe.probe(services, 4L).status == HostRootProbe.Status.UNPRIVILEGED
        ) notifyRootUnavailable()
        return buildString {
            append(result.stdout.take(maxChars))
            if (FeatureRootCommands.errorText(result).isNotBlank() && length < maxChars) {
                if (isNotEmpty() && last() != '\n') append('\n')
                append(result.stderr.take(maxChars - length))
            }
            if (length < maxChars) {
                if (isNotEmpty() && last() != '\n') append('\n')
                appendLine(if (result.timedOut) "[timeout=${timeoutSeconds}s]" else "[exit=${result.code}]")
            }
        }
    }

    fun invalidateRootCache() {
        HostRootProbe.invalidate(services.featureId)
    }

    private fun hostRun(command: String, timeoutSeconds: Long): HostCommandResult =
        FeatureRootCommands.execute(services, "root-shell", command, timeoutSeconds)

    private fun notifyRootUnavailable() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRootToastAt < 3000L) return
        lastRootToastAt = now
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, com.yagay.suite.api.YLocale.text(com.yagay.YNFC.R.string.ynfc_generated_d592225d23cd), Toast.LENGTH_LONG).show()
        }
    }
}
