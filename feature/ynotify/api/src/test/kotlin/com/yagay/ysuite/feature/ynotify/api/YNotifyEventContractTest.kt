package com.yagay.ysuite.feature.ynotify.api
import org.junit.Assert.*
import org.junit.Test
class YNotifyEventContractTest {
    @Test fun distinctNotificationAndAccessibilityTypesRemainRepresented() {
        assertTrue(YNotifyEventType.entries.contains(YNotifyEventType.Notification))
        assertTrue(YNotifyEventType.entries.contains(YNotifyEventType.SystemUi))
        assertTrue(YNotifyEventType.entries.contains(YNotifyEventType.Toast))
        assertTrue(YNotifyNotificationKind.entries.contains(YNotifyNotificationKind.ForegroundService))
    }
}
