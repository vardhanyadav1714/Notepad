package `in`.innovaticshub.notepad.feature.editor

import android.content.Context
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.media.PdfPageImages
import `in`.innovaticshub.notepad.ui.canvas.drawing.textLayout
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.storage.*
import java.util.UUID

/** Imports legacy blocks once; original files stay intact until the note is purged. */
internal fun seedPageDocument(context:Context,session:SavedSession,blocks:List<NoteBlock>,documentKey:String):SavedSession {
    var result=session
    var y=(result.texts.filter {it.pageId==result.pages.first().id}.maxOfOrNull {it.y+it.height+24} ?: 160f)
    val pageId=result.pages.first().id
    blocks.sortedBy {it.position}.forEach {block->
        if(block.id in result.importedBlocks || block.id==documentKey || result.texts.any {it.id==block.id} || result.images.any {it.id==block.id} || result.audio.any {it.id==block.id}) return@forEach
        when(block.type) {
            BlockType.TEXT,BlockType.CHECKLIST_ITEM -> {
                val text=PageText(id=block.id,pageId=pageId,y=y,content=block.richText,alignment=block.textAlign,
                    checked=if(block.type==BlockType.CHECKLIST_ITEM) block.isChecked ?: false else null)
                val sized=text.copy(height=textLayout(text).height.toFloat().coerceAtLeast(56f))
                result=result.copy(texts=result.texts+sized);y+=sized.height+24
            }
            BlockType.IMAGE -> block.image?.let {image->
                val w=700f;val h=w*image.height/image.width.coerceAtLeast(1)
                result=result.copy(images=result.images+PageImage(block.id,pageId,image.path,y=y,width=w,height=h),assets=result.assets+image.path);y+=h+24
            }
            BlockType.RECORDING -> block.recording?.let {audio->result=result.copy(audio=result.audio+PageAudio(block.id,audio.path,audio.durationMs),assets=result.assets+audio.path)}
            BlockType.PDF -> block.pdf?.let {pdf->
                val pages=PdfPageImages.sizes(context,pdf.file).mapIndexed {i,size->CanvasPage(UUID.randomUUID().toString(),pdf.file,i,1000f,1000f*size.second/size.first.coerceAtLeast(1))}
                result=result.copy(pages=result.pages+pages,assets=result.assets+pdf.file)
            }
            BlockType.DRAWING -> block.drawing?.let {drawing->DrawingStorage.loadDocument(context,drawing.strokesFile)?.let {other->
                val ids=other.pages.associate {it.id to UUID.randomUUID().toString()}
                result=result.copy(pages=result.pages+other.pages.map {it.copy(id=ids.getValue(it.id))},
                    strokes=result.strokes+other.strokes.map {it.copy(pageId=ids[it.pageId] ?: ids.values.first())},
                    texts=result.texts+other.texts.map {it.copy(pageId=ids[it.pageId] ?: ids.values.first())},
                    images=result.images+other.images.map {it.copy(pageId=ids[it.pageId] ?: ids.values.first())},audio=(result.audio+other.audio).distinctBy {it.id},assets=result.assets+other.assets)
            } }
        }
        result=result.copy(importedBlocks=result.importedBlocks+block.id)
    }
    return result.copy(pages=result.pages.map {if(it.id==pageId && it.pdfPath==null) it.copy(height=maxOf(it.height,y+64)) else it})
}
