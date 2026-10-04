package com.yagay.ysuite.feature.yfiles

import android.content.Context
import android.util.Base64
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileConflictStrategy
import com.yagay.ysuite.feature.yfiles.api.YFileFailure
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import com.yagay.ysuite.feature.yfiles.provider.root.RootFileProvider
import java.util.UUID

class YFilesTrashService(
    context: Context,
    private val engine: YFilesEngine,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    suspend fun records(): List<YTrashRecord> =
        loadRecords()
            .filter {
                engine.stat(it.trashedRef)
                    is Outcome.Success
            }
            .sortedByDescending {
                it.deletedAtMillis
            }

    suspend fun moveToTrash(
        refs: List<YFileRef>,
    ): YFileBatchResult {
        var succeeded = 0
        val failures =
            mutableListOf<YFileFailure>()

        for (ref in refs.distinct()) {
            val node = when (
                val result = engine.stat(ref)
            ) {
                is Outcome.Success ->
                    result.value
                is Outcome.Failure -> {
                    failures += failure(
                        ref,
                        result,
                    )
                    continue
                }
            }
            val originalParent = when (
                val result = engine.parent(ref)
            ) {
                is Outcome.Success ->
                    result.value
                is Outcome.Failure -> {
                    failures += failure(
                        ref,
                        result,
                    )
                    continue
                }
            } ?: run {
                failures += YFileFailure(
                    ref = ref,
                    code = "trash_root_forbidden",
                    message =
                        ROOT_TRASH_MESSAGE,
                )
                continue
            }

            val trashParent =
                findTrashParent(ref)
            if (trashParent is Outcome.Failure) {
                failures += failure(
                    ref,
                    trashParent,
                )
                continue
            }
            trashParent as Outcome.Success

            val trashDirectory =
                ensureTrashDirectory(
                    trashParent.value,
                )
            if (trashDirectory is Outcome.Failure) {
                failures += failure(
                    ref,
                    trashDirectory,
                )
                continue
            }
            trashDirectory as Outcome.Success

            val moved = engine.move(
                source = ref,
                destinationDirectory =
                    trashDirectory.value.ref,
                strategy =
                    YFileConflictStrategy.Rename,
            )
            if (moved is Outcome.Failure) {
                failures += failure(ref, moved)
                continue
            }
            moved as Outcome.Success

            saveRecord(
                YTrashRecord(
                    id = UUID.randomUUID()
                        .toString(),
                    originalParent =
                        originalParent,
                    originalName = node.name,
                    trashedRef =
                        moved.value.ref,
                    deletedAtMillis =
                        System.currentTimeMillis(),
                ),
            )
            succeeded += 1
        }

        return YFileBatchResult(
            succeeded = succeeded,
            skipped = 0,
            failures = failures,
        )
    }

    suspend fun restore(
        ids: Set<String>,
    ): YFileBatchResult {
        val records = loadRecords()
            .filter { it.id in ids }
        var succeeded = 0
        val failures =
            mutableListOf<YFileFailure>()

        for (record in records) {
            val moved = engine.move(
                source = record.trashedRef,
                destinationDirectory =
                    record.originalParent,
                strategy =
                    YFileConflictStrategy.Rename,
            )
            when (moved) {
                is Outcome.Success -> {
                    removeRecord(record.id)
                    succeeded += 1
                }
                is Outcome.Failure ->
                    failures += failure(
                        record.trashedRef,
                        moved,
                    )
            }
        }

        return YFileBatchResult(
            succeeded = succeeded,
            skipped = 0,
            failures = failures,
        )
    }

    suspend fun empty(): YFileBatchResult {
        val records = loadRecords()
        var succeeded = 0
        val failures =
            mutableListOf<YFileFailure>()

        for (record in records) {
            when (
                val deleted =
                    engine.delete(
                        record.trashedRef,
                    )
            ) {
                is Outcome.Success -> {
                    removeRecord(record.id)
                    succeeded += 1
                }
                is Outcome.Failure -> {
                    if (
                        deleted.error.code
                            .contains(
                                "not_found",
                                ignoreCase = true,
                            )
                    ) {
                        removeRecord(record.id)
                        succeeded += 1
                    } else {
                        failures += failure(
                            record.trashedRef,
                            deleted,
                        )
                    }
                }
            }
        }

        return YFileBatchResult(
            succeeded = succeeded,
            skipped = 0,
            failures = failures,
        )
    }

    private suspend fun findTrashParent(
        ref: YFileRef,
    ): Outcome<YFileRef> {
        if (
            ref.providerId ==
                RootFileProvider.PROVIDER_ID
        ) {
            return Outcome.Success(
                YFileRef(
                    providerId =
                        RootFileProvider.PROVIDER_ID,
                    path = ROOT_TRASH_PARENT,
                ),
            )
        }

        val providerRoot = when (
            val result =
                engine.root(ref.providerId)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        }

        var current = ref
        var parent = when (
            val result = engine.parent(current)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        }

        while (
            parent != null &&
            parent != providerRoot
        ) {
            current = parent
            parent = when (
                val result =
                    engine.parent(current)
            ) {
                is Outcome.Success ->
                    result.value
                is Outcome.Failure ->
                    return result
            }
        }

        return Outcome.Success(current)
    }

    private suspend fun ensureTrashDirectory(
        parent: YFileRef,
    ): Outcome<YFileNode> {
        val existing = engine.list(
            parent,
            YFileQuery(
                showHidden = true,
                maxResults = 10_000,
            ),
        )
        if (existing is Outcome.Failure) {
            return existing
        }
        existing as Outcome.Success

        val found = existing.value
            .firstOrNull {
                it.name == TRASH_DIRECTORY
            }
        if (found != null) {
            return Outcome.Success(found)
        }

        return engine.createDirectory(
            parent,
            TRASH_DIRECTORY,
        )
    }

    @Synchronized
    private fun loadRecords(): List<YTrashRecord> =
        preferences
            .getStringSet(KEY_RECORDS, emptySet())
            .orEmpty()
            .mapNotNull(::decode)

    @Synchronized
    private fun saveRecord(
        record: YTrashRecord,
    ) {
        val next = preferences
            .getStringSet(KEY_RECORDS, emptySet())
            .orEmpty()
            .toMutableSet()
        next += encode(record)
        preferences.edit()
            .putStringSet(KEY_RECORDS, next)
            .apply()
    }

    @Synchronized
    private fun removeRecord(
        id: String,
    ) {
        val next = preferences
            .getStringSet(KEY_RECORDS, emptySet())
            .orEmpty()
            .filterNot {
                decode(it)?.id == id
            }
            .toSet()
        preferences.edit()
            .putStringSet(KEY_RECORDS, next)
            .apply()
    }

    private fun encode(
        record: YTrashRecord,
    ): String =
        listOf(
            record.id,
            record.originalParent.providerId,
            record.originalParent.path,
            record.originalName,
            record.trashedRef.providerId,
            record.trashedRef.path,
            record.deletedAtMillis.toString(),
        ).joinToString(FIELD_SEPARATOR) {
            encodePart(it)
        }

    private fun decode(
        value: String,
    ): YTrashRecord? =
        runCatching {
            val parts =
                value.split(FIELD_SEPARATOR)
                    .map(::decodePart)
            require(parts.size == 7)
            YTrashRecord(
                id = parts[0],
                originalParent = YFileRef(
                    providerId = parts[1],
                    path = parts[2],
                ),
                originalName = parts[3],
                trashedRef = YFileRef(
                    providerId = parts[4],
                    path = parts[5],
                ),
                deletedAtMillis =
                    parts[6].toLong(),
            )
        }.getOrNull()

    private fun encodePart(
        value: String,
    ): String =
        Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or
                Base64.URL_SAFE,
        )

    private fun decodePart(
        value: String,
    ): String =
        String(
            Base64.decode(
                value,
                Base64.NO_WRAP or
                    Base64.URL_SAFE,
            ),
            Charsets.UTF_8,
        )

    private fun failure(
        ref: YFileRef,
        result: Outcome.Failure,
    ): YFileFailure =
        YFileFailure(
            ref = ref,
            code = result.error.code,
            message = result.message,
        )

    companion object {
        private const val PREFERENCES_NAME =
            "yfiles_trash"
        private const val KEY_RECORDS =
            "records"
        private const val FIELD_SEPARATOR =
            "."
        private const val TRASH_DIRECTORY =
            ".YSuiteTrash"
        private const val ROOT_TRASH_PARENT =
            "/data/local/tmp"
        private const val ROOT_TRASH_MESSAGE =
            "Provider root cannot be moved to trash"
    }
}
