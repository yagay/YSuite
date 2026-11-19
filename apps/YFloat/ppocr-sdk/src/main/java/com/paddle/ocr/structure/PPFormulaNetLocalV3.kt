package com.paddle.ocr.structure

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * PP-FormulaNet_plus-S single-graph ONNX export.
 *
 * Unlike PP-OCR CTC, the model emits token sequences; the UniMERNet
 * ByteLevel vocabulary is mandatory. No text or formula is invented from
 * ordinary OCR glyph positions. This is separate, optional, offline inference.
 */
class PPFormulaNetLocalV3(model:File, tokenizer:File):Closeable {
    data class Formula(val latex:String,val tokenCount:Int)
    private val environment=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    private val vocabulary:List<String>
    private val byteDecoder:Map<Int,Int>

    init {
        require(model.isFile && model.length()>200000000L &&
            model.length()<400000000L){"FormulaNet-S ONNX model missing"}
        val raw=JSONObject(tokenizer.readText(Charsets.UTF_8))
            .getJSONObject("model").getJSONObject("vocab")
        require(raw.length() in 5000..100000){"Unexpected UniMERNet vocabulary"}
        val vocab=MutableList(raw.length()){""}
        val iterator=raw.keys()
        while(iterator.hasNext()) {
            val token=iterator.next()
            val id=raw.getInt(token)
            require(id in vocab.indices){"Invalid formula token id"}
            vocab[id]=token
        }
        require(vocab.none {it.isEmpty()}){"Non-contiguous formula vocabulary"}
        vocabulary=vocab
        byteDecoder=buildByteDecoder()

        val settings=OrtSession.SessionOptions()
        try{
            settings.setIntraOpNumThreads(2)
            session=environment.createSession(model.absolutePath,settings)
            require(session.inputNames.size==1){"FormulaNet must have one input"}
        }finally{settings.close()}
    }

    @Synchronized fun recognize(image:Bitmap):Formula {
        require(!image.isRecycled&&image.width>0&&image.height>0)
        val tensor=preprocess(image)
        OnnxTensor.createTensor(environment,FloatBuffer.wrap(tensor),
            longArrayOf(1,1,384,384)).use{input->
            session.run(mapOf(session.inputNames.first() to input)).use {outputs->
                val tensors=outputs.asSequence().mapNotNull{it.value as? OnnxTensor}.toList()
                require(tensors.isNotEmpty()){"No formula ONNX outputs"}
                // Converted graph can expose generated integer IDs or
                // per-time-step logits. Reject unsupported cache tensors.
                val ids=tensors.firstNotNullOfOrNull {takeTokenIds(it)}
                    ?: error("FormulaNet token sequence output missing")
                val decoded=ArrayList<String>()
                for(id in ids.take(1024)) {
                    if(id==2)break // UniMERNet EOS
                    if(id in intArrayOf(0,1,3))continue // BOS/PAD/UNK
                    require(id in vocabulary.indices){"Formula token outside vocabulary"}
                    decoded.add(vocabulary[id])
                }
                val latex=decodeByteLevel(decoded).trim()
                require(latex.isNotEmpty()){"Formula model produced no LaTeX"}
                return Formula(latex,decoded.size)
            }
        }
    }

    private fun takeTokenIds(tensor:OnnxTensor):List<Int>? {
        val dims=tensor.info.shape
        val type=tensor.info.type
        if(type==OnnxJavaType.INT64 || type==OnnxJavaType.INT32) {
            if(dims.size!=1 && !(dims.size==2&&dims[0]==1L))return null
            val count=dims.last().toInt()
            if(count !in 1..8192)return null
            return if(type==OnnxJavaType.INT64) {
                val buf=tensor.longBuffer.duplicate().apply{rewind()}
                List(count){buf.get().toInt()}
            }else{
                val buf=tensor.intBuffer.duplicate().apply{rewind()}
                List(count){buf.get()}
            }
        }
        if(type!=OnnxJavaType.FLOAT)return null
        val vocabSize=vocabulary.size
        val time=when{
            dims.size==2&&dims[1]==vocabSize.toLong()->dims[0].toInt()
            dims.size==3&&dims[0]==1L&&dims[2]==vocabSize.toLong()->dims[1].toInt()
            else->return null
        }
        if(time !in 1..2560)return null
        val buffer=tensor.floatBuffer.duplicate().apply{rewind()}
        require(buffer.remaining().toLong()>=time.toLong()*vocabSize){
            "Formula logits output truncated"
        }
        val ids=ArrayList<Int>(time)
        for(step in 0 until time){
            var best=Float.NEGATIVE_INFINITY
            var bestId=0
            for(k in 0 until vocabSize){
                val x=buffer.get()
                if(x>best){best=x;bestId=k}
            }
            ids.add(bestId)
            if(bestId==2)break
        }
        return ids
    }

