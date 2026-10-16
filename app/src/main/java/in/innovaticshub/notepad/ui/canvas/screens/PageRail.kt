package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel

/** Tablet page sorter stays beside the document; the writing surface has no add-page footer. */
@Composable
internal fun PageRail(model:DrawingViewModel,onOpen:(String)->Unit,onAdd:()->Unit,onDelete:(String)->Unit,onClose:()->Unit) {
    val state by model.uiState.collectAsState()
    Surface(color=MaterialTheme.colorScheme.surface,modifier=Modifier.width(180.dp).fillMaxHeight().testTag("page-sorter")) {
        Column(Modifier.padding(horizontal=12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pages",style=MaterialTheme.typography.titleSmall)
                    Text("${state.pages.indexOfFirst {it.id==state.currentPageId}+1} of ${state.pages.size}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onClose) {Icon(Icons.Outlined.Close,"Close page sorter")}
            }
            LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                itemsIndexed(state.pages,key={_,page->page.id}) {index,page->
                    var menu by remember {mutableStateOf(false)}
                    Column {
                        Surface(onClick={onOpen(page.id)},shape=RoundedCornerShape(14.dp),
                            border=BorderStroke(if(page.id==state.currentPageId) 2.dp else 1.dp,if(page.id==state.currentPageId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Box(Modifier.fillMaxWidth().padding(8.dp)) {PagePreview(model,page)
                                Box(Modifier.align(Alignment.BottomEnd)) {
                                    IconButton(onClick={menu=true},modifier=Modifier.size(32.dp)) {Icon(Icons.Outlined.MoreVert,"Page ${index+1} options",Modifier.size(18.dp))}
                                    DropdownMenu(menu,{menu=false}) {
                                        DropdownMenuItem(text={Text("Duplicate")},onClick={menu=false;model.duplicatePage(page.id);onOpen(model.uiState.value.currentPageId)})
                                        DropdownMenuItem(text={Text("Move earlier")},enabled=index>0,onClick={menu=false;model.movePage(page.id,-1)})
                                        DropdownMenuItem(text={Text("Move later")},enabled=index<state.pages.lastIndex,onClick={menu=false;model.movePage(page.id,1)})
                                        DropdownMenuItem(text={Text("Delete")},enabled=state.pages.size>1,onClick={menu=false;onDelete(page.id)})
                                    }
                                }
                            }
                        }
                        Text("Page ${index+1}",style=MaterialTheme.typography.labelMedium,modifier=Modifier.align(Alignment.CenterHorizontally).clickable {onOpen(page.id)}.padding(horizontal=12.dp,vertical=5.dp))
                    }
                }
            }
            OutlinedButton(onClick=onAdd,modifier=Modifier.fillMaxWidth().padding(vertical=10.dp),shape=RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Add,"Add page",Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("New page")
            }
        }
    }
}
