package com.yagay.ysuite.feature.ydownload

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

class YDownloadScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scheduler =
        appContext.getSystemService(
            Context.JOB_SCHEDULER_SERVICE,
        ) as JobScheduler

    fun schedule(
        downloadId: String,
        scheduledAtMillis: Long,
    ): Boolean {
        val delay =
            (scheduledAtMillis - System.currentTimeMillis())
                .coerceAtLeast(0L)
        val extras = PersistableBundle().apply {
            putString(EXTRA_DOWNLOAD_ID, downloadId)
        }
        val info =
            JobInfo.Builder(
                jobId(downloadId),
                ComponentName(
                    appContext,
                    YDownloadScheduledJobService::class.java,
                ),
            )
                .setMinimumLatency(delay)
                .setOverrideDeadline(
                    delay + DEADLINE_WINDOW_MILLIS,
                )
                .setPersisted(true)
                .setExtras(extras)
                .build()

        return scheduler.schedule(info) ==
            JobScheduler.RESULT_SUCCESS
    }

    fun cancel(downloadId: String) {
        scheduler.cancel(jobId(downloadId))
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val DEADLINE_WINDOW_MILLIS =
            15L * 60L * 1000L

        fun jobId(downloadId: String): Int =
            downloadId.hashCode() and 0x7fffffff
    }
}

class YDownloadScheduledJobService : JobService() {
    override fun onStartJob(
        params: JobParameters,
    ): Boolean {
        val id =
            params.extras.getString(
                YDownloadScheduler.EXTRA_DOWNLOAD_ID,
            )
        if (!id.isNullOrBlank()) {
            YDownloadService.start(
                applicationContext,
                id,
            )
        }
        return false
    }

    override fun onStopJob(
        params: JobParameters,
    ): Boolean = true
}
