package com.yagay.YFloat

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.structure.PPDocLayoutV2
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** V2 is the only active Paddle application adapter; legacy bridge is retired. */
object PaddlePipelineV2 {
    interface Callback {
        fun onSuccess(document: OcrDocument, elapsedMs: Long, lineCount: Int)
        fun onFailure(message: String)
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lock=Mutex()
    private val models=mutableMapOf<Int,PaddleOCR>()
    private val modelSignatures=mutableMapOf<Int,String>()
    private fun signature(app:Context,model:Int):String {
        val files=listOf(OcrModelManager.detFile(app,model),
            OcrModelManager.recFile(app,model),OcrModelManager.ymlFile(app,model))
        check(OcrModelManager.isReady(app,model)){"PP-OCRv6 model not downloaded"}
        return files.joinToString(":") { "${it.length()}-${it.lastModified()}" }
    }
    @Volatile private var latestPage:PaddlePageV2.Page?=null
    @JvmStatic fun lastPage():PaddlePageV2.Page?=latestPage

    @JvmStatic fun recognize(context:Context,image:Bitmap,model:Int,callback:Callback){
        val app=context.applicationContext
        scope.launch {
            try {
                require(!image.isRecycled && image.width>0 && image.height>0)
                val started=System.currentTimeMillis()
                val page=lock.withLock {
                    val revision=signature(app,model)
                    if(models.containsKey(model) && modelSignatures[model]!=revision){
                        models.remove(model)?.release()
                        modelSignatures.remove(model)
                        DiagnosticLog.i(app,"PADDLE_V2","model_replaced="+model)
                    }
                    val ocr=models[model] ?: run {
                        check(OcrModelManager.verifyIntegrity(app,model)){"PP-OCRv6 model integrity failed"}
                        check(OpenCVUtils.init(app)){"OpenCV initialization failed"}
                        val cfg=PaddleOCRConfig(
                            detThresh=.20f,detBoxThresh=.45f,detUnclipRatio=1.4f,
                            recScoreThresh=0f,recBatchSize=if(model==OcrModelManager.MEDIUM)2 else 4)
                        PaddleOCR.create(app,cfg,EngineConfig(numThreads=4),
                            OcrModelManager.detFile(app,model).absolutePath,
                            OcrModelManager.recFile(app,model).absolutePath,
                            OcrModelManager.ymlFile(app,model).absolutePath).also{
                                models[model]=it
                                modelSignatures[model]=revision
                            }
                    }
                    val output=ocr.recognize(image)
                    val structure=if(PaddleStructureModelV2.enabled(app)) {
                        try {
                            PPDocLayoutV2(PaddleStructureModelV2.model(app)).use {it.detect(image)}
                        }catch(t:Throwable){
                            DiagnosticLog.i(app,"PP_STRUCTURE_V2",
                                "layout_failed="+t.javaClass.simpleName)
                            emptyList()
                        }
                    }else emptyList()
                    DiagnosticLog.i(app,"PADDLE_V2",
                        "ocr_lines="+output.results.size+" layout_regions="+structure.size+
                        " layoutEnabled="+PaddleStructureModelV2.enabled(app))
                    PaddlePageV2.fromOcr(output.results,image.width,image.height,structure)
                }
                latestPage=page
                val compat=page.asLegacy("ppocr-v2-"+model)
                withContext(Dispatchers.Main){
                    callback.onSuccess(compat,System.currentTimeMillis()-started,
                        compat.lines().size)
                }
            }catch(t:Throwable){
                withContext(Dispatchers.Main) {
                    callback.onFailure(t.javaClass.simpleName+": "+(t.message ?: "unknown"))
                }
            }
        }
    }
    @JvmStatic fun releaseModel(model:Int){
        scope.launch {
            lock.withLock {
                models.remove(model)?.release()
                modelSignatures.remove(model)
            }
        }
    }
}
