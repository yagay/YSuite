package com.yagay.ysuite.feature.yentrycleaner.runtime

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class YEntryComponentReconcileJobService :
    JobService() {
    private val executor =
        Executors.newSingleThreadExecutor()
    @Volatile private var stopped = false

    override fun onStartJob(
        params: JobParameters,
    ): Boolean {
        stopped = false
        executor.execute {
            if (stopped) return@execute
            val reason =
                params.extras.getString(
                    EXTRA_REASON,
                    "scheduled",
                )
            runCatching {
                reconcile(
                    applicationContext,
                    reason,
                )
            }
            if (
                reason == "boot_completed" ||
                reason == "user_unlocked"
            ) {
                schedule(
                    applicationContext,
                    reason + "_settled",
                    settled = true,
                )
            }
            if (!stopped) {
                jobFinished(
                    params,
                    false,
                )
            }
        }
        return true
    }

    override fun onStopJob(
        params: JobParameters,
    ): Boolean {
        stopped = true
        return true
    }

    override fun onDestroy() {
        stopped = true
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun reconcile(
        context: Context,
        reason: String,
    ) {
        val started =
            System.currentTimeMillis()
        val prefs =
            context.getSharedPreferences(
                "ysuite_yentrycleaner",
                Context.MODE_PRIVATE,
            )
        val keys =
            prefs.getStringSet(
                "disabled_components",
                emptySet(),
            ).orEmpty()
        val currentUser =
            Process.myUid() / 100_000
        val pm =
            context.packageManager
        var missing = 0
        var mismatched = 0
        var repaired = 0
        var failed = 0
        keys.forEach { key ->
            if (stopped || Thread.currentThread().isInterrupted) return@forEach
            val parts =
                key.split(
                    '|',
                    limit = 3,
                )
            if (parts.size != 3) {
                return@forEach
            }
            val user =
                parts[0].toIntOrNull()
                    ?: return@forEach
            if (user != currentUser) {
                return@forEach
            }
            val component =
                ComponentName(
                    parts[1],
                    parts[2],
                )
            if (!exists(pm, component)) {
                missing += 1
                return@forEach
            }
            val state =
                runCatching {
                    pm.getComponentEnabledSetting(
                        component,
                    )
                }.getOrDefault(
                    PackageManager
                        .COMPONENT_ENABLED_STATE_DEFAULT,
                )
            if (
                state ==
                PackageManager
                    .COMPONENT_ENABLED_STATE_DISABLED ||
                state ==
                PackageManager
                    .COMPONENT_ENABLED_STATE_DISABLED_USER
            ) {
                return@forEach
            }
            mismatched += 1
            val flat =
                component.flattenToString()
                    .replace(
                        "'",
                        "'\\''",
                    )
            val ok =
                shell(
                    "pm disable --user " +
                        user +
                        " '" +
                        flat +
                        "'",
                )
            val observed =
                runCatching {
                    pm.getComponentEnabledSetting(
                        component,
                    )
                }.getOrDefault(-1)
            if (
                ok &&
                (
                    observed ==
                        PackageManager
                            .COMPONENT_ENABLED_STATE_DISABLED ||
                        observed ==
                        PackageManager
                            .COMPONENT_ENABLED_STATE_DISABLED_USER
                    )
            ) {
                repaired += 1
            } else {
                failed += 1
            }
        }
        context.getSharedPreferences(
            "yentry_component_reconcile",
            Context.MODE_PRIVATE,
        ).edit()
            .putString(
                "reason",
                reason,
            )
            .putInt(
                "persisted",
                keys.size,
            )
            .putInt(
                "mismatched",
                mismatched,
            )
            .putInt(
                "repaired",
                repaired,
            )
            .putInt(
                "failed",
                failed,
            )
            .putInt(
                "missing",
                missing,
            )
            .putLong(
                "started",
                started,
            )
            .putLong(
                "finished",
                System.currentTimeMillis(),
            )
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun exists(
        pm: PackageManager,
        component: ComponentName,
    ): Boolean {
        val flags =
            PackageManager
                .MATCH_DISABLED_COMPONENTS or
                PackageManager
                    .MATCH_DISABLED_UNTIL_USED_COMPONENTS
        return runCatching {
            pm.getServiceInfo(
                component,
                flags,
            )
        }.isSuccess ||
            runCatching {
                pm.getActivityInfo(
                    component,
                    flags,
                )
            }.isSuccess ||
            runCatching {
                pm.getReceiverInfo(
                    component,
                    flags,
                )
            }.isSuccess ||
            runCatching {
                pm.getProviderInfo(
                    component,
                    flags,
                )
            }.isSuccess
    }

    private fun shell(
        command: String,
    ): Boolean =
        runCatching {
            val process =
                ProcessBuilder(
                    "su",
                    "-c",
                    command,
                )
                    .redirectErrorStream(true)
                    .start()
            process.inputStream.close()
            val finished =
                process.waitFor(
                    8,
                    TimeUnit.SECONDS,
                )
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)

    companion object {
        private const val JOB_PRIMARY =
            0x59454301
        private const val JOB_SETTLED =
            0x59454302
        private const val EXTRA_REASON =
            "reason"

        fun schedule(
            context: Context,
            reason: String,
            settled: Boolean = false,
        ): Boolean {
            val scheduler =
                context.getSystemService(
                    JobScheduler::class.java,
                ) ?: return false
            val delay =
                when {
                    settled -> 20_000L
                    reason ==
                        "boot_completed" ->
                        5_000L
                    reason ==
                        "user_unlocked" ->
                        8_000L
                    reason ==
                        "manager_replaced" ->
                        2_000L
                    else -> 3_000L
                }
            val info =
                JobInfo.Builder(
                    if (settled) {
                        JOB_SETTLED
                    } else {
                        JOB_PRIMARY
                    },
                    ComponentName(
                        context,
                        YEntryComponentReconcileJobService::
                            class.java,
                    ),
                )
                    .setMinimumLatency(
                        delay,
                    )
                    .setOverrideDeadline(
                        delay +
                            15_000L,
                    )
                    .setExtras(
                        PersistableBundle().apply {
                            putString(
                                EXTRA_REASON,
                                reason,
                            )
                        },
                    )
                    .build()
            return scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS
        }
    }
}
