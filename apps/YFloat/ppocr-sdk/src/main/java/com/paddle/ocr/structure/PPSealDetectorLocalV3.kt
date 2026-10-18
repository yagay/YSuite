package com.paddle.ocr.structure

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.PointF
import com.paddle.ocr.postprocess.PolygonUnclip
import com.paddle.ocr.preprocess.DetPreprocessor
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * PP-OCRv4 mobile seal detector. Preserve model-derived curved polygon
 * boundaries instead of masquerading as ordinary four-corner text boxes.
 */
class PPSealDetectorLocalV3(model:File):Closeable {
    data class Region(val polygon:List<PointF>,val confidence:Float)
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

    @Synchronized fun detect(image:Bitmap):List<Region> {
        require(!image.isRecycled&&image.width>0&&image.height>0)
        // PaddleOCR PP-OCRv4 mobile seal preset: BGR, resize_long 736,
        // DB score threshold 0.6, unclip_ratio 0.5 and box_type poly.
        val input=DetPreprocessor.preprocess(image,736,"resize_long",4000,"BGR")
        OnnxTensor.createTensor(env,FloatBuffer.wrap(input.tensorData),input.shape).use{tensor->
            session.run(mapOf(session.inputNames.first() to tensor)).use{outputs->
                val confidence=outputs.asSequence().mapNotNull{it.value as? OnnxTensor}
                    .firstOrNull{
                        val shape=it.info.shape
                        shape.size==4&&shape[0]==1L&&shape[1]==1L&&
                            shape[2]>0L&&shape[3]>0L
                    }?:error("Seal detector probability map missing")
                val shape=confidence.info.shape
                val height=shape[2].toInt()
                val width=shape[3].toInt()
                val count=height.toLong()*width
                val buffer=confidence.floatBuffer.duplicate().apply{rewind()}
                require(count in 1..20000000 && buffer.remaining().toLong()>=count){
                    "Seal detector map invalid"
                }
                val values=FloatArray(count.toInt())
                buffer.get(values)
                return polygons(values,width,height,image.width,image.height)
            }
        }
    }

    private fun polygons(probabilities:FloatArray,width:Int,height:Int,
                         originalWidth:Int,originalHeight:Int):List<Region> {
        val scoreMap=Mat(height,width,CvType.CV_32FC1)
        val thresholded=Mat()
        val mask=Mat()
        val hierarchy=Mat()
        val contours=mutableListOf<MatOfPoint>()
        try {
            scoreMap.put(0,0,probabilities)
            Imgproc.threshold(scoreMap,thresholded,0.2,255.0,Imgproc.THRESH_BINARY)
            thresholded.convertTo(mask,CvType.CV_8UC1)
            Imgproc.findContours(mask,contours,hierarchy,
                Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE)
            val out=ArrayList<Region>()
            for(contour in contours.take(1000)) {
                if(Imgproc.contourArea(contour)<12.0)continue
                val points=contour.toList()
                val bounds=Imgproc.boundingRect(contour)
                if(bounds.width<3||bounds.height<3)continue
                val roi=scoreMap.submat(bounds)
                val stencil=Mat(bounds.height,bounds.width,CvType.CV_8UC1,Scalar(0.0))
                val shifted=MatOfPoint()
                try{
                    shifted.fromList(points.map{Point(it.x-bounds.x,it.y-bounds.y)})
                    Imgproc.fillPoly(stencil,listOf(shifted),Scalar(1.0))
                    val confidence=Core.mean(roi,stencil).`val`[0].toFloat()
                    if(!confidence.isFinite()||confidence<0.60f)continue
                    val unclip=PolygonUnclip.unclip(points,0.5f)
                    val boundary=if(unclip.size>=3)unclip else points
                    val transformed=boundary.map{
                        PointF((it.x*originalWidth/width).toFloat()
                            .coerceIn(0f,originalWidth.toFloat()),
                            (it.y*originalHeight/height).toFloat()
                            .coerceIn(0f,originalHeight.toFloat()))
                    }
                    if(transformed.size>=3)out.add(Region(transformed,confidence))
                }finally{
                    shifted.release()
                    stencil.release()
                    roi.release()
                }
            }
            return out
        }finally{
            contours.forEach{it.release()}
            hierarchy.release()
            mask.release()
            thresholded.release()
            scoreMap.release()
        }
    }

    override fun close(){session.close()}
}
