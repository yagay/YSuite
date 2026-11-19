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
            gid == 0x0f -> "PROPRIETARY_NCI_NOTIFICATION_OR_RESPONSE"
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

        val beforeWindow = frames.count {
            commandEpochMillis > 0 && it.epochMillis < commandEpochMillis - BEFORE_MS
        }
        val afterWindow = frames.count {
            commandEpochMillis > 0 && it.epochMillis > commandEpochMillis + AFTER_MS
        }
        val earliestDelta = frames.minOfOrNull { it.epochMillis - commandEpochMillis }
        val latestDelta = frames.maxOfOrNull { it.epochMillis - commandEpochMillis }
        val allTx = frames.count { it.tx }
        val proprietaryNotifications = frames.count {
            !it.tx && it.kind == 0x60 && it.gid == 0x0f
        }
        val coreTx = window.filter {
            it.tx && it.kind == 0x20 && it.gid == 0 && it.oid == 2
        }
        val uidTxFrames = coreTx.filter { uidParameterLength(it.data) != null }
        val uidLengths = uidTxFrames.mapNotNull { uidParameterLength(it.data) }
        val uidFirstTxDelta = uidTxFrames.map { it.epochMillis - commandEpochMillis }
            .filter { it >= 0L }.minOrNull()
        // Do not discard the earlier NCI frames when assessing RF discovery.
        // This remains only a hint derived from retained logcat, never proof of
        // the NFCC's exact RF state at the moment of the UID write.
        val uidWriteEpoch = uidTxFrames.filter { it.epochMillis >= commandEpochMillis }
            .minOfOrNull { it.epochMillis } ?: commandEpochMillis
        val rfHistory = summarizePreWriteRfHistory(frames, uidWriteEpoch)

        // Correlate CORE_SET_CONFIG responses with preceding TX commands in
        // log order. Never report a response from an unrelated command as the
        // ACK for a UID request. NCI status is not over-the-air UID proof.
        val responseStatuses = ArrayList<Int>()
        val rejectedParamLists = ArrayList<String>()
        val uidResponseStatuses = ArrayList<Int>()
        val uidRejectedParamLists = ArrayList<String>()
        val pendingConfig = ArrayDeque<Boolean>()
        for (frame in window) {
            if (frame.tx && frame.kind == 0x20 && frame.gid == 0 && frame.oid == 2) {
                pendingConfig.addLast(uidParameterLength(frame.data) != null)
            } else if (!frame.tx && frame.kind == 0x40 &&
                frame.gid == 0 && frame.oid == 2 && pendingConfig.isNotEmpty()) {
                val isUidWrite = pendingConfig.removeFirst()
                val status = if (frame.data.size >= 4) frame.data[3].toInt() and 0xff else -1
                responseStatuses.add(status)
                val invalidIds = if (status == 0x09 && frame.data.size >= 5) {
                    val count = frame.data[4].toInt() and 0xff
                    if (count > 0 && frame.data.size == 5 + count) {
                        (5 until frame.data.size).joinToString(",") {
                            "0x" + (frame.data[it].toInt() and 0xff)
                                .toString(16).padStart(2, '0').uppercase()
                        }
                    } else "UNKNOWN"
                } else "NONE"
                rejectedParamLists.add(invalidIds)
                if (isUidWrite) {
                    uidResponseStatuses.add(status)
                    uidRejectedParamLists.add(invalidIds)
                }
            }
        }
        // Scan later retained packets too: a late export may no longer
        // contain the original APPLY, but can show whether a natural discovery
        // reassert actually sent 0x33. Do not expose any UID payload.
        val afterApply = if (commandEpochMillis > 0L) frames.filter {
            it.epochMillis > commandEpochMillis + AFTER_MS
        } else emptyList()
        val laterPending = ArrayDeque<Boolean>()
        var laterUidTx = 0
        var laterUidEmptyTx = 0
        var laterUidFixedLengthTx = 0
        var laterUidAccepted = 0
        var laterUidRejected = 0
        var laterUidOther = 0
        for (frame in afterApply) {
            if (frame.tx && frame.kind == 0x20 && frame.gid == 0 && frame.oid == 2) {
                val uidLen = uidParameterLength(frame.data)
                val isUid = uidLen != null
                laterPending.addLast(isUid)
                if (isUid) {
                    laterUidTx++
                    if (uidLen == 0) laterUidEmptyTx++
                    else if (uidLen in listOf(4, 7, 10)) laterUidFixedLengthTx++
                }
            } else if (!frame.tx && frame.kind == 0x40 && frame.gid == 0 && frame.oid == 2 &&
                laterPending.isNotEmpty()) {
                if (laterPending.removeFirst()) {
                    when (frame.data.getOrNull(3)?.toInt()?.and(0xff)) {
                        0 -> laterUidAccepted++
                        0x09 -> laterUidRejected++
                        else -> laterUidOther++
                    }
                }
            }
        }
        val configAccepted = responseStatuses.count { it == 0 }
        val configRejected = responseStatuses.count { it > 0 }
        val configUnknown = responseStatuses.count { it < 0 }
        val routing = window.count { it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 1 }
        val discover = window.count { it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 3 }
        val deactivate = window.count { it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 6 }

        return buildString {
            appendLine("SNAPSHOT_VALID_FRAMES=" + frames.size + " malformed_or_truncated=" + malformed)
            appendLine("COMMAND_GENERATION_EPOCH_MS=" + commandEpochMillis)
            appendLine("COMMAND_WINDOW_MS=-" + BEFORE_MS + "..+" + AFTER_MS)
            appendLine("ALL_CAPTURED_HAL_TX=" + allTx +
                " HAL_RX=" + (frames.size - allTx) +
                " PROPRIETARY_RX_NOTIFICATIONS=" + proprietaryNotifications)
            appendLine("OUTSIDE_WINDOW_BEFORE=" + beforeWindow +
                " AFTER=" + afterWindow +
                " FIRST_DELTA_MS=" + (earliestDelta?.toString() ?: "NONE") +
                " LAST_DELTA_MS=" + (latestDelta?.toString() ?: "NONE"))
            appendLine("IN_WINDOW_FRAMES=" + window.size +
                " HAL_TX=" + window.count { it.tx } +
                " HAL_RX=" + window.count { !it.tx })
            appendLine("RF_DEACTIVATE_TX=" + deactivate +
                " RF_SET_LISTEN_MODE_ROUTING_TX=" + routing +
                " RF_DISCOVER_TX=" + discover)
            rfHistory.forEach { appendLine(it) }
            appendLine("HAL_OBSERVATION_SOURCE=EXISTING_NXP_LOGCAT_ONLY")
            appendLine("HAL_NATIVE_WRITE_HOOK=NOT_INSTALLED")
            appendLine("POST_APPLY_NCI_33_TX=" + laterUidTx)
            appendLine("POST_APPLY_NCI_33_ZERO_LENGTH_TX=" + laterUidEmptyTx)
            appendLine("POST_APPLY_NCI_33_FIXED_LENGTH_TX=" + laterUidFixedLengthTx)
            appendLine("POST_APPLY_NCI_33_RSP_ACCEPTED=" + laterUidAccepted +
                " REJECTED_INVALID_PARAM=" + laterUidRejected + " OTHER=" + laterUidOther)
            appendLine("POST_APPLY_RF_DEACTIVATE_TX=" + afterApply.count {
                it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 6
            } + " RF_DISCOVER_TX=" + afterApply.count {
                it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 3
            })
            appendLine("POST_APPLY_RF_LISTEN_ROUTING_TX=" + afterApply.count {
                it.tx && it.kind == 0x20 && it.gid == 1 && it.oid == 1
            } + " RF_INTF_ACTIVATED_NTF=" + afterApply.count {
                !it.tx && it.kind == 0x60 && it.gid == 1 && it.oid == 5
            })
            appendLine("POST_APPLY_UID_CONFIG_INTERPRETATION=" + when {
                laterUidEmptyTx > 0 -> "EMPTY_NFCID1_CONFIG_OBSERVED_POSSIBLE_RESET"
                laterUidFixedLengthTx > 0 -> "NONEMPTY_NFCID1_CONFIG_OBSERVED_RF_UNVERIFIED"
                afterApply.isNotEmpty() -> "NO_VISIBLE_NFCID1_WRITE_NOT_PROOF_OF_NO_RESET"
                else -> "NO_POST_WINDOW_PACKETS"
            })
            appendLine("WALLET_RISK_NOTE=RF_UID_OVERRIDE_IS_GLOBAL_WHILE_ACTIVE; NCI logs do not identify payment transactions or prove secure-element routing")
            appendLine("CORE_SET_CONFIG_TX=" + coreTx.size)
            appendLine("LA_NFCID1_TAG_33_IN_CONFIG_TX=" + uidLengths.size)
            appendLine("LA_NFCID1_LENGTHS_ONLY=" + uidLengths.joinToString(",").ifBlank { "NONE" })
            appendLine("LA_NFCID1_FIRST_TX_DELTA_MS=" + (uidFirstTxDelta?.toString() ?: "NONE"))
            appendLine("LA_NFCID1_RSP_MATCHED=" + uidResponseStatuses.size)
            appendLine("LA_NFCID1_RSP_STATUS=" +
                uidResponseStatuses.joinToString(",") { status ->
                    when {
                        status == 0 -> "0x00_ACCEPTED"
                        status > 0 -> "0x" + status.toString(16).padStart(2, '0').uppercase() +
                            "_REJECTED"
                        else -> "MALFORMED"
                    }
                }.ifBlank { "NO_MATCHING_RESPONSE_IN_LOG" })
            appendLine("LA_NFCID1_RSP_INVALID_PARAM_IDS=" +
                uidRejectedParamLists.joinToString(";").ifBlank { "NONE" })
            appendLine("CORE_SET_CONFIG_RSP_MATCHED=" + responseStatuses.size)
            appendLine("CORE_SET_CONFIG_RSP_ACCEPTED=" + configAccepted)
            appendLine("CORE_SET_CONFIG_RSP_REJECTED=" + configRejected)
            appendLine("CORE_SET_CONFIG_RSP_UNKNOWN=" + configUnknown)
            appendLine("CORE_SET_CONFIG_RSP_STATUS=" +
                responseStatuses.joinToString(",") { status ->
                    when {
                        status == 0 -> "0x00_ACCEPTED"
                        status > 0 -> "0x" + status.toString(16).padStart(2, '0').uppercase() + "_REJECTED"
                        else -> "MALFORMED"
                    }
                }.ifBlank { "NO_MATCHING_RESPONSE_IN_LOG" })
            appendLine("CORE_SET_CONFIG_RSP_INVALID_PARAM_IDS=" +
                rejectedParamLists.joinToString(";").ifBlank { "NONE" })
            appendLine("CORE_SET_CONFIG_RSP_CODE_MEANING=" +
                if (responseStatuses.size == 1 && responseStatuses[0] == 0x09)
                    "NCI_STATUS_INVALID_PARAM" else "SEE_NCI_SPEC")
            appendLine("CORE_SET_CONFIG_RSP_NOTE=Native NCI acceptance is not proof of the NFC-A UID seen by a reader")
            appendLine("EVIDENCE=" + when {
                commandEpochMillis <= 0L -> "NO_COMMAND_TIMESTAMP"
                window.isEmpty() && afterWindow == frames.size && frames.isNotEmpty() ->
                    "ONLY_POST_WINDOW_HAL_FRAMES_EARLIER_TX_MAY_HAVE_BEEN_EVICTED"
                window.isEmpty() && beforeWindow == frames.size && frames.isNotEmpty() ->
                    "ONLY_PRE_WINDOW_HAL_FRAMES_NO_CURRENT_WINDOW_EVIDENCE"
                window.isEmpty() -> "NO_HAL_FRAMES_IN_COMMAND_WINDOW_OR_BUFFER_TRUNCATED"
                uidLengths.isNotEmpty() &&
                    uidResponseStatuses.contains(0x09) &&
                    uidRejectedParamLists.any { it == "0x33" } ->
                    "UID_PARAM_REJECTED_NCI_INVALID_PARAM"
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
            appendLine("NOTE: A zero CORE_SET_CONFIG count is not proof none was sent if the command window has no retained TX records.")
            appendLine("NOTE: Logcat can omit native packets. Only a separate NFC reader can verify the transmitted UID.")
        }
    }

    /**
     * Read-only evidence from *all* retained frames before the current UID TX,
     * including those outside the narrow -1.5s APPLY window. Match a command
     * with its response to avoid claiming an RF transition from a lone TX.
     * A missing packet or later unlogged notification can invalidate any
     * state inference; never use this hint to trigger an NFC command.
     */
    private fun summarizePreWriteRfHistory(frames: List<Frame>, writeAt: Long): List<String> {
        if (writeAt <= 0L) return listOf("PRE_UID_RF_STATE_HINT=UNKNOWN_NO_COMMAND_TIME")
        val history = frames.filter { it.epochMillis < writeAt }.sortedBy { it.epochMillis }
        var discoverTx = 0
        var discoverAck = 0
        var deactivateTx = 0
        var deactivateIdleAck = 0
        var pendingDiscover = false
        var pendingDeactivateMode: Int? = null
        var lastEvent = "NONE"
        var lastEventAt = 0L
        val timeline = ArrayList<String>()
        for (frame in history) {
            if (frame.gid != 1) continue
            val isCmd = frame.tx && frame.kind == 0x20
            val isRsp = !frame.tx && frame.kind == 0x40
            val status = frame.data.getOrNull(3)?.toInt()?.and(0xff)
            when {
                isCmd && frame.oid == 3 -> {
                    discoverTx++
                    pendingDiscover = true
                }
                isRsp && frame.oid == 3 && pendingDiscover -> {
                    pendingDiscover = false
                    if (status == 0) {
                        discoverAck++
                        lastEvent = "RF_DISCOVER_RSP_OK"
                        lastEventAt = frame.epochMillis
                        timeline.add(lastEvent + " delta_ms=" + (frame.epochMillis - writeAt))
                    }
                }
                isCmd && frame.oid == 6 -> {
                    deactivateTx++
                    pendingDeactivateMode = frame.data.getOrNull(3)?.toInt()?.and(0xff)
                }
                isRsp && frame.oid == 6 && pendingDeactivateMode != null -> {
                    val mode = pendingDeactivateMode
                    pendingDeactivateMode = null
                    if (status == 0) {
                        lastEvent = if (mode == 0) "RF_DEACTIVATE_TO_IDLE_RSP_OK"
                            else "RF_DEACTIVATE_NON_IDLE_RSP_OK"
                        if (mode == 0) deactivateIdleAck++
                        lastEventAt = frame.epochMillis
                        timeline.add(lastEvent + " delta_ms=" + (frame.epochMillis - writeAt))
                    }
                }
                !frame.tx && frame.kind == 0x60 && frame.oid == 5 -> {
                    lastEvent = "RF_INTF_ACTIVATED_NTF"
                    lastEventAt = frame.epochMillis
                    timeline.add(lastEvent + " delta_ms=" + (frame.epochMillis - writeAt))
                }
            }
        }
        val stateHint = when (lastEvent) {
            "RF_DISCOVER_RSP_OK" -> "DISCOVERY_POSSIBLE_NOT_PROVEN"
            "RF_DEACTIVATE_TO_IDLE_RSP_OK" -> "IDLE_POSSIBLE_NOT_PROVEN"
            "RF_INTF_ACTIVATED_NTF" -> "ACTIVE_POSSIBLE_NOT_PROVEN"
            else -> "UNKNOWN"
        }
        return listOf(
            "PRE_UID_RF_HISTORY_FRAMES=" + history.size,
            "PRE_UID_RF_DISCOVER_TX=" + discoverTx + " RSP_OK=" + discoverAck,
            "PRE_UID_RF_DEACTIVATE_TX=" + deactivateTx + " IDLE_RSP_OK=" + deactivateIdleAck,
            "PRE_UID_RF_LAST_CONFIRMED_EVENT=" + lastEvent,
            "PRE_UID_RF_LAST_EVENT_AGE_MS=" +
                if (lastEventAt > 0L) (writeAt - lastEventAt).toString() else "NONE",
            "PRE_UID_RF_STATE_HINT=" + stateHint,
            "PRE_UID_RF_EVENT_TIMELINE_NO_PAYLOAD=" +
                timeline.takeLast(6).joinToString("|").ifBlank { "NONE" },
            "PRE_UID_RF_STATE_NOTE=Retained HAL packets can be incomplete; hint is not current hardware state proof"
        )
    }

    /**
     * Conservative correlation: one complete UID-containing config TX, one
     * following response with STATUS_INVALID_PARAM, and the NFCC explicitly
     * names LA_NFCID1 (0x33) as its rejected parameter. Time-bound to the
     * current APPLY, never use a stale logcat snapshot to revise state.
     * No UID bytes or raw NCI payload leave the parser.
     */
    /**
     * A single UID-containing 0x33 TX with its matching successful NCI response
     * in the current command window. This is NFCC acceptance, never RF proof.
     */
    fun isOwnUidParameterAccepted(summary: String): Boolean {
        val properties = summary.lineSequence().mapNotNull { line ->
            val cut = line.indexOf('=')
            if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 1)
        }.toMap()
        val delta = properties["LA_NFCID1_FIRST_TX_DELTA_MS"]?.toLongOrNull() ?: return false
        return delta in 0L..AFTER_MS &&
            properties["LA_NFCID1_TAG_33_IN_CONFIG_TX"] == "1" &&
            properties["LA_NFCID1_RSP_MATCHED"] == "1" &&
            properties["LA_NFCID1_RSP_STATUS"] == "0x00_ACCEPTED"
    }

    fun isOwnUidParameterRejected(summary: String): Boolean {
        val properties = summary.lineSequence().mapNotNull { line ->
            val cut = line.indexOf('=')
            if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 1)
        }.toMap()
        val delta = properties["LA_NFCID1_FIRST_TX_DELTA_MS"]?.toLongOrNull() ?: return false
        // RF deactivation may complete asynchronously on some NXP stacks.
        // Use the same bounded APPLY interval as summarize(), not the old
        // 1-second direct-write assumption. Generation and exact invalid
        // parameter checks still prevent unrelated responses being promoted.
        return delta in 0L..AFTER_MS &&
            properties["LA_NFCID1_TAG_33_IN_CONFIG_TX"] == "1" &&
            properties["LA_NFCID1_RSP_MATCHED"] == "1" &&
            properties["LA_NFCID1_RSP_STATUS"] == "0x09_REJECTED" &&
            properties["LA_NFCID1_RSP_INVALID_PARAM_IDS"] == "0x33"
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
