package `in`.innovaticshub.notepad.ui.canvas.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import `in`.innovaticshub.notepad.ui.canvas.model.*

internal fun isDarkPaper(page:CanvasPage,color:Color)=page.pdfPath==null && color.red+color.green+color.blue<1.5f

internal fun DrawScope.renderPaper(page:CanvasPage,color:Color,style:PaperStyle,pdf:ImageBitmap?=null) {
    drawRect(if(page.pdfPath!=null) Color.White else color,Offset.Zero,Size(page.width,page.height))
    if(pdf!=null) {
        drawImage(pdf,srcOffset=IntOffset.Zero,srcSize=IntSize(pdf.width,pdf.height),dstOffset=IntOffset.Zero,
            dstSize=IntSize(page.width.toInt().coerceAtLeast(1),page.height.toInt().coerceAtLeast(1)))
        return
    }
    if(style==PaperStyle.PLAIN) return
    val dark=color.red+color.green+color.blue<1.5f
    val ruling=if(dark) Color.White.copy(alpha=0.12f) else Color(0xFF758D80).copy(alpha=0.22f)
    val step=32f
    var y=step
    while(y<page.height) {
        if(style==PaperStyle.DOTTED) {
            var x=step
            while(x<page.width) { drawCircle(ruling,1.3f,Offset(x,y)); x+=step }
        } else drawLine(ruling,Offset(0f,y),Offset(page.width,y),1f)
        y+=step
    }
    if(style==PaperStyle.GRID) { var x=step; while(x<page.width) { drawLine(ruling,Offset(x,0f),Offset(x,page.height),1f); x+=step } }
}
