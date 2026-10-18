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
            val hits=ArrayList<Pair<String,Rect>>()
            for(line in lines){
                for(word in line.words){
                    for(symbol in word.symbols){
                        if(symbol.text.isBlank())continue
                        val rect=symbol.bounds.rect()
                        if(rect.isEmpty)continue
                        val cx=rect.centerX()
                        val cy=rect.centerY()
                        if(bounds.contains(cx,cy))hits.add(symbol.text to rect)
                    }
                }
            }
            // Preserve original recognizer symbol order. Sorting individual
            // glyphs by Y causes baseline jitter to scramble horizontal text.
            val text=if(hits.isNotEmpty())hits.joinToString(""){it.first}
                else lines.filter{line->
                    val r=line.bounds.rect()
                    val overlap=Rect()
                    overlap.setIntersect(r,bounds)&&
                        overlap.width().toLong()*overlap.height()>=
                        r.width().toLong()*r.height()/2
                }.joinToString(" "){it.text}
            TextCell(Rect(bounds),text,cell.score)
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
