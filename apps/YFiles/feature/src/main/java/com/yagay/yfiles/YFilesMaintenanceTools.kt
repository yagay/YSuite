package com.yagay.yfiles

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class YRenamePlan(val source: String, val target: String)
data class YFileCompareResult(
    val identical: Boolean,
    val leftSize: Long,
    val rightSize: Long,
    val leftSha256: String,
    val rightSha256: String,
)
data class YMaintenanceCleanupResult(val completed: Int, val failed: Int)

private object YFilesMaintenanceBackend {
    private const val MAX_TEXT_BYTES = 2L * 1024L * 1024L
    private const val MAX_SAVE_TEXT_BYTES = 4L * 1024L * 1024L
    private const val MAX_EMPTY_SCAN_ENTRIES = 50_000
    private const val IO_BUFFER = 256 * 1024

    fun loadText(path: String): Result<String> = runCatching {
        val file = requireFile(path)
        require(file.length() <= MAX_TEXT_BYTES) { "Text file is larger than 2 MiB" }
        file.readText(Charsets.UTF_8)
    }

    fun saveText(path: String, text: String): Result<Unit> = runCatching {
        val file = requireFile(path)
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_SAVE_TEXT_BYTES) { "Edited text is larger than 4 MiB" }
        val parent = file.parentFile ?: error("Missing parent folder")
        val temp = File(parent, ".${file.name}.yfiles-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            runCatching {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.recoverCatching {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
        } finally {
            if (temp.exists()) runCatching { temp.delete() }
        }
    }

    fun compare(leftPath: String, rightPath: String): Result<YFileCompareResult> = runCatching {
        val left = requireFile(leftPath)
        val right = requireFile(rightPath)
        val leftHash = sha256(left)
        val rightHash = sha256(right)
        YFileCompareResult(
            identical = left.length() == right.length() && leftHash == rightHash,
            leftSize = left.length(),
            rightSize = right.length(),
            leftSha256 = leftHash,
            rightSha256 = rightHash,
        )
    }

    fun cleanupDuplicates(
        path: String,
        keepNewest: Boolean,
        trashStore: YFilesExtrasStore,
    ): Result<YMaintenanceCleanupResult> = runCatching {
        val scan = trashStore.scanDuplicates(path).getOrThrow()
        var completed = 0
        var failed = 0
        scan.groups.forEach { group ->
            val files = group.files.map(::File).filter(File::isFile)
            if (files.size < 2) return@forEach
            val keeper = if (keepNewest) {
                files.maxByOrNull(File::lastModified)
            } else {
                files.minByOrNull(File::lastModified)
            } ?: return@forEach
            files.filterNot { it.absolutePath == keeper.absolutePath }.forEach { victim ->
                val result = trashStore.moveToTrash(victim.toEntry())
                if (result.isSuccess) completed++ else failed++
            }
        }
        YMaintenanceCleanupResult(completed, failed)
    }

    fun scanEmptyTrees(path: String): Result<List<String>> = runCatching {
        val root = File(path.trim())
        require(root.isDirectory) { "Directory does not exist" }
        val roots = mutableListOf<String>()
        var visited = 0

        fun containsRealFile(directory: File): Boolean {
            val children = directory.listFiles().orEmpty()
            if (children.isEmpty()) return false
            for (child in children) {
                visited++
                require(visited <= MAX_EMPTY_SCAN_ENTRIES) { "Empty-directory scan limit reached" }
                if (isManagedTrash(child) || Files.isSymbolicLink(child.toPath())) return true
                if (child.isFile) return true
                if (child.isDirectory && containsRealFile(child)) return true
            }
            return false
        }

        root.listFiles().orEmpty().filter(File::isDirectory).forEach { child ->
            if (!isManagedTrash(child) && !Files.isSymbolicLink(child.toPath()) && !containsRealFile(child)) {
                roots += child.absolutePath
            }
        }
        roots.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun cleanupEmptyTrees(path: String, trashStore: YFilesExtrasStore): Result<YMaintenanceCleanupResult> = runCatching {
        val candidates = scanEmptyTrees(path).getOrThrow()
        var completed = 0
        var failed = 0
        candidates.forEach { candidate ->
            val file = File(candidate)
            if (file.isDirectory) {
                val result = trashStore.moveToTrash(file.toEntry())
                if (result.isSuccess) completed++ else failed++
            }
        }
        YMaintenanceCleanupResult(completed, failed)
    }

    fun planRename(
        directoryPath: String,
        find: String,
        replacement: String,
        regex: Boolean,
    ): Result<List<YRenamePlan>> = runCatching {
        val directory = File(directoryPath.trim())
        require(directory.isDirectory) { "Directory does not exist" }
        require(find.isNotBlank()) { "Find text is empty" }
        val matcher = if (regex) Regex(find) else null
        val entries = directory.listFiles().orEmpty().take(1000)
        val plans = entries.mapNotNull { source ->
            if (isManagedTrash(source)) return@mapNotNull null
            val targetName = if (matcher != null) {
                matcher.replace(source.name, replacement)
            } else {
                source.name.replace(find, replacement)
            }.trim()
            if (targetName.isBlank() || targetName == source.name || '/' in targetName || '\u0000' in targetName) {
                return@mapNotNull null
            }
            YRenamePlan(source.absolutePath, File(directory, targetName).absolutePath)
        }
        require(plans.map(YRenamePlan::target).distinct().size == plans.size) { "Rename targets contain duplicates" }
        val plannedSources = plans.mapTo(mutableSetOf(), YRenamePlan::source)
        plans.forEach { plan ->
            val target = File(plan.target)
            require(!target.exists() || target.absolutePath in plannedSources) { "Target already exists: ${target.name}" }
        }
        plans
    }

    fun executeRename(plans: List<YRenamePlan>): Result<Int> = runCatching {
        if (plans.isEmpty()) return@runCatching 0
        val staged = mutableListOf<Triple<File, File, File>>()
        try {
            plans.forEachIndexed { index, plan ->
                val source = File(plan.source)
                require(source.exists() || Files.isSymbolicLink(source.toPath())) { "Source disappeared: ${source.name}" }
                val temp = File(source.parentFile, ".yfiles-rename-${UUID.randomUUID()}-$index")
                Files.move(source.toPath(), temp.toPath())
                staged += Triple(source, temp, File(plan.target))
            }
            staged.forEach { (_, temp, target) -> Files.move(temp.toPath(), target.toPath()) }
            staged.size
        } catch (error: Throwable) {
            staged.asReversed().forEach { (source, temp, target) ->
                when {
                    temp.exists() -> runCatching { Files.move(temp.toPath(), source.toPath()) }
                    target.exists() && !source.exists() -> runCatching { Files.move(target.toPath(), source.toPath()) }
                }
            }
            throw error
        }
    }

    fun splitFile(path: String, partSizeMiB: Int): Result<List<File>> = runCatching {
        val source = requireFile(path)
        val size = partSizeMiB.coerceIn(1, 4096).toLong() * 1024L * 1024L
        val parent = source.parentFile ?: error("Missing parent folder")
        val first = File(parent, source.name + ".part001")
        require(!first.exists()) { "Part files already exist" }
        val outputs = mutableListOf<File>()
        try {
            BufferedInputStream(FileInputStream(source), IO_BUFFER).use { input ->
                var index = 1
                var finished = false
                while (!finished) {
                    val target = File(parent, source.name + ".part" + String.format("%03d", index))
                    require(!target.exists()) { "Part already exists: ${target.name}" }
                    var written = 0L
                    BufferedOutputStream(FileOutputStream(target), IO_BUFFER).use { output ->
                        val buffer = ByteArray(IO_BUFFER)
                        while (written < size) {
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), size - written).toInt())
                            if (count < 0) {
                                finished = true
                                break
                            }
                            output.write(buffer, 0, count)
                            written += count
                        }
                    }
                    if (written > 0L) {
                        outputs += target
                        index++
                    } else {
                        target.delete()
                    }
                }
            }
            require(outputs.isNotEmpty()) { "No parts were created" }
            outputs
        } catch (error: Throwable) {
            outputs.forEach { runCatching { it.delete() } }
            throw error
        }
    }

    fun joinParts(firstPartPath: String): Result<File> = runCatching {
        val first = requireFile(firstPartPath)
        val match = Regex("^(.*)\\.part(\\d{3,4})$").matchEntire(first.name)
            ?: error("Choose a .part001 file")
        require(match.groupValues[2].toIntOrNull() == 1) { "Choose the first part" }
        val baseName = match.groupValues[1]
        val parent = first.parentFile ?: error("Missing parent folder")
        val parts = mutableListOf<File>()
        var index = 1
        while (index <= 9999) {
            val part = File(parent, baseName + ".part" + String.format("%03d", index))
            if (!part.isFile) break
            parts += part
            index++
        }
        require(parts.isNotEmpty()) { "No parts found" }
        val target = uniqueTarget(parent, baseName)
        try {
            BufferedOutputStream(FileOutputStream(target), IO_BUFFER).use { output ->
                parts.forEach { part ->
                    BufferedInputStream(FileInputStream(part), IO_BUFFER).use { input -> input.copyTo(output, IO_BUFFER) }
                }
            }
            target
        } catch (error: Throwable) {
            runCatching { target.delete() }
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file), IO_BUFFER).use { input ->
            val buffer = ByteArray(IO_BUFFER)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requireFile(path: String): File {
        val file = File(path.trim())
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Regular file does not exist" }
        require(!isManagedTrash(file)) { "Recycle bin is managed internally" }
        return file
    }

    private fun isManagedTrash(file: File): Boolean =
        file.name == ".YFilesTrash" || file.absolutePath.contains("/.YFilesTrash/")

    private fun uniqueTarget(parent: File, name: String): File {
        var candidate = File(parent, name)
        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, "$name ($index)")
            index++
        }
        return candidate
    }
}

