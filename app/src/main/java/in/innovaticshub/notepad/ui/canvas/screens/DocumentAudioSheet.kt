package `in`.innovaticshub.notepad.ui.canvas.screens

import android.Manifest
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.innovaticshub.notepad.media.VoiceRecorder
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import java.io.File

@Composable
internal fun DocumentAudioSheet(model:DrawingViewModel) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val state by model.uiState.collectAsState()
    val recorder=remember {VoiceRecorder(context)}
    var recording by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf<String?>(null)}
    var player by remember {mutableStateOf<MediaPlayer?>(null)}
    var playing by remember {mutableStateOf<String?>(null)}
    fun stop() {recorder.stop()?.let {model.addAudio(it.first,it.second)};recording=false}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(granted) {recording=recorder.start()!=null;if(!recording) status="Could not start recording"}
        else status="Microphone permission is needed to record audio."
    }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE) {stop();player?.pause();playing=null}}
        owner.lifecycle.addObserver(observer)
        onDispose {stop();recorder.release();player?.release();owner.lifecycle.removeObserver(observer)}
    }
    Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Voice recordings",style=MaterialTheme.typography.titleLarge)
        Button(onClick={if(recording) stop() else {player?.pause();playing=null;permission.launch(Manifest.permission.RECORD_AUDIO)}},modifier=Modifier.fillMaxWidth()) {
            Icon(if(recording) Icons.Outlined.Stop else Icons.Outlined.Mic,null);Spacer(Modifier.width(8.dp));Text(if(recording) "Stop and save recording" else "Record audio")
        }
        state.audio.forEachIndexed {index,audio->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={
                if(playing==audio.id) {player?.pause();playing=null}
                else runCatching {
                    player?.release();player=MediaPlayer().apply {
                        setDataSource(File(context.filesDir,audio.path).path);prepare();setOnCompletionListener {playing=null};start()
                    };playing=audio.id
                }.onFailure {status="Could not play this recording"}
            }) {Icon(if(playing==audio.id) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing==audio.id) "Pause recording" else "Play recording")}
            Text("Recording ${index+1}",Modifier.weight(1f));Text("${audio.durationMs/60000}:${(audio.durationMs/1000%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.bodySmall)
        } }
        status?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(state.audio.isEmpty()) Text("Recordings stay with this note on your device.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
