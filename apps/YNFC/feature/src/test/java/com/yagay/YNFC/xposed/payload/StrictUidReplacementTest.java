package com.yagay.YNFC.xposed.payload;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

/** YSuite RF mode must replace existing NFCID1, never append a new parameter. */
public final class StrictUidReplacementTest {
    private static final byte[] UID = hex("C1B0BC1B");
    private final RawNciCodec raw = new RawNciCodec();
    private final OplusTextConfigCodec oplus = new OplusTextConfigCodec();
    private final RfPayloadEngine engine = new RfPayloadEngine();

    @Test public void replacesExistingFourByteUidWithoutChangingFrameShape() {
        byte[] input = hex("20020701330411223344");
        byte[] before = input.clone();
        RewriteResult r = engine.replaceExisting(input, UID);
        assertTrue(r.reason, r.changed);
        assertEquals("STRICT_REPLACED_EXISTING_LA_NFCID1", r.reason);
        assertArrayEquals(hex("200207013304C1B0BC1B"), r.data);
        assertArrayEquals(before, input);
        assertEquals(r.oldParamCount, r.newParamCount);
    }

    @Test public void replacesZeroLengthExistingUidByResizingOriginalParameter() {
        RewriteResult r = raw.replaceExisting(hex("200203013300"), UID);
        assertTrue(r.changed);
        assertEquals("STRICT_RESIZED_EXISTING_LA_NFCID1", r.reason);
        assertArrayEquals(hex("200207013304C1B0BC1B"), r.data);
        assertEquals(1, r.oldParamCount);
        assertEquals(1, r.newParamCount);
    }

    @Test public void noExistingUidNeverAppends() {
        RewriteResult r = engine.replaceExisting(hex("20020401320108"), UID);
        assertFalse(r.changed);
        assertNull(r.data);
        assertTrue(r.reason.contains("NO_EXISTING_LA_NFCID1_TO_REPLACE"));
    }

    @Test public void duplicateUidParametersAreRejectedAsAmbiguous() {
        RewriteResult r = raw.replaceExisting(hex("20020D02330411223344330455667788"), UID);
        assertFalse(r.changed);
        assertEquals("UNVERIFIED_OR_AMBIGUOUS_EXISTING_LA_NFCID1", r.reason);
    }

    @Test public void separateFramesWithUidsAreAlsoAmbiguous() {
        byte[] input = concat(hex("20020701330411223344"), hex("20020701330455667788"));
        RewriteResult r = raw.replaceExisting(input, UID);
        assertFalse(r.changed);
        assertEquals("MULTIPLE_EXISTING_LA_NFCID1_FRAMES", r.reason);
    }

    @Test public void uidLikeBytesInsideOtherParameterAreNotRewritten() {
        // 33 04 occurs inside the 0x32 parameter's VALUE, not as an NCI tag.
        byte[] input = hex("200209013206330401020304");
        assertFalse(raw.replaceExisting(input, UID).changed);
    }

    @Test public void malformedOrUnsupportedExistingUidRemainsUntouched() {
        assertFalse(raw.replaceExisting(hex("200206013303112233"), UID).changed);
        assertFalse(raw.replaceExisting(hex("20020A01330411223344"), UID).changed);
    }

    @Test public void preservesNonNciTrailingBytes() {
        byte[] tail = hex("7E7D7C01020304");
        byte[] input = concat(hex("20020701330411223344"), tail);
        RewriteResult r = engine.replaceExisting(input, UID);
        assertTrue(r.reason, r.changed);
        assertArrayEquals(concat(hex("200207013304C1B0BC1B"), tail), r.data);
    }

    @Test public void supportsSevenAndTenByteReplacementWithoutAddingParameters() {
        RewriteResult seven = engine.replaceExisting(hex("200203013300"), hex("04112233445566"));
        assertTrue(seven.changed);
        assertArrayEquals(hex("20020A01330704112233445566"), seven.data);
        RewriteResult ten = engine.replaceExisting(hex("20020701330411223344"), hex("0102030405060708090A"));
        assertTrue(ten.changed);
        assertArrayEquals(hex("20020D01330A0102030405060708090A"), ten.data);
        assertEquals(1, ten.newParamCount);
    }

    @Test public void oplusTextWrapperReplacesExistingTagOnly() {
        byte[] input = "OPLUS_CONF_EXTN = { 20,02,07,01,33,04,11,22,33,44 }"
                .getBytes(StandardCharsets.UTF_8);
        RewriteResult r = engine.replaceExisting(input, UID);
        assertTrue(r.reason, r.changed);
        String compact = new String(r.data, StandardCharsets.UTF_8).replaceAll("\\s+", "");
        assertTrue(compact.contains("33,04,C1,B0,BC,1B"));
        assertFalse(compact.contains("11,22,33,44"));
        assertEquals(1, r.newParamCount);
    }

    @Test public void oplusTextWrapperWithoutUidIsUnchanged() {
        byte[] input = "OPLUS_CONF_EXTN = { 20,02,04,01,32,01,08 }"
                .getBytes(StandardCharsets.UTF_8);
        assertFalse(oplus.replaceExisting(input, UID).changed);
        assertFalse(engine.replaceExisting(input, UID).changed);
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int size = Arrays.stream(parts).mapToInt(p -> p.length).sum();
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
