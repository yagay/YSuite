package com.yagay.YFloat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
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
 * NEW independent Circle OCR pipeline.
 *
 * One original Paddle detector + recognizer pass. No PaddlePipelineV2,
 * CircleStableOcr, PaddleRecognitionRuntime, orientation reruns, spelling
 * fixes, spacing normalization, synthetic glyph segmentation, layout,
 * tables, charts or other postprocessing. The SDK's OCRResult is the
 * authoritative source of text, glyphs and quads.
 *
 * Installed ONNX weights and cancellation tokens are infrastructure, not
 * the legacy OCR engine. The model session is serialized and reused.
 */
internal object CircleRawPaddleOcr {
    private data class Loaded(val model:Int,val signature:String,val engine:PaddleOCR)
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lane=Mutex()
    private var loaded:Loaded?=null

    /**
     * Compatibility surface for the original inline Circle UI. Both UIs
     * execute the SAME raw SDK pipeline, not a legacy OCR adapter.
     */
    @JvmStatic fun recognizeFullScreenSelected(
        context:Context,source:Bitmap,callback:OcrEngine.DocumentCallback
    ) = recognize(context,source,PaddleRequestToken(),callback)

    @JvmStatic fun recognizeCorrectionSelected(
        context:Context,source:Bitmap,callback:OcrEngine.DocumentCallback
    ) {
        val preference=FloatSettings(context.applicationContext).circleCorrectionEngine()
        if(preference==0){
            callback.onFailure(IllegalStateException(
                context.getString(R.string.yfloat_circle_correction_disabled)))
            return
        }
        recognize(context,source,PaddleRequestToken(),callback,preference)
    }

    @JvmStatic fun fullModeLabel(context:Context):String =
        modeLabel(FloatSettings(context.applicationContext).circleFullOcrEngine())

    @JvmStatic fun correctionModeLabel(context:Context):String {
        val mode=FloatSettings(context.applicationContext).circleCorrectionEngine()
        return if(mode==0)context.getString(R.string.yfloat_ocr_off) else modeLabel(mode)
    }

    private fun modeLabel(mode:Int):String=when(mode){
        1 -> "paddle_tiny"
        2 -> "paddle_small"
        3 -> "paddle_medium"
        4 -> "paddle_mobile"
        else -> "paddle_auto"
    }

    @JvmStatic fun recognize(context:Context,source:Bitmap,
                             request:PaddleRequestToken,
                             callback:OcrEngine.DocumentCallback) =
        recognize(context,source,request,callback,
            FloatSettings(context.applicationContext).circleFullOcrEngine())

