package com.yagay.YFloat

import android.graphics.PointF
import android.graphics.Rect
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRCharacter
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.structure.PPDocLayoutV2
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Independent, provenance-aware PP-OCRv6 + offline PP-DocLayout-S result model.
 * No ML Kit and no misleading legacy "blocks == lines" conversion.
 */
object PaddlePageV2 {
    enum class Origin { DETECTOR, CTC_ALIGNED, WORD_GROUP, LAYOUT_MODEL, FALLBACK }
    data class Quad(val points: List<PointF>,val origin: Origin){
        fun rect(): Rect {
            if(points.isEmpty())return Rect()
            return Rect(floor(points.minOf {it.x}).toInt(),
                floor(points.minOf {it.y}).toInt(),
                ceil(points.maxOf {it.x}.toDouble()).toInt(),
                ceil(points.maxOf {it.y}.toDouble()).toInt())
        }
    }
    data class Symbol(val text:String,val bounds:Quad,val confidence:Float)
    data class Word(val text:String,val bounds:Quad,val symbols:List<Symbol>)
    data class Line(val text:String,val bounds:Quad,val confidence:Float,
                    val words:List<Word>,val rawText:String=text)
    data class Block(val label:String,val bounds:Quad,val origin:Origin,
                     val lines:List<Line>,val confidence:Float)
    data class Page(val width:Int,val height:Int,val blocks:List<Block>){
        val lines:List<Line> get()=blocks.flatMap {it.lines}
        fun toDocument(engine:String): OcrDocument {
            var index=0
            var group=0
            val linesOut=ArrayList<OcrDocument.Line>()
            for(line in lines){
                val chars=ArrayList<OcrDocument.CharUnit>()
                for(word in line.words){
                    val wordGroup=group++
                    val before=chars.size
                    for(symbol in word.symbols){
                        val box=symbol.bounds.rect()
                        if(!box.isEmpty && symbol.text.isNotBlank())chars.add(OcrDocument.CharUnit(
                            symbol.text,box,symbol.confidence,linesOut.size,wordGroup,index++,
                            symbol.bounds.points))
                    }
                    // If recognizer glyph alignment is absent, preserve only
                    // a real word-derived quad. Do not synthesize character boxes.
                    if(chars.size==before && word.text.isNotBlank()){
                        val realWord=word.bounds.rect()
                        if(!realWord.isEmpty)chars.add(OcrDocument.CharUnit(
                            word.text,realWord,line.confidence,linesOut.size,wordGroup,index++,
                            word.bounds.points))
                    }
                }
                if(chars.isEmpty()&&line.text.isNotBlank()){
                    val detectorBox=line.bounds.rect()
                    if(!detectorBox.isEmpty)chars.add(OcrDocument.CharUnit(
                        line.text,detectorBox,line.confidence,linesOut.size,group++,index++,
                        line.bounds.points))
                }
                linesOut.add(OcrDocument.Line(line.text,line.bounds.rect(),line.confidence,chars,
                    line.bounds.points))
            }
            val text=linesOut.joinToString("\n") {it.text()}
            val summary=blocks.mapNotNull { block ->
                block.lines.joinToString("\n"){it.text}.takeIf {it.isNotBlank()}
            }
            val confidence=lines.map {it.confidence}.average().let {if(it.isNaN())0f else it.toFloat()}
            // Persist the quality score in the immutable document. Otherwise
            // Auto Small -> Medium comparison silently sees 0 vs 0 and always
            // accepts the later result, even when recognition is worse.
            val quality=confidence*1000.0+
                minOf(300,text.codePointCount(0,text.length))+
                minOf(12,blocks.size)*5.0
            val typedBlocks=blocks.map { block ->
                OcrStructure.Block(block.label,
                    OcrStructure.Quad(block.bounds.points,block.bounds.origin.name),
                    block.confidence,
                    block.lines.map { line ->
                        OcrStructure.Line(line.text,
                            OcrStructure.Quad(line.bounds.points,line.bounds.origin.name),
                            line.confidence,
                            line.words.map { word ->
                                OcrStructure.Word(word.text,
                                    OcrStructure.Quad(word.bounds.points,word.bounds.origin.name),
                                    word.symbols.map { symbol ->
                                        OcrStructure.Symbol(symbol.text,
                                            OcrStructure.Quad(symbol.bounds.points,
                                                symbol.bounds.origin.name),
                                            symbol.confidence)
                                    })
                            })
                    })
            }
            return OcrDocument(text,summary,linesOut,engine,confidence,quality,width,height)
                .withStructure(OcrStructure(typedBlocks))
        }
    }

