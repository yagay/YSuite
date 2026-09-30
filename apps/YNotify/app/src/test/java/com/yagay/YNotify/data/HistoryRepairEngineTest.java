package com.yagay.YNotify.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistoryRepairEngineTest {
    @Test
    public void normalizationIgnoresSpacingAndPunctuation() {
        assertEquals("微信张三你好", HistoryRepairEngine.normalize("微信 · 张三：你好"));
        assertEquals("微信张三你好", HistoryRepairEngine.normalize("微信  张三 你好"));
    }

    @Test
    public void diceRecognizesHighlySimilarNotificationText() {
        double score = HistoryRepairEngine.dice(
                HistoryRepairEngine.normalize("张三：你好，今晚见"),
                HistoryRepairEngine.normalize("张三 你好 今晚见"));
        assertTrue(score >= 0.90d);
    }

    @Test
    public void notificationEvidenceOverridesOldWrongToastType() {
        EventRecord r = new EventRecord();
        r.eventType = EventTypes.TOAST;
        r.source = "notification_listener";
        r.notificationKey = "0|com.example|42|null|10001";
        assertEquals(EventTypes.NOTIFICATION, HistoryRepairEngine.inferEventType(r));
    }

    @Test
    public void classNameCanCorrectOldUiType() {
        EventRecord r = new EventRecord();
        r.eventType = EventTypes.OTHER_UI;
        r.source = "accessibility";
        r.className = "com.google.android.material.snackbar.Snackbar";
        assertEquals(EventTypes.SNACKBAR, HistoryRepairEngine.inferEventType(r));
    }

    @Test
    public void sourceClassCanCorrectOldUiType() {
        EventRecord r = new EventRecord();
        r.eventType = EventTypes.OTHER_UI;
        r.source = "accessibility";
        r.accessibilitySourceClass = "android.widget.Toast$TN";
        assertEquals(EventTypes.TOAST, HistoryRepairEngine.inferEventType(r));
    }

    @Test
    public void systemUiOldOtherUiBecomesSystemUi() {
        EventRecord r = new EventRecord();
        r.eventType = EventTypes.OTHER_UI;
        r.originalEventType = EventTypes.OTHER_UI;
        r.source = "accessibility";
        r.packageName = "com.android.systemui";
        assertEquals(EventTypes.SYSTEM_UI, HistoryRepairEngine.inferEventType(r));
    }

    @Test
    public void staleAccessibilityToastWithoutEvidenceBecomesOtherUi() {
        EventRecord r = new EventRecord();
        r.eventType = EventTypes.TOAST;
        r.originalEventType = EventTypes.TOAST;
        r.source = "accessibility";
        r.packageName = "com.example";
        r.className = "android.widget.FrameLayout";
        assertEquals(EventTypes.OTHER_UI, HistoryRepairEngine.inferEventType(r));
    }

    @Test
    public void bannerSimilarityMatchesIndividualNotificationFields() {
        EventRecord ui = new EventRecord();
        ui.text = "张三：今晚见";

        EventRecord notification = new EventRecord();
        notification.title = "微信";
        notification.text = "张三：今晚见";
        notification.fullText = "张三：今晚见";

        assertEquals(1d, HistoryRepairEngine.bestTextSimilarity(ui, notification), 0.0001d);
    }
}