    private fun recognize(context:Context,source:Bitmap,
                          request:PaddleRequestToken,
                          callback:OcrEngine.DocumentCallback,selectedMode:Int) {
        val app=context.applicationContext
        worker.launch {
            try {
                val start=SystemClock.uptimeMillis()
                require(!source.isRecycled && source.width>0 && source.height>0) {
                    "Circle OCR requires a live, non-empty bitmap"
                }
                val doc=lane.withLock {
                    request.check()
                    val selected=when(selectedMode){
                        1 -> OcrModelManager.TINY
                        2 -> OcrModelManager.SMALL
                        3 -> OcrModelManager.MEDIUM
                        4 -> OcrModelManager.MOBILE
                        else -> listOf(OcrModelManager.SMALL,OcrModelManager.MEDIUM,
                            OcrModelManager.MOBILE,OcrModelManager.TINY)
                            .firstOrNull{OcrModelManager.isReady(app,it)} ?: 0
                    }
                    require(selected!=0 && OcrModelManager.isReady(app,selected)){
                        "Install the selected offline PaddleOCR model in YFloat settings"
                    }
                    val files=listOf(OcrModelManager.detFile(app,selected),
                        OcrModelManager.recFile(app,selected),
                        OcrModelManager.ymlFile(app,selected))
                    val signature=files.joinToString("|"){
                        "${it.absolutePath}:${it.length()}:${it.lastModified()}"
                    }
                    var active=loaded
                    if(active!=null&&(active.model!=selected||
                            active.signature!=signature)){
                        active.engine.release()
                        loaded=null
                        active=null
                    }
                    if(active==null){
                        check(OcrModelManager.verifyIntegrity(app,selected)){
                            "Offline PaddleOCR files failed integrity verification"
                        }
                        check(OpenCVUtils.init(app)){"OpenCV initialization failed"}
                        val threads=Runtime.getRuntime().availableProcessors().coerceIn(1,4)
                        val engine=PaddleOCR.create(app,PaddleOCRConfig(),
                            EngineConfig(numThreads=threads),
                            files[0].absolutePath,files[1].absolutePath,
                            files[2].absolutePath)
                        active=Loaded(selected,signature,engine)
                        loaded=active
                    }
                    request.check()
                    // This is the ORIGINAL SDK recognition, not the wrapper's
                    // fast-detection/rerun/spacing/structure pipeline.
                    val result=checkNotNull(active).engine.recognize(source)
                    request.check()
                    val document=convert(result.results,source.width,source.height,selected)
                    DiagnosticLog.i(app,"CIRCLE_RAW_OCR",
                        "model=$selected source=raw_sdk"+
                        " input=${source.width}x${source.height}"+
                        " lines=${document.lines().size} glyphs=${document.chars().size}"+
                        " durationMs=${SystemClock.uptimeMillis()-start}")
                    document
                }
                withContext(Dispatchers.Main){callback.onSuccess(doc)}
            }catch(error:Throwable){
                // An obsolete request must still complete so its bitmap
                // owner can recycle the snapshot; the owner ignores old IDs.
                withContext(Dispatchers.Main){callback.onFailure(error)}
            }
        }
    }

    private fun convert(results:List<OCRResult>,width:Int,height:Int,model:Int):OcrDocument{
        val lines=ArrayList<OcrDocument.Line>()
        var order=0
        for(result in results){
            // Never trim or normalize the SDK's recognized content.
            val text=result.text
            if(text.isEmpty()||text.isBlank())continue
            val quad=corners(result.box)
            val boundary=bounds(quad)
            if(boundary.isEmpty)continue
            val row=lines.size
            val chars=ArrayList<OcrDocument.CharUnit>()
            for(glyph in result.characters){
                // Genuine SDK CTC-aligned glyphs only.
                val points=corners(glyph.box)
                val box=bounds(points)
                if(box.isEmpty||glyph.text.isEmpty())continue
                chars.add(OcrDocument.CharUnit(glyph.text,box,
                    glyph.confidence,row,row,order++,points))
            }
            if(chars.isEmpty()){
                // A real detector LINE quad, not fabricated per-letter boxes.
                // This remains selectable as ONE original text unit.
                chars.add(OcrDocument.CharUnit(text,boundary,result.confidence,
                    row,row,order++,quad))
            }
            lines.add(OcrDocument.Line(text,boundary,result.confidence,chars,quad))
        }
        val body=lines.joinToString("\n"){it.text()}
        val confidence=if(lines.isEmpty())0f else
            lines.map{it.confidence().toDouble()}.average().toFloat()
        val document=OcrDocument(body,emptyList(),lines,
            "paddle-raw-circle-$model",confidence,confidence.toDouble(),
            width,height)
        check(document.chars().isNotEmpty() && document.fullText().isNotBlank()){
            "PaddleOCR found no selectable text"
        }
        return document
    }

    private fun corners(box:OCRBox):List<PointF> =
        box.points.map{PointF(it.x,it.y)}

    private fun bounds(quad:List<PointF>):Rect {
        if(quad.size!=4 || quad.any{!it.x.isFinite()||!it.y.isFinite()})
            return Rect()
        return Rect(floor(quad.minOf{it.x}).toInt(),
            floor(quad.minOf{it.y}).toInt(),
            ceil(quad.maxOf{it.x}.toDouble()).toInt(),
            ceil(quad.maxOf{it.y}.toDouble()).toInt())
    }
}
