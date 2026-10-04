package com.yagay.ysuite.permissions.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionCheckerTest {
    @Test
    fun snapshotPreservesPerPermissionStatus() {
        val checker = object : PermissionChecker {
            override fun status(permission: String): PermissionStatus =
                if (permission == "granted") {
                    PermissionStatus.Granted
                } else {
                    PermissionStatus.Denied
                }
        }

        val result = checker.snapshot(
            listOf(
                PermissionRequirement("granted"),
                PermissionRequirement("denied"),
            ),
        )

        assertEquals(
            PermissionStatus.Granted,
            result.statuses["granted"],
        )
        assertEquals(
            PermissionStatus.Denied,
            result.statuses["denied"],
        )
        assertFalse(result.allGranted)
    }
}
