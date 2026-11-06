package com.yagay.YNFC.xposed.payload;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import static org.junit.Assert.*;

/** UID-only discovery must distinguish a real NCI command from OEM bytes. */
public final class UidPayloadProbeTest {
    @Test public void identifiesCompleteNciSetConfig() {
        assertEquals("CORE_SET_CONFIG_FRAME",
                UidPayloadProbe.shape(hex("200207013304C1B0BC1B")));
        assertEquals("CORE_SET_CONFIG_FRAME",
                UidPayloadProbe.shape(hex("200203013300")));
    }

    @Test public void doesNotMisidentifyBareTlvAsNciFrame() {
        assertEquals("BARE_CONFIG_TLV_LIST",
                UidPayloadProbe.shape(hex("3304C1B0BC1B")));
        assertEquals("BARE_CONFIG_TLV_LIST",
                UidPayloadProbe.shape(hex("3001003101003304C1B0BC1B")));
    }

    @Test public void refusesTruncatedFramesAndOpaqueData() {
        assertEquals("CORE_SET_CONFIG_UNVERIFIED",
                UidPayloadProbe.shape(hex("200207013304C1B0")));
        assertEquals("OPAQUE_OR_VENDOR_PRIVATE",
                UidPayloadProbe.shape(hex("A00101FF")));
        assertEquals("EMPTY", UidPayloadProbe.shape(null));
    }

    @Test public void identifiesOplusTextWrapperWithoutMutatingIt() {
        byte[] text = "OPLUS_CONF_EXTN = { 20, 02, 03, 01, 33, 00 }"
                .getBytes(StandardCharsets.UTF_8);
        assertEquals("OPLUS_CONF_EXTN_TEXT", UidPayloadProbe.shape(text));
    }

    @Test public void prefixIsBoundedAndDeterministic() {
        assertEquals("200207013304", UidPayloadProbe.prefixHex(hex("200207013304C1B0BC1B"), 6));
        assertEquals("", UidPayloadProbe.prefixHex(null, 6));
        assertEquals("", UidPayloadProbe.prefixHex(hex("33"), -1));
    }

    @Test public void randomDataClassificationIsTotalAndReadOnly() {
        Random random = new Random(0x55494444L);
        for (int i = 0; i < 500; i++) {
            byte[] data = new byte[random.nextInt(80)];
            random.nextBytes(data);
            byte[] original = data.clone();
            assertNotNull(UidPayloadProbe.shape(data));
            assertArrayEquals(original, data);
        }
    }

    private static byte[] hex(String source) {
        byte[] data = new byte[source.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(source.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }
}
