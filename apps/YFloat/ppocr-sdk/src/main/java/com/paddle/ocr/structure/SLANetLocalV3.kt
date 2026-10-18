package com.paddle.ocr.structure

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Rect
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * ONNX SLANet-1M table structure recognizer. Produces actual cell coordinates
 * and HTML token structure from model outputs, not inferred grid lines.
 * Text insertion is owned by the surrounding Paddle OCR pipeline.
 */
class SLANetLocalV3(model:File,vocabulary:File):Closeable {
    data class Cell(val bounds:Rect,val score:Float)
    data class Result(val tokens:List<String>,val cells:List<Cell>,val score:Float) {
        fun html(texts:List<String>):String {
            var cellIndex=0
            val result=StringBuilder("<table>")
            var pendingCell=false
            for(token in tokens){
                when {
                    token=="<td></td>" -> {
                        result.append("<td>")
                        result.append(escape(texts.getOrNull(cellIndex++) ?: ""))
                        result.append("</td>")
                    }
                    token=="<td" || token=="<td>" -> {
                        result.append(token)
                        pendingCell=true
                        if(token=="<td>"){
                            result.append(escape(texts.getOrNull(cellIndex++) ?: ""))
                            pendingCell=false
                        }
                    }
                    token==">" && pendingCell -> {
                        result.append('>')
                        result.append(escape(texts.getOrNull(cellIndex++) ?: ""))
                        pendingCell=false
                    }
                    token in setOf("<tr>","</tr>","<thead>","</thead>","<tbody>","</tbody>",
                        "</td>","<table>","</table>") ||
                        token.startsWith(" rowspan=") || token.startsWith(" colspan=") -> {
                        result.append(token)
                    }
                }
            }
            return result.append("</table>").toString()
        }
        private fun escape(value:String):String=value.replace("&","&amp;")
            .replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    }
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    private val dict:List<String>
    init {
        require(model.isFile && model.length()==7671589L)
        val content=vocabulary.readLines()
        val start=content.indexOfFirst {it.trim()=="character_dict:"}
        require(start>=0){"Table character_dict missing"}
        val tokens=ArrayList<String>()
        for(line in content.drop(start+1)){
            val s=line.trim()
            if(!s.startsWith("- ")){
                if(tokens.isNotEmpty())break
                continue
            }
            var v=s.substring(2).trim()
            if(v.length>=2&&v[0]== '\'' &&v.last()=='\'')v=v.substring(1,v.length-1)
            else if(v.length>=2&&v[0]=='"'&&v.last()=='"')v=v.substring(1,v.length-1)
            tokens.add(v)
        }
        require(tokens.size==28){"Table model expects 28 vocabulary tokens"}
        dict=listOf("sos")+tokens+"eos"
        val opt=OrtSession.SessionOptions()
        try{
            opt.setIntraOpNumThreads(2)
            session=env.createSession(model.absolutePath,opt)
            require(session.inputNames.size==1){"Table ONNX input contract mismatch"}
        }finally{opt.close()}
    }
    @Synchronized fun detect(image:Bitmap):Result{
        require(image.width>0&&image.height>0&&!image.isRecycled)
        val ratio=488f/maxOf(image.width,image.height)
        val rw=(image.width*ratio).roundToInt().coerceIn(1,488)
        val rh=(image.height*ratio).roundToInt().coerceIn(1,488)
        val resized=Bitmap.createScaledBitmap(image,rw,rh,true)
        val pixels=IntArray(rw*rh)
        try{resized.getPixels(pixels,0,rw,0,0,rw,rh)}
        finally{if(resized!==image)resized.recycle()}
        val area=488*488
        val chw=FloatArray(3*area)
        val mean=floatArrayOf(.485f,.456f,.406f)
        val std=floatArrayOf(.229f,.224f,.225f)
        for(y in 0 until rh)for(x in 0 until rw){
            val p=pixels[y*rw+x]
            val i=y*488+x
            // cv2.imread => BGR but Paddle NormalizeImage uses ImageNet values
            chw[i]=(((p and 255)/255f)-mean[0])/std[0]
            chw[area+i]=((((p ushr 8) and 255)/255f)-mean[1])/std[1]
            chw[2*area+i]=((((p ushr 16) and 255)/255f)-mean[2])/std[2]
        }
        OnnxTensor.createTensor(env,FloatBuffer.wrap(chw),longArrayOf(1,3,488,488)).use{input->
            session.run(mapOf(session.inputNames.first() to input)).use{output->
                var boxTensor:OnnxTensor?=null
                var probTensor:OnnxTensor?=null
                for(item in output){
                    val t=item.value as? OnnxTensor?:continue
                    val dims=t.info.shape
                    if(dims.size==3 && dims.last()==4L)boxTensor=t
                    if(dims.size==3 && dims.last()==dict.size.toLong())probTensor=t
                }
                val bbox=boxTensor ?: error("SLANet output missing cell boxes")
                val probs=probTensor ?: error("SLANet output missing HTML logits")
                val steps=probs.info.shape[1].toInt()
                require(steps in 1..4096){"Invalid table sequence length"}
                val boxBuffer=bbox.floatBuffer.duplicate().apply{rewind()}
                val probBuffer=probs.floatBuffer.duplicate().apply{rewind()}
                require(boxBuffer.remaining()>=steps*4 &&
                    probBuffer.remaining()>=steps*dict.size){"Truncated SLANet output"}
                val tokens=ArrayList<String>()
                val cells=ArrayList<Cell>()
                var confidence=0f
                var seen=0
                for(i in 0 until steps){
                    var best=Float.NEGATIVE_INFINITY
                    var index=0
                    for(k in dict.indices) {
                        val value=probBuffer.get()
                        if(value>best){best=value;index=k}
                    }
                    val x0=boxBuffer.get()
                    val y0=boxBuffer.get()
                    val x1=boxBuffer.get()
                    val y1=boxBuffer.get()
                    if(index==dict.size-1)break
                    if(index==0)continue
                    val token=dict[index]
                    tokens.add(token)
                    confidence+=best;seen++
                    if(token.startsWith("<td")){
                        // TableBoxEncode emits normalized xyxy for table cells.
                        val rect=Rect((x0*image.width).roundToInt().coerceIn(0,image.width),
                            (y0*image.height).roundToInt().coerceIn(0,image.height),
                            (x1*image.width).roundToInt().coerceIn(0,image.width),
                            (y1*image.height).roundToInt().coerceIn(0,image.height))
                        if(!rect.isEmpty) cells.add(Cell(rect,best))
                    }
                }
                return Result(tokens,cells,if(seen>0)confidence/seen else 0f)
            }
        }
    }
    override fun close(){session.close()}
}
