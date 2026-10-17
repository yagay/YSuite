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

    @Test public void freezeOnlyOwnsFrozenImageProperties() {
        assertTrue(GoogleV2ScenePolicy.shouldBlockTransform(true, "setScaleX", 0.915f));
        assertTrue(GoogleV2ScenePolicy.shouldBlockTransform(true, "setTranslationY", -1369f));
        assertFalse(GoogleV2ScenePolicy.shouldBlockTransform(false, "setScaleX", 0.915f));
        assertFalse(GoogleV2ScenePolicy.shouldBlockTransform(true, "setScaleY", 1f));
        assertFalse(GoogleV2ScenePolicy.shouldBlockTransform(true, "setAlpha", 0f));
    }

    @Test public void reconcilerTracksHardwareAnimatorChanges() {
        assertTrue(GoogleV2ScenePolicy.needsReconcile(true, 0.915f, 1f, 0f, -558f));
        assertFalse(GoogleV2ScenePolicy.needsReconcile(true, 1f, 1f, 0f, 0f));
        assertFalse(GoogleV2ScenePolicy.needsReconcile(false, 0.915f, 1f, 0f, 0f));
    }
}
