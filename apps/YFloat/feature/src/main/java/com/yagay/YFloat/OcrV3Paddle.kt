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

/** Independent V3 local Paddle recognizer. No legacy OCR adapters are called. */
object OcrV3Paddle {
    interface Callback {
        fun success(page:OcrV3Page)
        fun failure(error:Throwable)
    }
    private data class Session(val model:Int,val stamp:String,val sdk:PaddleOCR)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lock=Mutex()
    private var session:Session?=null

    @JvmStatic fun recognize(context:Context, source:Bitmap, callback:Callback){
        val app=context.applicationContext
        scope.launch {
            try {
                require(!source.isRecycled && source.width>0 && source.height>0)
                val page=lock.withLock {
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
                    if(existing!=null && (existing.model!=model||existing.stamp!=stamp)){
                        existing.sdk.release()
                        existing=null;session=null
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
                    val result=checkNotNull(existing).sdk.recognize(source)
                    parse(result.results)
                }
                withContext(Dispatchers.Main){callback.success(page)}
            }catch(t:Throwable){
                withContext(Dispatchers.Main){callback.failure(t)}
            }finally{
                // The OCR input is a private copy owned by V3.
                if(!source.isRecycled)source.recycle()
            }
        }
    }

    private fun parse(results:List<OCRResult>):OcrV3Page {
        val lines=ArrayList<OcrV3Page.Line>()
        for(result in results){
            val raw=result.text  // NEVER trim, correct spaces, or rewrite.
            if(raw.isEmpty())continue
            val row=lines.size
            val rect=box(result.box)
            if(rect.isEmpty)continue
            val chars=ArrayList<OcrV3Page.Glyph>()
            var from=0
            for(c in result.characters){
                if(c.text.isEmpty())continue
                val b=box(c.box)
                if(b.isEmpty)continue
                val start=raw.indexOf(c.text,from)
                if(start<0)continue // no synthetic replacement for unmatched CTC
                val end=start+c.text.length
                chars.add(OcrV3Page.Glyph(c.text,b,row,start,end))
                from=end
            }
            // If SDK has no CTC glyphs, keep its WHOLE line as one source
            // unit. Do not invent per-character geometry.
            if(chars.isEmpty())
                chars.add(OcrV3Page.Glyph(raw,rect,row,0,raw.length))
            lines.add(OcrV3Page.Line(raw,rect,chars))
        }
        return OcrV3Page(lines)
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
