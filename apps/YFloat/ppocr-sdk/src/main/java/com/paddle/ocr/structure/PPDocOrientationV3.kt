package com.paddle.ocr.structure

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Genuine PP-LCNet_x1_0_doc_ori ONNX inference, 0/90/180/270 classes.
 * Preprocess matches PaddleOCR: resize short edge 256, center-crop 224,
 * ImageNet RGB channel normalization, CHW tensor.
 */
class PPDocOrientationV3(model:File):Closeable {
    data class Prediction(val degrees:Int,val confidence:Float)
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    init {
        require(model.isFile && model.length()==6781504L)
        val options=OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            session=env.createSession(model.absolutePath,options)
            require(session.inputNames.size==1){"Orientation ONNX input contract mismatch"}
        }finally{options.close()}
    }
    @Synchronized fun classify(image:Bitmap):Prediction {
        require(image.width>0&&image.height>0&&!image.isRecycled)
        val minSide=minOf(image.width,image.height)
        val resizedWidth=(image.width*256f/minSide).roundToInt().coerceAtLeast(224)
        val resizedHeight=(image.height*256f/minSide).roundToInt().coerceAtLeast(224)
        val resized=Bitmap.createScaledBitmap(image,resizedWidth,resizedHeight,true)
        val crop=Bitmap.createBitmap(resized,(resizedWidth-224)/2,(resizedHeight-224)/2,224,224)
        if(resized!==image)resized.recycle()
        val pixels=IntArray(224*224)
        try{crop.getPixels(pixels,0,224,0,0,224,224)}
        finally{crop.recycle()}
        val chw=FloatArray(3*224*224)
        val mean=floatArrayOf(.485f,.456f,.406f)
        val std=floatArrayOf(.229f,.224f,.225f)
        val area=224*224
        for(i in pixels.indices){
            val p=pixels[i]
            chw[i]=(((p ushr 16) and 255)/255f-mean[0])/std[0]
            chw[area+i]=(((p ushr 8) and 255)/255f-mean[1])/std[1]
            chw[2*area+i]=((p and 255)/255f-mean[2])/std[2]
        }
        OnnxTensor.createTensor(env,FloatBuffer.wrap(chw),longArrayOf(1,3,224,224)).use{input->
            session.run(mapOf(session.inputNames.first() to input)).use {out->
                val tensor=out[0] as OnnxTensor
                val buffer=tensor.floatBuffer.duplicate().apply{rewind()}
                require(buffer.remaining()>=4){"Orientation model output must contain four classes"}
                val values=FloatArray(4){buffer.get()}
                val top=values.indices.maxByOrNull{values[it]}?:0
                // The ONNX graph can already output softmax probabilities.
                // Softmaxing probabilities again would lower a confident 0.99
                // prediction to about 0.47, silently disabling rotation.
                val sum=values.sum()
                val normalized=values.all{it>=0f && it<=1f && it.isFinite()} &&
                    kotlin.math.abs(sum-1f)<0.03f
                val score=if(normalized)values[top] else{
                    val peak=values[top].toDouble()
                    val probs=values.map{exp(it.toDouble()-peak)}
                    (probs[top]/probs.sum()).toFloat()
                }
                return Prediction(intArrayOf(0,90,180,270)[top],score)
            }
        }
    }
    override fun close(){session.close()}
}
