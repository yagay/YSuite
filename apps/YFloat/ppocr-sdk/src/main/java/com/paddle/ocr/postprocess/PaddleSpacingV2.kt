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
        // Normalize only CJK-internal spacing independent of glyph geometry.
        // Paddle can emit an explicit high-confidence space between Han
        // characters; that is a segmentation artefact, not a visual gap.
        val safeFallback=normalizeCjkSpacing(fallbackText)
        val fallbackRemoved=countCandidateSpaces(fallbackText)-
            countCandidateSpaces(safeFallback)
        if (characters.isEmpty())
            return Correction(safeFallback, emptyList(), fallbackRemoved.coerceAtLeast(0))
        // Synchronize script-only space removal in BOTH recognized text
        // and CTC tokens. Previously the text was cleaned while its geometry
        // still contained whitespace characters that split selection groups.
        val aligned=characters.joinToString("") { it.text }
        val cjkCleaned=stripCjkWhitespaceTokens(characters)
        // The CTC geometry can be incomplete even when recognition succeeds.
        // Never reconstruct the complete recognized string from a partial
        // glyph list. But keep its CJK-safe tokens consistent with the text.
        if(fallbackText.isNotBlank() && aligned!=fallbackText)
            return Correction(safeFallback,cjkCleaned,fallbackRemoved.coerceAtLeast(0))
        val kept = ArrayList<OCRCharacter>(cjkCleaned.size)
        var removed = 0
        for (i in cjkCleaned.indices) {
            val current = cjkCleaned[i]
            if (current.text.isBlank() && current.text.any { it.isWhitespace() }) {
                val prev = cjkCleaned.subList(0, i).lastOrNull { it.text.isNotBlank() }
                val next = cjkCleaned.subList(i + 1, cjkCleaned.size)
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
        val source=corrected.ifEmpty { fallbackText }
        val normalized=normalizeCjkSpacing(source)
        val additional=(countCandidateSpaces(source)-countCandidateSpaces(normalized))
            .coerceAtLeast(0)
        return Correction(normalized,kept,removed+additional)
    }

    /**
     * Remove only CJK-internal explicit blank tokens, never Latin separators.
     * Keep all remaining OCRCharacter instances and their original quads;
     * no guessed bounding boxes are introduced.
     */
    private fun stripCjkWhitespaceTokens(tokens:List<OCRCharacter>):List<OCRCharacter>{
        if(tokens.isEmpty())return tokens
        val out=ArrayList<OCRCharacter>(tokens.size)
        for(i in tokens.indices){
            val token=tokens[i].text
            val blank=token.isNotEmpty() &&
                token.codePoints().toArray().all { isCandidateSpace(it) }
            if(blank){
                val left=tokens.subList(0,i).lastOrNull{it.text.isNotBlank()}?.text
                val right=tokens.subList(i+1,tokens.size)
                    .firstOrNull{it.text.isNotBlank()}?.text
                if(!left.isNullOrEmpty()&&!right.isNullOrEmpty()&&
                    joinAroundCjk(left.codePointBefore(left.length),
                        right.codePointAt(0)))continue
            }
            out.add(tokens[i])
        }
        return out
    }

    /**
     * Safe even if CTC glyph-box alignment is incomplete. Only remove spaces
     * separating two CJK scripts or adjacent to CJK punctuation. Never strip
     * normal Latin word separators, CJK/Latin separators, or line breaks.
     * Repeating this normalization does not change the result.
     */
    @JvmStatic fun normalizeCjkSpacing(text:String):String {
        if(text.isEmpty())return text
        val cps=text.codePoints().toArray()
        val out=StringBuilder(text.length)
        for(i in cps.indices){
            val current=cps[i]
            if(isCandidateSpace(current)){
                var a=i-1
                while(a>=0&&isCandidateSpace(cps[a]))a--
                var b=i+1
                while(b<cps.size&&isCandidateSpace(cps[b]))b++
                if(a>=0&&b<cps.size&&joinAroundCjk(cps[a],cps[b]))continue
            }
            out.appendCodePoint(current)
        }
        return out.toString()
    }

    private fun isCandidateSpace(cp:Int):Boolean =
        cp==32 || cp==0x00a0 || cp==0x3000 || cp==0x202f

    private fun countCandidateSpaces(text:String):Int =
        text.codePoints().filter{isCandidateSpace(it)}.count().toInt()

    private fun joinAroundCjk(left:Int,right:Int):Boolean =
        (isCjk(left)&&isCjk(right)) ||
        (isCjk(left)&&isCjkClosing(right)) ||
        (isCjkOpening(left)&&isCjk(right)) ||
        (isCjkClosing(left)&&isCjk(right)) ||
        (isCjk(left)&&isCjkOpening(right))

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
