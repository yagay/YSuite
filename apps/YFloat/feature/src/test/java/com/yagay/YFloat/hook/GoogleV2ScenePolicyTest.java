package com.yagay.YFloat.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GoogleV2ScenePolicyTest {
    @Test public void onlyOwnedContextualSearchIsSuppressed() {
        String search = "android.app.contextualsearch.action.LAUNCH_CONTEXTUAL_SEARCH";
        assertTrue(GoogleV2ScenePolicy.suppressNativeSearch(true, search));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(false, search));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(true, "android.intent.action.VIEW"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(true, ""));
    }

    @Test public void blockOnlySearchHandoffsInOwnedSessions() {
        assertTrue(GoogleV2ScenePolicy.suppressNativeSearch(
                true,"android.intent.action.WEB_SEARCH",null,null));
        assertTrue(GoogleV2ScenePolicy.suppressNativeSearch(
                true,"android.intent.action.VIEW","www.google.com","/search"));
        assertTrue(GoogleV2ScenePolicy.suppressNativeSearch(
                true,"android.intent.action.VIEW","lens.google","/lens"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(
                false,"android.intent.action.VIEW","www.google.com","/search"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(
                true,"android.intent.action.VIEW","example.com","/search"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(
                true,"android.intent.action.VIEW","www.google.com","/maps"));
    }

    @Test public void interceptCountrySpecificGoogleSearchWithoutMatchingLookalikes() {
        assertTrue(GoogleV2ScenePolicy.suppressNativeSearch(true,
                "android.intent.action.VIEW","www.google.co.uk","/search"));
        assertTrue(GoogleV2ScenePolicy.suppressNativeSearch(true,
                "android.intent.action.VIEW","www.google.com.hk","/search"));
        assertTrue(GoogleV2ScenePolicy.suppressNativeSearch(true,
                "android.intent.action.VIEW","lens.google","/lens"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(false,
                "android.intent.action.VIEW","www.google.co.uk","/search"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(true,
                "android.intent.action.VIEW","www.google.co.uk.evil","/search"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(true,
                "android.intent.action.VIEW","notgoogle.co.uk","/search"));
        assertFalse(GoogleV2ScenePolicy.suppressNativeSearch(true,
                "android.intent.action.VIEW","www.google.co.uk","/maps"));
    }

    @Test public void onlyCloseRevokedYFloatOwnedLensActivity() {
        assertTrue(GoogleV2ScenePolicy.shouldFinishStaleOwnedLens(
                true,true,false,true,false));
        assertFalse(GoogleV2ScenePolicy.shouldFinishStaleOwnedLens(
                false,true,false,true,false));
        assertFalse(GoogleV2ScenePolicy.shouldFinishStaleOwnedLens(
                true,true,true,true,false));
        assertFalse(GoogleV2ScenePolicy.shouldFinishStaleOwnedLens(
                true,false,false,true,false));
        assertFalse(GoogleV2ScenePolicy.shouldFinishStaleOwnedLens(
                true,true,false,false,false));
        assertFalse(GoogleV2ScenePolicy.shouldFinishStaleOwnedLens(
                true,true,false,true,true));
    }

    @Test public void revokedOwnedSessionClosesEvenWithoutActivityResume() {
        assertTrue(GoogleV2ScenePolicy.shouldCloseRevokedOwnedSession(
                true,true,false,false));
        assertFalse(GoogleV2ScenePolicy.shouldCloseRevokedOwnedSession(
                false,true,false,false));
        assertFalse(GoogleV2ScenePolicy.shouldCloseRevokedOwnedSession(
                true,false,false,false));
        assertFalse(GoogleV2ScenePolicy.shouldCloseRevokedOwnedSession(
                true,true,true,false));
        // Fast consecutive CTS launches must not terminate the next session.
        assertFalse(GoogleV2ScenePolicy.shouldCloseRevokedOwnedSession(
                true,true,false,true));
    }

    @Test public void freezeOnlyOwnsFrozenImageProperties() {
        assertTrue(GoogleV2ScenePolicy.shouldBlockTransform(true, "setScaleX", 0.915f));
        assertTrue(GoogleV2ScenePolicy.shouldBlockTransform(true, "setTranslationY", -1369f));
        assertFalse(GoogleV2ScenePolicy.shouldBlockTransform(false, "setScaleX", 0.915f));
        assertFalse(GoogleV2ScenePolicy.shouldBlockTransform(true, "setScaleY", 1f));
        assertFalse(GoogleV2ScenePolicy.shouldBlockTransform(true, "setAlpha", 0f));
    }

    @Test public void textHandleDragMustNotHaveItsViewportForceReset() {
        assertTrue(GoogleV2ScenePolicy.shouldProtectFrozenViewport(true, false));
        assertFalse(GoogleV2ScenePolicy.shouldProtectFrozenViewport(true, true));
        assertFalse(GoogleV2ScenePolicy.shouldProtectFrozenViewport(false, false));
    }

    @Test public void reconcilerTracksHardwareAnimatorChanges() {
        assertTrue(GoogleV2ScenePolicy.needsReconcile(true, 0.915f, 1f, 0f, -558f));
        assertFalse(GoogleV2ScenePolicy.needsReconcile(true, 1f, 1f, 0f, 0f));
        assertFalse(GoogleV2ScenePolicy.needsReconcile(false, 0.915f, 1f, 0f, 0f));
    }
}
