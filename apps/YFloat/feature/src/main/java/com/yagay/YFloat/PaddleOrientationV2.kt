package com.yagay.YFloat

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRCharacter
import com.paddle.ocr.model.OCRResult
import kotlin.math.sqrt

/**
 * Fully offline four-orientation OCR verification, based on the *same*
 * installed recognition model. Not a PP-LCNet orientation classifier.
 *
 * Source image is never recycled. Candidate temporary bitmaps are always
 * released, and original-image glyph geometry is restored after rotation.
 */
object PaddleOrientationV2 {
    data class Choice(val results: List<OCRResult>,val degrees: Int,
                      val confidenceScore: Double)

    suspend fun recognize(engine: PaddleOCR, image: Bitmap, verifyRotation: Boolean): Choice {
        val original=engine.recognize(image).results
        val baseline=score(original)
        if(!verifyRotation)return Choice(original,0,baseline)
        var best=Choice(original,0,baseline)
        for(degrees in intArrayOf(90,180,270)) {
            val matrix=Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated=Bitmap.createBitmap(image,0,0,image.width,image.height,matrix,true)
            try {
                val recognized=engine.recognize(rotated).results
                val candidate=score(recognized)
                // A rotated candidate must be materially more convincing than
                // the normal orientation; tie always keeps original geometry.
                if(candidate>best.confidenceScore*1.30+0.9 &&
                    recognized.sumOf {it.text.codePointCount(0,it.text.length)}>=4) {
                    best=Choice(recognized.map{backToImage(it,degrees,image.width,image.height)},
                        degrees,candidate)
                }
            } finally { if(rotated!==image)rotated.recycle() }
        }
        return best
    }

    private fun score(results:List<OCRResult>):Double {
        var weighted=0.0
        var characters=0
        for(result in results) {
            val value=result.text
            val meaningful=value.codePoints().filter {Character.isLetterOrDigit(it)}.count().toInt()
            if(meaningful<=0)continue
            weighted+=meaningful*result.confidence.coerceIn(0f,1f)
            characters+=meaningful
        }
        return if(characters==0)0.0 else weighted/characters*sqrt(characters.toDouble())
    }

    private fun backToImage(source:OCRResult,degrees:Int,width:Int,height:Int):OCRResult {
        fun point(p:PointF):PointF {
            val x=p.x
            val y=p.y
            val mapped=when(degrees){
                90 -> PointF(y,height-x)
                180 -> PointF(width-x,height-y)
                270 -> PointF(width-y,x)
                else -> PointF(x,y)
            }
            return PointF(mapped.x.coerceIn(0f,width.toFloat()),
                mapped.y.coerceIn(0f,height.toFloat()))
        }
        fun box(box:OCRBox)=OCRBox(box.points.map(::point))
        fun character(c:OCRCharacter)=c.copy(box=box(c.box))
        val chars=source.characters.map(::character)
        val words=source.words.map { word ->
            word.copy(box=box(word.box),characters=word.characters.map(::character))
        }
        return source.copy(box=box(source.box),characters=chars,
            words=words,wordBoxes=words.map{it.box}.takeIf{it.isNotEmpty()})
    }
}
