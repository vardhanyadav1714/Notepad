package `in`.innovaticshub.notepad.ui.canvas.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Document coordinates are global for navigation; stored ink/text coordinates remain page-local. */
class PageStackLayout(val pages:List<CanvasPage>,val gap:Float=24f) {
    val width=pages.maxOfOrNull {it.width} ?: 1000f
    val frames:Map<String,Rect>
    val height:Float
    init {
        var y=0f
        frames=pages.associate {page->
            val left=(width-page.width)/2
            val bounds=Rect(left,y,left+page.width,y+page.height)
            y+=page.height+gap
            page.id to bounds
        }
        height=(y-gap).coerceAtLeast(0f)
    }
    fun origin(id:String)=frames[id]?.topLeft ?: Offset.Zero
    fun pageAt(point:Offset)=pages.firstOrNull {frames.getValue(it.id).contains(point)}
    fun nearestPage(y:Float)=pages.minByOrNull {page->val r=frames.getValue(page.id);when {y<r.top->r.top-y;y>r.bottom->y-r.bottom;else->0f}}
}
