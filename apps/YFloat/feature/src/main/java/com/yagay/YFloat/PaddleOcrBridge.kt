package com.yagay.YFloat

import android.content.Context
import android.graphics.Bitmap

/**
 * Compatibility entrypoint for pre-V2 callers only.
 *
 * The former bridge duplicated Paddle model sessions, rewrote OCR output into
 * ML Kit-like element/group semantics, and fabricated per-character boxes.
 * Both operations are now forbidden: there is ONE PaddlePipelineV2 runtime
 * and its original OCR geometry flows unchanged into OcrDocument.
 */
@Deprecated("Use PaddleRecognitionRuntime and PaddlePipelineV2 directly")
object PaddleOcrBridge {
    interface Callback {
        fun onSuccess(document:OcrDocument,totalMs:Long,lineCount:Int)
        fun onFailure(message:String)
    }

    @JvmStatic fun recognize(context:Context,bitmap:Bitmap,model:Int,callback:Callback){
        PaddlePipelineV2.recognize(context,bitmap,model,object:PaddlePipelineV2.Callback {
            override fun onSuccess(document:OcrDocument,elapsedMs:Long,lineCount:Int){
                callback.onSuccess(document,elapsedMs,lineCount)
            }
            override fun onFailure(message:String){callback.onFailure(message)}
        })
    }

    @JvmStatic fun releaseModel(model:Int){PaddlePipelineV2.releaseModel(model)}

    @JvmStatic fun isLoaded(model:Int):Boolean = PaddlePipelineV2.isLoaded(model)
}
