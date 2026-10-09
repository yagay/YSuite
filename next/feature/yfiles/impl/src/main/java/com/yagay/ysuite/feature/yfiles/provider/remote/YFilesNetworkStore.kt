package com.yagay.ysuite.feature.yfiles.provider.remote

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

enum class YFilesNetworkProtocol(
    val defaultPort: Int,
) {
    SMB(445),
    SFTP(22),
    FTP(21),
    FTPS(990),
    WebDAV(443),
}

data class YFilesNetworkProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: YFilesNetworkProtocol,
    val host: String,
    val port: Int = protocol.defaultPort,
    val username: String = "",
    val password: String = "",
    val shareName: String = "",
    val remotePath: String = "/",
    val privateKeyPath: String = "",
    val useTls: Boolean =
        protocol == YFilesNetworkProtocol.FTPS ||
            protocol == YFilesNetworkProtocol.WebDAV,
)

class YFilesNetworkStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            "yfiles_network_profiles",
            Context.MODE_PRIVATE,
        )
    private val cipher = CredentialCipher()

    fun profiles(): List<YFilesNetworkProfile> =
        runCatching {
            val array =
                JSONArray(
                    prefs.getString(KEY_PROFILES, "[]"),
                )
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        YFilesNetworkProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            protocol =
                                YFilesNetworkProtocol
                                    .valueOf(
                                        item.getString(
                                            "protocol",
                                        ),
                                    ),
                            host = item.getString("host"),
                            port = item.getInt("port"),
                            username =
                                item.optString(
                                    "username",
                                ),
                            password =
                                cipher.decrypt(
                                    item.optString(
                                        "password",
                                    ),
                                ),
                            shareName =
                                item.optString(
                                    "shareName",
                                ),
                            remotePath =
                                item.optString(
                                    "remotePath",
                                    "/",
                                ),
                            privateKeyPath =
                                item.optString(
                                    "privateKeyPath",
                                ),
                            useTls =
                                item.optBoolean(
                                    "useTls",
                                    false,
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun profile(id: String):
        YFilesNetworkProfile? =
        profiles().firstOrNull { it.id == id }

    fun save(profile: YFilesNetworkProfile) {
        val all =
            profiles()
                .filterNot { it.id == profile.id }
                .toMutableList()
                .apply { add(profile) }
                .sortedBy { it.name.lowercase() }
        persist(all)
    }

    fun delete(id: String) {
        persist(
            profiles().filterNot { it.id == id },
        )
    }

    private fun persist(
        profiles: List<YFilesNetworkProfile>,
    ) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put(
                        "protocol",
                        profile.protocol.name,
                    )
                    .put("host", profile.host)
                    .put("port", profile.port)
                    .put(
                        "username",
                        profile.username,
                    )
                    .put(
                        "password",
                        cipher.encrypt(
                            profile.password,
                        ),
                    )
                    .put(
                        "shareName",
                        profile.shareName,
                    )
                    .put(
                        "remotePath",
                        profile.remotePath,
                    )
                    .put(
                        "privateKeyPath",
                        profile.privateKeyPath,
                    )
                    .put("useTls", profile.useTls),
            )
        }
        prefs.edit()
            .putString(
                KEY_PROFILES,
                array.toString(),
            )
            .apply()
    }

    private class CredentialCipher {
        private val key: SecretKey
            get() {
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
                        KeyProperties
                            .KEY_ALGORITHM_AES,
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
                            KeyProperties
                                .BLOCK_MODE_GCM,
                        )
                        .setEncryptionPaddings(
                            KeyProperties
                                .ENCRYPTION_PADDING_NONE,
                        )
                        .build(),
                )
                return generator.generateKey()
            }

        fun encrypt(value: String): String {
            if (value.isEmpty()) return ""
            return runCatching {
                val cipher =
                    Cipher.getInstance(
                        "AES/GCM/NoPadding",
                    )
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                )
                val data =
                    cipher.doFinal(
                        value.toByteArray(
                            Charsets.UTF_8,
                        ),
                    )
                Base64.encodeToString(
                    cipher.iv + data,
                    Base64.NO_WRAP,
                )
            }.getOrDefault("")
        }

        fun decrypt(value: String): String {
            if (value.isEmpty()) return ""
            return runCatching {
                val raw =
                    Base64.decode(
                        value,
                        Base64.NO_WRAP,
                    )
                require(raw.size > IV_BYTES)
                val iv =
                    raw.copyOfRange(
                        0,
                        IV_BYTES,
                    )
                val encrypted =
                    raw.copyOfRange(
                        IV_BYTES,
                        raw.size,
                    )
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
                String(
                    cipher.doFinal(encrypted),
                    Charsets.UTF_8,
                )
            }.getOrDefault("")
        }

        companion object {
            private const val KEY_ALIAS =
                "ysuite_yfiles_network"
            private const val IV_BYTES = 12
        }
    }

    companion object {
        private const val KEY_PROFILES =
            "profiles_json"
    }
}
