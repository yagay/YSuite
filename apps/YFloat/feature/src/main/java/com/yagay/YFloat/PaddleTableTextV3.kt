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
                     val tabSeparated:String,val score:Float)

    fun link(prediction:SLANetLocalV3.Result,lines:List<PaddlePageV2.Line>):Table{
        val cells=prediction.cells.map{cell->
            val bounds=cell.bounds
            val cellLines=ArrayList<String>()
            for(line in lines) {
                val lineBounds=line.bounds.rect()
                if(lineBounds.isEmpty)continue
                val overlap=Rect()
                if(!overlap.setIntersect(lineBounds,bounds))continue
                val area=lineBounds.width().toLong()*lineBounds.height()
                val covered=overlap.width().toLong()*overlap.height()
                // Preserve the corrected text verbatim (including meaningful
                // spaces) when a single model cell contains the whole line.
                if(area>0 && covered*100L>=area*80L) {
                    line.text.trim().takeIf{it.isNotEmpty()}?.let(cellLines::add)
                    continue
                }
                val fragments=ArrayList<String>()
                for(word in line.words) {
                    val r=word.bounds.rect()
                    if(r.isEmpty||!bounds.contains(r.centerX(),r.centerY()))continue
                    if(word.text.isNotBlank())fragments.add(word.text)
                }
                if(fragments.isNotEmpty()) {
                    // Partial-line extraction has no trustworthy blank-glyph
                    // offsets. Never fabricate English spacing in geometry.
                    cellLines.add(fragments.joinToString(""))
                } else if(covered*2L>=area) {
                    // CTC may supply a line without glyph or word boxes.
                    line.text.trim().takeIf{it.isNotEmpty()}?.let(cellLines::add)
                }
            }
            // Separate distinct recognized lines instead of joining the end
            // of one row to the beginning of the next word.
            TextCell(Rect(bounds),cellLines.joinToString("\n"),cell.score)
        }
        val html=prediction.html(cells.map{it.text})
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
                tsv.append(cells.getOrNull(cellIndex++)?.text.orEmpty())
                rowStart=false
            }else if(token=="</tr>"){
                if(tsv.isNotEmpty() && tsv.last()!='\n')tsv.append('\n')
            }
        }
        return Table(cells,html,tsv.toString().trim(),prediction.score)
    }
}
