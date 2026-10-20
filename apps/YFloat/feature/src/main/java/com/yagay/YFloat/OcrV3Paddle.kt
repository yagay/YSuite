package com.yagay.YFloat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Independent local Paddle V3. One warmed ONNX session serves both the
 * original full-frame recognition and a detection-free ML-region CTC pass.
 * Only Paddle supplies copyable text; ML never substitutes its transcription.
 */
object OcrV3Paddle {
    interface Callback {
        fun success(page:OcrV3Page)
        fun failure(error:Throwable)
    }
    interface RegionCallback {
        fun success(raw:String,confidence:Float)
        fun failure(error:Throwable)
    }
    private data class Session(val model:Int,val stamp:String,val sdk:PaddleOCR)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lock=Mutex()
    private var session:Session?=null

    /** Called under lock. No concurrent model reload during local recognition. */
    private suspend fun requireSession(app:Context):Session {
        val preference=FloatSettings(app).circleFullOcrEngine()
        val model=when(preference){
            1->OcrModelManager.TINY
            2->OcrModelManager.SMALL
            3->OcrModelManager.MEDIUM
            4->OcrModelManager.MOBILE
            else->listOf(OcrModelManager.SMALL,OcrModelManager.MEDIUM,
                OcrModelManager.MOBILE,OcrModelManager.TINY)
                .firstOrNull{OcrModelManager.isReady(app,it)}
                ?:throw IllegalStateException("Download an offline Paddle model first")
        }
        check(OcrModelManager.isReady(app,model)){
            "Selected offline Paddle model is not installed"
        }
        val files=listOf(OcrModelManager.detFile(app,model),
            OcrModelManager.recFile(app,model),
            OcrModelManager.ymlFile(app,model))
        val stamp=files.joinToString("|"){
            "${it.absolutePath}:${it.length()}:${it.lastModified()}"
        }
        var existing=session
        if(existing!=null&&(existing.model!=model||existing.stamp!=stamp)){
            existing.sdk.release()
            existing=null
            session=null
        }
        if(existing==null){
            check(OcrModelManager.verifyIntegrity(app,model)){
                "Paddle model integrity check failed"
            }
            check(OpenCVUtils.init(app)){"OpenCV initialization failed"}
            existing=Session(model,stamp,PaddleOCR.create(
                app,PaddleOCRConfig(),
                EngineConfig(numThreads=Runtime.getRuntime()
                    .availableProcessors().coerceIn(1,4)),
                files[0].absolutePath,files[1].absolutePath,
                files[2].absolutePath))
            session=existing
        }
        return checkNotNull(existing)
    }

    @JvmStatic fun recognize(context:Context, source:Bitmap, callback:Callback){
        val app=context.applicationContext
        scope.launch {
            try {
                require(!source.isRecycled&&source.width>0&&source.height>0)
                val page=lock.withLock {
                    val result=requireSession(app).sdk.recognize(source)
                    parse(result.results)
                }
                withContext(Dispatchers.Main){callback.success(page)}
            }catch(t:Throwable){
                withContext(Dispatchers.Main){callback.failure(t)}
            }finally{
                if(!source.isRecycled)source.recycle()
            }
        }
    }

    /**
     * Runs CTC recognition on source pixels inside the requested source-space
     * rectangle with NO DB detector stage. The input bitmap is private and
     * always recycled. It cannot mutate the page or ML geometry.
     */
    @JvmStatic fun recognizeRegion(context:Context, source:Bitmap,
                                  region:Rect, callback:RegionCallback){
        val app=context.applicationContext
        val bounds=Rect(region)
        scope.launch {
            try {
                require(!source.isRecycled&&source.width>0&&source.height>0)
                val result=lock.withLock{
                    requireSession(app).sdk.recognizeRegion(source,bounds)
                }
                withContext(Dispatchers.Main){
                    callback.success(result?.text ?: "",result?.confidence ?: 0f)
                }
            }catch(t:Throwable){
                withContext(Dispatchers.Main){callback.failure(t)}
            }finally{
                if(!source.isRecycled)source.recycle()
            }
        }
    }

    private fun parse(results:List<OCRResult>):OcrV3Page {
        val lines=ArrayList<OcrV3Page.Line>()
        var sdkCharacters=0
        var matchedCharacters=0
        var originalMisses=0
        var invalidGeometry=0
        var wholeLineFallbacks=0
        for(result in results){
            val raw=result.text  // NEVER trim, correct spaces, or rewrite.
            if(raw.isEmpty())continue
            val row=lines.size
            val rect=box(result.box)
            if(rect.isEmpty)continue
            val chars=ArrayList<OcrV3Page.Glyph>()
            var from=0
            for(c in result.characters){
                sdkCharacters++
                if(c.text.isEmpty()){originalMisses++;continue}
                val b=box(c.box)
                if(b.isEmpty){invalidGeometry++;continue}
                val start=raw.indexOf(c.text,from)
                if(start<0){
                    // Never fabricate a character or silently change Paddle
                    // text. Report exactly where source alignment failed.
                    originalMisses++
                    continue
                }
                val end=start+c.text.length
                chars.add(OcrV3Page.Glyph(c.text,b,row,start,end))
                matchedCharacters++
                from=end
            }
            // If SDK has no CTC glyphs, keep its WHOLE line as one source
            // unit. Do not invent per-character geometry.
            if(chars.isEmpty()){
                wholeLineFallbacks++
                chars.add(OcrV3Page.Glyph(raw,rect,row,0,raw.length))
            }
            lines.add(OcrV3Page.Line(raw,rect,chars))
        }
        return OcrV3Page(lines,sdkCharacters,matchedCharacters,
            originalMisses,invalidGeometry,wholeLineFallbacks)
    }

    private fun box(b:OCRBox):Rect {
        val p=b.points
        if(p.size!=4||p.any{!it.x.isFinite()||!it.y.isFinite()})return Rect()
        return Rect(floor(p.minOf{it.x}).toInt(),
            floor(p.minOf{it.y}).toInt(),
            ceil(p.maxOf{it.x}.toDouble()).toInt(),
            ceil(p.maxOf{it.y}.toDouble()).toInt())
    }
}
