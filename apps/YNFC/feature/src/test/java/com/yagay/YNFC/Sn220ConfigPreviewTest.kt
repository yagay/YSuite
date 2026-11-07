package com.yagay.YNFC

import java.nio.charset.StandardCharsets
import org.junit.Assert.*
import org.junit.Test

class Sn220ConfigPreviewTest {
    private val stock = (
        "# Source RF tuning lines must not change\n" +
        "NXP_SET_CONFIG_ALWAYS=0x00\n" +
        "NXP_CORE_CONF={20, 02, 06, 02, 30, 01, 08, 33, 00}\n" +
        "NXP_RF_CONF_BLK_1={01, 02, 03}\n"
    ).toByteArray(StandardCharsets.UTF_8)

    @Test fun generatesModifiedCopyWithoutTouchingSource() {
        val before = stock.clone()
        val candidate = Sn220ConfigPreview.preview(stock, "C1B0BC1B")
        assertArrayEquals(before, stock)
        val result = String(candidate.modified, StandardCharsets.UTF_8)
        assertTrue(result.contains("33, 04, C1, B0, BC, 1B"))
        assertTrue(result.contains("20, 02, 0A, 02"))
        assertTrue(result.contains("NXP_RF_CONF_BLK_1={01, 02, 03}"))
        assertTrue(result.contains("NXP_SET_CONFIG_ALWAYS=0x00"))
        assertEquals(9, candidate.oldCoreBytes)
        assertEquals(13, candidate.newCoreBytes)
        assertEquals(Sn220ConfigPreview.sha256(before), candidate.originalHash)
        assertEquals(Sn220ConfigPreview.sha256(candidate.modified), candidate.modifiedHash)
    }

    @Test fun sevenAndTenByteUidsPreserveNciFrameAccounting() {
        val seven = Sn220ConfigPreview.preview(stock, "04112233445566")
        val ten = Sn220ConfigPreview.preview(stock, "0102030405060708090A")
        assertTrue(String(seven.modified).contains("20, 02, 0D, 02"))
        assertTrue(String(ten.modified).contains("20, 02, 10, 02"))
    }

    @Test fun refusesDuplicatesAndNonEmptyStockIdentity() {
        val existing = stock.decodeToString()
            .replace("20, 02, 06, 02, 30, 01, 08, 33, 00",
                "20, 02, 0A, 02, 30, 01, 08, 33, 04, 11, 22, 33, 44")
        assertThrows(IllegalArgumentException::class.java) {
            Sn220ConfigPreview.preview(existing.toByteArray(), "C1B0BC1B")
        }
        val duplicate = stock.decodeToString()
            .replace("20, 02, 06, 02, 30, 01, 08, 33, 00",
                "20, 02, 08, 03, 30, 01, 08, 33, 00, 33, 00")
        assertThrows(IllegalArgumentException::class.java) {
            Sn220ConfigPreview.preview(duplicate.toByteArray(), "C1B0BC1B")
        }
    }

    @Test fun realSn220NineteenParameterLayoutIsPreserved() {
        // Real device NXP_CORE_CONF parameter order; no proprietary RF
        // block or sensitive data is needed in this fixture.
        val input = "NXP_CORE_CONF={20,02,3A,13,00,02,64,00,18,01,01,21,01,00,28,01,00," +
            "30,01,08,31,01,03,32,01,20,33,00,38,01,01,3E,01,00," +
            "50,01,02,54,01,06,58,01,74,5B,01,00,68,01,01," +
            "80,01,01,81,01,01,82,01,0E,85,01,01}\n"
        val before = input.toByteArray(Charsets.UTF_8)
        val output = Sn220ConfigPreview.preview(before, "C1B0BC1B")
        assertArrayEquals(before, input.toByteArray(Charsets.UTF_8))
        assertEquals(61, output.oldCoreBytes)
        assertEquals(65, output.newCoreBytes)
        assertEquals(0x08, output.stockLaBitFrameSdd)
        assertEquals(0x03, output.stockLaPlatformConfig)
        assertEquals(0x20, output.stockLaSelInfo)
        assertTrue(String(output.modified, Charsets.UTF_8).contains("33, 04, C1, B0, BC, 1B"))
    }

    @Test fun refusesMalformedOrMissingCoreWithoutSilentlyCreatingConfig() {
        val badLength = stock.decodeToString().replace("20, 02, 06, 02", "20, 02, 07, 02")
        assertThrows(IllegalArgumentException::class.java) {
            Sn220ConfigPreview.preview(badLength.toByteArray(), "01020304")
        }
        val noCore = "NXP_CORE_CONF_EXTN={20, 02, 06, 02, 30, 01, 08, 33, 00}"
        assertThrows(IllegalArgumentException::class.java) {
            Sn220ConfigPreview.preview(noCore.toByteArray(), "01020304")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Sn220ConfigPreview.preview(stock, "010203")
        }
    }
}
