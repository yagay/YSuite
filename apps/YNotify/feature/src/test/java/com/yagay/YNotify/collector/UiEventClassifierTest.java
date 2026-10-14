package com.yagay.YNotify.collector;

import android.view.accessibility.AccessibilityEvent;
import com.yagay.YNotify.data.EventTypes;
import org.junit.Test;
import static org.junit.Assert.*;

public class UiEventClassifierTest {
    @Test public void postedNotificationPayloadNeverBecomesToast() {
        assertNull(UiEventClassifier.classify(
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
                "android.widget.Toast", "", true));
        assertEquals(EventTypes.TOAST,UiEventClassifier.classify(
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
                "android.widget.Toast", "", false));
    }

    @Test public void popupRequiresWindowTransition() {
        assertNull(UiEventClassifier.classify(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                "android.widget.PopupWindow", "", false));
        assertEquals(EventTypes.POPUP,UiEventClassifier.classify(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "android.widget.PopupWindow", "", false));
    }

    @Test public void dialogOnlyObservedOnWindowTransition() {
        assertNull(UiEventClassifier.classify(
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                "android.app.AlertDialog", "", false));
        assertEquals(EventTypes.DIALOG,UiEventClassifier.classify(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "android.app.AlertDialog", "", false));
    }

    @Test public void genericSystemUiTextDoesNotCreateUiEvent() {
        assertNull(UiEventClassifier.classify(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                "android.widget.TextView","",false));
        assertNull(UiEventClassifier.classify(
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
                "android.widget.FrameLayout","",false));
    }
}
