package `in`.innovaticshub.notepad

import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.collab.CollaborationRepository
import okhttp3.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in paired-device test. Uses only an isolated in-memory document and a supplied QA room.
 * Each device receives commands through its own external-files directory; no user notes are read.
 * Not part of the normal suite: pass liveCollabRoom explicitly to authorize live network traffic.
 */
class LiveCollaborationDeviceTest {
    @Test fun pairedDeviceSession() {
        val args=InstrumentationRegistry.getArguments()
        val room=args.getString("liveCollabRoom")
        assumeTrue("Live testing requires an explicit isolated QA room",room?.matches(Regex("qa-[a-f0-9]{16}"))==true)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=instrumentation.targetContext.getExternalFilesDir("collaboration-review")!!
        directory.mkdirs()
        val command=File(directory,"command.json");command.delete()
        val output=File(directory,"state.json")
        // Interrupt only this QA client's transport. Wireless ADB and other apps stay connected.
        val disconnected=java.util.concurrent.atomic.AtomicBoolean(false)
        var socket:WebSocket?=null
        val client=OkHttpClient.Builder().addInterceptor {chain->
            if(disconnected.get()) throw java.io.IOException("QA transport interruption")
            chain.proceed(chain.request())
        }.build()
        val factory=object:WebSocket.Factory {
            override fun newWebSocket(request:Request,listener:WebSocketListener):WebSocket =
                client.newWebSocket(request,listener).also {socket=it}
        }
        val model=DrawingViewModel(CollaborationRepository(client=factory))
        var lastCommand="ready"
        var failure:String?=null
        fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
        fun capture() {
            main {
                val state=model.uiState.value
                val saved=DrawingStorage.encodeSession(state.savedSession())
                val restored=DrawingStorage.decodeSession(saved)
                assertEquals(state.strokes.map {it.id},restored.strokes.map {it.id})
                val json=JSONObject(saved).put("connected",state.collaborationConnected)
                    .put("status",state.collaborationStatus).put("command",lastCommand)
                    .put("failure",failure ?: JSONObject.NULL)
                val temporary=File(directory,"state.tmp");temporary.writeText(json.toString())
                check(temporary.renameTo(output))
            }
        }
        try {
            main {model.setDocumentTitle("Isolated live collaboration QA");model.joinCollaborationRoom(room!!)}
            val deadline=System.currentTimeMillis()+300_000
            var finished=false
            while(!finished && System.currentTimeMillis()<deadline) {
                if(command.exists()) {
                    val next=JSONObject(command.readText());command.delete()
                    lastCommand=next.getString("id")
                    try { main {
                        when(next.getString("action")) {
                            "draw" -> {
                                if(next.optBoolean("newPage")) model.addPage(next.optBoolean("landscape"))
                                model.setTool(ToolType.BRUSH)
                                model.setToolConfig(model.uiState.value.currentTool.copy(color=Color(0xff3c82f6),baseWidth=7f,smoothing=0.72f,nibAngle=35f))
                                val y=next.optDouble("y",200.0).toFloat()
                                model.startStroke(100f,y,0.3f,timestamp=100,tilt=0.4f,orientation=0.2f)
                                model.addStrokePoint(300f,y+25f,0.8f,timestamp=200,tilt=0.6f,orientation=0.3f)
                                model.endStroke()
                            }
                            "undo" -> model.undo()
                            "redo" -> model.redo()
                            "move" -> {model.selectAll();model.nudgeSelection(35f,20f);model.clearSelection()}
                            "erase" -> {
                                val target=model.uiState.value.strokes.last()
                                model.openPage(target.pageId)
                                model.setTool(ToolType.ERASER)
                                model.setToolConfig(model.uiState.value.currentTool.copy(eraserMode=EraserMode.STROKE))
                                model.startStroke(target.points.first().x,target.points.first().y)
                                model.endStroke()
                            }
                            "leave" -> model.leaveCollaborationRoom()
                            "join" -> model.joinCollaborationRoom(room!!)
                            "freshJoin" -> {
                                model.leaveCollaborationRoom()
                                model.restoreSession(`in`.innovaticshub.notepad.ui.canvas.storage.SavedSession(emptyList(),null,null))
                                model.joinCollaborationRoom(room!!)
                            }
                            "interrupt" -> {disconnected.set(true);socket?.cancel()}
                            "resume" -> disconnected.set(false)
                            "finish" -> finished=true
                            else -> error("Unknown paired test command")
                        }
                    }} catch(error:Throwable) {failure=error.toString()}
                }
                capture()
                Thread.sleep(100)
            }
            assertTrue("Paired session did not receive finish command",finished)
            assertNull(failure)
        } finally {main {model.leaveCollaborationRoom()}}
    }
}
