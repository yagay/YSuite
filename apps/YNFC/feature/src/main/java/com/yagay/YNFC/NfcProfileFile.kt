package com.yagay.YNFC

import org.json.JSONObject
import java.io.InputStream

/**
 * A file is an offline profile, not an executable script or an NFC controller
 * configuration. Only three explicit NFC-A parameters are accepted and copied
 * into the existing per-card profile store. The NFC Hook never reads user files.
 */
internal object NfcProfileFile {
    private const val MAX_FILE_BYTES = 4096

    fun importProfile(input: InputStream): CardModel {
        val content = input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (out.size() + count > MAX_FILE_BYTES) {
                    throw IllegalArgumentException("Profile file exceeds 4096 bytes")
                }
                out.write(buffer, 0, count)
            }
            out.toString(Charsets.UTF_8.name())
        }
        val obj = JSONObject(content)
        require(obj.optString("type") == "NFC_A") { "Profile type must be NFC_A" }
        val uid = normalizedHex(obj.getString("uid"), 4, 7, 10)
        val atqa = normalizedHex(obj.getString("atqa"), 2)
        val sak = normalizedHex(obj.getString("sak"), 1)
        val name = obj.optString("name", "Imported NFC-A").trim()
        require(name.isNotEmpty() && name.length <= 48) { "Profile name must be 1–48 characters" }
        return CardModel(name, uid, sak, atqa)
    }

    private fun normalizedHex(value: String, vararg sizes: Int): String {
        val hex = value.replace(Regex("[\\s:.-]"), "").uppercase(java.util.Locale.ROOT)
        require(hex.matches(Regex("[0-9A-F]+")) && sizes.any { it * 2 == hex.length }) {
            "Invalid NFC-A hex field: expected ${sizes.joinToString("/")} bytes"
        }
        return hex
    }
}
