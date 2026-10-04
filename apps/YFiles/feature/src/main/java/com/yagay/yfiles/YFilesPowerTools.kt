package com.yagay.yfiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.system.Os
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YTextField
import com.yagay.yui.YSection
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.ArrayDeque
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YCleanupKind { LARGE, OLD, HIDDEN, EMPTY_DIRECTORY, APK, OLD_DOWNLOAD, SCREENSHOT, RECORDING }

data class YCleanupBucket(val count: Int = 0, val bytes: Long = 0L)
data class YCleanupCandidate(val path: String, val size: Long, val kind: YCleanupKind, val modified: Long)
data class YCleanupScan(
    val buckets: Map<YCleanupKind, YCleanupBucket>,
    val candidates: List<YCleanupCandidate>,
    val scannedEntries: Long,
    val truncated: Boolean,
)

data class YApkDetails(val label: String, val packageName: String, val versionName: String, val versionCode: Long)

private object YFilesPowerBackend {
    private const val MAX_SCAN_ENTRIES = 75_000L
    private const val LARGE_BYTES = 100L * 1024L * 1024L
    private const val OLD_AGE_MS = 180L * 24L * 60L * 60L * 1000L
    private const val OLD_DOWNLOAD_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val PREVIEW_BYTES = 64 * 1024
    private const val HEX_BYTES = 512
    private val MAGIC = byteArrayOf('Y'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())

