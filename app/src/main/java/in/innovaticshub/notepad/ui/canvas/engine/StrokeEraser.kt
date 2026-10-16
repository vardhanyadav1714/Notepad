package `in`.innovaticshub.notepad.ui.canvas.engine

import `in`.innovaticshub.notepad.ui.canvas.model.*
import kotlin.math.*

/** Clips vector segments against swept eraser capsules, including sparse and tap strokes. */
object StrokeEraser {
    data class Interval(val start: Float, val end: Float)
    fun erase(strokes: List<Stroke>, eraserStroke: Stroke): List<Stroke> {
        if (eraserStroke.points.isEmpty()) return strokes
        val eraser = eraserStroke.toolConfig
        val radius = eraser.strokeWidth()/2f
        val bounds=RectF(eraserStroke.points.minOf {it.x}-radius,eraserStroke.points.minOf {it.y}-radius,
            eraserStroke.points.maxOf {it.x}+radius,eraserStroke.points.maxOf {it.y}+radius)
        return strokes.flatMap { stroke ->
            if (!stroke.bounds.intersects(bounds) || stroke.toolConfig.isEraser || (eraser.eraseHighlighterOnly && stroke.toolConfig.type != ToolType.HIGHLIGHTER)) listOf(stroke)
            else split(stroke,eraserStroke.points,radius,eraser.eraserMode)
        }
    }

    private fun split(stroke:Stroke, eraser:List<StrokePoint>, radius:Float, mode:EraserMode):List<Stroke> {
        val points=InkGeometry.samples(stroke.points,stroke.toolConfig.smoothing)
        if(points.isEmpty()) return listOf(stroke)
        if(points.size==1) {
            val p=points.single()
            return if(eraser.any { hypot(p.x-it.x,p.y-it.y)<=radius+stroke.toolConfig.strokeWidth(p.pressure)/2 } ||
                eraser.zipWithNext().any { (a,b)-> pointDistance(p,a,b)<=radius+stroke.toolConfig.strokeWidth(p.pressure)/2 }) emptyList() else listOf(stroke)
        }
        val runs=mutableListOf<MutableList<StrokePoint>>()
        var current=mutableListOf<StrokePoint>()
        var changed=false
        points.zipWithNext().forEach { (a,b) ->
            val hitRadius=radius+maxOf(stroke.toolConfig.strokeWidth(a.pressure),stroke.toolConfig.strokeWidth(b.pressure))/2
            val cuts=cuts(a,b,eraser,hitRadius)
            if(cuts.isNotEmpty()) {
                changed=true
                if(mode==EraserMode.STROKE) return emptyList()
            }
            val kept=complement(cuts)
            kept.forEach { interval ->
                val from=interpolate(a,b,interval.start); val to=interpolate(a,b,interval.end)
                if(current.isNotEmpty() && hypot(current.last().x-from.x,current.last().y-from.y)>0.01f) {
                    if(current.size>=2) runs+=current
                    current=mutableListOf()
                }
                if(current.isEmpty()) current+=from
                current+=to
                if(interval.end<0.99999f) { if(current.size>=2) runs+=current; current=mutableListOf() }
            }
            if(kept.isEmpty() && current.isNotEmpty()) { if(current.size>=2) runs+=current; current=mutableListOf() }
        }
        if(current.size>=2) runs+=current
        if(!changed) return listOf(stroke)
        return runs.mapIndexed { index, run ->
            val tool=stroke.toolConfig.copy(smoothing=0f)
            Stroke(if(index==0) stroke.id else Stroke.generateId(),run,InkGeometry.path(run),tool,stroke.pageId)
        }
    }

    fun survivingIntervals(a:StrokePoint,b:StrokePoint,eraser:List<StrokePoint>,radius:Float):List<Interval> =
        complement(cuts(a,b,eraser,radius))

