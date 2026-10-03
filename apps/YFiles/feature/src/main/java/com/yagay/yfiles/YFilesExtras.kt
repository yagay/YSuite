package com.yagay.yfiles

import android.content.Context
import android.os.Environment
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YActionRow
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class YDirectoryAnalysis(
    val totalBytes: Long,
    val fileCount: Long,
    val directoryCount: Long,
    val scannedEntries: Long,
    val truncated: Boolean,
)

data class YDuplicateGroup(
    val sizeBytes: Long,
    val sha256: String,
    val files: List<String>,
) {
    val wastedBytes: Long
        get() = sizeBytes * (files.size - 1L).coerceAtLeast(0L)
}

data class YDuplicateScan(
    val groups: List<YDuplicateGroup>,
    val scannedFiles: Long,
    val truncated: Boolean,
) {
    val duplicateFiles: Int
        get() = groups.sumOf { (it.files.size - 1).coerceAtLeast(0) }
    val wastedBytes: Long
        get() = groups.sumOf(YDuplicateGroup::wastedBytes)
}

data class YTrashRecord(
    val id: String,
    val name: String,
    val originalPath: String,
    val trashPath: String,
    val deletedAt: Long,
)

class YFilesExtrasStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val trashRoot: File
        get() = File(Environment.getExternalStorageDirectory(), TRASH_DIRECTORY)

    fun favorites(): List<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
        .filter { File(it).exists() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun isFavorite(path: String): Boolean = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().contains(path)

    fun toggleFavorite(path: String): Boolean {
        val current = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toMutableSet()
        val added = if (current.contains(path)) {
            current.remove(path)
            false
        } else {
            current.add(path)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return added
    }

    @Synchronized
    fun recentPaths(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_RECENT, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank() && File(value).isDirectory && value !in this) add(value)
            }
        }.take(MAX_RECENT_LOCATIONS)
    }.getOrDefault(emptyList())

    @Synchronized
    fun recordRecent(path: String) {
        val directory = File(path)
        if (!directory.isDirectory) return
        val normalized = runCatching { directory.canonicalPath }.getOrElse { directory.absolutePath }
        val next = buildList {
            add(normalized)
            recentPaths().filterNot { it == normalized }.forEach(::add)
        }.take(MAX_RECENT_LOCATIONS)
        val array = JSONArray()
        next.forEach(array::put)
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    fun analyze(path: String, maxEntries: Long = MAX_ANALYSIS_ENTRIES): Result<YDirectoryAnalysis> = runCatching {
        val root = File(path)
        require(root.isDirectory) { "Not a directory: $path" }
        val queue = ArrayDeque<File>()
        queue.add(root)
        var bytes = 0L
        var files = 0L
        var directories = 0L
        var scanned = 0L
        var truncated = false
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.listFiles().orEmpty().forEach { child ->
                if (scanned >= maxEntries) {
                    truncated = true
                    return@forEach
                }
                scanned++
                if (Files.isSymbolicLink(child.toPath())) return@forEach
                if (child.isDirectory) {
                    directories++
                    queue.add(child)
                } else {
                    files++
                    bytes += child.length().coerceAtLeast(0L)
                }
            }
            if (truncated) break
        }
        YDirectoryAnalysis(bytes, files, directories, scanned, truncated)
    }

    fun scanDuplicates(
        path: String,
        maxFiles: Long = MAX_DUPLICATE_SCAN_FILES,
        maxGroups: Int = MAX_DUPLICATE_GROUPS,
    ): Result<YDuplicateScan> = runCatching {
        val root = File(path)
        require(root.isDirectory) { "Not a directory: $path" }
        val queue = ArrayDeque<File>()
        queue.add(root)
        val bySize = linkedMapOf<Long, MutableList<File>>()
        var scannedFiles = 0L
        var truncated = false
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.listFiles().orEmpty().forEach { child ->
                if (Files.isSymbolicLink(child.toPath())) return@forEach
                if (child.isDirectory) {
                    queue.add(child)
                } else if (child.isFile) {
                    if (scannedFiles >= maxFiles) {
                        truncated = true
                        return@forEach
                    }
                    scannedFiles++
                    val size = child.length().coerceAtLeast(0L)
                    if (size > 0L) bySize.getOrPut(size) { mutableListOf() }.add(child)
                }
            }
            if (truncated) break
        }

        val groups = mutableListOf<YDuplicateGroup>()
        bySize.asSequence()
            .filter { (_, files) -> files.size > 1 }
            .forEach { (size, candidates) ->
                val byHash = linkedMapOf<String, MutableList<String>>()
                candidates.forEach { candidate ->
                    val hash = hashFile(candidate)
                    byHash.getOrPut(hash) { mutableListOf() }.add(candidate.absolutePath)
                }
                byHash.forEach { (hash, files) ->
                    if (files.size > 1) groups += YDuplicateGroup(size, hash, files.sortedWith(String.CASE_INSENSITIVE_ORDER))
                }
            }
        YDuplicateScan(
            groups = groups.sortedByDescending(YDuplicateGroup::wastedBytes).take(maxGroups),
            scannedFiles = scannedFiles,
            truncated = truncated,
        )
    }

    fun sha256(entry: FileEntry): Result<String> = runCatching {
        require(!entry.isDirectory) { "Checksum is only available for files" }
        val file = File(entry.path)
        require(file.isFile) { "File no longer exists" }
        hashFile(file)
    }

    @Synchronized
    fun moveToTrash(entry: FileEntry): Result<YTrashRecord> = runCatching {
        val source = File(entry.path)
        require(source.exists() || Files.isSymbolicLink(source.toPath())) { "Source no longer exists" }
        require(source.canonicalPath != trashRoot.canonicalPath) { "Trash folder cannot be moved to trash" }
        require(!source.canonicalPath.startsWith(trashRoot.canonicalPath + File.separator)) {
            "Item is already in trash"
        }
        require(trashRoot.mkdirs() || trashRoot.isDirectory) { "Unable to create trash folder" }
        val id = "${System.currentTimeMillis()}-${Integer.toHexString(source.absolutePath.hashCode())}"
        val target = uniqueTarget(trashRoot, "$id-${source.name.ifBlank { "item" }}")
        moveWithFallback(source, target)
        val record = YTrashRecord(
            id = id,
            name = source.name.ifBlank { source.absolutePath },
            originalPath = source.absolutePath,
            trashPath = target.absolutePath,
            deletedAt = System.currentTimeMillis(),
        )
        saveTrash(trashRecords() + record)
        record
    }

    @Synchronized
    fun trashRecords(): List<YTrashRecord> = runCatching {
        val array = JSONArray(prefs.getString(KEY_TRASH, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val trashPath = item.optString("trashPath")
                if (trashPath.isBlank() || !File(trashPath).exists()) continue
                add(
                    YTrashRecord(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        originalPath = item.optString("originalPath"),
                        trashPath = trashPath,
                        deletedAt = item.optLong("deletedAt"),
                    ),
                )
            }
        }.sortedByDescending { it.deletedAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun restore(record: YTrashRecord): Result<File> = runCatching {
        val source = File(record.trashPath)
        require(source.exists() || Files.isSymbolicLink(source.toPath())) { "Trash item no longer exists" }
        val original = File(record.originalPath)
        val parent = original.parentFile ?: error("Original folder is unavailable")
        require(parent.mkdirs() || parent.isDirectory) { "Unable to create original folder" }
        val target = if (original.exists()) uniqueTarget(parent, original.name) else original
        moveWithFallback(source, target)
        saveTrash(trashRecords().filterNot { it.id == record.id })
        target
    }

    @Synchronized
    fun emptyTrash(): Result<Int> = runCatching {
        val records = trashRecords()
        var removed = 0
        records.forEach { record ->
            val file = File(record.trashPath)
            val success = removeRecursivelySafe(file)
            if (success || !file.exists()) removed++
        }
        saveTrash(emptyList())
        runCatching { if (trashRoot.listFiles().isNullOrEmpty()) trashRoot.delete() }
        removed
    }

    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun saveTrash(records: List<YTrashRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("name", record.name)
                put("originalPath", record.originalPath)
                put("trashPath", record.trashPath)
                put("deletedAt", record.deletedAt)
            })
        }
        prefs.edit().putString(KEY_TRASH, array.toString()).apply()
    }

    private fun moveWithFallback(source: File, target: File) {
        target.parentFile?.mkdirs()
        runCatching {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            Files.move(source.toPath(), target.toPath())
        }.recoverCatching {
            copyRecursivelySafe(source, target)
            require(removeRecursivelySafe(source)) { "Copied but could not remove original" }
        }.getOrThrow()
    }

    private fun copyRecursivelySafe(source: File, target: File) {
        if (Files.isSymbolicLink(source.toPath())) {
            Files.createSymbolicLink(target.toPath(), Files.readSymbolicLink(source.toPath()))
            return
        }
        if (source.isDirectory) {
            require(target.mkdirs() || target.isDirectory) { "Unable to create ${target.name}" }
            source.listFiles().orEmpty().forEach { child -> copyRecursivelySafe(child, File(target, child.name)) }
            runCatching { target.setLastModified(source.lastModified()) }
        } else {
            target.parentFile?.mkdirs()
            Files.copy(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.COPY_ATTRIBUTES,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun removeRecursivelySafe(file: File): Boolean {
        if (!file.exists() && !Files.isSymbolicLink(file.toPath())) return true
        if (Files.isSymbolicLink(file.toPath()) || file.isFile) return file.delete()
        return file.listFiles().orEmpty().all(::removeRecursivelySafe) && file.delete()
    }

    private fun uniqueTarget(parent: File, originalName: String): File {
        var candidate = File(parent, originalName)
        if (!candidate.exists()) return candidate
        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, "$originalName ($index)")
            index++
        }
        return candidate
    }

    companion object {
        private const val PREFS = "yfiles_extras"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENT = "recent_locations"
        private const val KEY_TRASH = "trash"
        private const val TRASH_DIRECTORY = ".YFilesTrash"
        private const val HASH_BUFFER_SIZE = 256 * 1024
        private const val MAX_ANALYSIS_ENTRIES = 100_000L
        private const val MAX_RECENT_LOCATIONS = 12
        private const val MAX_DUPLICATE_SCAN_FILES = 50_000L
        private const val MAX_DUPLICATE_GROUPS = 100
    }
}

