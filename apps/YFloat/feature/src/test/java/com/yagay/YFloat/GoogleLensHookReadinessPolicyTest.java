package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleLensHookReadinessPolicyTest {
    @Test public void staleStatusAloneNeverBlocksLaunch() {
        // Preflight never calls this policy: running Google's actual Hook is
        // more reliable than comparing process-version status strings.
        assertFalse(GoogleLensHookReadinessPolicy.shouldSuggestRestartAfterTimeout(
                true,true,true,true));
    }

    @Test public void suggestOnlyAfterFailedHandshakeWithOldRunningProcess() {
        assertTrue(GoogleLensHookReadinessPolicy.shouldSuggestRestartAfterTimeout(
                true,true,true,false));
    }

    @Test public void noSuggestionWhenGoogleIsCurrentOrColdStarting() {
        assertFalse(GoogleLensHookReadinessPolicy.shouldSuggestRestartAfterTimeout(
                true,true,false,false));
        assertFalse(GoogleLensHookReadinessPolicy.shouldSuggestRestartAfterTimeout(
                true,false,true,false));
    }

    @Test public void noSuggestionWithoutGoogleScope() {
        assertFalse(GoogleLensHookReadinessPolicy.shouldSuggestRestartAfterTimeout(
                false,true,true,false));
    }
}
