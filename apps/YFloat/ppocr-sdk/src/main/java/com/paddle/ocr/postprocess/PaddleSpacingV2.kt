package com.paddle.ocr.postprocess

import com.paddle.ocr.model.OCRCharacter
import kotlin.math.max

/**
 * Local, lossless-by-default spacing refinement after CTC alignment.
 *
 * Changes only strongly-supported, spurious whitespace and returns surviving
 * source tokens with their original quads. It never creates synthetic boxes,
 * and it does not rewrite latin words using a language-model guess.
 */
object PaddleSpacingV2 {
    data class Correction(
        val text: String,
        val characters: List<OCRCharacter>,
        val removedSpaces: Int,
    )

    @JvmStatic fun correct(characters: List<OCRCharacter>, fallbackText: String): Correction {
        if (characters.isEmpty()) return Correction(fallbackText, emptyList(), 0)
        val kept = ArrayList<OCRCharacter>(characters.size)
        var removed = 0
        for (i in characters.indices) {
            val current = characters[i]
            if (current.text.isBlank() && current.text.any { it.isWhitespace() }) {
                val prev = characters.subList(0, i).lastOrNull { it.text.isNotBlank() }
                val next = characters.subList(i + 1, characters.size)
                    .firstOrNull { it.text.isNotBlank() }
                if (prev != null && next != null && spurious(prev, current, next)) {
                    removed++
                    continue
                }
            }
            kept.add(current)
        }
        // Do not lose decoder text if the SDK can't align a token to geometry.
        val corrected = kept.joinToString("") { it.text }
        return Correction(corrected.ifEmpty { fallbackText }, kept, removed)
    }

    private fun spurious(prev: OCRCharacter, space: OCRCharacter, next: OCRCharacter): Boolean {
        val left = prev.text.codePointBefore(prev.text.length)
        val right = next.text.codePointAt(0)
        // Unspaced CJK scripts and adjacent full-width punctuation.
        if (isCjk(left) && isCjk(right)) return true
        if (isCjk(left) && isCjkClosing(right)) return true
        if (isCjkOpening(left) && isCjk(right)) return true
        if (isCjkClosing(left) && isCjk(right)) return true
        if (isCjk(left) && isCjkOpening(right)) return true

        // Split decimals, IP addresses, version numbers and digit separators.
        if (Character.isDigit(left) && right in intArrayOf(
                '.'.code, ','.code, ':'.code, '/'.code, '%'.code)) return true
        if (left in intArrayOf('.'.code, ','.code, ':'.code, '/'.code) &&
            Character.isDigit(right)) return true

        // Latin words retain spaces unless a low-confidence CTC whitespace
        // occupies essentially no image gap between tightly adjoining glyphs.
        if (Character.isLetterOrDigit(left) && Character.isLetterOrDigit(right)
            && !isCjk(left) && !isCjk(right) && space.confidence < 0.45f) {
            val a = prev.box.bounds()
            val b = next.box.bounds()
            val xGap = max(0, b.left - a.right)
            val averageWidth = ((a.width() + b.width()) * 0.5f).coerceAtLeast(1f)
            val yOverlap = max(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
            val minHeight = minOf(a.height(), b.height()).coerceAtLeast(1)
            return yOverlap >= minHeight * 0.45f && xGap <= averageWidth * 0.12f
        }
        return false
    }

    private fun isCjk(cp: Int): Boolean {
        return when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
            else -> false
        }
    }
    private fun isCjkClosing(cp: Int): Boolean =
        cp in intArrayOf('，'.code,'。'.code,'！'.code,'？'.code,'：'.code,'；'.code,
            '）'.code,'】'.code,'》'.code,'、'.code,'”'.code,'’'.code)
    private fun isCjkOpening(cp: Int): Boolean =
        cp in intArrayOf('（'.code,'【'.code,'《'.code,'“'.code,'‘'.code)
}
