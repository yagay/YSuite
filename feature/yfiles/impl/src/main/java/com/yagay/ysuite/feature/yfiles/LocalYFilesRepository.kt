package com.yagay.ysuite.feature.yfiles

import android.os.Environment
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileProperties
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFilesRepository
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalYFilesRepository(
    private val rootPath: String =
        Environment.getExternalStorageDirectory().absolutePath,
) : YFilesRepository {
    override fun initialPath(): String = rootPath

    override suspend fun list(
        query: YFileQuery,
    ): Outcome<List<YFileEntry>> =
        withContext(Dispatchers.IO) {
            runOutcome("list_failed") {
                val root = File(query.path)
                require(root.isDirectory) {
                    "Not a readable directory"
                }

                val text = query.text.trim().lowercase()
                val entries =
                    if (query.recursive && text.isNotBlank()) {
                        recursiveEntries(
                            root = root,
                            text = text,
                            showHidden = query.showHidden,
                            maxResults = query.maxResults,
                        )
                    } else {
                        root.listFiles()
                            ?.asSequence()
                            ?.filter {
                                query.showHidden || !it.isHidden
                            }
                            ?.filter {
                                text.isBlank() ||
                                    it.name.lowercase()
                                        .contains(text)
                            }
                            ?.map(::toEntry)
                            ?.toList()
                            ?: emptyList()
                    }

                sort(
                    entries = entries,
                    mode = query.sort,
                    descending = query.descending,
                )
            }
        }

    override suspend fun properties(
        entry: YFileEntry,
    ): Outcome<YFileProperties> =
        withContext(Dispatchers.IO) {
            runOutcome("properties_failed") {
                val file = File(entry.path)
                require(file.exists()) {
                    "File no longer exists"
                }

                YFileProperties(
                    name = file.name.ifBlank {
                        file.absolutePath
                    },
                    path = file.absolutePath,
                    directory = file.isDirectory,
                    sizeBytes = if (file.isFile) {
                        file.length()
                    } else {
                        0L
                    },
                    modifiedAtMillis = file.lastModified(),
                    readable = file.canRead(),
                    writable = file.canWrite(),
                    executable = file.canExecute(),
                    hidden = file.isHidden,
                    childCount = if (file.isDirectory) {
                        file.listFiles()?.size
                    } else {
                        null
                    },
                )
            }
        }

    override fun parent(path: String): String? =
        File(path).parentFile?.absolutePath

    private fun recursiveEntries(
        root: File,
        text: String,
        showHidden: Boolean,
        maxResults: Int,
    ): List<YFileEntry> {
        val queue = ArrayDeque<File>()
        val result = mutableListOf<YFileEntry>()
        queue.add(root)

        while (queue.isNotEmpty() && result.size < maxResults) {
            val directory = queue.removeFirst()
            val children = directory.listFiles().orEmpty()

            for (child in children) {
                if (!showHidden && child.isHidden) continue

                if (child.name.lowercase().contains(text)) {
                    result += toEntry(child)
                    if (result.size >= maxResults) break
                }

                if (
                    child.isDirectory &&
                    !Files.isSymbolicLink(child.toPath())
                ) {
                    queue.addLast(child)
                }
            }
        }

        return result
    }

    private fun sort(
        entries: List<YFileEntry>,
        mode: YFileSort,
        descending: Boolean,
    ): List<YFileEntry> {
        val detail = when (mode) {
            YFileSort.Name ->
                compareBy<YFileEntry> { it.name.lowercase() }
            YFileSort.Modified ->
                compareBy<YFileEntry> { it.modifiedAtMillis }
                    .thenBy { it.name.lowercase() }
            YFileSort.Size ->
                compareBy<YFileEntry> { it.sizeBytes }
                    .thenBy { it.name.lowercase() }
            YFileSort.Type ->
                compareBy<YFileEntry> {
                    if (it.directory) {
                        ""
                    } else {
                        it.name.substringAfterLast(
                            delimiter = '.',
                            missingDelimiterValue = "",
                        ).lowercase()
                    }
                }.thenBy { it.name.lowercase() }
        }

        val direction =
            if (descending) detail.reversed() else detail

        return entries.sortedWith(
            compareByDescending<YFileEntry> { it.directory }
                .then(direction),
        )
    }

    private fun toEntry(file: File): YFileEntry =
        YFileEntry(
            name = file.name.ifBlank {
                file.absolutePath
            },
            path = file.absolutePath,
            directory = file.isDirectory,
            sizeBytes = if (file.isFile) {
                file.length()
            } else {
                0L
            },
            modifiedAtMillis = file.lastModified(),
            hidden = file.isHidden,
        )

    private inline fun <T> runOutcome(
        code: String,
        block: () -> T,
    ): Outcome<T> =
        try {
            Outcome.Success(block())
        } catch (error: Throwable) {
            Outcome.Failure(
                message = error.message ?: "File operation failed",
                cause = error,
                code = code,
                retryable = true,
            )
        }
}
