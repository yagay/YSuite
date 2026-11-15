package com.yagay.YMiniGuard;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class YMiniAudioUidPolicyTest {
    @Test public void matchActiveUidWithUsage() {
        assertEquals(YMiniAudioUidPolicy.ACTIVE,
                YMiniAudioUidPolicy.decision("ready", "10123:1,10234:14", 1000, 10234));
    }
    @Test public void differentAppDoesNotBorrowPlayback() {
        assertEquals(YMiniAudioUidPolicy.INACTIVE,
                YMiniAudioUidPolicy.decision("ready", "10123:1", 1000, 10345));
    }
    @Test public void staleOrUnavailableCannotClaimInactivity() {
        assertEquals(YMiniAudioUidPolicy.UNKNOWN,
                YMiniAudioUidPolicy.decision("ready", "10123:1", 17000, 10123));
        assertEquals(YMiniAudioUidPolicy.UNKNOWN,
                YMiniAudioUidPolicy.decision("unavailable", "", 1000, 10123));
    }
    @Test public void anonymousAudioPreventsNegativeAttribution() {
        assertEquals(YMiniAudioUidPolicy.UNKNOWN,
                YMiniAudioUidPolicy.decision("partial", "10123:1", 500, 10999));
        assertEquals(YMiniAudioUidPolicy.ACTIVE,
                YMiniAudioUidPolicy.decision("partial", "10123:1", 500, 10123));
    }
    @Test public void missingUidIsUnknown() {
        assertEquals(YMiniAudioUidPolicy.UNKNOWN,
                YMiniAudioUidPolicy.decision("ready", "", 500, -1));
    }
}
