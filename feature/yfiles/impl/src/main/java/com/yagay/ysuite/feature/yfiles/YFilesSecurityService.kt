package com.yagay.ysuite.feature.yfiles

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class YIntegrityWatch(
    val ref: YFileRef,
    val expectedSha256: String,
    val addedAtMillis: Long,
)

enum class YIntegrityStatus {
    Unchanged,
    Changed,
    Missing,
    Unreadable,
}

data class YIntegrityResult(
    val watch: YIntegrityWatch,
    val status: YIntegrityStatus,
    val actualSha256: String? = null,
)

data class YVaultEntry(
    val id: String,
    val originalName: String,
    val sizeBytes: Long,
    val addedAtMillis: Long,
)

class YFilesSecurityService(
    context: Context,
    private val engine: YFilesEngine,
    private val cacheDirectory: File,
) {
    private val appContext =
        context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            "yfiles_security",
            Context.MODE_PRIVATE,
        )
    private val random = SecureRandom()
    private val vaultDirectory =
        File(
            appContext.noBackupFilesDir,
            "yfiles-vault",
        ).apply { mkdirs() }

    suspend fun encryptFile(
        source: YFileRef,
        destination: YFileRef,
        outputName: String? = null,
    ): Outcome<YFileNode> =
        transformToDestination(
            source = source,
            destination = destination,
            outputName =
                outputName
                    ?: source.path
                        .substringAfterLast('/')
                        .ifBlank { "file" } +
                        ".encrypted",
            encrypt = true,
        )

    suspend fun decryptFile(
        source: YFileRef,
        destination: YFileRef,
        outputName: String? = null,
    ): Outcome<YFileNode> =
        transformToDestination(
            source = source,
            destination = destination,
            outputName =
                outputName
                    ?: source.path
                        .substringAfterLast('/')
                        .removeSuffix(
                            ".encrypted",
                        )
                        .ifBlank {
                            "decrypted-file"
                        },
            encrypt = false,
        )

    suspend fun secureDelete(
        ref: YFileRef,
        passes: Int = 1,
    ): Outcome<Unit> =
        withContext<Outcome<Unit>>(
            Dispatchers.IO,
        ) {
            if (ref.providerId != "local") {
                return@withContext
                    Outcome.Failure(
                        code =
                            "secure_delete_unsupported",
                        message =
                            appContext.getString(
                                R.string.yfiles_msg_secure_delete_local_only,
                            ),
                    )
            }
            try {
                val file = File(ref.path)
                require(file.isFile) {
                    "Secure delete requires a regular file"
                }
                val length = file.length()
                RandomAccessFile(
                    file,
                    "rw",
                ).use { raf ->
                    repeat(
                        passes.coerceIn(1, 3),
                    ) {
                        raf.seek(0L)
                        var remaining = length
                        val buffer =
                            ByteArray(
                                BUFFER_SIZE,
                            )
                        while (remaining > 0L) {
                            random.nextBytes(buffer)
                            val count =
                                minOf(
                                    remaining,
                                    buffer.size
                                        .toLong(),
                                ).toInt()
                            raf.write(
                                buffer,
                                0,
                                count,
                            )
                            remaining -= count
                        }
                        raf.fd.sync()
                    }
                }
                require(file.delete()) {
                    "Unable to delete overwritten file"
                }
                Outcome.Success(Unit)
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "secure_delete_failed",
                    message =
                        error.message
                            ?: "Secure delete failed",
                    cause = error,
                )
            }
        }

    suspend fun addIntegrityWatch(
        ref: YFileRef,
    ): Outcome<YIntegrityWatch> =
        when (
            val hash = sha256(ref)
        ) {
            is Outcome.Success -> {
                val watch =
                    YIntegrityWatch(
                        ref = ref,
                        expectedSha256 =
                            hash.value,
                        addedAtMillis =
                            System.currentTimeMillis(),
                    )
                val all =
                    integrityWatches()
                        .filterNot {
                            it.ref == ref
                        } + watch
                saveWatches(all)
                Outcome.Success(watch)
            }
            is Outcome.Failure -> hash
        }

    fun removeIntegrityWatch(
        ref: YFileRef,
    ) {
        saveWatches(
            integrityWatches()
                .filterNot {
                    it.ref == ref
                },
        )
    }

    fun integrityWatches():
        List<YIntegrityWatch> =
        runCatching {
            val array =
                JSONArray(
                    prefs.getString(
                        KEY_WATCHES,
                        "[]",
                    ),
                )
            buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.getJSONObject(
                            index,
                        )
                    add(
                        YIntegrityWatch(
                            ref =
                                YFileRef(
                                    item.getString(
                                        "provider",
                                    ),
                                    item.getString(
                                        "path",
                                    ),
                                ),
                            expectedSha256 =
                                item.getString(
                                    "sha256",
                                ),
                            addedAtMillis =
                                item.getLong(
                                    "addedAt",
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    suspend fun scanIntegrity():
        List<YIntegrityResult> =
        integrityWatches().map { watch ->
            when (
                val hash =
                    sha256(watch.ref)
            ) {
                is Outcome.Success ->
                    YIntegrityResult(
                        watch = watch,
                        status =
                            if (
                                hash.value.equals(
                                    watch.expectedSha256,
                                    ignoreCase = true,
                                )
                            ) {
                                YIntegrityStatus
                                    .Unchanged
                            } else {
                                YIntegrityStatus
                                    .Changed
                            },
                        actualSha256 =
                            hash.value,
                    )
                is Outcome.Failure ->
                    YIntegrityResult(
                        watch = watch,
                        status =
                            if (
                                hash.error.code
                                    .contains(
                                        "not_found",
                                        true,
                                    ) ||
                                hash.message
                                    .contains(
                                        "exist",
                                        true,
                                    )
                            ) {
                                YIntegrityStatus
                                    .Missing
                            } else {
                                YIntegrityStatus
                                    .Unreadable
                            },
                    )
            }
        }

    suspend fun vaultAdd(
        source: YFileRef,
        originalName: String,
    ): Outcome<YVaultEntry> =
        withContext(Dispatchers.IO) {
            try {
                val plain =
                    materialize(
                        source,
                        "vault-source",
                    )
                val id =
                    UUID.randomUUID()
                        .toString()
                val target =
                    File(
                        vaultDirectory,
                        "$id.bin",
                    )
                encryptLocal(
                    plain,
                    target,
                    vaultKey(),
                )
                val entry =
                    YVaultEntry(
                        id = id,
                        originalName =
                            originalName,
                        sizeBytes =
                            plain.length(),
                        addedAtMillis =
                            System.currentTimeMillis(),
                    )
                saveVaultIndex(
                    vaultEntries() + entry,
                )
                if (
                    source.providerId !=
                    "local"
                ) {
                    plain.delete()
                }
                Outcome.Success(entry)
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "vault_add_failed",
                    message =
                        error.message
                            ?: "Unable to add file to vault",
                    cause = error,
                )
            }
        }

    suspend fun vaultRestore(
        id: String,
        destination: YFileRef,
    ): Outcome<YFileNode> =
        withContext(Dispatchers.IO) {
            try {
                val entry =
                    vaultEntries()
                        .firstOrNull {
                            it.id == id
                        }
                        ?: error(
                            "Vault entry does not exist",
                        )
                val encrypted =
                    File(
                        vaultDirectory,
                        "$id.bin",
                    )
                require(encrypted.isFile) {
                    "Vault payload is missing"
                }
                val plain =
                    File(
                        cacheDirectory,
                        "vault-restore-$id.tmp",
                    )
                plain.parentFile?.mkdirs()
                decryptLocal(
                    encrypted,
                    plain,
                    vaultKey(),
                )
                val created =
                    createFromFile(
                        plain,
                        destination,
                        entry.originalName,
                    )
                plain.delete()
                created
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "vault_restore_failed",
                    message =
                        error.message
                            ?: "Unable to restore vault item",
                    cause = error,
                )
            }
        }

    fun vaultEntries():
        List<YVaultEntry> =
        runCatching {
            val json =
                decryptString(
                    prefs.getString(
                        KEY_VAULT_INDEX,
                        null,
                    ).orEmpty(),
                    vaultKey(),
                )
            if (json.isBlank()) {
                return@runCatching emptyList()
            }
            val array = JSONArray(json)
            buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.getJSONObject(index)
                    add(
                        YVaultEntry(
                            id =
                                item.getString(
                                    "id",
                                ),
                            originalName =
                                item.getString(
                                    "name",
                                ),
                            sizeBytes =
                                item.getLong(
                                    "size",
                                ),
                            addedAtMillis =
                                item.getLong(
                                    "addedAt",
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun vaultDelete(id: String): Boolean {
        val payload =
            File(
                vaultDirectory,
                "$id.bin",
            )
        val removed =
            !payload.exists() ||
                payload.delete()
        if (removed) {
            saveVaultIndex(
                vaultEntries()
                    .filterNot {
                        it.id == id
                    },
            )
        }
        return removed
    }

    fun biometricAvailable(): Boolean {
        val manager =
            BiometricManager.from(
                appContext,
            )
        return manager.canAuthenticate(
            BiometricManager
                .Authenticators
                .BIOMETRIC_STRONG or
                BiometricManager
                    .Authenticators
                    .DEVICE_CREDENTIAL,
        ) ==
            BiometricManager
                .BIOMETRIC_SUCCESS
    }

    private suspend fun transformToDestination(
        source: YFileRef,
        destination: YFileRef,
        outputName: String,
        encrypt: Boolean,
    ): Outcome<YFileNode> =
        withContext(Dispatchers.IO) {
            try {
                require(
                    cacheDirectory.mkdirs() ||
                        cacheDirectory.isDirectory,
                )
                val input =
                    materialize(
                        source,
                        "crypto-input",
                    )
                val transformed =
                    File(
                        cacheDirectory,
                        "crypto-" +
                            UUID.randomUUID() +
                            ".tmp",
                    )
                if (encrypt) {
                    encryptLocal(
                        input,
                        transformed,
                        fileKey(),
                    )
                } else {
                    decryptLocal(
                        input,
                        transformed,
                        fileKey(),
                    )
                }
                val result =
                    createFromFile(
                        transformed,
                        destination,
                        outputName,
                    )
                if (
                    source.providerId !=
                    "local"
                ) {
                    input.delete()
                }
                transformed.delete()
                result
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        if (encrypt) {
                            "encrypt_failed"
                        } else {
                            "decrypt_failed"
                        },
                    message =
                        error.message
                            ?: "Cryptographic file operation failed",
                    cause = error,
                )
            }
        }

    private suspend fun createFromFile(
        file: File,
        destination: YFileRef,
        name: String,
    ): Outcome<YFileNode> {
        val created =
            engine.createFile(
                destination,
                safeName(name),
            )
        if (created is Outcome.Failure) {
            return created
        }
        created as Outcome.Success
        FileInputStream(file).use { input ->
            var offset = 0L
            var first = true
            val buffer =
                ByteArray(BUFFER_SIZE)
            while (true) {
                val count =
                    input.read(buffer)
                if (count < 0) break
                val write =
                    engine.write(
                        ref =
                            created.value.ref,
                        offset = offset,
                        data =
                            buffer.copyOf(
                                count,
                            ),
                        truncate = first,
                    )
                if (write is Outcome.Failure) {
                    engine.delete(
                        created.value.ref,
                    )
                    return write
                }
                first = false
                offset += count
            }
        }
        return engine.stat(
            created.value.ref,
        )
    }

    private suspend fun materialize(
        ref: YFileRef,
        prefix: String,
    ): File {
        if (ref.providerId == "local") {
            return File(ref.path)
        }
        require(
            cacheDirectory.mkdirs() ||
                cacheDirectory.isDirectory,
        )
        val target =
            File(
                cacheDirectory,
                "$prefix-" +
                    UUID.randomUUID() +
                    ".tmp",
            )
        FileOutputStream(target).use {
            output ->
            var offset = 0L
            while (true) {
                when (
                    val chunk =
                        engine.read(
                            ref,
                            offset,
                            BUFFER_SIZE,
                        )
                ) {
                    is Outcome.Success -> {
                        output.write(
                            chunk.value.data,
                        )
                        offset +=
                            chunk.value
                                .data.size
                        if (chunk.value.eof) {
                            break
                        }
                    }
                    is Outcome.Failure ->
                        error(
                            chunk.message,
                        )
                }
            }
        }
        return target
    }

    private suspend fun sha256(
        ref: YFileRef,
    ): Outcome<String> =
        withContext<Outcome<String>>(
            Dispatchers.IO,
        ) {
            try {
                val digest =
                    MessageDigest.getInstance(
                        "SHA-256",
                    )
                var offset = 0L
                while (true) {
                    when (
                        val chunk =
                            engine.read(
                                ref,
                                offset,
                                BUFFER_SIZE,
                            )
                    ) {
                        is Outcome.Success -> {
                            digest.update(
                                chunk.value.data,
                            )
                            offset +=
                                chunk.value
                                    .data.size
                            if (
                                chunk.value.eof
                            ) {
                                break
                            }
                        }
                        is Outcome.Failure ->
                            return@withContext
                                chunk
                    }
                }
                Outcome.Success(
                    digest.digest()
                        .joinToString("") {
                            "%02x".format(it)
                        },
                )
            } catch (error: Throwable) {
                Outcome.Failure(
                    code = "hash_failed",
                    message =
                        error.message
                            ?: "Unable to hash file",
                    cause = error,
                )
            }
        }

    private fun encryptLocal(
        source: File,
        target: File,
        key: SecretKey,
    ) {
        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding",
            )
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
        )
        DataOutputStream(
            FileOutputStream(target),
        ).use { output ->
            output.write(MAGIC)
            output.writeInt(
                cipher.iv.size,
            )
            output.write(cipher.iv)
            CipherOutputStream(
                output,
                cipher,
            ).use {
                encrypted ->
                FileInputStream(source)
                    .use { input ->
                        input.copyTo(
                            encrypted,
                            BUFFER_SIZE,
                        )
                    }
            }
        }
    }

    private fun decryptLocal(
        source: File,
        target: File,
        key: SecretKey,
    ) {
        DataInputStream(
            FileInputStream(source),
        ).use { input ->
            val magic =
                ByteArray(MAGIC.size)
            input.readFully(magic)
            require(
                magic.contentEquals(MAGIC),
            ) {
                "Not a YFiles encrypted file"
            }
            val ivSize =
                input.readInt()
            require(
                ivSize in 12..32,
            ) {
                "Invalid encrypted file header"
            }
            val iv =
                ByteArray(ivSize)
            input.readFully(iv)
            val cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding",
                )
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(
                    128,
                    iv,
                ),
            )
            CipherInputStream(
                input,
                cipher,
            ).use {
                decrypted ->
                FileOutputStream(target)
                    .use { output ->
                        decrypted.copyTo(
                            output,
                            BUFFER_SIZE,
                        )
                    }
            }
        }
    }

    private fun fileKey(): SecretKey =
        key(FILE_KEY_ALIAS)

    private fun vaultKey(): SecretKey =
        key(VAULT_KEY_ALIAS)

    private fun key(alias: String): SecretKey {
        val store =
            KeyStore.getInstance(
                "AndroidKeyStore",
            ).apply { load(null) }
        val existing =
            store.getKey(
                alias,
                null,
            ) as? SecretKey
        if (existing != null) {
            return existing
        }
        val generator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties
                    .PURPOSE_ENCRYPT or
                    KeyProperties
                        .PURPOSE_DECRYPT,
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM,
                )
                .setEncryptionPaddings(
                    KeyProperties
                        .ENCRYPTION_PADDING_NONE,
                )
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun saveWatches(
        watches: List<YIntegrityWatch>,
    ) {
        val array = JSONArray()
        watches.forEach { watch ->
            array.put(
                JSONObject()
                    .put(
                        "provider",
                        watch.ref.providerId,
                    )
                    .put(
                        "path",
                        watch.ref.path,
                    )
                    .put(
                        "sha256",
                        watch.expectedSha256,
                    )
                    .put(
                        "addedAt",
                        watch.addedAtMillis,
                    ),
            )
        }
        prefs.edit()
            .putString(
                KEY_WATCHES,
                array.toString(),
            )
            .apply()
    }

    private fun saveVaultIndex(
        entries: List<YVaultEntry>,
    ) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put(
                        "name",
                        entry.originalName,
                    )
                    .put(
                        "size",
                        entry.sizeBytes,
                    )
                    .put(
                        "addedAt",
                        entry.addedAtMillis,
                    ),
            )
        }
        prefs.edit()
            .putString(
                KEY_VAULT_INDEX,
                encryptString(
                    array.toString(),
                    vaultKey(),
                ),
            )
            .apply()
    }

    private fun encryptString(
        value: String,
        key: SecretKey,
    ): String {
        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding",
            )
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
        )
        val encrypted =
            cipher.doFinal(
                value.toByteArray(
                    Charsets.UTF_8,
                ),
            )
        return android.util.Base64
            .encodeToString(
                cipher.iv + encrypted,
                android.util.Base64
                    .NO_WRAP,
            )
    }

    private fun decryptString(
        value: String,
        key: SecretKey,
    ): String {
        if (value.isBlank()) return ""
        val raw =
            android.util.Base64.decode(
                value,
                android.util.Base64.NO_WRAP,
            )
        require(raw.size > 12)
        val iv =
            raw.copyOfRange(0, 12)
        val encrypted =
            raw.copyOfRange(
                12,
                raw.size,
            )
        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding",
            )
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, iv),
        )
        return String(
            cipher.doFinal(encrypted),
            Charsets.UTF_8,
        )
    }

    private fun safeName(
        value: String,
    ): String =
        value.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')
            .ifBlank { "file" }

    companion object {
        private const val FILE_KEY_ALIAS =
            "ysuite_yfiles_file_crypto"
        private const val VAULT_KEY_ALIAS =
            "ysuite_yfiles_vault"
        private const val KEY_WATCHES =
            "integrity_watches"
        private const val KEY_VAULT_INDEX =
            "vault_index"
        private const val BUFFER_SIZE =
            128 * 1024
        private val MAGIC =
            byteArrayOf(
                'Y'.code.toByte(),
                'F'.code.toByte(),
                'E'.code.toByte(),
                'N'.code.toByte(),
                'C'.code.toByte(),
                '1'.code.toByte(),
            )
    }
}
