package com.yagay.YFloat

import android.graphics.Rect

/**
 * Local chart-region OCR evidence extracted from PP-DocLayout-S + PP-OCRv6.
 *
 * This is NOT PP-Chart2Table: it faithfully returns visible OCR labels and a
 * nearby chart title, never guesses data-series numbers or axis calibration.
 * Labels retain original bitmap-space bounds so selection stays correct.
 */
object PaddleChartTextV3 {
    data class Label(val text:String,val bounds:Rect,val confidence:Float)
    data class Chart(val bounds:Rect,val title:String,val labels:List<Label>) {
        val searchableText:String
            get()=buildList {
                if(title.isNotBlank())add(title)
                labels.map {it.text}.filter {it.isNotBlank()}.forEach { add(it) }
            }.joinToString("\n")
    }
    @JvmStatic fun extract(page:PaddlePageV2.Page):List<Chart> {
        val regions=page.blocks.filter{it.label=="chart" &&
            it.origin==PaddlePageV2.Origin.LAYOUT_MODEL}
        if(regions.isEmpty())return emptyList()
        val titles=page.blocks.filter{it.label=="chart_title"}
        return regions.map{block->
            val rect=block.bounds.rect()
            val width=rect.width().coerceAtLeast(1)
            val height=rect.height().coerceAtLeast(1)
            val nearest=titles.filter{title->
                val r=title.bounds.rect()
                val dx=kotlin.math.abs(r.centerX()-rect.centerX())
                val gap=rect.top-r.bottom
                dx<=width*0.65f && gap>=-height*0.05f && gap<=height*0.3f
            }.minByOrNull {title->
                kotlin.math.abs(title.bounds.rect().bottom-rect.top)
            }
            val title=nearest?.lines?.joinToString(" "){it.text}.orEmpty()
            val labels=block.lines.map {line->
                Label(line.text,Rect(line.bounds.rect()),line.confidence)
            }.filter{it.text.isNotBlank()}
            Chart(Rect(rect),title,labels)
        }
    }
}
