package com.yagay.ysuite.logging.android

import com.yagay.ysuite.logging.api.LogCollector
import com.yagay.ysuite.logging.api.LogLevel
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.LogSource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLogcatCollector :
    LogCollector {
    override suspend fun collect(
        maxLines: Int,
    ): List<LogRecord> =
        withContext(Dispatchers.IO) {
            val safeLimit =
                maxLines.coerceIn(1, 2_000)
            val process =
                ProcessBuilder(
                    "logcat",
                    "-d",
                    "-v",
                    "brief",
                    "-t",
                    safeLimit.toString(),
                )
                    .redirectErrorStream(true)
                    .start()

            if (
                !process.waitFor(
                    TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            ) {
                process.destroyForcibly()
                return@withContext emptyList()
            }

            process.inputStream
                .bufferedReader()
                .useLines { lines ->
                    lines.mapNotNull(::parse)
                        .take(safeLimit)
                        .toList()
                }
        }

    private fun parse(
        line: String,
    ): LogRecord? {
        val match =
            BRIEF_PATTERN.matchEntire(line)
                ?: return line
                    .takeIf(String::isNotBlank)
                    ?.let {
                        LogRecord(
                            timestampMillis =
                                System.currentTimeMillis(),
                            level = LogLevel.Info,
                            tag = "logcat",
                            message = it,
                            source =
                                LogSource.Logcat,
                        )
                    }

        val marker = match.groupValues[1]
        return LogRecord(
            timestampMillis =
                System.currentTimeMillis(),
            level =
                when (marker) {
                    "V" -> LogLevel.Verbose
                    "D" -> LogLevel.Debug
                    "I" -> LogLevel.Info
                    "W" -> LogLevel.Warning
                    "E", "F" -> LogLevel.Error
                    else -> LogLevel.Info
                },
            tag =
                match.groupValues[2].trim(),
            message =
                match.groupValues[3],
            source = LogSource.Logcat,
        )
    }

    private companion object {
        const val TIMEOUT_MILLIS = 3_000L

        val BRIEF_PATTERN =
            Regex(
                """^([VDIWEF])/([^(]+)\(\s*\d+\):\s?(.*)$""",
            )
    }
}
