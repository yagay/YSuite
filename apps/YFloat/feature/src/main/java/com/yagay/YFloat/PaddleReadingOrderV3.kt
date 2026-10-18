package com.yagay.YFloat

import android.graphics.Rect

/**
 * Conservative local reading-order heuristic. Preserves original PP-DocLayout
 * block labels and quads; explicitly does NOT claim learned reading order.
 * Full-width headings divide sections; within each section, detect two
 * spatially disjoint text columns and read each top-to-bottom.
 */
object PaddleReadingOrderV3 {
    fun order(input:List<PaddlePageV2.Block>,width:Int):List<PaddlePageV2.Block> {
        if(input.size<3||width<=0)return input.sortedWith(byPosition())
        val wide=input.filter {it.bounds.rect().width()>=width*.73f}
            .sortedWith(byPosition())
        val narrow=input.filter {it !in wide}
        if(narrow.isEmpty())return wide
        val output=ArrayList<PaddlePageV2.Block>()
        var previousBottom=Int.MIN_VALUE
        for(heading in wide) {
            val cutoff=heading.bounds.rect().top
            val section=narrow.filter {
                val r=it.bounds.rect()
                r.top>=previousBottom && r.top<cutoff
            }
            output.addAll(orderWithinSection(section,width))
            output.add(heading)
            previousBottom=heading.bounds.rect().bottom
        }
        output.addAll(orderWithinSection(narrow.filter {
            it.bounds.rect().top>=previousBottom
        },width))
        // A full-width page element can overlap smaller blocks; retain all
        // blocks exactly once even when the conservative separator drops one.
        for(block in input)if(block !in output)output.add(block)
        return output
    }

    private fun orderWithinSection(blocks:List<PaddlePageV2.Block>,
                                   width:Int):List<PaddlePageV2.Block> {
        if(blocks.size<4)return blocks.sortedWith(byPosition())
        val mid=width/2f
        val left=blocks.filter{it.bounds.rect().centerX()<mid}
        val right=blocks.filter{it.bounds.rect().centerX()>=mid}
        if(left.size<2||right.size<2)return blocks.sortedWith(byPosition())
        val leftRight=left.maxOf{it.bounds.rect().right}
        val rightLeft=right.minOf{it.bounds.rect().left}
        if(rightLeft-leftRight<width*.035f)return blocks.sortedWith(byPosition())
        val leftTop=left.minOf{it.bounds.rect().top}
        val leftBottom=left.maxOf{it.bounds.rect().bottom}
        val rightTop=right.minOf{it.bounds.rect().top}
        val rightBottom=right.maxOf{it.bounds.rect().bottom}
        if(minOf(leftBottom,rightBottom)-maxOf(leftTop,rightTop)<=0)
            return blocks.sortedWith(byPosition())
        return left.sortedWith(byPosition())+right.sortedWith(byPosition())
    }
    private fun byPosition():Comparator<PaddlePageV2.Block> =
        compareBy({it.bounds.rect().top},{it.bounds.rect().left})
}
