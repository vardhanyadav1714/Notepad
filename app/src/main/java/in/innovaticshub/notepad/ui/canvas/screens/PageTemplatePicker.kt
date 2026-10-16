package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderPaper
import `in`.innovaticshub.notepad.ui.canvas.model.*

@Composable
internal fun PageTemplatePicker(style:PaperStyle,background:Color,onStyle:(PaperStyle)->Unit,onBackground:(Color)->Unit,onCustomColour:()->Unit) {
    val displayPaper=`in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance.current.paper(background)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp)) {
        Text("Page template",style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        PaperStyle.entries.chunked(2).forEach {row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                row.forEach {paper->
                    val label=when(paper) {PaperStyle.PLAIN->"Blank";PaperStyle.RULED->"Lined";PaperStyle.GRID->"Grid";PaperStyle.DOTTED->"Dotted"}
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Surface(onClick={onStyle(paper)},shape=RoundedCornerShape(12.dp),
                            border=BorderStroke(if(style==paper) 2.dp else 1.dp,if(style==paper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier=Modifier.fillMaxWidth().height(142.dp).semantics {contentDescription="$label paper";selected=style==paper}) {
                            Canvas(Modifier.fillMaxSize()) {scale(size.width/1000f,pivot=Offset.Zero) {renderPaper(CanvasPage(height=1100f),displayPaper,paper)}}
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {RadioButton(style==paper,{onStyle(paper)});Text(label,style=MaterialTheme.typography.bodyMedium)}
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical=12.dp))
        Text("Background colour",style=MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf(Color(0xFFFAFAFA),Color(0xFFFFF8E8),Color(0xFFF8E4E4),Color(0xFFE8F0E9),Color(0xFFE3ECF7),Color(0xFF1C2520)).forEachIndexed {index,colour->
                IconButton(onClick={onBackground(colour)},modifier=Modifier.size(42.dp).semantics {contentDescription="Paper colour ${index+1}";selected=background==colour}) {
                    Box(Modifier.size(30.dp).background(colour,CircleShape).border(1.dp,MaterialTheme.colorScheme.outlineVariant,CircleShape),contentAlignment=Alignment.Center) {
                        if(background==colour) Icon(Icons.Outlined.Check,null,Modifier.size(18.dp),tint=if(index==5) Color.White else Color.Black)
                    }
                }
            }
        }
        TextButton(onClick=onCustomColour,modifier=Modifier.align(Alignment.End)) {Text("Custom colour")}
    }
}
