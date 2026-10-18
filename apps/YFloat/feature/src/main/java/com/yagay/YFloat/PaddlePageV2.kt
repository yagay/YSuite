package com.yagay.YFloat

import android.graphics.PointF
import android.graphics.Rect
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.model.OCRCharacter
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.model.OCRWordSegmenter
import com.paddle.ocr.postprocess.PaddleSpacingV2
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
        fun asLegacy(engine:String): OcrDocument {
            var index=0
            var group=0
            val linesOut=ArrayList<OcrDocument.Line>()
            for(line in lines){
                val chars=ArrayList<OcrDocument.CharUnit>()
                for(word in line.words){
                    val wordGroup=group++
                    for(symbol in word.symbols){
                        val box=symbol.bounds.rect()
                        if(!box.isEmpty)chars.add(OcrDocument.CharUnit(
                            symbol.text,box,symbol.confidence,linesOut.size,wordGroup,index++))
                    }
                }
                // A recognizer can return text even if CTC glyph geometry
                // is unavailable. Preserve readable text, never fake a glyph box.
                linesOut.add(OcrDocument.Line(line.text,line.bounds.rect(),line.confidence,chars))
            }
            val text=linesOut.joinToString("\n") {it.text()}
            val summary=blocks.mapNotNull { block ->
                block.lines.joinToString("\n"){it.text}.takeIf {it.isNotBlank()}
            }
            val confidence=lines.map {it.confidence}.average().let {if(it.isNaN())0f else it.toFloat()}
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
            return OcrDocument(text,summary,linesOut,engine,confidence,0.0,width,height)
                .withStructure(OcrStructure(typedBlocks))
        }
    }

    fun fromOcr(results:List<OCRResult>,width:Int,height:Int,
                regions:List<PPDocLayoutV2.Region>,fixSpacing:Boolean=true):Page {
        val lines=results.mapNotNull { result ->
            val refined=if(fixSpacing)
                PaddleSpacingV2.correct(result.characters,result.text)
                else PaddleSpacingV2.Correction(result.text,result.characters,0)
            val text=refined.text.trim()
            if(text.isBlank())return@mapNotNull null
            val quad=quad(result.box,Origin.DETECTOR)
            val symbols=refined.characters.map { c ->
                Symbol(c.text,quad(c.box,Origin.CTC_ALIGNED),c.confidence)
            }
            // Re-segment after removing spurious spaces. Never use stale word
            // boundaries whose characters no longer match the corrected text.
            val segments=if(fixSpacing)
                OCRWordSegmenter.segment(refined.characters) else result.words
            val words=if(segments.isNotEmpty())segments.map { word ->
                Word(word.text,quad(word.box,Origin.WORD_GROUP),
                    word.characters.map { symbol ->
                        Symbol(symbol.text,quad(symbol.box,Origin.CTC_ALIGNED),
                            symbol.confidence)
                    })
            } else groupWords(symbols,text,quad)
            Line(text,quad,result.confidence,words,result.text)
        }.sortedWith(compareBy({it.bounds.rect().centerY()},{it.bounds.rect().left}))
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

    fun groupWords(symbols:List<Symbol>,recognized:String,line:Quad):List<Word>{
        if(symbols.isEmpty()) return emptyList()
        val words=ArrayList<Word>()
        val current=ArrayList<Symbol>()
        fun flush(){
            if(current.isEmpty())return
            val box=Rect()
            for(s in current){if(box.isEmpty)box.set(s.bounds.rect()) else box.union(s.bounds.rect())}
            val first=current.first().bounds.points
            val last=current.last().bounds.points
            val grouped=if(first.size==4&&last.size==4)
                Quad(listOf(PointF(first[0].x,first[0].y),
                    PointF(last[1].x,last[1].y),
                    PointF(last[2].x,last[2].y),
                    PointF(first[3].x,first[3].y)),Origin.WORD_GROUP)
                else quadFromRect(box,Origin.WORD_GROUP)
            words.add(Word(current.joinToString(""){it.text},
                grouped,current.toList()))
            current.clear()
        }
        for(symbol in symbols){
            if(symbol.text.isBlank()){flush();continue}
            // CJK characters remain separately selectable; Latin/digits group
            // until a Unicode space or script boundary is reached.
            val cp=symbol.text.codePointAt(0)
            val ideograph=Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN ||
                Character.UnicodeScript.of(cp)==Character.UnicodeScript.HIRAGANA ||
                Character.UnicodeScript.of(cp)==Character.UnicodeScript.KATAKANA
            if(ideograph){flush();current.add(symbol);flush()}
            else current.add(symbol)
        }
        flush()
        return words
    }
    fun quad(box:OCRBox,origin:Origin):Quad =
        Quad(box.points.map{PointF(it.x,it.y)},origin)
    fun quadFromRect(r:Rect,origin:Origin):Quad =
        Quad(listOf(PointF(r.left.toFloat(),r.top.toFloat()),
            PointF(r.right.toFloat(),r.top.toFloat()),
            PointF(r.right.toFloat(),r.bottom.toFloat()),
            PointF(r.left.toFloat(),r.bottom.toFloat())),origin)
}
