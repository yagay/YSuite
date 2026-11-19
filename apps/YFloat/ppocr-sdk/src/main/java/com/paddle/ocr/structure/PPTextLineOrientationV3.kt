package com.paddle.ocr.structure

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.exp

/**
 * PP-LCNet textline orientation ONNX (0 or 180 degrees).
 * Preprocess is pinned to the official 3x80x160 RGB/ImageNet config.
 * Use only on real perspective-corrected detector line crops.
 */
class PPTextLineOrientationV3(file:File):Closeable {
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    init{
        require(file.isFile&&file.length()>5_000_000L&&file.length()<10_000_000L)
        val options=OrtSession.SessionOptions()
        try{
            options.setIntraOpNumThreads(2)
            session=env.createSession(file.absolutePath,options)
            require(session.inputNames.size==1){"Textline model expects one image input"}
        }finally{options.close()}
    }

    @Synchronized fun shouldRotate180(source:Mat,threshold:Float=.90f):Boolean{
        if(source.empty()||source.cols()<=0||source.rows()<=0)return false
        val rgb=Mat()
        val resized=Mat()
        try{
            Imgproc.cvtColor(source,rgb,Imgproc.COLOR_BGR2RGB)
            Imgproc.resize(rgb,resized,Size(160.0,80.0))
            require(resized.type()==CvType.CV_8UC3)
            val bytes=ByteArray(160*80*3)
            resized.get(0,0,bytes)
            val area=160*80
            val chw=FloatArray(area*3)
            val mean=floatArrayOf(.485f,.456f,.406f)
            val std=floatArrayOf(.229f,.224f,.225f)
            for(i in 0 until area){
                for(c in 0..2){
                    chw[c*area+i]=((bytes[i*3+c].toInt() and 255)/255f-mean[c])/std[c]
                }
            }
            OnnxTensor.createTensor(env,FloatBuffer.wrap(chw),
                longArrayOf(1,3,80,160)).use{input->
                session.run(mapOf(session.inputNames.first() to input)).use{output->
                    val tensor=output.asSequence().mapNotNull{it.value as? OnnxTensor}
                        .firstOrNull{
                            val shape=it.info.shape
                            shape.isNotEmpty() && shape.last()==2L
                        }?:error("Textline model output is not 2-class probabilities")
                    val data=tensor.floatBuffer.duplicate().apply{rewind()}
                    require(data.remaining()>=2){"Textline output truncated"}
                    val a=data.get()
                    val b=data.get()
                    require(a.isFinite()&&b.isFinite()){"Invalid textline scores"}
                    val probability=if(a>=0f&&b>=0f&&
                        kotlin.math.abs(a+b-1f)<.02f)b
                        else{
                            val ea=exp((a-maxOf(a,b)).toDouble())
                            val eb=exp((b-maxOf(a,b)).toDouble())
                            (eb/(ea+eb)).toFloat()
                        }
                    return probability>=threshold
                }
            }
        }finally{
            resized.release()
            rgb.release()
        }
    }
    override fun close(){session.close()}
}
