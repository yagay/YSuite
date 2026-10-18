package com.paddle.ocr.structure

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.postprocess.DBPostProcessor
import com.paddle.ocr.preprocess.DetPreprocessor
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

/**
 * Genuine mobile PP-OCRv4 seal text detector. Outputs detected text quads,
 * not fabricated OCR text or an assumed circular transcript.
 */
class PPSealDetectorLocalV3(model:File):Closeable {
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    init {
        require(model.isFile&&model.length()>1000000L)
        val options=OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            session=env.createSession(model.absolutePath,options)
            require(session.inputNames.size==1){"Seal ONNX input contract mismatch"}
        }finally{options.close()}
    }
    @Synchronized fun detect(image:Bitmap):List<OCRBox>{
        require(!image.isRecycled&&image.width>0&&image.height>0)
        val pre=DetPreprocessor.preprocess(image,960,"max",2000,"BGR")
        OnnxTensor.createTensor(env,FloatBuffer.wrap(pre.tensorData),pre.shape).use{input->
            session.run(mapOf(session.inputNames.first() to input)).use{outputs->
                val imageTensor=outputs.asSequence().mapNotNull{it.value as? OnnxTensor}
                    .firstOrNull{v->
                        val dims=v.info.shape
                        dims.size==4 && dims[0]==1L&&dims[1]==1L&&
                            dims[2]>0L&&dims[3]>0L
                    }?:error("Seal detector probability map missing")
                val dims=imageTensor.info.shape
                val buffer=imageTensor.floatBuffer.duplicate().apply{rewind()}
                val count=(dims[2]*dims[3]).toInt()
                require(count in 1..20000000&&buffer.remaining()>=count) {
                    "Seal detector probability map invalid"
                }
                val scores=FloatArray(count)
                buffer.get(scores)
                return DBPostProcessor.process(scores,dims,0.25f,0.5f,1.7f,
                    500,true,"fast","quad",pre.originalH,pre.originalW)
            }
        }
    }
    override fun close(){session.close()}
}
