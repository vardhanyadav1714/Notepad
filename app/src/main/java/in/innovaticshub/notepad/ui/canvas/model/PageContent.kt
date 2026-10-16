package `in`.innovaticshub.notepad.ui.canvas.model

import `in`.innovaticshub.notepad.data.content.RichTextData
import `in`.innovaticshub.notepad.data.content.TextAlign
import java.util.UUID

data class PageText(
    val id:String=UUID.randomUUID().toString(),val pageId:String="page-1",
    val x:Float=48f,val y:Float=64f,val width:Float=904f,val height:Float=56f,
    val content:RichTextData=RichTextData.EMPTY,val fontSize:Float=40f,
    val color:Int=0xFF202124.toInt(),val alignment:TextAlign=TextAlign.LEFT,
    val checked:Boolean?=null
)

data class PageImage(
    val id:String=UUID.randomUUID().toString(),val pageId:String="page-1",val path:String,
    val x:Float=48f,val y:Float=64f,val width:Float=600f,val height:Float=400f,
    val rotation:Float=0f,val cropLeft:Float=0f,val cropTop:Float=0f,val cropRight:Float=1f,val cropBottom:Float=1f
)

fun PageImage.bounds():RectF {
    val angle=Math.toRadians(rotation.toDouble())
    val w=(kotlin.math.abs(kotlin.math.cos(angle))*width+kotlin.math.abs(kotlin.math.sin(angle))*height).toFloat()
    val h=(kotlin.math.abs(kotlin.math.sin(angle))*width+kotlin.math.abs(kotlin.math.cos(angle))*height).toFloat()
    return RectF(x+(width-w)/2,y+(height-h)/2,x+(width+w)/2,y+(height+h)/2)
}

data class PageAudio(val id:String=UUID.randomUUID().toString(),val path:String,val durationMs:Long)