    private fun cuts(a:StrokePoint,b:StrokePoint,eraser:List<StrokePoint>,radius:Float):List<Interval> {
        val result=mutableListOf<Interval>()
        eraser.forEach { disk(a,b,it,radius)?.let(result::add) }
        eraser.zipWithNext().forEach { (c,d) ->
            val dx=d.x-c.x; val dy=d.y-c.y
            val length2=dx*dx+dy*dy
            if(length2>0.000001f) {
                var range=Interval(0f,1f)
                fun constrain(origin:Float,slope:Float,min:Float,max:Float):Boolean {
                    val interval=linear(origin,slope,min,max) ?: return false
                    range=Interval(maxOf(range.start,interval.start),minOf(range.end,interval.end))
                    return range.end>=range.start
                }
                val projection=(a.x-c.x)*dx+(a.y-c.y)*dy
                val projectionSlope=(b.x-a.x)*dx+(b.y-a.y)*dy
                val cross=(a.x-c.x)*dy-(a.y-c.y)*dx
                val crossSlope=(b.x-a.x)*dy-(b.y-a.y)*dx
                if(constrain(projection,projectionSlope,0f,length2) &&
                    constrain(cross,crossSlope,-radius*sqrt(length2),radius*sqrt(length2))) result+=range
            }
        }
        val merged=mutableListOf<Interval>()
        result.filter { it.end>=0 && it.start<=1 }.map { Interval(it.start.coerceIn(0f,1f),it.end.coerceIn(0f,1f)) }
            .sortedBy { it.start }.forEach { interval ->
                val last=merged.lastOrNull()
                if(last!=null && interval.start<=last.end+0.000001f) merged[merged.lastIndex]=Interval(last.start,maxOf(last.end,interval.end))
                else merged+=interval
            }
        return merged
    }
    private fun complement(cuts:List<Interval>):List<Interval> = buildList {
        var cursor=0f
        cuts.forEach { interval -> if(interval.start-cursor>0.000001f) add(Interval(cursor,interval.start)); cursor=maxOf(cursor,interval.end) }
        if(cursor<0.999999f) add(Interval(cursor,1f))
    }
    private fun linear(origin:Float,slope:Float,min:Float,max:Float):Interval? {
        if(abs(slope)<0.000001f) return if(origin in min..max) Interval(0f,1f) else null
        val a=(min-origin)/slope; val b=(max-origin)/slope
        return Interval(minOf(a,b),maxOf(a,b))
    }
    private fun disk(a:StrokePoint,b:StrokePoint,c:StrokePoint,r:Float):Interval? {
        val dx=b.x-a.x; val dy=b.y-a.y; val ox=a.x-c.x; val oy=a.y-c.y
        val aa=dx*dx+dy*dy; val cc=ox*ox+oy*oy-r*r
        if(aa<0.000001f) return if(cc<=0) Interval(0f,1f) else null
        val bb=2*(ox*dx+oy*dy); val determinant=bb*bb-4*aa*cc
        if(determinant<0) return null
        val root=sqrt(determinant)
        return Interval((-bb-root)/(2*aa),(-bb+root)/(2*aa))
    }
    private fun interpolate(a:StrokePoint,b:StrokePoint,t:Float)=a.copy(
        x=a.x+(b.x-a.x)*t,y=a.y+(b.y-a.y)*t,pressure=a.pressure+(b.pressure-a.pressure)*t,
        timestamp=a.timestamp+((b.timestamp-a.timestamp)*t).toLong(),tilt=a.tilt+(b.tilt-a.tilt)*t,
        orientation=a.orientation+(b.orientation-a.orientation)*t)
    fun pointDistance(p:StrokePoint,a:StrokePoint,b:StrokePoint):Float {
        val dx=b.x-a.x; val dy=b.y-a.y
        val length2=dx*dx+dy*dy
        val t=if(length2>0) (((p.x-a.x)*dx+(p.y-a.y)*dy)/length2).coerceIn(0f,1f) else 0f
        return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
    }
}
