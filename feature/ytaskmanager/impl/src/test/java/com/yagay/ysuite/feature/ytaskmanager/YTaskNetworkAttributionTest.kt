package com.yagay.ysuite.feature.ytaskmanager

import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess
import org.junit.Assert.assertEquals
import org.junit.Test

class YTaskNetworkAttributionTest {
    @Test
    fun sharedUidCountersAppearOnlyOnMainProcess() {
        val remote = row(
            pid = 201,
            uid = 10234,
            packageName = "com.example.app",
            command = "com.example.app:remote",
            rx = 1234L,
        )
        val main = row(
            pid = 202,
            uid = 10234,
            packageName = "com.example.app",
            command = "com.example.app",
            rx = 1234L,
        )
        val other = row(
            pid = 203,
            uid = 10235,
            packageName = "com.example.other",
            command = "com.example.other",
            rx = 500L,
        )

        val result =
            attributeNetworkToPrimaryProcess(
                listOf(remote, main, other),
            )

        assertEquals(0L, result[0].rxBytesPerSecond)
        assertEquals(1234L, result[1].rxBytesPerSecond)
        assertEquals(500L, result[2].rxBytesPerSecond)
    }

    @Test
    fun sharedUidWithoutMainUsesOneRepresentative() {
        val first =
            row(
                pid = 301,
                uid = 11000,
                packageName = null,
                command = "native-one",
                rx = 500L,
            )
        val second =
            row(
                pid = 302,
                uid = 11000,
                packageName = null,
                command = "native-two",
                rx = 500L,
            )

        val result =
            attributeNetworkToPrimaryProcess(
                listOf(first, second),
            )

        assertEquals(500L, result[0].rxBytesPerSecond)
        assertEquals(0L, result[1].rxBytesPerSecond)
    }

    private fun row(
        pid: Int,
        uid: Int,
        packageName: String?,
        command: String,
        rx: Long,
    ) = YTaskProcess(
        pid = pid,
        ppid = 1,
        uid = uid,
        rssKb = 0L,
        cpuPercent = 0f,
        name = command,
        command = command,
        packageName = packageName,
        rxBytesPerSecond = rx,
        txBytesPerSecond = rx,
    )
}
