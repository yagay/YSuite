package com.paddle.ocr.structure

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * UVDoc ONNX document image unwarping. Input "img" is BGR float32 in [0,1],
 * NCHW; output is a corrected BGR image, also NCHW in [0,1].
 *
 * UVDoc does not provide an inverse deformation field. NEVER reuse output OCR
 * coordinates as coordinates on the original screenshot.
 */
class UVDocLocalV3(model:File):Closeable {
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    init {
        require(model.isFile && model.length()==31684150L) {
            "Pinned UVDoc model not present"
        }
        val options=OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            session=env.createSession(model.absolutePath,options)
            require(session.inputNames.size==1){"UVDoc expected one input"}
        }finally{options.close()}
    }
    @Synchronized fun rectify(image:Bitmap):Bitmap {
        require(!image.isRecycled && image.width>0 && image.height>0)
        // Runtime bounded to a document preview, never upsample large screenshot
        // into a 4000x4000 ONNX tensor.
        val longest=maxOf(image.width,image.height)
        val factor=minOf(1.0f,512f/longest)
        fun round32(v:Int)=((v+16)/32*32).coerceIn(64,512)
        val width=round32((image.width*factor).roundToInt())
        val height=round32((image.height*factor).roundToInt())
        val resized=Bitmap.createScaledBitmap(image,width,height,true)
        val pixels=IntArray(width*height)
        try{resized.getPixels(pixels,0,width,0,0,width,height)}
        finally{if(resized!==image)resized.recycle()}
        val n=width*height
        val chw=FloatArray(n*3)
        for(i in pixels.indices){
            val rgb=pixels[i]
            chw[i]=(rgb and 255)/255f
            chw[n+i]=((rgb ushr 8) and 255)/255f
            chw[2*n+i]=((rgb ushr 16) and 255)/255f
        }
        OnnxTensor.createTensor(env,FloatBuffer.wrap(chw),
            longArrayOf(1,3,height.toLong(),width.toLong())).use{input->
            session.run(mapOf(session.inputNames.first() to input)).use{result->
                val candidates=result.filter{ it.value is OnnxTensor &&
                    (it.value as OnnxTensor).info.shape.size==4 }
                require(candidates.size==1){"Expected a single UVDoc image output"}
                val output=candidates.single().value as OnnxTensor
                val shape=output.info.shape
                require(shape[0]==1L&&shape[1]==3L&&shape[2]>0&&shape[3]>0&&
                    shape[2]*shape[3]<=1024L*1024L) {
                    "UVDoc output contract mismatch"
                }
                val oh=shape[2].toInt()
                val ow=shape[3].toInt()
                val area=oh*ow
                val src=output.floatBuffer.duplicate().apply{rewind()}
                require(src.remaining()>=area*3){"UVDoc output truncated"}
                val data=FloatArray(area*3)
                src.get(data)
                fun byte(value:Float):Int= if(value.isFinite())
                    (value*255f).roundToInt().coerceIn(0,255) else 0
                val dest=IntArray(area)
                for(i in 0 until area){
                    val blue=byte(data[i])
                    val green=byte(data[area+i])
                    val red=byte(data[2*area+i])
                    dest[i]=(255 shl 24) or (red shl 16) or (green shl 8) or blue
                }
                return Bitmap.createBitmap(dest,ow,oh,Bitmap.Config.ARGB_8888)
            }
        }
    }
    override fun close(){session.close()}
}
