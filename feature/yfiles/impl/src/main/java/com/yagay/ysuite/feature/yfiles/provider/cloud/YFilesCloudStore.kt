package com.yagay.ysuite.feature.yfiles.provider.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

enum class YFilesCloudKind {
    GoogleDrive,
    Dropbox,
    OneDrive,
}

data class YFilesCloudProfile(
    val id: String =
        UUID.randomUUID().toString(),
    val name: String,
    val kind: YFilesCloudKind,
    val accessToken: String,
)

class YFilesCloudStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                "yfiles_cloud_profiles",
                Context.MODE_PRIVATE,
            )

    fun profiles():
        List<YFilesCloudProfile> =
        runCatching {
            val array =
                JSONArray(
                    prefs.getString(
                        KEY_PROFILES,
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
                        YFilesCloudProfile(
                            id =
                                item.getString(
                                    "id",
                                ),
                            name =
                                item.getString(
                                    "name",
                                ),
                            kind =
                                YFilesCloudKind
                                    .valueOf(
                                        item.getString(
                                            "kind",
                                        ),
                                    ),
                            accessToken =
                                decrypt(
                                    item.getString(
                                        "token",
                                    ),
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun profile(id: String):
        YFilesCloudProfile? =
        profiles().firstOrNull {
            it.id == id
        }

    fun save(profile: YFilesCloudProfile) {
        val next =
            profiles()
                .filterNot {
                    it.id == profile.id
                } + profile
        persist(next)
    }

    fun delete(id: String) {
        persist(
            profiles().filterNot {
                it.id == id
            },
        )
    }

    private fun persist(
        profiles: List<YFilesCloudProfile>,
    ) {
        val array = JSONArray()
        profiles.sortedBy {
            it.name.lowercase()
        }.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put(
                        "name",
                        profile.name,
                    )
                    .put(
                        "kind",
                        profile.kind.name,
                    )
                    .put(
                        "token",
                        encrypt(
                            profile.accessToken,
                        ),
                    ),
            )
        }
        prefs.edit()
            .putString(
                KEY_PROFILES,
                array.toString(),
            )
            .apply()
    }

    private fun encrypt(
        value: String,
    ): String {
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

    private fun decrypt(
        value: String,
    ): String {
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
                KEY_ALIAS,
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
                KEY_ALIAS,
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

    companion object {
        private const val KEY_PROFILES =
            "profiles"
        private const val KEY_ALIAS =
            "ysuite_yfiles_cloud"
        private const val IV_SIZE = 12
    }
}
