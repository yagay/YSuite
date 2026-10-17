package com.yagay.YFloat;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GoogleV2MenuRecoveryPolicyTest {
    @Test public void rapidWordUpdatesDeferMenuToTrailingQuietPeriod() {
        assertEquals(260,GoogleV2MenuRecoveryPolicy.waitForIdle(
                1000,1000,0,false,260,700));
        assertEquals(140,GoogleV2MenuRecoveryPolicy.waitForIdle(
                1120,1000,0,false,260,700));
        assertEquals(0,GoogleV2MenuRecoveryPolicy.waitForIdle(
                1270,1000,0,false,260,700));
    }
    @Test public void missingFingerUpEventuallyRecoversOnceIdle() {
        assertEquals(600,GoogleV2MenuRecoveryPolicy.waitForIdle(
                1100,1020,1000,true,260,700));
        assertEquals(0,GoogleV2MenuRecoveryPolicy.waitForIdle(
                1720,1020,1000,true,260,700));
    }
    @Test public void freshDraggingHeartbeatStillSuppressesMenu() {
        assertEquals(700,GoogleV2MenuRecoveryPolicy.waitForIdle(
                2000,1200,2000,true,260,700));
        assertEquals(0,GoogleV2MenuRecoveryPolicy.waitForIdle(
                2300,1200,2000,false,260,700));
    }
}
