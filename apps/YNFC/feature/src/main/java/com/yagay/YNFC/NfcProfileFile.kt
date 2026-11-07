package com.yagay.YNFC

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * A file is an offline profile, not an executable script or an NFC controller
 * configuration. Only three explicit NFC-A parameters are accepted and copied
 * into the existing per-card profile store. The NFC Hook never reads user files.
 */
internal object NfcProfileFile {
    private const val MAX_FILE_BYTES = 4096
    private const val PROFILE_DIR = "ynfc"
    private const val SELECTED_PROFILE_FILE = "selected-nfc-a.json"

    /**
     * Generate a private JSON profile from a card already owned by this app.
     * Always read back the exact bytes written before the hook command is
     * published, so an incomplete write can never activate stale parameters.
     * com.android.nfc reads the validated Provider fields, not an app-private file.
     */
    fun createForCard(context: Context, card: CardModel): CardModel {
        val bytes = encodeCard(card)
        val directory = File(context.filesDir, PROFILE_DIR)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create private NFC-A profile directory")
        }
        val selected = File(directory, SELECTED_PROFILE_FILE)
        val temporary = File.createTempFile("nfc-a-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            if (!temporary.renameTo(selected)) {
                throw IOException("Could not save the selected NFC-A profile")
            }
            return selected.inputStream().use(::importProfile)
        } finally {
            temporary.delete()
        }
    }

    /** Pure serialization used for JVM unit tests and file creation. */
    fun encodeCard(card: CardModel): ByteArray {
        val name = card.name.trim()
        require(name.isNotEmpty() && name.length <= 48) { "Card name must be 1–48 characters" }
        val uid = normalizedHex(card.uid, 4, 7, 10)
        val atqa = normalizedHex(card.atqa, 2)
        val sak = normalizedHex(card.sak, 1)
        val obj = JsonObject().apply {
            addProperty("type", "NFC_A")
            addProperty("name", name)
            addProperty("uid", uid)
            addProperty("atqa", atqa)
            addProperty("sak", sak)
        }
        val bytes = obj.toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_FILE_BYTES) { "Generated profile file is too large" }
        // Re-use the importer for a single validation contract in both paths.
        importProfile(ByteArrayInputStream(bytes))
        return bytes
    }

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
