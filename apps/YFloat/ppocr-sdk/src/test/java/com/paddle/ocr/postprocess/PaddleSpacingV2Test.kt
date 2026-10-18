package com.paddle.ocr.postprocess

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression tests: Circle text must never confuse Unicode script spacing with CTC gaps. */
class PaddleSpacingV2Test {
    @Test fun removesOnlySpuriousChineseSpaces() {
        assertEquals("中华人民共和国", PaddleSpacingV2.normalizeCjkSpacing("中 华 人 民 共 和 国"))
        assertEquals("你好，世界！", PaddleSpacingV2.normalizeCjkSpacing("你好 ， 世 界 ！"))
        assertEquals("测试用例", PaddleSpacingV2.normalizeCjkSpacing("测　试 用 例"))
    }

    @Test fun preservesMeaningfulLatinAndMixedScriptSpaces() {
        assertEquals("Hello world", PaddleSpacingV2.normalizeCjkSpacing("Hello world"))
        assertEquals("中文 English 中文", PaddleSpacingV2.normalizeCjkSpacing("中文 English 中文"))
        assertEquals("数据 2026 年", PaddleSpacingV2.normalizeCjkSpacing("数据 2026 年"))
        assertEquals("ABC DEF", PaddleSpacingV2.normalizeCjkSpacing("ABC DEF"))
        assertEquals("甲\n乙", PaddleSpacingV2.normalizeCjkSpacing("甲\n乙"))
    }

    @Test fun cleanupIsIdempotent() {
        val input="识 别 错 误， 文 本 English words 2026 年"
        val first=PaddleSpacingV2.normalizeCjkSpacing(input)
        assertEquals(first,PaddleSpacingV2.normalizeCjkSpacing(first))
    }

    @Test fun incompleteCtcAlignmentDoesNotDisableTextCleanup() {
        val corrected=PaddleSpacingV2.correct(emptyList(),"中 文 测 试")
        assertEquals("中文测试",corrected.text)
        assertEquals(3,corrected.removedSpaces)
    }
}