@Composable
fun YFilesExtraToolsCard(
    path: String,
    onNavigate: (String) -> Unit,
    onChanged: () -> Unit,
    onError: (String?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { YFilesExtrasStore(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var analysis by remember(path) { mutableStateOf<YDirectoryAnalysis?>(null) }
    var duplicateScan by remember(path) { mutableStateOf<YDuplicateScan?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) { store.recordRecent(path) }
        revision++
    }

    val favorites = remember(revision, path) { store.favorites() }
    val recents = remember(revision, path) { store.recentPaths().filterNot { it == path } }
    val trash = remember(revision) { store.trashRecords() }
    val isFavorite = remember(revision, path) { store.isFavorite(path) }

    YFeatureCard(
        title = stringResource(R.string.navigation_safety_tools),
        subtitle = stringResource(R.string.navigation_safety_tools_summary),
    ) {
        YStatusRow(
            stringResource(R.string.favorite_folder),
            if (isFavorite) stringResource(R.string.yfiles_yes) else stringResource(R.string.yfiles_no),
            if (isFavorite) YStatusTone.Good else YStatusTone.Neutral,
        )
        YActionRow {
            OutlinedButton(
                onClick = {
                    store.toggleFavorite(path)
                    revision++
                },
                enabled = !busy,
            ) {
                Text(if (isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite))
            }
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { store.analyze(path) }
                        result.onSuccess { analysis = it; onError(null) }
                            .onFailure { onError(it.message) }
                        busy = false
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.analyze_folder)) }
        }
        if (favorites.isNotEmpty()) {
            Text(stringResource(R.string.favorite_folders, favorites.size))
            favorites.take(4).forEach { favorite ->
                OutlinedButton(
                    onClick = { onNavigate(favorite) },
                    enabled = !busy,
                ) { Text(File(favorite).name.ifBlank { favorite }) }
            }
        }
        if (recents.isNotEmpty()) {
            Text(stringResource(R.string.yfiles_recent_locations, recents.size))
            recents.take(4).forEach { recent ->
                OutlinedButton(
                    onClick = { onNavigate(recent) },
                    enabled = !busy,
                ) { Text(File(recent).name.ifBlank { recent }) }
            }
        }
        analysis?.let { result ->
            YStatusRow(stringResource(R.string.analysis_size), formatExtraBytes(result.totalBytes), YStatusTone.Neutral)
            YStatusRow(
                stringResource(R.string.analysis_items),
                stringResource(R.string.analysis_items_value, result.fileCount, result.directoryCount),
                if (result.truncated) YStatusTone.Warning else YStatusTone.Good,
            )
            if (result.truncated) Text(stringResource(R.string.analysis_truncated, result.scannedEntries))
        }
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { store.scanDuplicates(path) }
                    result.onSuccess { duplicateScan = it; onError(null) }
                        .onFailure { onError(it.message) }
                    busy = false
                }
            },
            enabled = !busy,
        ) { Text(stringResource(R.string.yfiles_find_duplicates)) }
        duplicateScan?.let { scan ->
            YStatusRow(
                stringResource(R.string.yfiles_duplicate_summary),
                stringResource(R.string.yfiles_duplicate_summary_value, scan.duplicateFiles, formatExtraBytes(scan.wastedBytes)),
                if (scan.groups.isEmpty()) YStatusTone.Good else YStatusTone.Warning,
            )
            if (scan.truncated) Text(stringResource(R.string.yfiles_duplicate_truncated, scan.scannedFiles))
            scan.groups.take(3).forEach { group ->
                val first = group.files.first()
                YActionRow {
                    Text(stringResource(R.string.yfiles_duplicate_group, group.files.size, formatExtraBytes(group.sizeBytes)))
                    OutlinedButton(
                        onClick = { File(first).parentFile?.absolutePath?.let(onNavigate) },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.yfiles_show_location)) }
                }
            }
        }
        YStatusRow(
            stringResource(R.string.recycle_bin),
            stringResource(R.string.recycle_bin_items, trash.size),
            if (trash.isEmpty()) YStatusTone.Neutral else YStatusTone.Warning,
        )
        trash.take(3).forEach { record ->
            YActionRow {
                Text(record.name)
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { store.restore(record) }
                            result.onSuccess {
                                revision++
                                onChanged()
                                onError(null)
                            }.onFailure { onError(it.message) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.restore)) }
            }
        }
        if (trash.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { store.emptyTrash() }
                        result.onSuccess {
                            revision++
                            onChanged()
                            onError(null)
                        }.onFailure { onError(it.message) }
                        busy = false
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.empty_recycle_bin)) }
        }
    }
}

@Composable
fun YFilesEntryExtraActions(
    entry: FileEntry,
    onChanged: () -> Unit,
    onError: (String?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { YFilesExtrasStore(context) }
    val scope = rememberCoroutineScope()
    var busy by remember(entry.path) { mutableStateOf(false) }
    var checksum by remember(entry.path) { mutableStateOf<String?>(null) }

    YActionRow {
        if (!entry.isDirectory) {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { store.sha256(entry) }
                        result.onSuccess { checksum = it; onError(null) }
                            .onFailure { onError(it.message) }
                        busy = false
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.sha256)) }
        }
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { store.moveToTrash(entry) }
                    result.onSuccess {
                        onChanged()
                        onError(null)
                    }.onFailure { onError(it.message) }
                    busy = false
                }
            },
            enabled = !busy,
        ) { Text(stringResource(R.string.move_to_recycle_bin)) }
    }
    checksum?.let { Text(stringResource(R.string.sha256_value, it)) }
}