@Composable
fun YFilesMaintenanceToolsCard(context: Context) {
    val scope = rememberCoroutineScope()
    val trashStore = remember(context) { YFilesExtrasStore(context) }
    var targetPath by remember { mutableStateOf("") }
    var secondPath by remember { mutableStateOf("") }
    var editorText by remember { mutableStateOf("") }
    var editorLoaded by remember { mutableStateOf(false) }
    var keepNewest by remember { mutableStateOf(true) }
    var emptyTrees by remember { mutableStateOf<List<String>>(emptyList()) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var regexRename by remember { mutableStateOf(false) }
    var renamePlan by remember { mutableStateOf<List<YRenamePlan>>(emptyList()) }
    var partSize by remember { mutableStateOf("256") }
    var compareResult by remember { mutableStateOf<YFileCompareResult?>(null) }
    var outputText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun <T> runIo(block: () -> Result<T>, onSuccess: (T) -> Unit = {}) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            result.onSuccess {
                error = null
                onSuccess(it)
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        YSection(
            title = stringResource(R.string.yfiles_maintenance_title),
            subtitle = stringResource(R.string.yfiles_maintenance_summary),
        ) {
            OutlinedTextField(
                value = targetPath,
                onValueChange = {
                    targetPath = it
                    editorLoaded = false
                    renamePlan = emptyList()
                    compareResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.yfiles_maintenance_path)) },
                singleLine = true,
            )

            Text(stringResource(R.string.yfiles_maintenance_text_editor))
            YHorizontalActions {
                OutlinedButton(onClick = {
                    runIo({ YFilesMaintenanceBackend.loadText(targetPath) }) {
                        editorText = it
                        editorLoaded = true
                    }
                }, enabled = !busy && targetPath.isNotBlank()) {
                    Text(stringResource(R.string.yfiles_maintenance_load_text))
                }
                Button(onClick = {
                    runIo({ YFilesMaintenanceBackend.saveText(targetPath, editorText) }) {
                        outputText = context.getString(R.string.yfiles_maintenance_saved)
                    }
                }, enabled = !busy && editorLoaded) {
                    Text(stringResource(R.string.yfiles_maintenance_save_text))
                }
            }
            if (editorLoaded) {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.yfiles_maintenance_utf8_content)) },
                    minLines = 5,
                    maxLines = 14,
                )
            }

            Text(stringResource(R.string.yfiles_maintenance_cleanup))
            YSwitchItem(
                title = stringResource(R.string.yfiles_maintenance_keep_newest),
                subtitle = stringResource(R.string.yfiles_maintenance_keep_newest_summary),
                checked = keepNewest,
                onCheckedChange = { keepNewest = it },
            )
            YHorizontalActions {
                Button(onClick = {
                    runIo({ YFilesMaintenanceBackend.cleanupDuplicates(targetPath, keepNewest, trashStore) }) { result ->
                        outputText = context.getString(
                            R.string.yfiles_maintenance_cleanup_result,
                            result.completed,
                            result.failed,
                        )
                    }
                }, enabled = !busy && targetPath.isNotBlank()) {
                    Text(stringResource(R.string.yfiles_maintenance_clean_duplicates))
                }
                OutlinedButton(onClick = {
                    runIo({ YFilesMaintenanceBackend.scanEmptyTrees(targetPath) }) { emptyTrees = it }
                }, enabled = !busy && targetPath.isNotBlank()) {
                    Text(stringResource(R.string.yfiles_maintenance_scan_empty))
                }
            }
            if (emptyTrees.isNotEmpty()) {
                YStatusLine(
                    stringResource(R.string.yfiles_maintenance_empty_found),
                    emptyTrees.size.toString(),
                    YStatusTone.Warning,
                )
                emptyTrees.take(5).forEach { Text(it) }
                OutlinedButton(onClick = {
                    runIo({ YFilesMaintenanceBackend.cleanupEmptyTrees(targetPath, trashStore) }) { result ->
                        outputText = context.getString(
                            R.string.yfiles_maintenance_cleanup_result,
                            result.completed,
                            result.failed,
                        )
                        emptyTrees = emptyList()
                    }
                }, enabled = !busy) {
                    Text(stringResource(R.string.yfiles_maintenance_trash_empty))
                }
            }

            Text(stringResource(R.string.yfiles_maintenance_bulk_rename))
            OutlinedTextField(
                value = findText,
                onValueChange = { findText = it; renamePlan = emptyList() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.yfiles_maintenance_find)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = replaceText,
                onValueChange = { replaceText = it; renamePlan = emptyList() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.yfiles_maintenance_replace)) },
                singleLine = true,
            )
            YSwitchItem(
                title = stringResource(R.string.yfiles_maintenance_regex),
                checked = regexRename,
                onCheckedChange = { regexRename = it; renamePlan = emptyList() },
            )
            YHorizontalActions {
                OutlinedButton(onClick = {
                    runIo({ YFilesMaintenanceBackend.planRename(targetPath, findText, replaceText, regexRename) }) {
                        renamePlan = it
                    }
                }, enabled = !busy && targetPath.isNotBlank() && findText.isNotBlank()) {
                    Text(stringResource(R.string.yfiles_maintenance_preview_rename))
                }
                Button(onClick = {
                    runIo({ YFilesMaintenanceBackend.executeRename(renamePlan) }) { count ->
                        outputText = context.getString(R.string.yfiles_maintenance_renamed, count)
                        renamePlan = emptyList()
                    }
                }, enabled = !busy && renamePlan.isNotEmpty()) {
                    Text(stringResource(R.string.yfiles_maintenance_execute_rename))
                }
            }
            renamePlan.take(8).forEach { plan ->
                Text(
                    stringResource(
                        R.string.yfiles_maintenance_rename_preview,
                        File(plan.source).name,
                        File(plan.target).name,
                    ),
                )
            }
            if (renamePlan.size > 8) {
                Text(stringResource(R.string.yfiles_maintenance_more, renamePlan.size - 8))
            }

            Text(stringResource(R.string.yfiles_maintenance_split_join))
            OutlinedTextField(
                value = partSize,
                onValueChange = { value -> partSize = value.filter { it.isDigit() }.take(4) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.yfiles_maintenance_part_size)) },
                singleLine = true,
            )
            YHorizontalActions {
                Button(onClick = {
                    val size = partSize.toIntOrNull() ?: 256
                    runIo({ YFilesMaintenanceBackend.splitFile(targetPath, size) }) { parts ->
                        outputText = context.getString(R.string.yfiles_maintenance_parts_created, parts.size)
                    }
                }, enabled = !busy && targetPath.isNotBlank()) {
                    Text(stringResource(R.string.yfiles_maintenance_split))
                }
                OutlinedButton(onClick = {
                    runIo({ YFilesMaintenanceBackend.joinParts(targetPath) }) { file ->
                        outputText = context.getString(R.string.yfiles_maintenance_joined, file.absolutePath)
                    }
                }, enabled = !busy && targetPath.isNotBlank()) {
                    Text(stringResource(R.string.yfiles_maintenance_join))
                }
            }

            Text(stringResource(R.string.yfiles_maintenance_compare))
            OutlinedTextField(
                value = secondPath,
                onValueChange = { secondPath = it; compareResult = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.yfiles_maintenance_second_path)) },
                singleLine = true,
            )
            OutlinedButton(onClick = {
                runIo({ YFilesMaintenanceBackend.compare(targetPath, secondPath) }) { compareResult = it }
            }, enabled = !busy && targetPath.isNotBlank() && secondPath.isNotBlank()) {
                Text(stringResource(R.string.yfiles_maintenance_compare_now))
            }
            compareResult?.let { result ->
                YStatusLine(
                    stringResource(R.string.yfiles_maintenance_compare_result),
                    if (result.identical) {
                        stringResource(R.string.yfiles_maintenance_identical)
                    } else {
                        stringResource(R.string.yfiles_maintenance_different)
                    },
                    if (result.identical) YStatusTone.Good else YStatusTone.Warning,
                )
                YStatusLine(
                    stringResource(R.string.yfiles_maintenance_left_size),
                    formatExtraBytes(result.leftSize),
                    YStatusTone.Neutral,
                )
                YStatusLine(
                    stringResource(R.string.yfiles_maintenance_right_size),
                    formatExtraBytes(result.rightSize),
                    YStatusTone.Neutral,
                )
                Text(stringResource(R.string.yfiles_maintenance_hash_pair, result.leftSha256, result.rightSha256))
            }

            outputText?.let { Text(it) }
            error?.let { Text(it) }
            Text(stringResource(R.string.yfiles_maintenance_note))
        }
    }
}
