package com.yagay.YFloat

import android.graphics.Rect
import com.paddle.ocr.structure.SLANetLocalV3

/**
 * Links SLANet-predicted cell geometry to PP-OCR aligned character boxes.
 *
 * Unlike geometric row heuristics, every cell originates in SLANet. OCR is
 * assigned spatially and never invents HTML rows, spans, or table coordinates.
 */
object PaddleTableTextV3 {
    data class TextCell(val bounds:Rect,val text:String,val confidence:Float)
    data class Table(val cells:List<TextCell>,val html:String,
                     val tabSeparated:String,val score:Float,val source:String="slanet_model")

    fun link(prediction:SLANetLocalV3.Result,lines:List<PaddlePageV2.Line>,
             source:String="slanet_model"):Table {
        val buckets=Array(prediction.cells.size){ArrayList<String>()}
        val cells=prediction.cells
        // Each OCR line/word has exactly one winning destination. The old
        // independent per-cell intersection loop duplicated words crossing
        // adjacent cell borders and lost line-wise ordering.
        fun winningCell(bounds:Rect,minCoverage:Double):Int{
            if(bounds.isEmpty)return -1
            val area=bounds.width().toLong()*bounds.height()
            if(area<=0L)return -1
            var best=-1
            var maxOverlap=0L
            for(i in cells.indices){
                val clipped=Rect()
                if(!clipped.setIntersect(bounds,cells[i].bounds))continue
                val overlap=clipped.width().toLong()*clipped.height()
                if(overlap>maxOverlap){maxOverlap=overlap;best=i}
            }
            return if(maxOverlap>=area*minCoverage)best else -1
        }
        val ordered=lines.sortedWith(compareBy({it.bounds.rect().top},{it.bounds.rect().left}))
        for(line in ordered){
            if(line.text.isBlank())continue
            val whole=winningCell(line.bounds.rect(),.80)
            if(whole>=0){
                buckets[whole].add(line.text.trim())
                continue
            }
            val portions=linkedMapOf<Int,StringBuilder>()
            for(word in line.words){
                if(word.text.isBlank())continue
                val area=word.bounds.rect()
                var target=winningCell(area,.42)
                if(target<0 && !area.isEmpty)
                    target=cells.indexOfFirst{it.bounds.contains(area.centerX(),area.centerY())}
                if(target>=0){
                    portions.getOrPut(target){StringBuilder()}.append(word.text)
                }
            }
            if(portions.isEmpty()){
                val index=winningCell(line.bounds.rect(),.50)
                if(index>=0)buckets[index].add(line.text.trim())
            }else for((index,fragment) in portions){
                if(fragment.isNotEmpty())buckets[index].add(fragment.toString())
            }
        }
        val textCells=cells.mapIndexed {i,cell->
            TextCell(Rect(cell.bounds),buckets[i].joinToString("\n"),cell.score)
        }
        val html=prediction.html(textCells.map{it.text})
        // TSV is deliberately based on the model's HTML row tokens, not
        // on guessed x-coordinates (spanning cells are preserved in HTML).
        val tsv=StringBuilder()
        var cellIndex=0
        var rowStart=true
        for(token in prediction.tokens){
            if(token=="<tr>"){
                if(tsv.isNotEmpty() && tsv.last()!='\n')tsv.append('\n')
                rowStart=true
            }else if(token=="<td></td>" || token=="<td" || token=="<td>"){
                if(!rowStart)tsv.append('\t')
                tsv.append(textCells.getOrNull(cellIndex++)?.text.orEmpty())
                rowStart=false
            }else if(token=="</tr>"){
                if(tsv.isNotEmpty() && tsv.last()!='\n')tsv.append('\n')
            }
        }
        return Table(textCells,html,tsv.toString().trim(),prediction.score,source)
    }
}
