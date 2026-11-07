package com.yagay.YNFC

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Offline, fail-closed preview. This does NOT write the controller and is never
 * consumed by the NFC HAL. The original file remains byte-for-byte unchanged.
 */
internal object Sn220ConfigPreview {
    private val coreBlock = Regex("""(?m)^([ \t]*NXP_CORE_CONF[ \t]*=[ \t]*\{)([^}]*)(\})""")
    private val byteToken = Regex("""(?:0[xX])?[0-9a-fA-F]{1,2}""")
    private const val MAX_CONFIG_BYTES = 524_288

    data class Result(
        val modified: ByteArray,
        val originalHash: String,
        val modifiedHash: String,
        val oldCoreBytes: Int,
        val newCoreBytes: Int,
    )

    fun preview(original: ByteArray, uidHex: String): Result {
        require(original.isNotEmpty() && original.size <= MAX_CONFIG_BYTES) {
            "NXP source configuration size must be 1..524288 bytes"
        }
        val text = String(original, StandardCharsets.UTF_8)
        require(text.toByteArray(StandardCharsets.UTF_8).contentEquals(original)) {
            "NXP source is not lossless UTF-8; refusing to modify"
        }
        val uid = parseUid(uidHex)
        val matches = coreBlock.findAll(text).toList()
        require(matches.size == 1) { "Expected one uncommented NXP_CORE_CONF block" }
        val match = matches.single()
        val body = match.groupValues[2].lineSequence()
            .map { it.substringBefore('#') }
            .joinToString("\n")
        val tokens = body.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        require(tokens.isNotEmpty() && tokens.all(byteToken::matches)) {
            "Unsupported NXP_CORE_CONF byte format"
        }
        val frame = tokens.map { token ->
            token.removePrefix("0x").removePrefix("0X").toInt(16)
        }.toMutableList()
        val target = findNfcid1(frame)
        require(frame[target + 1] == 0) {
            "Stock LA_NFCID1 is not empty; refusing to overwrite an existing identity"
        }
        val count = uid.size
        require(frame[2] + count <= 255) { "NCI frame too large" }
        frame[target + 1] = count
        frame.addAll(target + 2, uid.toList())
        frame[2] += count
        // Validate exact TLV structure and the new target length before export.
        check(findNfcid1(frame) == target)
        check(frame[target + 1] == count)
        check(frame.size == 3 + frame[2])

        val formatted = frame.chunked(16)
            .joinToString(",\n") { chunk ->
                "    " + chunk.joinToString(", ") { String.format(Locale.ROOT, "%02X", it) }
            }
        val replacement = match.groupValues[1] + "\n" + formatted + "\n" + match.groupValues[3]
        val modifiedText = text.replaceRange(match.range, replacement)
        val modifiedBytes = modifiedText.toByteArray(StandardCharsets.UTF_8)
        require(modifiedBytes.size <= MAX_CONFIG_BYTES) { "Modified NXP config too large" }
        return Result(
            modified = modifiedBytes,
            originalHash = sha256(original),
            modifiedHash = sha256(modifiedBytes),
            oldCoreBytes = tokens.size,
            newCoreBytes = frame.size,
        )
    }

    private fun parseUid(text: String): List<Int> {
        val hex = text.replace(Regex("""[\s:.-]"""), "")
        require(hex.matches(Regex("[0-9a-fA-F]+")) &&
            hex.length in setOf(8, 14, 20)) {
            "UID must be 4/7/10 bytes in hexadecimal"
        }
        return hex.chunked(2).map { it.toInt(16) }
    }

    /** Returns the offset of the single standard 0x33 TLV in a complete NCI frame. */
    private fun findNfcid1(frame: List<Int>): Int {
        require(frame.size >= 4 && frame[0] == 0x20 && frame[1] == 0x02) {
            "Expected NCI CORE_SET_CONFIG"
        }
        require(frame[2] == frame.size - 3) { "Invalid NCI payload length" }
        var offset = 4
        var match = -1
        repeat(frame[3]) {
            require(offset < frame.size) { "Incomplete NCI parameter list" }
            val vendor = frame[offset] >= 0xA0
            val lengthIndex = offset + if (vendor) 2 else 1
            require(lengthIndex < frame.size) { "Truncated NCI parameter" }
            val length = frame[lengthIndex]
            val next = lengthIndex + 1 + length
            require(next <= frame.size) { "Invalid NCI parameter length" }
            if (!vendor && frame[offset] == 0x33) {
                require(match < 0) { "Duplicate LA_NFCID1 entries" }
                match = offset
            }
            offset = next
        }
        require(offset == frame.size) { "Trailing or malformed NCI parameters" }
        require(match >= 0) { "LA_NFCID1 not found in the stock config" }
        return match
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }
}
