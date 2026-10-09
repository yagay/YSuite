package com.yagay.ysuite.feature.yfiles

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class YFilesSecretCipher(
    private val alias: String,
) {
    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding",
            )
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key(),
        )
        val encrypted =
            cipher.doFinal(
                value.toByteArray(
                    Charsets.UTF_8,
                ),
            )
        return Base64.encodeToString(
            cipher.iv + encrypted,
            Base64.NO_WRAP,
        )
    }

    fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val raw =
                Base64.decode(
                    value,
                    Base64.NO_WRAP,
                )
            require(raw.size > IV_SIZE)
            val iv =
                raw.copyOfRange(
                    0,
                    IV_SIZE,
                )
            val cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding",
                )
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(
                    128,
                    iv,
                ),
            )
            String(
                cipher.doFinal(
                    raw.copyOfRange(
                        IV_SIZE,
                        raw.size,
                    ),
                ),
                Charsets.UTF_8,
            )
        }.getOrDefault("")
    }

    private fun key(): SecretKey {
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

    private companion object {
        const val IV_SIZE = 12
    }
}
