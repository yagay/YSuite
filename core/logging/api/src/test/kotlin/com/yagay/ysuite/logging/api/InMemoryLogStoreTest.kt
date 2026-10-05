package com.yagay.ysuite.logging.api

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryLogStoreTest {
    @Test
    fun storeKeepsOnlyLatestRecordsWithinCapacity() {
        val store = InMemoryLogStore(capacity = 2)

        store.write(record("one"))
        store.write(record("two"))
        store.write(record("three"))

        assertEquals(
            listOf("two", "three"),
            store.snapshot.value.map(LogRecord::message),
        )
    }

    @Test
    fun queryFiltersBySourceLevelAndText() {
        val store = InMemoryLogStore()

        store.write(
            record("network ready"),
        )
        store.write(
            LogRecord(
                timestampMillis = 2L,
                level = LogLevel.Error,
                tag = "system",
                message = "network failed",
                source = LogSource.Logcat,
            ),
        )

        val result =
            store.query(
                LogQuery(
                    text = "failed",
                    levels =
                        setOf(LogLevel.Error),
                    sources =
                        setOf(LogSource.Logcat),
                ),
            )

        assertEquals(
            listOf("network failed"),
            result.map(LogRecord::message),
        )
    }

    private fun record(message: String) =
        LogRecord(
            timestampMillis = 1L,
            level = LogLevel.Debug,
            tag = "test",
            message = message,
        )
}
