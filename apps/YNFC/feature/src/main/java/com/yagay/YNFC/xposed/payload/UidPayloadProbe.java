package com.yagay.YNFC.xposed.payload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Read-only classification of NFC-A UID writer payloads.
 *
 * A Java method named setNciConfig does not prove that its byte[] contains an
 * NCI CORE_SET_CONFIG command. Do not mutate a TLV-only or opaque payload
 * without verifying the current OEM ABI and native result.
 */
public final class UidPayloadProbe {
    private UidPayloadProbe() { }

    public static String shape(byte[] data) {
        if (data == null || data.length == 0) return "EMPTY";
        String head = new String(data, StandardCharsets.UTF_8);
        if (head.contains("OPLUS_CONF_EXTN")) return "OPLUS_CONF_EXTN_TEXT";
        if (data.length >= 4 && (data[0] & 0xFF) == 0x20
                && (data[1] & 0xFF) == 0x02) {
            int payloadSize = data[2] & 0xFF;
            if (payloadSize + 3 == data.length && validParams(data, 4,
                    data.length, data[3] & 0xFF)) return "CORE_SET_CONFIG_FRAME";
            return "CORE_SET_CONFIG_UNVERIFIED";
        }
        int count = countBareConfigTlvs(data);
        if (count > 0) return "BARE_CONFIG_TLV_LIST";
        return "OPAQUE_OR_VENDOR_PRIVATE";
    }

    public static String prefixHex(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) return "";
        int count = Math.min(data.length, Math.max(0, Math.min(maxBytes, 16)));
        StringBuilder out = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            out.append(String.format(Locale.ROOT, "%02X", data[i] & 0xFF));
        }
        return out.toString();
    }

    private static int countBareConfigTlvs(byte[] data) {
        // Only classify when a complete, bounded TLV stream begins at offset 0
        // and contains a Listen-A (0x30..0x33) parameter. Never infer this
        // merely from the substring 0x33 inside an opaque OEM payload.
        int position = 0, count = 0;
        boolean knownListenA = false;
        while (position < data.length && count < 64) {
            int id = data[position] & 0xFF;
            int lenOffset = position + 1;
            if (id == 0xA0) {
                if (position + 2 >= data.length) return 0;
                lenOffset = position + 2;
            }
            if (lenOffset >= data.length) return 0;
            int size = data[lenOffset] & 0xFF;
            int next = lenOffset + 1 + size;
            if (next > data.length) return 0;
            if (id >= 0x30 && id <= 0x33) knownListenA = true;
            position = next;
            count++;
        }
        return position == data.length && knownListenA ? count : 0;
    }

    private static boolean validParams(byte[] data, int position, int end, int count) {
        if (count < 1 || count > 64) return false;
        for (int i = 0; i < count; i++) {
            if (position >= end) return false;
            int id = data[position] & 0xFF;
            int lenOffset = position + (id == 0xA0 ? 2 : 1);
            if (lenOffset >= end) return false;
            int size = data[lenOffset] & 0xFF;
            position = lenOffset + 1 + size;
            if (position > end) return false;
        }
        return position == end;
    }
}
