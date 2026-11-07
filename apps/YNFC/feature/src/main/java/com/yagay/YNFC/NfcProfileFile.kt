package com.yagay.YNFC

import com.google.gson.JsonParser
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
        val root = JsonParser.parseString(content)
        require(root.isJsonObject) { "Expected JSON object" }
        val obj = root.asJsonObject
        require(obj.get("type")?.asString == "NFC_A") { "Profile type must be NFC_A" }
        val uid = normalizedHex(obj.get("uid")?.asString
            ?: throw IllegalArgumentException("Missing UID"), 4, 7, 10)
        val atqa = normalizedHex(obj.get("atqa")?.asString
            ?: throw IllegalArgumentException("Missing ATQA"), 2)
        val sak = normalizedHex(obj.get("sak")?.asString
            ?: throw IllegalArgumentException("Missing SAK"), 1)
        val name = obj.get("name")?.asString?.trim().orEmpty().ifBlank { "Imported NFC-A" }
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