    fun fromOcr(results:List<OCRResult>,width:Int,height:Int,
                regions:List<PPDocLayoutV2.Region>):Page {
        fun withinImage(q:Quad):Quad=Quad(q.points.map{point->
            PointF(point.x.coerceIn(0f,width.toFloat()),
                point.y.coerceIn(0f,height.toFloat()))
        },q.origin)
        val lines=results.mapNotNull { result ->
            // Preserve the original CTC string and SDK glyph/word geometry.
            // No spacing repair, resplitting, invented character boxes or
            // application-specific script normalization.
            val text=result.text
            if(text.isBlank())return@mapNotNull null
            val lineQuad=withinImage(quad(result.box,Origin.DETECTOR))
            val words=if(result.words.isNotEmpty())result.words.map { word ->
                Word(word.text,withinImage(quad(word.box,Origin.WORD_GROUP)),
                    word.characters.map { symbol ->
                        Symbol(symbol.text,withinImage(quad(symbol.box,Origin.CTC_ALIGNED)),
                            symbol.confidence)
                    })
            } else listOf(Word(text,Quad(lineQuad.points,Origin.DETECTOR),
                result.characters.map { symbol ->
                    Symbol(symbol.text,withinImage(quad(symbol.box,Origin.CTC_ALIGNED)),
                        symbol.confidence)
                }))
            Line(text,lineQuad,result.confidence,words,result.text)
                .takeIf{!lineQuad.rect().isEmpty}
        } // Preserve SDK result order; layout export can apply its own reading order.
        val grouped=mutableMapOf<Int,MutableList<Line>>()
        for(line in lines){
            val rect=line.bounds.rect()
            var best=-1
            var bestCover=0f
            for(i in regions.indices){
                val layout=regions[i].bounds
                val overlap=Rect()
                if(!overlap.setIntersect(layout,rect))continue
                val cover=overlap.width().toFloat()*overlap.height()/
                    (rect.width()*rect.height()).coerceAtLeast(1)
                if(cover>bestCover){bestCover=cover;best=i}
            }
            if(bestCover<.45f)best=-1
            grouped.getOrPut(best){mutableListOf()}.add(line)
        }
        val blocks=ArrayList<Block>()
        regions.forEachIndexed {index,region ->
            val included=grouped.remove(index).orEmpty()
            blocks.add(Block(region.label,
                quadFromRect(region.bounds,Origin.LAYOUT_MODEL),
                Origin.LAYOUT_MODEL,included,region.score))
        }
        // Lines without a layout-model region can still form adjacent paragraphs.
        // Mark them FALLBACK, not as a model-predicted document block.
        val fallback=grouped.remove(-1).orEmpty()
        val paragraphs=ArrayList<MutableList<Line>>()
        for(line in fallback){
            val prev=paragraphs.lastOrNull()?.lastOrNull()
            if(prev!=null && sameParagraph(prev,line))paragraphs.last().add(line)
            else paragraphs.add(mutableListOf(line))
        }
        for(paragraph in paragraphs) {
            val bounds=Rect(paragraph.first().bounds.rect())
            paragraph.drop(1).forEach { bounds.union(it.bounds.rect()) }
            blocks.add(Block("unclassified",quadFromRect(bounds,Origin.FALLBACK),
                Origin.FALLBACK,paragraph,
                paragraph.map{it.confidence}.average().toFloat()))
        }
        return Page(width,height,PaddleReadingOrderV3.order(blocks,width))
    }

    private fun sameParagraph(a:Line,b:Line):Boolean {
        val above=a.bounds.rect()
        val below=b.bounds.rect()
        val height=maxOf(above.height(),below.height()).coerceAtLeast(1)
        val gap=below.top-above.bottom
        if(gap< -height/3 || gap>height*1.5f)return false
        if(kotlin.math.abs(above.left-below.left)>height*1.25f)return false
        val overlap=maxOf(0,minOf(above.right,below.right)-maxOf(above.left,below.left))
        return overlap>minOf(above.width(),below.width())*0.35f
    }

    fun quad(box:OCRBox,origin:Origin):Quad =
        Quad(box.points.map{PointF(it.x,it.y)},origin)
    fun quadFromRect(r:Rect,origin:Origin):Quad =
        Quad(listOf(PointF(r.left.toFloat(),r.top.toFloat()),
            PointF(r.right.toFloat(),r.top.toFloat()),
            PointF(r.right.toFloat(),r.bottom.toFloat()),
            PointF(r.left.toFloat(),r.bottom.toFloat())),origin)
}