    fun scanStorage(): Result<YCleanupScan> = runCatching {
        val root = Environment.getExternalStorageDirectory()
        require(root.isDirectory) { "Primary shared storage is unavailable" }
        val queue = ArrayDeque<File>()
        queue.add(root)
        val counts = mutableMapOf<YCleanupKind, Int>()
        val bytes = mutableMapOf<YCleanupKind, Long>()
        val candidates = mutableListOf<YCleanupCandidate>()
        var scanned = 0L
        var truncated = false
        val now = System.currentTimeMillis()

        fun add(kind: YCleanupKind, file: File, size: Long) {
            counts[kind] = counts.getOrDefault(kind, 0) + 1
            bytes[kind] = bytes.getOrDefault(kind, 0L) + size
            if (candidates.size < 500) candidates += YCleanupCandidate(file.absolutePath, size, kind, file.lastModified())
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.listFiles().orEmpty().forEach { child ->
                if (scanned >= MAX_SCAN_ENTRIES) {
                    truncated = true
                    return@forEach
                }
                if (child.name == ".YFilesTrash" || child.absolutePath.contains("/.YFilesTrash/")) return@forEach
                if (java.nio.file.Files.isSymbolicLink(child.toPath())) return@forEach
                scanned++
                if (child.isDirectory) {
                    if (child.listFiles()?.isEmpty() == true) add(YCleanupKind.EMPTY_DIRECTORY, child, 0L)
                    queue.add(child)
                    return@forEach
                }
                if (!child.isFile) return@forEach
                val size = child.length().coerceAtLeast(0L)
                val age = (now - child.lastModified()).coerceAtLeast(0L)
                val lowerPath = child.absolutePath.lowercase()
                val lowerName = child.name.lowercase()
                if (size >= LARGE_BYTES) add(YCleanupKind.LARGE, child, size)
                if (age >= OLD_AGE_MS) add(YCleanupKind.OLD, child, size)
                if (child.isHidden || lowerName.startsWith('.')) add(YCleanupKind.HIDDEN, child, size)
                if (lowerName.endsWith(".apk") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk") || lowerName.endsWith(".apkm")) add(YCleanupKind.APK, child, size)
                if (lowerPath.contains("/download/") && age >= OLD_DOWNLOAD_AGE_MS) add(YCleanupKind.OLD_DOWNLOAD, child, size)
                if (lowerPath.contains("screenshot")) add(YCleanupKind.SCREENSHOT, child, size)
                if (lowerPath.contains("screenrecord") || lowerPath.contains("/recordings/") || lowerPath.contains("/recording/")) add(YCleanupKind.RECORDING, child, size)
            }
            if (truncated) break
        }
        val buckets = YCleanupKind.values().associateWith { kind ->
            YCleanupBucket(counts.getOrDefault(kind, 0), bytes.getOrDefault(kind, 0L))
        }
        YCleanupScan(
            buckets = buckets,
            candidates = candidates.distinctBy(YCleanupCandidate::path).sortedByDescending(YCleanupCandidate::size),
            scannedEntries = scanned,
            truncated = truncated,
        )
    }

    fun textPreview(path: String): Result<String> = runCatching {
        readPrefix(requireFile(path), PREVIEW_BYTES).toString(Charsets.UTF_8)
    }

    fun hexPreview(path: String): Result<String> = runCatching {
        val bytes = readPrefix(requireFile(path), HEX_BYTES)
        buildString {
            bytes.toList().chunked(16).forEachIndexed { line, chunk ->
                append(String.format("%08x  ", line * 16))
                append(chunk.joinToString(" ") { "%02x".format(it.toInt() and 0xff) })
                append('\n')
            }
        }.trimEnd()
    }

    fun checksums(path: String): Result<Map<String, String>> = runCatching {
        val file = requireFile(path)
        val digests = linkedMapOf(
            "MD5" to MessageDigest.getInstance("MD5"),
            "SHA-1" to MessageDigest.getInstance("SHA-1"),
            "SHA-256" to MessageDigest.getInstance("SHA-256"),
        )
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digests.values.forEach { it.update(buffer, 0, count) }
            }
        }
        digests.mapValues { (_, digest) -> digest.digest().joinToString("") { "%02x".format(it) } }
    }

    fun linuxInfo(path: String): Result<String> = runCatching {
        val file = File(path)
        require(file.exists() || java.nio.file.Files.isSymbolicLink(file.toPath())) { "Path does not exist" }
        val stat = Os.lstat(file.absolutePath)
        val mode = Integer.toOctalString(stat.st_mode and 0x0fff)
        val link = if (java.nio.file.Files.isSymbolicLink(file.toPath())) runCatching { Os.readlink(file.absolutePath) }.getOrNull() else null
        buildString {
            append("mode=").append(mode)
            append(" uid=").append(stat.st_uid)
            append(" gid=").append(stat.st_gid)
            append(" size=").append(stat.st_size)
            if (link != null) append(" -> ").append(link)
        }
    }

    fun chmod(path: String, rawMode: String): Result<Unit> = runCatching {
        require(rawMode.matches(Regex("[0-7]{3,4}"))) { "Mode must be 3 or 4 octal digits" }
        Os.chmod(path.trim(), rawMode.toInt(8))
    }

    fun createSymlink(targetPath: String, linkPath: String): Result<Unit> = runCatching {
        val target = targetPath.trim()
        val link = linkPath.trim()
        require(target.isNotBlank() && link.isNotBlank()) { "Target and link paths are required" }
        val linkFile = File(link)
        require(!linkFile.exists() && !java.nio.file.Files.isSymbolicLink(linkFile.toPath())) { "Link path already exists" }
        linkFile.parentFile?.let { require(it.mkdirs() || it.isDirectory) { "Unable to create link parent" } }
        Os.symlink(target, link)
    }

    fun inspectApk(context: Context, path: String): Result<YApkDetails> = runCatching {
        val file = requireFile(path)
        require(file.extension.equals("apk", ignoreCase = true)) { "Not an APK file" }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: error("Unable to parse APK")
        val appInfo = info.applicationInfo
        val label = runCatching {
            if (appInfo != null) {
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                context.packageManager.getApplicationLabel(appInfo).toString()
            } else info.packageName
        }.getOrDefault(info.packageName)
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        YApkDetails(label, info.packageName, info.versionName.orEmpty(), code)
    }

    fun installApk(context: Context, path: String): Result<Unit> = runCatching {
        val file = requireFile(path)
        require(file.extension.equals("apk", ignoreCase = true)) { "Not an APK file" }
        val uri = YFilesSuiteRuntime.sharedFileUri(file) ?: error("File sharing URI is unavailable")
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun encrypt(path: String, password: String): Result<File> {
        var output: File? = null
        return runCatching {
            val source = requireFile(path)
            require(password.length >= 6) { "Password must contain at least 6 characters" }
            val random = SecureRandom()
            val salt = ByteArray(16).also { random.nextBytes(it) }
            val iv = ByteArray(12).also { random.nextBytes(it) }
            output = uniqueTarget(source.parentFile ?: error("Missing parent folder"), source.name + ".yenc")
            val target = output!!
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            DataOutputStream(BufferedOutputStream(FileOutputStream(target))).use { raw ->
                raw.write(MAGIC)
                raw.write(salt)
                raw.write(iv)
                CipherOutputStream(raw, cipher).use { encrypted ->
                    BufferedInputStream(FileInputStream(source)).use { input -> input.copyTo(encrypted, 256 * 1024) }
                }
            }
            target
        }.onFailure { output?.let { runCatching { it.delete() } } }
    }

    fun decrypt(path: String, password: String): Result<File> {
        var output: File? = null
        return runCatching {
            val source = requireFile(path)
            require(password.length >= 6) { "Password must contain at least 6 characters" }
            DataInputStream(BufferedInputStream(FileInputStream(source))).use { raw ->
                val magic = ByteArray(MAGIC.size)
                raw.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Not a YFiles encrypted file" }
                val salt = ByteArray(16).also { raw.readFully(it) }
                val iv = ByteArray(12).also { raw.readFully(it) }
                val baseName = source.name.removeSuffix(".yenc").ifBlank { source.name + ".decrypted" }
                output = uniqueTarget(source.parentFile ?: error("Missing parent folder"), baseName)
                val target = output!!
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
                CipherInputStream(raw, cipher).use { decrypted ->
                    BufferedOutputStream(FileOutputStream(target)).use { out -> decrypted.copyTo(out, 256 * 1024) }
                }
                target
            }
        }.onFailure { output?.let { runCatching { it.delete() } } }
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(encoded, "AES")
    }

    private fun requireFile(path: String): File {
        val file = File(path.trim())
        require(file.isFile) { "File does not exist" }
        return file
    }

    private fun readPrefix(file: File, maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8192))
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var remaining = maxBytes
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                if (count > 0) {
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
        }
        return output.toByteArray()
    }

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
fun YFilesPowerToolsCard(context: Context) {
    val scope = rememberCoroutineScope()
    val trashStore = remember(context) { YFilesExtrasStore(context) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scan by remember { mutableStateOf<YCleanupScan?>(null) }
    var toolPath by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    var checksumText by remember { mutableStateOf<String?>(null) }
    var linuxInfo by remember { mutableStateOf<String?>(null) }
    var chmodMode by remember { mutableStateOf("0644") }
    var linkPath by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apkDetails by remember { mutableStateOf<YApkDetails?>(null) }
    var lastOutput by remember { mutableStateOf<String?>(null) }

    fun <T> runIo(block: () -> Result<T>, onSuccess: (T) -> Unit = {}) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            result.onSuccess { value ->
                error = null
                onSuccess(value)
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        YSection(
            title = stringResource(R.string.yfiles_power_cleanup_title),
            subtitle = stringResource(R.string.yfiles_power_cleanup_summary),
        ) {
            YPrimaryButton(
                text = stringResource(R.string.yfiles_power_scan_storage),
                onClick = { runIo(YFilesPowerBackend::scanStorage) { scan = it } },
                enabled = !busy,
            )
            scan?.let { result ->
                YStatusLine(
                    stringResource(R.string.yfiles_power_scanned),
                    result.scannedEntries.toString(),
                    if (result.truncated) YStatusTone.Warning else YStatusTone.Good,
                )
                YCleanupKind.values().forEach { kind ->
                    val bucket = result.buckets[kind] ?: YCleanupBucket()
                    if (bucket.count > 0) {
                        YStatusLine(
                            cleanupKindLabel(kind),
                            stringResource(R.string.yfiles_power_count_size, bucket.count, formatExtraBytes(bucket.bytes)),
                            YStatusTone.Neutral,
                        )
                    }
                }
                result.candidates.take(6).forEach { candidate ->
                    val file = File(candidate.path)
                    YHorizontalActions {
                        Text(file.name.ifBlank { candidate.path })
                        YSecondaryButton(
                            text = stringResource(R.string.move_to_recycle_bin),
                            onClick = {
                                runIo({ trashStore.moveToTrash(file.toEntry()) }) {
                                    scan = scan?.copy(candidates = scan!!.candidates.filterNot { it.path == candidate.path })
                                }
                            },
                            enabled = !busy,
                        )
                    }
                }
                if (result.truncated) Text(stringResource(R.string.yfiles_power_scan_truncated))
            }
        }

        YSection(
            title = stringResource(R.string.yfiles_power_lab_title),
            subtitle = stringResource(R.string.yfiles_power_lab_summary),
        ) {
            YTextField(
                value = toolPath,
                onValueChange = { toolPath = it; preview = null; checksumText = null; linuxInfo = null; apkDetails = null },
                label = stringResource(R.string.yfiles_power_path),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            YHorizontalActions {
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_text_preview),
                    onClick = { runIo({ YFilesPowerBackend.textPreview(toolPath) }) { preview = it } },
                    enabled = !busy && toolPath.isNotBlank(),
                )
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_hex_preview),
                    onClick = { runIo({ YFilesPowerBackend.hexPreview(toolPath) }) { preview = it } },
                    enabled = !busy && toolPath.isNotBlank(),
                )
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_checksums),
                    onClick = {
                    runIo({ YFilesPowerBackend.checksums(toolPath) }) { map ->
                        checksumText = map.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    }
                },
                    enabled = !busy && toolPath.isNotBlank(),
                )
            }
            YHorizontalActions {
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_linux_info),
                    onClick = { runIo({ YFilesPowerBackend.linuxInfo(toolPath) }) { linuxInfo = it } },
                    enabled = !busy && toolPath.isNotBlank(),
                )
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_copy_path),
                    onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("path", toolPath))
                    lastOutput = toolPath
                },
                    enabled = toolPath.isNotBlank(),
                )
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_apk_info),
                    onClick = { runIo({ YFilesPowerBackend.inspectApk(context, toolPath) }) { apkDetails = it } },
                    enabled = !busy && toolPath.isNotBlank(),
                )
            }
            apkDetails?.let { details ->
                YStatusLine(details.label, details.packageName, YStatusTone.Good)
                Text(stringResource(R.string.yfiles_power_apk_version, details.versionName, details.versionCode))
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_install_apk),
                    onClick = { runIo({ YFilesPowerBackend.installApk(context, toolPath) }) },
                    enabled = !busy,
                )
            }
            preview?.let { Text(it) }
            checksumText?.let { Text(it) }
            linuxInfo?.let { Text(it) }

            YTextField(
                value = chmodMode,
                onValueChange = { chmodMode = it.take(4) },
                label = stringResource(R.string.yfiles_power_chmod_mode),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            YTextField(
                value = linkPath,
                onValueChange = { linkPath = it },
                label = stringResource(R.string.yfiles_power_link_path),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            YHorizontalActions {
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_apply_chmod),
                    onClick = { runIo({ YFilesPowerBackend.chmod(toolPath, chmodMode) }) { linuxInfo = null } },
                    enabled = !busy && toolPath.isNotBlank(),
                )
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_create_symlink),
                    onClick = { runIo({ YFilesPowerBackend.createSymlink(toolPath, linkPath) }) { lastOutput = linkPath } },
                    enabled = !busy && toolPath.isNotBlank() && linkPath.isNotBlank(),
                )
            }

            YTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.yfiles_power_password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            YHorizontalActions {
                YPrimaryButton(
                    text = stringResource(R.string.yfiles_power_encrypt),
                    onClick = { runIo({ YFilesPowerBackend.encrypt(toolPath, password) }) { lastOutput = it.absolutePath } },
                    enabled = !busy && toolPath.isNotBlank() && password.length >= 6,
                )
                YSecondaryButton(
                    text = stringResource(R.string.yfiles_power_decrypt),
                    onClick = { runIo({ YFilesPowerBackend.decrypt(toolPath, password) }) { lastOutput = it.absolutePath } },
                    enabled = !busy && toolPath.isNotBlank() && password.length >= 6,
                )
            }
            lastOutput?.let { Text(stringResource(R.string.yfiles_power_output, it)) }
            error?.let { Text(it) }
        }
    }
}

@Composable
private fun cleanupKindLabel(kind: YCleanupKind): String = when (kind) {
    YCleanupKind.LARGE -> stringResource(R.string.yfiles_power_large_files)
    YCleanupKind.OLD -> stringResource(R.string.yfiles_power_old_files)
    YCleanupKind.HIDDEN -> stringResource(R.string.yfiles_power_hidden_files)
    YCleanupKind.EMPTY_DIRECTORY -> stringResource(R.string.yfiles_power_empty_dirs)
    YCleanupKind.APK -> stringResource(R.string.yfiles_power_apk_files)
    YCleanupKind.OLD_DOWNLOAD -> stringResource(R.string.yfiles_power_old_downloads)
    YCleanupKind.SCREENSHOT -> stringResource(R.string.yfiles_power_screenshots)
    YCleanupKind.RECORDING -> stringResource(R.string.yfiles_power_recordings)
}
