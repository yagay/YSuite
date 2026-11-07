package com.yagay.YNFC

/**
 * Read-only decoder of the NXP HAL's existing NxpNciX/NxpNciR logcat lines.
 * No NFC command is sent and no card UID or NCI payload is exported.
 *
 * A CORE_SET_CONFIG observation is still NOT proof of the UID seen by an
 * external reader, only evidence that the NFC stack attempted a config write.
 */
internal object NciHalTrace {
    private val logPattern = Regex(
        """(?m)^\s*(\d{10})(?:\.(\d{1,9}))?\s+.*?\b(NxpNciX|NxpNciR)\s*:\s*len\s*=\s*(\d+)\s*>\s*([0-9A-Fa-f]+)\s*$"""
    )
    private const val BEFORE_MS = 1_500L
    private const val AFTER_MS = 7_000L

    private data class Frame(
        val epochMillis: Long,
        val tx: Boolean,
        val kind: Int,
        val gid: Int,
        val oid: Int,
        val data: ByteArray
    ) {
        val name: String get() = when {
            gid == 0 && oid == 2 -> "CORE_SET_CONFIG"
            gid == 0 && oid == 3 -> "CORE_GET_CONFIG"
            gid == 1 && oid == 1 -> "RF_SET_LISTEN_MODE_ROUTING"
            gid == 1 && oid == 3 -> "RF_DISCOVER"
            gid == 1 && oid == 6 -> "RF_DEACTIVATE"
            gid == 1 -> "RF_OTHER_" + oid
            gid == 0 -> "CORE_OTHER_" + oid
            else -> "OTHER_GID_" + gid + "_OID_" + oid
        }
        val direction: String get() = (if (tx) "TX" else "RX") + "/" +
            when (kind) {
                0x20 -> "CMD"
                0x40 -> "RSP"
                0x60 -> "NTF"
                else -> "OTHER"
            }
    }

    fun summarize(rawLogcat: String, commandEpochMillis: Long): String {
        val frames = ArrayList<Frame>()
        var malformed = 0
        for (m in logPattern.findAll(rawLogcat)) {
            if (frames.size >= 600) break
            val seconds = m.groupValues[1].toLongOrNull() ?: continue
            val fraction = m.groupValues[2].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            val statedLength = m.groupValues[4].toIntOrNull() ?: continue
            val hex = m.groupValues[5]
            if (hex.length % 2 != 0 || statedLength * 2 != hex.length || statedLength < 3 ||
                statedLength > 400) {
                malformed++
                continue
            }
            val data = try {
                ByteArray(statedLength) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (_: NumberFormatException) {
                malformed++
                continue
            }
            if ((data[2].toInt() and 0xff) != data.size - 3) {
                malformed++
                continue
            }
            val first = data[0].toInt() and 0xff
            frames.add(Frame(
                epochMillis = seconds * 1000 + fraction,
                tx = m.groupValues[3] == "NxpNciX",
                kind = first and 0xe0,
                gid = first and 0x0f,
                oid = data[1].toInt() and 0x3f,
                data = data
            ))
        }
        val window = if (commandEpochMillis > 0) frames.filter {
            it.epochMillis >= commandEpochMillis - BEFORE_MS &&
                it.epochMillis <= commandEpochMillis + AFTER_MS
        } else emptyList()

        val coreTx = window.filter {
            it.tx && it.kind == 0x20 && it.gid == 0 && it.oid == 2
        }
        val uidLengths = coreTx.mapNotNull { uidParameterLength(it.data) }
        val routing = window.count { it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 1 }
        val discover = window.count { it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 3 }
        val deactivate = window.count { it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 6 }

        return buildString {
            appendLine("SNAPSHOT_VALID_FRAMES=" + frames.size + " malformed_or_truncated=" + malformed)
            appendLine("COMMAND_GENERATION_EPOCH_MS=" + commandEpochMillis)
            appendLine("COMMAND_WINDOW_MS=-" + BEFORE_MS + "..+" + AFTER_MS)
            appendLine("IN_WINDOW_FRAMES=" + window.size +
                " HAL_TX=" + window.count { it.tx } +
                " HAL_RX=" + window.count { !it.tx })
            appendLine("RF_DEACTIVATE_TX=" + deactivate +
                " RF_SET_LISTEN_MODE_ROUTING_TX=" + routing +
                " RF_DISCOVER_TX=" + discover)
            appendLine("CORE_SET_CONFIG_TX=" + coreTx.size)
            appendLine("LA_NFCID1_TAG_33_IN_CONFIG_TX=" + uidLengths.size)
            appendLine("LA_NFCID1_LENGTHS_ONLY=" + uidLengths.joinToString(",").ifBlank { "NONE" })
            appendLine("EVIDENCE=" + when {
                commandEpochMillis <= 0L -> "NO_COMMAND_TIMESTAMP"
                window.isEmpty() -> "NO_HAL_FRAMES_IN_COMMAND_WINDOW_OR_BUFFER_TRUNCATED"
                uidLengths.isNotEmpty() -> "UID_PARAM_SENT_NOT_RF_VERIFIED"
                coreTx.isNotEmpty() -> "CORE_CONFIG_SENT_NO_PARSEABLE_UID_PARAM"
                else -> "RF_ROUTING_OR_OTHER_ONLY_NO_CORE_CONFIG_VISIBLE"
            })
            appendLine("HAL_TIMELINE_NO_PAYLOAD:")
            window.take(20).forEach { frame ->
                val delta = frame.epochMillis - commandEpochMillis
                appendLine("  " + (if (delta >= 0L) "+" else "") + delta + "ms " +
                    frame.direction + " " + frame.name + " bytes=" + frame.data.size)
            }
            appendLine("NOTE: Logcat can omit native packets. Only a separate NFC reader can verify the transmitted UID.")
        }
    }

    /** Return length only, never expose an incidental card identifier. */
    private fun uidParameterLength(frame: ByteArray): Int? {
        if (frame.size < 4) return null
        val count = frame[3].toInt() and 0xff
        var offset = 4
        var uidLen: Int? = null
        if (count == 0 || count > 64) return null
        repeat(count) {
            if (offset + 1 >= frame.size) return null
            val first = frame[offset].toInt() and 0xff
            val idBytes = if (first in 0xa0..0xaf) 2 else 1
            if (offset + idBytes >= frame.size) return null
            val size = frame[offset + idBytes].toInt() and 0xff
            val next = offset + idBytes + 1 + size
            if (next > frame.size) return null
            if (first == 0x33) uidLen = size
            offset = next
        }
        return if (offset == frame.size) uidLen else null
    }
}
