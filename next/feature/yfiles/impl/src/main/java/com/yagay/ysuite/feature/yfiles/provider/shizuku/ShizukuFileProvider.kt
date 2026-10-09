package com.yagay.ysuite.feature.yfiles.provider.shizuku

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileCapability
import com.yagay.ysuite.feature.yfiles.api.YFileChunk
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.platform.api.ShizukuGateway
import com.yagay.ysuite.platform.api.RootRequest
import java.io.File
import java.util.ArrayDeque
import java.util.Base64

class ShizukuFileProvider(
    private val gateway: ShizukuGateway,
) : YFileProvider {
    override val descriptor =
        YFileProviderDescriptor(
            id = PROVIDER_ID,
            kind = YFileProviderKind.Shizuku,
            capabilities = setOf(
                YFileCapability.Browse,
                YFileCapability.Search,
                YFileCapability.Read,
                YFileCapability.Write,
                YFileCapability.Create,
                YFileCapability.Rename,
                YFileCapability.Delete,
                YFileCapability.Copy,
                YFileCapability.Move,
                YFileCapability.PosixMode,
                YFileCapability.SymbolicLink,
            ),
        )

    override fun root(): YFileRef =
        ref("/")

    override fun parent(
        ref: YFileRef,
    ): YFileRef? {
        requireProvider(ref)
        if (ref.path == "/") {
            return null
        }
        return File(ref.path).parent
            ?.ifBlank { "/" }
            ?.let(::ref)
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> {
        requireProvider(directory)
        val needle =
            query.text.trim().lowercase()

        val entries = if (
            query.recursive &&
            needle.isNotEmpty()
        ) {
            val queue = ArrayDeque<YFileRef>()
            val result = mutableListOf<YFileNode>()
            queue.add(directory)

            while (
                queue.isNotEmpty() &&
                result.size < query.maxResults
            ) {
                val current = queue.removeFirst()
                val listed = listDirect(current)
                if (listed is Outcome.Failure) {
                    return listed
                }
                listed as Outcome.Success

                for (node in listed.value) {
                    if (
                        !query.showHidden &&
                        node.hidden
                    ) {
                        continue
                    }
                    if (
                        node.name.lowercase()
                            .contains(needle)
                    ) {
                        result += node
                        if (
                            result.size >=
                                query.maxResults
                        ) {
                            break
                        }
                    }
                    if (
                        node.type ==
                            YFileType.Directory
                    ) {
                        queue.addLast(node.ref)
                    }
                }
            }
            result
        } else {
            val listed = listDirect(directory)
            if (listed is Outcome.Failure) {
                return listed
            }
            listed as Outcome.Success
            listed.value
                .asSequence()
                .filter {
                    query.showHidden ||
                        !it.hidden
                }
                .filter {
                    needle.isEmpty() ||
                        it.name.lowercase()
                            .contains(needle)
                }
                .take(query.maxResults)
                .toList()
        }

        return Outcome.Success(
            sort(entries, query),
        )
    }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> {
        requireProvider(ref)
        return parseSingle(
            command = statCommand(ref.path),
            code = "root_stat_failed",
        )
    }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        create(
            parent = parent,
            name = name,
            directory = true,
        )

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        create(
            parent = parent,
            name = name,
            directory = false,
        )

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> {
        requireProvider(ref)
        val parent =
            File(ref.path).parent
                ?: return failure(
                    "root_rename_failed",
                    SHIZUKU_PARENT_MESSAGE,
                )
        val target =
            join(parent, sanitizeName(newName))
        val command =
            "mv -- " +
                quote(ref.path) +
                " " +
                quote(target)
        val executed = run(
            command,
            "root_rename_failed",
        )
        if (executed is Outcome.Failure) {
            return executed
        }
        return stat(ref(target))
    }

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> {
        requireProvider(ref)
        if (ref.path == "/") {
            return failure(
                "root_delete_failed",
                SHIZUKU_DELETE_MESSAGE,
            )
        }
        return unitCommand(
            command =
                "rm -rf -- " + quote(ref.path),
            code = "root_delete_failed",
        )
    }

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        transfer(
            source = source,
            destinationDirectory =
                destinationDirectory,
            targetName = targetName,
            replace = replace,
            move = false,
        )

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        transfer(
            source = source,
            destinationDirectory =
                destinationDirectory,
            targetName = targetName,
            replace = replace,
            move = true,
        )

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> {
        requireProvider(ref)
        if (offset < 0L || maxBytes <= 0) {
            return failure(
                "root_read_failed",
                INVALID_RANGE_MESSAGE,
            )
        }

        val command =
            "dd if=" +
                quote(ref.path) +
                " bs=1 skip=" +
                offset +
                " count=" +
                maxBytes +
                " 2>/dev/null | " +
                "base64 | tr -d '\\n'"

        val result = run(
            command,
            "root_read_failed",
        )
        if (result is Outcome.Failure) {
            return result
        }
        result as Outcome.Success

        return try {
            val data = if (
                result.value.stdout.isBlank()
            ) {
                ByteArray(0)
            } else {
                Base64.getDecoder().decode(
                    result.value.stdout.trim(),
                )
            }
            Outcome.Success(
                YFileChunk(
                    data = data,
                    eof = data.size < maxBytes,
                ),
            )
        } catch (error: Throwable) {
            Outcome.Failure(
                code = "root_read_decode_failed",
                message =
                    SHIZUKU_DECODE_MESSAGE,
                cause = error,
            )
        }
    }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> {
        requireProvider(ref)
        if (offset < 0L) {
            return failure(
                "root_write_failed",
                INVALID_RANGE_MESSAGE,
            )
        }

        val encoded =
            Base64.getEncoder()
                .encodeToString(data)
        val prefix = if (
            truncate && offset == 0L
        ) {
            ": > " + quote(ref.path) + " && "
        } else {
            ""
        }
        val command =
            prefix +
                "printf %s " +
                quote(encoded) +
                " | base64 -d | dd of=" +
                quote(ref.path) +
                " bs=1 seek=" +
                offset +
                " conv=notrunc 2>/dev/null"

        return unitCommand(
            command,
            "root_write_failed",
        )
    }

    override suspend fun setPosixMode(
        ref: YFileRef,
        mode: Int,
    ): Outcome<Unit> {
        requireProvider(ref)
        val octal =
            Integer.toOctalString(mode and 0xFFF)
        return unitCommand(
            "chmod " +
                octal +
                " -- " +
                quote(ref.path),
            "root_chmod_failed",
        )
    }

    override suspend fun createSymbolicLink(
        parent: YFileRef,
        name: String,
        target: String,
    ): Outcome<YFileNode> {
        requireProvider(parent)
        val path = join(
            parent.path,
            sanitizeName(name),
        )
        val result = run(
            "ln -s -- " +
                quote(target) +
                " " +
                quote(path),
            "root_symlink_failed",
        )
        if (result is Outcome.Failure) {
            return result
        }
        return stat(ref(path))
    }

    override suspend fun readSymbolicLink(
        ref: YFileRef,
    ): Outcome<String> {
        requireProvider(ref)
        val result = run(
            "readlink -- " + quote(ref.path),
            "root_readlink_failed",
        )
        return when (result) {
            is Outcome.Success ->
                Outcome.Success(
                    result.value.stdout
                        .trimEnd('\n', '\r'),
                )
            is Outcome.Failure ->
                result
        }
    }

    private suspend fun listDirect(
        directory: YFileRef,
    ): Outcome<List<YFileNode>> {
        requireProvider(directory)
        val directoryPath = directory.path
        val command = buildString {
            append("dir=")
            append(quote(directoryPath))
            append("; ")
            append("for p in \"\$dir\"/* \"\$dir\"/.[!.]* \"\$dir\"/..?*; do ")
            append("[ -e \"\$p\" ] || [ -L \"\$p\" ] || continue; ")
            append("t=o; ")
            append("if [ -L \"\$p\" ]; then t=l; ")
            append("elif [ -d \"\$p\" ]; then t=d; ")
            append("elif [ -f \"\$p\" ]; then t=f; fi; ")
            append("s=$(stat -c %s -- \"\$p\" 2>/dev/null || echo 0); ")
            append("m=$(stat -c %Y -- \"\$p\" 2>/dev/null || echo 0); ")
            append("x=$(printf %s \"\$p\" | base64 | tr -d '\\n'); ")
            append("printf '%s\\t%s\\t%s\\t%s\\n' \"\$t\" \"\$s\" \"\$m\" \"\$x\"; ")
            append("done")
        }

        val result = run(
            command,
            "root_list_failed",
        )
        if (result is Outcome.Failure) {
            return result
        }
        result as Outcome.Success

        return try {
            Outcome.Success(
                result.value.stdout
                    .lineSequence()
                    .filter(String::isNotBlank)
                    .mapNotNull(::parseLine)
                    .toList(),
            )
        } catch (error: Throwable) {
            Outcome.Failure(
                code = "root_list_parse_failed",
                message = SHIZUKU_PARSE_MESSAGE,
                cause = error,
            )
        }
    }

    private suspend fun create(
        parent: YFileRef,
        name: String,
        directory: Boolean,
    ): Outcome<YFileNode> {
        requireProvider(parent)
        val path = join(
            parent.path,
            sanitizeName(name),
        )
        val command = if (directory) {
            "mkdir -- " + quote(path)
        } else {
            "touch -- " + quote(path)
        }
        val code = if (directory) {
            "root_create_directory_failed"
        } else {
            "root_create_file_failed"
        }

        val result = run(command, code)
        if (result is Outcome.Failure) {
            return result
        }
        return stat(ref(path))
    }

    private suspend fun transfer(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
        move: Boolean,
    ): Outcome<YFileNode> {
        requireProvider(source)
        requireProvider(destinationDirectory)
        val target = join(
            destinationDirectory.path,
            sanitizeName(targetName),
        )
        val replaceCommand = if (replace) {
            "rm -rf -- " + quote(target) + "; "
        } else {
            "[ ! -e " +
                quote(target) +
                " ] || exit 17; "
        }
        val operation = if (move) {
            "mv -- "
        } else {
            "cp -a -- "
        }
        val command =
            replaceCommand +
                operation +
                quote(source.path) +
                " " +
                quote(target)

        val result = run(
            command,
            if (move) {
                "root_move_failed"
            } else {
                "root_copy_failed"
            },
        )
        if (result is Outcome.Failure) {
            return result
        }
        return stat(ref(target))
    }

    private suspend fun parseSingle(
        command: String,
        code: String,
    ): Outcome<YFileNode> {
        val result = run(command, code)
        if (result is Outcome.Failure) {
            return result
        }
        result as Outcome.Success

        val line = result.value.stdout
            .lineSequence()
            .firstOrNull(String::isNotBlank)
            ?: return failure(
                code,
                SHIZUKU_STAT_MESSAGE,
            )
        val node = parseLine(line)
            ?: return failure(
                code,
                SHIZUKU_PARSE_MESSAGE,
            )
        return Outcome.Success(node)
    }

    private fun statCommand(
        path: String,
    ): String =
        "p=" + quote(path) + "; " +
            "[ -e \"\$p\" ] || [ -L \"\$p\" ] || exit 2; " +
            "t=o; " +
            "if [ -L \"\$p\" ]; then t=l; " +
            "elif [ -d \"\$p\" ]; then t=d; " +
            "elif [ -f \"\$p\" ]; then t=f; fi; " +
            "s=$(stat -c %s -- \"\$p\" 2>/dev/null || echo 0); " +
            "m=$(stat -c %Y -- \"\$p\" 2>/dev/null || echo 0); " +
            "x=$(printf %s \"\$p\" | base64 | tr -d '\\n'); " +
            "printf '%s\\t%s\\t%s\\t%s\\n' \"\$t\" \"\$s\" \"\$m\" \"\$x\""

    private fun parseLine(
        line: String,
    ): YFileNode? {
        val parts = line.split('\t')
        if (parts.size != 4) {
            return null
        }

        val path = String(
            Base64.getDecoder().decode(parts[3]),
            Charsets.UTF_8,
        )
        val type = when (parts[0]) {
            "d" -> YFileType.Directory
            "f" -> YFileType.File
            "l" -> YFileType.SymbolicLink
            else -> YFileType.Other
        }
        val name = File(path).name
            .ifBlank { path }

        return YFileNode(
            ref = ref(path),
            name = name,
            type = type,
            sizeBytes = parts[1]
                .toLongOrNull()
                ?.takeIf {
                    type ==
                        YFileType.File
                },
            modifiedAtMillis = parts[2]
                .toLongOrNull()
                ?.times(1000L)
                ?.takeIf { it > 0L },
            hidden = name.startsWith("."),
            readable = true,
            writable = true,
            executable =
                type == YFileType.Directory,
        )
    }

    private fun sort(
        entries: List<YFileNode>,
        query: YFileQuery,
    ): List<YFileNode> {
        val comparator = when (query.sort) {
            YFileSort.Name ->
                compareBy<YFileNode> {
                    it.name.lowercase()
                }
            YFileSort.Modified ->
                compareBy<YFileNode> {
                    it.modifiedAtMillis ?: 0L
                }.thenBy {
                    it.name.lowercase()
                }
            YFileSort.Size ->
                compareBy<YFileNode> {
                    it.sizeBytes ?: 0L
                }.thenBy {
                    it.name.lowercase()
                }
            YFileSort.Type ->
                compareBy<YFileNode> {
                    it.type.ordinal
                }.thenBy {
                    it.name.lowercase()
                }
        }
        val directed =
            if (query.descending) {
                comparator.reversed()
            } else {
                comparator
            }
        return entries.sortedWith(
            compareByDescending<YFileNode> {
                it.type == YFileType.Directory
            }.then(directed),
        )
    }

    private suspend fun run(
        command: String,
        code: String,
    ): Outcome<com.yagay.ysuite.platform.api.RootResult> {
        val result = gateway.execute(
            RootRequest(
                command = command,
                timeoutMillis =
                    SHIZUKU_TIMEOUT_MILLIS,
            ),
        )
        if (result is Outcome.Failure) {
            return result
        }
        result as Outcome.Success

        return if (result.value.exitCode == 0) {
            result
        } else {
            Outcome.Failure(
                code = code,
                message =
                    result.value.stderr
                        .trim()
                        .ifBlank {
                            SHIZUKU_COMMAND_MESSAGE
                        },
                retryable = true,
            )
        }
    }

    private suspend fun unitCommand(
        command: String,
        code: String,
    ): Outcome<Unit> =
        when (val result = run(command, code)) {
            is Outcome.Success ->
                Outcome.Success(Unit)
            is Outcome.Failure ->
                result
        }

    private fun requireProvider(
        ref: YFileRef,
    ) {
        require(
            ref.providerId == PROVIDER_ID,
        ) {
            "Provider mismatch"
        }
    }

    private fun ref(
        path: String,
    ): YFileRef =
        YFileRef(
            providerId = PROVIDER_ID,
            path = normalize(path),
        )

    private fun normalize(
        path: String,
    ): String {
        val absolute =
            if (path.startsWith("/")) {
                path
            } else {
                "/" + path
            }
        val segments = absolute
            .split('/')
            .filter(String::isNotEmpty)
        val stack = ArrayDeque<String>()
        for (segment in segments) {
            when (segment) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) {
                    stack.removeLast()
                }
                else -> stack.addLast(segment)
            }
        }
        return "/" + stack.joinToString("/")
    }

    private fun join(
        parent: String,
        name: String,
    ): String =
        if (parent == "/") {
            "/" + name
        } else {
            parent.trimEnd('/') +
                "/" +
                name
        }

    private fun sanitizeName(
        value: String,
    ): String =
        value.trim()
            .replace('/', '_')
            .replace('\u0000', '_')
            .also {
                require(it.isNotBlank()) {
                    "Name is empty"
                }
            }

    private fun quote(
        value: String,
    ): String =
        "'" +
            value.replace(
                "'",
                "'\"'\"'",
            ) +
            "'"

    private fun <T> failure(
        code: String,
        message: String,
    ): Outcome<T> =
        Outcome.Failure(
            code = code,
            message = message,
            retryable = true,
        )

    companion object {
        const val PROVIDER_ID = "shizuku"
        private const val SHIZUKU_TIMEOUT_MILLIS =
            30_000L
        private const val SHIZUKU_PARENT_MESSAGE =
            "Shizuku item has no parent"
        private const val SHIZUKU_DELETE_MESSAGE =
            "Shizuku filesystem cannot be deleted"
        private const val INVALID_RANGE_MESSAGE =
            "Invalid byte range"
        private const val SHIZUKU_DECODE_MESSAGE =
            "Unable to decode Shizuku data"
        private const val SHIZUKU_PARSE_MESSAGE =
            "Unable to parse Shizuku file metadata"
        private const val SHIZUKU_STAT_MESSAGE =
            "Shizuku item does not exist"
        private const val SHIZUKU_COMMAND_MESSAGE =
            "Shizuku command failed"
    }
}
