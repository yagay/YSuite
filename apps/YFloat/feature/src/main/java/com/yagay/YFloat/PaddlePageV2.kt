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
 * Independent, provenance-aware PP-OCRv6 + PP-StructureV3 result model.
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
                    val words:List<Word>)
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
                if(chars.isNotEmpty())
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
                regions:List<PPDocLayoutV2.Region>):Page {
        val lines=results.mapNotNull { result ->
            val text=result.text.trim()
            if(text.isBlank())return@mapNotNull null
            val quad=quad(result.box,Origin.DETECTOR)
            val symbols=result.characters.map { c ->
                Symbol(c.text,quad(c.box,Origin.CTC_ALIGNED),c.confidence)
            }
            val words=if(result.words.isNotEmpty())result.words.map { word ->
                Word(word.text,quad(word.box,Origin.WORD_GROUP),
                    word.characters.map { symbol ->
                        Symbol(symbol.text,quad(symbol.box,Origin.CTC_ALIGNED),
                            symbol.confidence)
                    })
            } else groupWords(symbols,text,quad)
            Line(text,quad,result.confidence,words)
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
        // Without a layout model no actual document-layout block exists.
        // Keep visual lines in a distinctly marked fallback group.
        for(line in grouped.remove(-1).orEmpty())
            blocks.add(Block("unclassified",line.bounds,Origin.FALLBACK,
                listOf(line),line.confidence))
        return Page(width,height,blocks.sortedWith(compareBy({it.bounds.rect().top},
            {it.bounds.rect().left})))
    }

    fun groupWords(symbols:List<Symbol>,recognized:String,line:Quad):List<Word>{
        if(symbols.isEmpty()){
            // Legacy compatibility only. No fabricated symbol geometry.
            return listOf(Word(recognized,line.copy(origin=Origin.FALLBACK),
                listOf(Symbol(recognized,line.copy(origin=Origin.FALLBACK),0f))))
        }
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
