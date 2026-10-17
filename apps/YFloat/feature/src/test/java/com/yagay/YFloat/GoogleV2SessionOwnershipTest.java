package com.yagay.YFloat;
import static org.junit.Assert.*;
import org.junit.Test;
public class GoogleV2SessionOwnershipTest {
    @Test public void oldCallbacksCannotPaintNewWorkflow() {
        assertFalse(GoogleV2SessionOwnership.accepts("old","new",true,false));
        assertTrue(GoogleV2SessionOwnership.accepts("new","new",true,false));
    }
    @Test public void removedOrEndedSessionHasNoUiRights() {
        assertFalse(GoogleV2SessionOwnership.accepts("new","new",false,false));
        assertFalse(GoogleV2SessionOwnership.accepts("new","new",true,true));
        assertFalse(GoogleV2SessionOwnership.accepts("","",true,false));
    }
    @Test public void lateOutlineCleanupCannotRemoveNewGestureOutline() {
        assertTrue(GoogleV2SessionOwnership.ownsOutline("session2","session2"));
        assertFalse(GoogleV2SessionOwnership.ownsOutline("session2","session1"));
        assertFalse(GoogleV2SessionOwnership.ownsOutline("","session1"));
        assertFalse(GoogleV2SessionOwnership.ownsOutline("session2",null));
    }
    @Test public void nullTokensCannotMatch() {
        assertFalse(GoogleV2SessionOwnership.accepts(null,"new",true,false));
        assertFalse(GoogleV2SessionOwnership.accepts("new",null,true,false));
    }
}
