package com.yagay.YFloat

import android.graphics.Rect

/**
 * Conservative, deterministic local reading order.
 * Uses visual separators and validated non-overlapping column intervals.
 * It preserves actual layout-model blocks without claiming learned semantics.
 */
object PaddleReadingOrderV3 {
    private val POSITION=compareBy<PaddlePageV2.Block>(
        {it.bounds.rect().top},{it.bounds.rect().left})

    fun order(input:List<PaddlePageV2.Block>,width:Int):List<PaddlePageV2.Block> {
        if(input.size<3 || width<=0)return input.sortedWith(POSITION)
        val sorted=input.sortedWith(POSITION)
        val separators=sorted.filter {
            val box=it.bounds.rect()
            box.width()>=width*.78f
        }
        if(separators.isEmpty())return columns(sorted,width)
        val output=ArrayList<PaddlePageV2.Block>(input.size)
        val used=HashSet<Int>()
        for(sep in separators) {
            val cutoff=sep.bounds.rect().top
            val before=sorted.filter{
                val id=input.indexOfFirst{source->source===it}
                id !in used && it !== sep && it.bounds.rect().centerY()<cutoff
            }
            output.addAll(columns(before,width))
            for(block in before){
                used.add(input.indexOfFirst{source->source===block})
            }
            val id=input.indexOfFirst{source->source===sep}
            if(id !in used){output.add(sep);used.add(id)}
        }
        val remaining=sorted.filter{
            val id=input.indexOfFirst{source->source===it}
            id !in used
        }
        output.addAll(columns(remaining,width))
        // The separator heuristic must never delete or duplicate a region.
        return if(output.size==input.size)output else sorted
    }

    private fun columns(blocks:List<PaddlePageV2.Block>,width:Int):
            List<PaddlePageV2.Block> {
        if(blocks.size<4)return blocks.sortedWith(POSITION)
        // Find the strongest actual whitespace gutter. Do not sort into
        // "columns" unless both sides contain multiple elements.
        val options=(1..9).mapNotNull{step->
            val boundary=width*step/10
            val left=blocks.filter{it.bounds.rect().right<=boundary}
            val right=blocks.filter{it.bounds.rect().left>=boundary}
            if(left.size<2||right.size<2) return@mapNotNull null
            if(left.size+right.size!=blocks.size)return@mapNotNull null
            val gutter=right.minOf{it.bounds.rect().left}-
                left.maxOf{it.bounds.rect().right}
            if(gutter<width*.045f) return@mapNotNull null
            boundary to gutter
        }
        val split=options.maxByOrNull{it.second}?.first
            ?:return blocks.sortedWith(POSITION)
        val left=blocks.filter{it.bounds.rect().right<=split}
        val right=blocks.filter{it.bounds.rect().left>=split}
        if(left.size<2||right.size<2||left.size+right.size!=blocks.size)
            return blocks.sortedWith(POSITION)
        val verticalOverlap=minOf(left.maxOf{it.bounds.rect().bottom},
                right.maxOf{it.bounds.rect().bottom})-
            maxOf(left.minOf{it.bounds.rect().top},
                  right.minOf{it.bounds.rect().top})
        if(verticalOverlap<=0)return blocks.sortedWith(POSITION)
        // Recursion handles 3+ columns without requiring a hard-coded page
        // midpoint; blocks inside each column keep top-to-bottom order.
        return columns(left,width)+columns(right,width)
    }
}
