package com.paddle.ocr.model

import android.graphics.PointF
import kotlin.math.abs

/** Shared SDK word segmentation, independent of YFloat's presentation layer. */
object OCRWordSegmenter {
    @JvmStatic fun segment(characters: List<OCRCharacter>): List<OCRWord> {
        if (characters.isEmpty()) return emptyList()
        val out=ArrayList<OCRWord>()
        val current=ArrayList<OCRCharacter>()

        fun flush(){
            if(current.isEmpty())return
            val text=current.joinToString(""){it.text}
            if(text.isNotBlank()){
                out.add(OCRWord(text,merge(current),current.toList(),
                    current.map{it.confidence}.average().toFloat()))
            }
            current.clear()
        }

        for(character in characters){
            val text=character.text
            if(text.isBlank()){flush();continue}
            val cp=text.codePointAt(0)
            val script=Character.UnicodeScript.of(cp)
            val isolated=script==Character.UnicodeScript.HAN
                    ||script==Character.UnicodeScript.HIRAGANA
                    ||script==Character.UnicodeScript.KATAKANA
                    ||Character.getType(cp)==Character.OTHER_PUNCTUATION
                    ||Character.getType(cp)==Character.START_PUNCTUATION
                    ||Character.getType(cp)==Character.END_PUNCTUATION
            if(isolated)flush()
            current.add(character)
            if(isolated)flush()
        }
        flush()
        return out
    }

    private fun merge(chars:List<OCRCharacter>):OCRBox {
        if(chars.size==1)return chars[0].box
        val first=chars.first().box.points
        val last=chars.last().box.points
        val firstCx=(first[0].x+first[2].x)*.5f
        val firstCy=(first[0].y+first[2].y)*.5f
        val lastCx=(last[0].x+last[2].x)*.5f
        val lastCy=(last[0].y+last[2].y)*.5f
        val vertical=abs(lastCy-firstCy)>abs(lastCx-firstCx)*1.5f
        // Preserve a rotated four-point contour when joining CTC glyphs.
        return if(vertical){
            OCRBox(listOf(PointF(first[0].x,first[0].y),
                PointF(first[1].x,first[1].y),
                PointF(last[2].x,last[2].y),
                PointF(last[3].x,last[3].y)))
        }else{
            OCRBox(listOf(PointF(first[0].x,first[0].y),
                PointF(last[1].x,last[1].y),
                PointF(last[2].x,last[2].y),
                PointF(first[3].x,first[3].y)))
        }
    }
}