    private fun preprocess(image:Bitmap):FloatArray {
        val width=image.width
        val height=image.height
        // Match UniMERNet formula preprocessing: content bounds with
        // grayscale variation; retain entire image for near-empty crops.
        val pixels=IntArray(width*height)
        image.getPixels(pixels,0,width,0,0,width,height)
        var min=255
        var max=0
        val gray=IntArray(pixels.size)
        for(i in pixels.indices){
            val p=pixels[i]
            val g=(0.299*((p ushr 16) and 255)+
                0.587*((p ushr 8) and 255)+
                0.114*(p and 255)).roundToInt().coerceIn(0,255)
            gray[i]=g
            if(g<min)min=g
            if(g>max)max=g
        }
        var left=0
        var top=0
        var right=width-1
        var bottom=height-1
        if(max>min) {
            var l=width
            var t=height
            var r=-1
            var b=-1
            for(y in 0 until height)for(x in 0 until width){
                if((gray[y*width+x]-min)*255/(max-min)<200){
                    if(x<l)l=x
                    if(x>r)r=x
                    if(y<t)t=y
                    if(y>b)b=y
                }
            }
            if(r>=l&&b>=t&&
                maxOf(r-l+1,b-t+1).toFloat()/
                    minOf(r-l+1,b-t+1).coerceAtLeast(1)<=200f){
                left=l;right=r;top=t;bottom=b
            }
        }
        val cropped=Bitmap.createBitmap(image,left,top,right-left+1,bottom-top+1)
        val scale=minOf(384f/cropped.width,384f/cropped.height)
        val scaledWidth=(cropped.width*scale).toInt().coerceIn(1,384)
        val scaledHeight=(cropped.height*scale).toInt().coerceIn(1,384)
        val scaled=Bitmap.createScaledBitmap(cropped,scaledWidth,scaledHeight,true)
        try {
            val adjusted=IntArray(scaledWidth*scaledHeight)
            scaled.getPixels(adjusted,0,scaledWidth,0,0,scaledWidth,scaledHeight)
            // Padding is black (0), but the normalized tensor reserves 1
            // outside the model's actual image region.
            val tensor=FloatArray(384*384){1f}
            val padX=(384-scaledWidth)/2
            val padY=(384-scaledHeight)/2
            for(y in 0 until scaledHeight)for(x in 0 until scaledWidth){
                val pixel=adjusted[y*scaledWidth+x]
                val luminance=(0.299*((pixel ushr 16) and 255)+
                    0.587*((pixel ushr 8) and 255)+
                    0.114*(pixel and 255)).toFloat()/255f
                tensor[(y+padY)*384+x+padX]=(luminance-0.7931f)/0.1738f
            }
            return tensor
        }finally{
            if(scaled!==cropped)scaled.recycle()
            if(cropped!==image)cropped.recycle()
        }
    }

    private fun decodeByteLevel(tokens:List<String>):String {
        val bytes=ByteArrayOutputStream(tokens.sumOf{it.length})
        for(token in tokens) {
            val input=token.codePoints().toArray()
            for(cp in input) {
                val b=byteDecoder[cp]
                if(b!=null)bytes.write(b)
                else bytes.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
            }
        }
        return bytes.toString("UTF-8")
    }

    private fun buildByteDecoder():Map<Int,Int> {
        val bytes=ArrayList<Int>()
        for(n in 33..126)bytes.add(n)
        for(n in 161..172)bytes.add(n)
        for(n in 174..255)bytes.add(n)
        val chars=ArrayList<Int>(bytes)
        var next=0
        for(n in 0..255)if(n !in bytes){
            bytes.add(n)
            chars.add(256+next)
            next++
        }
        return chars.indices.associate{chars[it] to bytes[it]}
    }
    override fun close(){session.close()}
}
