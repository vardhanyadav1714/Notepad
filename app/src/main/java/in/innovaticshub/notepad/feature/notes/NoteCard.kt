package `in`.innovaticshub.notepad.feature.notes

import android.text.format.DateUtils
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.data.db.entity.NoteEntity
import `in`.innovaticshub.notepad.data.prefs.NoteViewMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import `in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance
import `in`.innovaticshub.notepad.ui.canvas.drawing.NoteThumbnailRenderer
import `in`.innovaticshub.notepad.data.content.BlockType

/** A page preview and its caption share one target, including in selection mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(note: NoteEntity, viewMode: NoteViewMode, isSelected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val appearance=LocalNoteAppearance.current
    val themedPreview by produceState<File?>(null,note.id,note.modifiedAt,note.thumbnailPath,note.isLocked,appearance) {
        value=null
        if(!note.isLocked && note.thumbnailPath!=null) {
            value=withContext(Dispatchers.IO) {
                val app=context.applicationContext as `in`.innovaticshub.notepad.DrawlyApplication
                val drawing=app.container.noteRepository.getBlocks(note.id).firstOrNull {it.type==BlockType.DRAWING}?.drawing
                if(drawing!=null) NoteThumbnailRenderer.render(context,"${note.id}-${appearance.dark}",drawing,cacheOnly=true,appearance=appearance)?.let(::File)
                else File(note.thumbnailPath).takeIf {it.exists()}
            }
        }
    }
    val paper = if (note.colorArgb == -1) colors.surface
        else if (colors.surface.red < 0.3f) lerp(colors.surface, Color(note.colorArgb), 0.12f) else Color(note.colorArgb)
    val grid = viewMode == NoteViewMode.GRID
    val interaction = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    val caption: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (note.isPinned) Icon(Icons.Outlined.StarBorder, "Favourite", Modifier.padding(start = 4.dp).size(15.dp), tint = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(DateUtils.getRelativeTimeSpanString(note.modifiedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        Surface(color = paper, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(if (isSelected) 2.dp else 0.5.dp, if (isSelected) colors.primary else colors.outlineVariant),
            modifier = modifier) {
            Box {
                if (note.isLocked) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, "Locked note", Modifier.size(24.dp), tint = colors.onSurfaceVariant)
                } else {
                    val image = themedPreview
                    if (image != null) coil.compose.AsyncImage(model = coil.request.ImageRequest.Builder(context).data(image)
                        .memoryCacheKey("${image.path}:${note.modifiedAt}").diskCacheKey("${image.path}:${note.modifiedAt}").build(), contentDescription = "Note preview",
                        contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                    else Text(note.searchText.lineSequence().filter { it.isNotBlank() && it != note.title }.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
                }
                if (isSelected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = colors.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).background(paper, CircleShape))
            }
        }
    }
    if (grid) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(interaction)) {
        preview(Modifier.fillMaxWidth().aspectRatio(0.72f))
        caption(Modifier.padding(start = 5.dp, end = 5.dp, top = 10.dp, bottom = 8.dp))
    } else Column(Modifier.fillMaxWidth().then(interaction)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            preview(Modifier.size(72.dp, 96.dp))
            caption(Modifier.weight(1f).padding(start = 18.dp, end = 8.dp))
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun FolderColorDot(colorArgb: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(Color(colorArgb), CircleShape))
}
