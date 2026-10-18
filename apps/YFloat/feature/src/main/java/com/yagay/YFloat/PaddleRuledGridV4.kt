package com.yagay.YFloat

import android.graphics.Bitmap
import android.graphics.Rect
import com.paddle.ocr.structure.SLANetLocalV3
import kotlin.math.roundToInt

/**
 * Offline pixel-grid detector for strictly ruled tables. This is NOT the
 * PP-TableMagic learned classifier or cell detector. Use it only when strong
 * horizontal AND vertical rules form a complete rectangular grid.
 *
 * Model output is always preferred. This conservative fallback can recover
 * table cells when no SLANet cells are available and the page has printed
 * table borders. It never infers merged cells from missing separators.
 */
object PaddleRuledGridV4 {
    data class Grid(val rows:Int,val columns:Int,
                    val prediction:SLANetLocalV3.Result,val source:String="pixel_ruled_grid")

    fun detect(bitmap:Bitmap):Grid? {
        if(bitmap.isRecycled || bitmap.width<48 || bitmap.height<48)return null
        val scale=minOf(1.0,1200.0/maxOf(bitmap.width,bitmap.height))
        val w=maxOf(1,(bitmap.width*scale).roundToInt())
        val h=maxOf(1,(bitmap.height*scale).roundToInt())
        val preview=if(w==bitmap.width&&h==bitmap.height)bitmap
            else Bitmap.createScaledBitmap(bitmap,w,h,true)
        val pixels=IntArray(w*h)
        try{preview.getPixels(pixels,0,w,0,0,w,h)}
        finally{if(preview!==bitmap)preview.recycle()}
        fun dark(x:Int,y:Int):Boolean {
            val c=pixels[y*w+x]
            val r=(c ushr 16) and 255
            val g=(c ushr 8) and 255
            val b=c and 255
            return (r*30+g*59+b*11)<12500
        }
        // A thin ruled line has near-continuous ink across the candidate
        // region; ordinary paragraph glyphs do not satisfy both axes.
        val rowHits=BooleanArray(h)
        for(y in 0 until h){
            var total=0
            for(x in 0 until w){if(dark(x,y))total++}
            rowHits[y]=total>=w*.76
        }
        val colHits=BooleanArray(w)
        for(x in 0 until w){
            var total=0
            for(y in 0 until h){if(dark(x,y))total++}
            colHits[x]=total>=h*.76
        }
        fun centers(hits:BooleanArray):List<Int>{
            val positions=ArrayList<Int>()
            var start=-1
            for(i in 0..hits.size) {
                val on=i<hits.size && hits[i]
                if(on && start<0)start=i
                if(!on && start>=0){
                    val end=i-1
                    if(end-start<=maxOf(8,hits.size/80))positions.add((start+end)/2)
                    start=-1
                }
            }
            return positions.filterIndexed {i,p->
                i==0||p-positions[i-1]>=6
            }
        }
        val xs=centers(colHits)
        val ys=centers(rowHits)
        if(xs.size !in 3..61 || ys.size !in 3..61)return null
        val xSpan=xs.last()-xs.first()
        val ySpan=ys.last()-ys.first()
        if(xSpan<w*.40||ySpan<h*.25)return null
        // Every proposed intersection must correspond to an actual border
        // pixel. Reject broken/unruled/merged grids rather than fake spans.
        var good=0
        var all=0
        for(y in ys)for(x in xs){
            all++
            var matched=false
            for(dy in -2..2)for(dx in -2..2){
                val px=x+dx;val py=y+dy
                if(px in 0 until w && py in 0 until h && dark(px,py))matched=true
            }
            if(matched)good++
        }
        if(good<all*.92f)return null
        val xscale=bitmap.width.toFloat()/w
        val yscale=bitmap.height.toFloat()/h
        val cells=ArrayList<SLANetLocalV3.Cell>()
        val tokens=ArrayList<String>()
        tokens.add("<table>")
        for(row in 0 until ys.size-1){
            tokens.add("<tr>")
            for(col in 0 until xs.size-1){
                val left=(xs[col]*xscale).roundToInt()
                val right=(xs[col+1]*xscale).roundToInt()
                val top=(ys[row]*yscale).roundToInt()
                val bottom=(ys[row+1]*yscale).roundToInt()
                if(right-left<5 || bottom-top<5)return null
                cells.add(SLANetLocalV3.Cell(Rect(left,top,right,bottom),good.toFloat()/all))
                tokens.add("<td></td>")
            }
            tokens.add("</tr>")
        }
        tokens.add("</table>")
        if(cells.isEmpty()||cells.size>600)return null
        return Grid(ys.size-1,xs.size-1,
            SLANetLocalV3.Result(tokens,cells,good.toFloat()/all))
    }
}
