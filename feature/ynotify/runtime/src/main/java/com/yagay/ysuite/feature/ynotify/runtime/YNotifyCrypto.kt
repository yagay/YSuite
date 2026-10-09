package com.yagay.ysuite.feature.ynotify.runtime

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class YNotifyCrypto {
    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty() || value.startsWith(PREFIX)) return value
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            PREFIX +
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (error: Exception) {
            // Never silently put sensitive notification text into the DB as
            // plaintext when AndroidKeyStore is unavailable.
            throw IllegalStateException("notification_content_encryption_failed", error)
        }
    }

    fun decrypt(value: String?): String? {
        if (value == null || !value.startsWith(PREFIX)) return value
        return runCatching {
            val body = value.removePrefix(PREFIX)
            val split = body.indexOf(':')
            require(split > 0)
            val iv = Base64.decode(body.substring(0, split), Base64.NO_WRAP)
            val payload = Base64.decode(body.substring(split + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrDefault("[encrypted content unavailable]")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFIX = "enc:v1:"
        private const val ALIAS = "ysuite_ynotify_content_v1"
    }
}
