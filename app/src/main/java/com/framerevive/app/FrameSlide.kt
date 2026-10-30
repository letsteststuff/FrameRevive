package com.framerevive.app

/** History stores catalog references, never decoded images. */
data class FrameSlide(val photos: List<FramePhoto>) {
    init { require(photos.size in 1..4); require(photos.map { it.id }.distinct().size == photos.size) }
    val collage: Boolean get() = photos.size > 1
    val id: String get() = photos.joinToString("") { "${it.id.length}:${it.id}" }
}

class CollageCadence {
    @Volatile var singles = 0
        private set
    fun due(every: Int): Boolean { require(every in 5..100); return singles >= every }
    fun displayed(collage: Boolean) { singles = if(collage) 0 else (singles+1).coerceAtMost(100) }
    private var collageNumber=0
    val nextCount get()=2+collageNumber%3
    fun collageShown() { collageNumber=(collageNumber+1)%3 }
    fun reset() { singles=0;collageNumber=0 }
    companion object {
        fun candidates(upcoming: List<FramePhoto>, previous: List<FramePhoto>, count: Int=4, ready: (FramePhoto)->Boolean): List<FramePhoto> {
            require(count in 2..4)
            val avoid=previous.map { it.id }.toSet()
            val selected=upcoming.filterNot { it.id in avoid }.distinctBy { it.id }.take(count)
            return if(selected.size == count && selected.all(ready)) selected else emptyList()
        }
    }
}

/** Pixel geometry kept independent of Android for coverage and fit tests. */
object CollageLayout {
    data class Box(val left: Float,val top: Float,val right: Float,val bottom: Float) {
        val width get()=right-left
        val height get()=bottom-top
    }
    fun cells(width: Int,height: Int,gap: Int=8): List<Box> {
        require(width > gap*3 && height > gap*3 && gap >= 0)
        val w=(width-gap*3)/2f; val h=(height-gap*3)/2f
        return (0..3).map { i -> val x=gap+(i%2)*(w+gap);val y=gap+(i/2)*(h+gap);Box(x,y,x+w,y+h) }
    }
    data class Size(val width:Int,val height:Int) { init { require(width>0 && height>0) } }
    data class Panel(val photo:Int,val box:Box)
    /** Pick a template and ordering that maximize visible photo area without cropping. */
    fun arrange(width:Int,height:Int,sizes:List<Size>,gap:Int=8):List<Panel> {
        require(sizes.size in 2..4 && gap>=0 && width>gap*5 && height>gap*5)
        fun stripes(columns:Int,rows:Int):List<Box> {
            val w=(width-gap*(columns+1)).toFloat()/columns;val h=(height-gap*(rows+1)).toFloat()/rows
            return (0 until columns*rows).map { i -> val x=gap+(i%columns)*(w+gap);val y=gap+(i/columns)*(h+gap);Box(x,y,x+w,y+h) }
        }
        val templates=ArrayList<List<Box>>()
        templates+=stripes(sizes.size,1);templates+=stripes(1,sizes.size)
        if(sizes.size==4) templates+=cells(width,height,gap)
        if(sizes.size==3) {
            val x=(width-gap)/2f;val y=(height-gap)/2f
            val right=Box(x+gap,gap.toFloat(),width-gap.toFloat(),height-gap.toFloat())
            val left=Box(gap.toFloat(),gap.toFloat(),x,height-gap.toFloat())
            val top=Box(gap.toFloat(),gap.toFloat(),width-gap.toFloat(),y)
            val bottom=Box(gap.toFloat(),y+gap,width-gap.toFloat(),height-gap.toFloat())
            templates+=listOf(left,Box(right.left,right.top,right.right,y),Box(right.left,y+gap,right.right,right.bottom))
            templates+=listOf(right,Box(left.left,left.top,left.right,y),Box(left.left,y+gap,left.right,left.bottom))
            templates+=listOf(top,Box(bottom.left,bottom.top,x,bottom.bottom),Box(x+gap,bottom.top,bottom.right,bottom.bottom))
            templates+=listOf(bottom,Box(top.left,top.top,x,top.bottom),Box(x+gap,top.top,top.right,top.bottom))
        }
        fun permutations(indices:List<Int>):List<List<Int>> = if(indices.isEmpty()) listOf(emptyList()) else indices.flatMap { i -> permutations(indices-i).map { listOf(i)+it } }
        var best=emptyList<Panel>();var bestScore=-1f
        for(template in templates) for(order in permutations(sizes.indices.toList())) {
            val score=template.indices.sumOf { i -> val size=sizes[order[i]];val fitted=fit(size.width,size.height,template[i]);(fitted.width*fitted.height).toDouble() }.toFloat()
            if(score>bestScore+1f) { bestScore=score;best=template.mapIndexed { i,box -> Panel(order[i],box) } }
        }
        return best
    }
    fun fit(width: Int,height: Int,cell: Box,fill: Boolean=false): Box {
        require(width>0 && height>0)
        val scale=if(fill) maxOf(cell.width/width,cell.height/height) else minOf(cell.width/width,cell.height/height)
        val x=cell.left+(cell.width-width*scale)/2;val y=cell.top+(cell.height-height*scale)/2
        return Box(x,y,x+width*scale,y+height*scale)
    }
}
