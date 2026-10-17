package com.paddle.ocr.structure

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Rect
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Independent PP-StructureV3 layout detection component backed by the
 * PP-DocLayout-S ONNX export, not a text-line grouping heuristic.
 *
 * Verified export input: image [1,3,480,480], scale_factor [1,2].
 * Output: padded detections [M,6], plus num_dets.
 * Does not claim to implement table cell reconstruction, formulas or charts.
 */
class PPDocLayoutV2(model: File) : Closeable {
    data class Region(val category: Int, val label: String,
                      val score: Float, val bounds: Rect)
    companion object {
        const val SIZE = 480
        private val MEAN = floatArrayOf(.485f,.456f,.406f)
        private val STD = floatArrayOf(.229f,.224f,.225f)
        private val LABELS = arrayOf("paragraph_title","image","text","number",
            "abstract","content","figure_title","formula","table","table_title",
            "reference","doc_title","footnote","header","algorithm","footer",
            "seal","chart_title","chart","formula_number","header_image",
            "footer_image","aside_text")
        fun label(category: Int): String = LABELS.getOrNull(category) ?: "unknown"
    }
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    init {
        require(model.isFile && model.length() > 1_000_000L) { "PP-DocLayout-S model missing" }
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            session = env.createSession(model.absolutePath, options)
            require(session.inputNames.contains("image") &&
                session.inputNames.contains("scale_factor")) {
                "PP-DocLayout-S has wrong ONNX input contract"
            }
        } finally { options.close() }
    }

    @Synchronized fun detect(image: Bitmap): List<Region> {
        require(!image.isRecycled && image.width > 0 && image.height > 0)
        val scaled = Bitmap.createScaledBitmap(image,SIZE,SIZE,true)
        val pixels = IntArray(SIZE * SIZE)
        try { scaled.getPixels(pixels,0,SIZE,0,0,SIZE,SIZE) }
        finally { if(scaled !== image)scaled.recycle() }
        val chw = FloatArray(SIZE*SIZE*3)
        val area = SIZE * SIZE
        for(i in pixels.indices){
            val rgb = pixels[i]
            chw[i] = (((rgb ushr 16) and 255) / 255f-MEAN[0])/STD[0]
            chw[area+i] = (((rgb ushr 8) and 255) / 255f-MEAN[1])/STD[1]
            chw[2*area+i] = ((rgb and 255) / 255f-MEAN[2])/STD[2]
        }
        val sf = floatArrayOf(SIZE.toFloat()/image.height,SIZE.toFloat()/image.width)
        OnnxTensor.createTensor(env,FloatBuffer.wrap(chw),longArrayOf(1,3,480,480)).use { input ->
            OnnxTensor.createTensor(env,FloatBuffer.wrap(sf),longArrayOf(1,2)).use { factor ->
                session.run(mapOf("image" to input,"scale_factor" to factor)).use { output ->
                    var dets: OnnxTensor? = null
                    var num: OnnxTensor? = null
                    for(entry in output){
                        val tensor=entry.value as? OnnxTensor ?: continue
                        val shape=tensor.info.shape
                        if(shape.isNotEmpty() && shape.last()==6L && shape.size>=2)
                            dets=tensor
                        else if(shape.size<=2 && shape.all { it==1L || it==-1L })
                            num=tensor
                    }
                    val boxes=dets ?: error("layout detection tensor [M,6] missing")
                    val shape=boxes.info.shape
                    val capacity = shape[shape.size-2].toInt()
                    val copy=boxes.floatBuffer.duplicate().apply { rewind() }
                    val rows=FloatArray(capacity*6)
                    require(copy.remaining()>=rows.size){"layout output is truncated"}
                    copy.get(rows)
                    val validCount = readDetectionCount(num ?: error("layout num_dets missing"))
                        .coerceIn(0,capacity)
                    val regions=ArrayList<Region>()
                    for(i in 0 until validCount){
                        val offset=i*6
                        val type=rows[offset].toInt()
                        val score=rows[offset+1]
                        val x1=rows[offset+2]
                        val y1=rows[offset+3]
                        val x2=rows[offset+4]
                        val y2=rows[offset+5]
                        if(type !in LABELS.indices || !score.isFinite() || score<.3f ||
                            !x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite())
                            continue
                        val rect=Rect(floor(x1).toInt().coerceIn(0,image.width),
                            floor(y1).toInt().coerceIn(0,image.height),
                            ceil(x2.toDouble()).toInt().coerceIn(0,image.width),
                            ceil(y2.toDouble()).toInt().coerceIn(0,image.height))
                        if(!rect.isEmpty)regions.add(Region(type,LABELS[type],score,rect))
                    }
                    return regions.sortedWith(compareBy({it.bounds.top},{it.bounds.left}))
                }
            }
        }
    }
    private fun readDetectionCount(tensor: OnnxTensor): Int {
        return when(val v=tensor.value){
            is IntArray -> v.firstOrNull() ?: 0
            is LongArray -> v.firstOrNull()?.toInt() ?: 0
            is Int -> v
            is Long -> v.toInt()
            is Array<*> -> when(val row=v.firstOrNull()){
                is IntArray -> row.firstOrNull() ?: 0
                is LongArray -> row.firstOrNull()?.toInt() ?: 0
                is Number -> row.toInt()
                else -> error("Unexpected num_dets tensor")
            }
            else -> error("Unexpected num_dets dtype")
        }
    }
    override fun close() { session.close() }
}
