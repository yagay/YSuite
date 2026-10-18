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
        // The CTC geometry can be incomplete even when recognition succeeds.
        // Do not replace the decoder's complete text with only aligned glyphs.
        val aligned=characters.joinToString("") { it.text }
        if(fallbackText.isNotBlank() && aligned!=fallbackText) return Correction(fallbackText,characters,0)
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
        if(prev.text.isEmpty()||next.text.isEmpty())return false
        val left=prev.text.codePointBefore(prev.text.length)
        val right=next.text.codePointAt(0)
        val cjkPair=isCjk(left)&&isCjk(right)
        val cjkPunctuation=(isCjk(left)&&isCjkClosing(right))||
            (isCjkOpening(left)&&isCjk(right))||
            (isCjkClosing(left)&&isCjk(right))||
            (isCjk(left)&&isCjkOpening(right))
        // A genuine OCR space has confidence and a measurable glyph gap.
        // Do not erase CJK word spacing merely because both sides are Han.
        if(cjkPair||cjkPunctuation) {
            return space.confidence<.72f && visuallyTight(prev,next,.38f)
        }
        val numberSeparator=Character.isDigit(left)&&
            (right=='.'.code||right==','.code||right==':'.code||
             right=='/'.code||right=='%'.code)
        val afterSeparator=(left=='.'.code||left==','.code||
                            left==':'.code||left=='/'.code)&&Character.isDigit(right)
        // "1, 2" can be a legitimate enumeration. Require stronger visual
        // evidence than script alone before joining number punctuation.
        if(numberSeparator||afterSeparator) {
            return space.confidence<.35f && visuallyTight(prev,next,.22f)
        }
        if(Character.isLetterOrDigit(left)&&Character.isLetterOrDigit(right)&&
            !isCjk(left)&&!isCjk(right)) {
            return space.confidence<.45f && visuallyTight(prev,next,.12f)
        }
        return false
    }

    /** Measure gaps along the CTC text direction rather than screen X alone. */
    private fun visuallyTight(a:OCRCharacter,b:OCRCharacter,maxGapRatio:Float):Boolean {
        val pa=a.box.points
        val pb=b.box.points
        if(pa.size!=4||pb.size!=4)return false
        val acx=pa.map{it.x}.average().toFloat()
        val acy=pa.map{it.y}.average().toFloat()
        val bcx=pb.map{it.x}.average().toFloat()
        val bcy=pb.map{it.y}.average().toFloat()
        val dx=bcx-acx
        val dy=bcy-acy
        val distance=kotlin.math.sqrt(dx*dx+dy*dy)
        if(distance<.01f)return false
        val ux=dx/distance
        val uy=dy/distance
        val aMin=pa.minOf{it.x*ux+it.y*uy}
        val aMax=pa.maxOf{it.x*ux+it.y*uy}
        val bMin=pb.minOf{it.x*ux+it.y*uy}
        val bMax=pb.maxOf{it.x*ux+it.y*uy}
        val advance=((aMax-aMin)+(bMax-bMin))*.5f
        if(advance<=.01f)return false
        val gap=max(0f,bMin-aMax)
        return gap<=advance*maxGapRatio
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
