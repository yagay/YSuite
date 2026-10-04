package com.yagay.yfiles

import android.content.Context
import android.os.Environment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSection
import com.yagay.yui.YStatusLine
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

enum class YStorageCategory { IMAGES, VIDEO, AUDIO, APPS, ARCHIVES, DOCUMENTS, OTHER }

data class YStorageCategoryUsage(
    val category: YStorageCategory,
    val bytes: Long,
    val files: Long,
)

data class YDirectoryAnalysis(
    val totalBytes: Long,
    val fileCount: Long,
    val directoryCount: Long,
    val scannedEntries: Long,
    val truncated: Boolean,
    val categories: List<YStorageCategoryUsage> = emptyList(),
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
        .filter { File(it).exists() && !isManagedTrash(File(it)) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun isFavorite(path: String): Boolean = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().contains(path)

    fun toggleFavorite(path: String): Boolean {
        require(!isManagedTrash(File(path))) { "Recycle bin is managed internally" }
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
                val file = File(value)
                if (value.isNotBlank() && file.isDirectory && !isManagedTrash(file) && value !in this) add(value)
            }
        }.take(MAX_RECENT_LOCATIONS)
    }.getOrDefault(emptyList())

    @Synchronized
    fun recordRecent(path: String) {
        val directory = File(path)
        if (!directory.isDirectory || isManagedTrash(directory)) return
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
        require(!isManagedTrash(root)) { "Recycle bin is managed internally" }
        val queue = ArrayDeque<File>()
        val categoryBytes = mutableMapOf<YStorageCategory, Long>()
        val categoryFiles = mutableMapOf<YStorageCategory, Long>()
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
                if (isManagedTrash(child)) return@forEach
                scanned++
                if (Files.isSymbolicLink(child.toPath())) return@forEach
                if (child.isDirectory) {
                    directories++
                    queue.add(child)
                } else {
                    files++
                    val size = child.length().coerceAtLeast(0L)
                    bytes += size
                    val category = categoryFor(child)
                    categoryBytes[category] = categoryBytes.getOrDefault(category, 0L) + size
                    categoryFiles[category] = categoryFiles.getOrDefault(category, 0L) + 1L
                }
            }
            if (truncated) break
        }
        val categories = YStorageCategory.values().mapNotNull { category ->
            val count = categoryFiles.getOrDefault(category, 0L)
            if (count <= 0L) null else YStorageCategoryUsage(
                category = category,
                bytes = categoryBytes.getOrDefault(category, 0L),
                files = count,
            )
        }.sortedByDescending(YStorageCategoryUsage::bytes)
        YDirectoryAnalysis(bytes, files, directories, scanned, truncated, categories)
    }

    fun scanDuplicates(
        path: String,
        maxFiles: Long = MAX_DUPLICATE_SCAN_FILES,
        maxGroups: Int = MAX_DUPLICATE_GROUPS,
    ): Result<YDuplicateScan> = runCatching {
        val root = File(path)
        require(root.isDirectory) { "Not a directory: $path" }
        require(!isManagedTrash(root)) { "Recycle bin is managed internally" }
        val queue = ArrayDeque<File>()
        queue.add(root)
        val bySize = linkedMapOf<Long, MutableList<File>>()
        var scannedFiles = 0L
        var truncated = false
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.listFiles().orEmpty().forEach { child ->
                if (isManagedTrash(child) || Files.isSymbolicLink(child.toPath())) return@forEach
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
            .filter { (_, candidates) -> candidates.size > 1 }
            .forEach { (size, candidates) ->
                val byHash = linkedMapOf<String, MutableList<String>>()
                candidates.forEach { candidate ->
                    val hash = hashFile(candidate)
                    byHash.getOrPut(hash) { mutableListOf() }.add(candidate.absolutePath)
                }
                byHash.forEach { (hash, matches) ->
                    if (matches.size > 1) {
                        groups += YDuplicateGroup(size, hash, matches.sortedWith(String.CASE_INSENSITIVE_ORDER))
                    }
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
        require(!isManagedTrash(file)) { "Recycle bin is managed internally" }
        hashFile(file)
    }

    @Synchronized
    fun moveToTrash(entry: FileEntry): Result<YTrashRecord> = runCatching {
        val source = File(entry.path)
        require(source.exists() || Files.isSymbolicLink(source.toPath())) { "Source no longer exists" }
        require(!isManagedTrash(source)) { "Item is already in trash" }
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
    fun trashRecords(): List<YTrashRecord> {
        val loaded = loadTrashRecords()
        val live = loaded.filter { record ->
            val file = File(record.trashPath)
            file.exists() || Files.isSymbolicLink(file.toPath())
        }
        if (live.size != loaded.size) saveTrash(live)
        return live.sortedByDescending(YTrashRecord::deletedAt)
    }

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
        val remaining = mutableListOf<YTrashRecord>()
        var removed = 0
        records.forEach { record ->
            val file = File(record.trashPath)
            val success = runCatching { removeRecursivelySafe(file) }.getOrDefault(false)
            if (success || (!file.exists() && !Files.isSymbolicLink(file.toPath()))) {
                removed++
            } else {
                remaining += record
            }
        }
        saveTrash(remaining)
        if (remaining.isEmpty()) runCatching { if (trashRoot.listFiles().isNullOrEmpty()) trashRoot.delete() }
        removed
    }

    private fun loadTrashRecords(): List<YTrashRecord> = runCatching {
        val array = JSONArray(prefs.getString(KEY_TRASH, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val trashPath = item.optString("trashPath")
                if (trashPath.isBlank()) continue
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
        }
    }.getOrDefault(emptyList())

    private fun isManagedTrash(file: File): Boolean {
        val filePath = file.absoluteFile.toPath().normalize()
        val rootPath = trashRoot.absoluteFile.toPath().normalize()
        return filePath == rootPath || filePath.startsWith(rootPath)
    }

    private fun categoryFor(file: File): YStorageCategory = when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg" -> YStorageCategory.IMAGES
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "m2ts" -> YStorageCategory.VIDEO
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr" -> YStorageCategory.AUDIO
        "apk", "apks", "xapk", "apkm", "aab" -> YStorageCategory.APPS
        "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst", "tgz" -> YStorageCategory.ARCHIVES
        "pdf", "txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "epub", "csv", "json", "xml" -> YStorageCategory.DOCUMENTS
        else -> YStorageCategory.OTHER
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

    YSection(
        title = stringResource(R.string.navigation_safety_tools),
        subtitle = stringResource(R.string.navigation_safety_tools_summary),
    ) {
        YFilesBatchToolbar(path = path, onChanged = onChanged, onError = onError)
        YStatusLine(
            stringResource(R.string.favorite_folder),
            if (isFavorite) stringResource(R.string.yfiles_yes) else stringResource(R.string.yfiles_no),
            if (isFavorite) YStatusTone.Good else YStatusTone.Neutral,
        )
        YHorizontalActions {
            YSecondaryButton(
                text = if (isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite),
                onClick = { store.toggleFavorite(path); revision++ },
                enabled = !busy,
            )
            YPrimaryButton(
                text = stringResource(R.string.analyze_folder),
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { store.analyze(path) }
                        result.onSuccess { analysis = it; onError(null) }.onFailure { onError(it.message) }
                        busy = false
                    }
                },
                enabled = !busy,
            )
        }
        if (favorites.isNotEmpty()) {
            Text(stringResource(R.string.favorite_folders, favorites.size))
            favorites.take(4).forEach { favorite ->
                YSecondaryButton(
                    text = File(favorite).name.ifBlank { favorite },
                    onClick = { onNavigate(favorite) },
                    enabled = !busy,
                )
            }
        }
        if (recents.isNotEmpty()) {
            Text(stringResource(R.string.yfiles_recent_locations, recents.size))
            recents.take(4).forEach { recent ->
                YSecondaryButton(
                    text = File(recent).name.ifBlank { recent },
                    onClick = { onNavigate(recent) },
                    enabled = !busy,
                )
            }
        }
        analysis?.let { result ->
            YStatusLine(stringResource(R.string.analysis_size), formatExtraBytes(result.totalBytes), YStatusTone.Neutral)
            YStatusLine(
                stringResource(R.string.analysis_items),
                stringResource(R.string.analysis_items_value, result.fileCount, result.directoryCount),
                if (result.truncated) YStatusTone.Warning else YStatusTone.Good,
            )
            result.categories.forEach { usage ->
                YStatusLine(
                    storageCategoryLabel(usage.category),
                    stringResource(R.string.yfiles_category_value, usage.files, formatExtraBytes(usage.bytes)),
                    YStatusTone.Neutral,
                )
            }
            if (result.truncated) Text(stringResource(R.string.analysis_truncated, result.scannedEntries))
        }
        YSecondaryButton(
            text = stringResource(R.string.yfiles_find_duplicates),
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { store.scanDuplicates(path) }
                    result.onSuccess { duplicateScan = it; onError(null) }.onFailure { onError(it.message) }
                    busy = false
                }
            },
            enabled = !busy,
        )
        duplicateScan?.let { scan ->
            YStatusLine(
                stringResource(R.string.yfiles_duplicate_summary),
                stringResource(R.string.yfiles_duplicate_summary_value, scan.duplicateFiles, formatExtraBytes(scan.wastedBytes)),
                if (scan.groups.isEmpty()) YStatusTone.Good else YStatusTone.Warning,
            )
            if (scan.truncated) Text(stringResource(R.string.yfiles_duplicate_truncated, scan.scannedFiles))
            scan.groups.take(3).forEach { group ->
                val first = group.files.first()
                YHorizontalActions {
                    Text(stringResource(R.string.yfiles_duplicate_group, group.files.size, formatExtraBytes(group.sizeBytes)))
                    YSecondaryButton(
                        text = stringResource(R.string.yfiles_show_location),
                        onClick = { File(first).parentFile?.absolutePath?.let(onNavigate) },
                        enabled = !busy,
                    )
                }
            }
        }
        YStatusLine(
            stringResource(R.string.recycle_bin),
            stringResource(R.string.recycle_bin_items, trash.size),
            if (trash.isEmpty()) YStatusTone.Neutral else YStatusTone.Warning,
        )
        trash.take(3).forEach { record ->
            YHorizontalActions {
                Text(record.name)
                YSecondaryButton(
                    text = stringResource(R.string.restore),
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { store.restore(record) }
                            result.onSuccess { revision++; onChanged(); onError(null) }.onFailure { onError(it.message) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                )
            }
        }
        if (trash.isNotEmpty()) {
            YSecondaryButton(
                text = stringResource(R.string.empty_recycle_bin),
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { store.emptyTrash() }
                        result.onSuccess { revision++; onChanged(); onError(null) }.onFailure { onError(it.message) }
                        busy = false
                    }
                },
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun storageCategoryLabel(category: YStorageCategory): String = when (category) {
    YStorageCategory.IMAGES -> stringResource(R.string.yfiles_category_images)
    YStorageCategory.VIDEO -> stringResource(R.string.yfiles_category_video)
    YStorageCategory.AUDIO -> stringResource(R.string.yfiles_category_audio)
    YStorageCategory.APPS -> stringResource(R.string.yfiles_category_apps)
    YStorageCategory.ARCHIVES -> stringResource(R.string.yfiles_category_archives)
    YStorageCategory.DOCUMENTS -> stringResource(R.string.yfiles_category_documents)
    YStorageCategory.OTHER -> stringResource(R.string.yfiles_category_other)
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

    YHorizontalActions { YFilesSelectionToggle(entry) }
    YHorizontalActions {
        if (!entry.isDirectory) {
            YSecondaryButton(
                text = stringResource(R.string.sha256),
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { store.sha256(entry) }
                        result.onSuccess { checksum = it; onError(null) }.onFailure { onError(it.message) }
                        busy = false
                    }
                },
                enabled = !busy,
            )
        }
        YSecondaryButton(
            text = stringResource(R.string.move_to_recycle_bin),
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { store.moveToTrash(entry) }
                    result.onSuccess { onChanged(); onError(null) }.onFailure { onError(it.message) }
                    busy = false
                }
            },
            enabled = !busy,
        )
    }
    checksum?.let { Text(stringResource(R.string.sha256_value, it)) }
}
