package com.explorer.fileexplorer.core.data

import com.explorer.fileexplorer.core.model.RepositoryError
import java.util.ArrayDeque

data class DiagnosticEntry(
    val error: RepositoryError,
    val context: String?,
    val timestampMillis: Long,
)

class DiagnosticLog {
    private val entries =
        ArrayDeque<DiagnosticEntry>()

    @Synchronized
    fun log(
        error: RepositoryError,
        context: String? = null,
    ) {
        if (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(
            DiagnosticEntry(
                error = error,
                context = context,
                timestampMillis =
                    System.currentTimeMillis(),
            ),
        )
    }

    @Synchronized
    fun snapshot():
        List<DiagnosticEntry> =
        entries.toList()

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
