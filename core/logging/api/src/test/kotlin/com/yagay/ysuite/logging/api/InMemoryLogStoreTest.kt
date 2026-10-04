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

    private fun record(message: String) =
        LogRecord(
            timestampMillis = 1L,
            level = LogLevel.Debug,
            tag = "test",
            message = message,
        )
}
