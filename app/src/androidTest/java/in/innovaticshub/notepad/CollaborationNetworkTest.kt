package `in`.innovaticshub.notepad

import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.collab.*
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises the real TLS/WebSocket client and current wire messages, without the live service. */
class CollaborationNetworkTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    private fun await(condition:()->Boolean) {
        val deadline=System.currentTimeMillis()+15000
        while(!condition() && System.currentTimeMillis()<deadline) Thread.sleep(25)
        assertTrue("Collaboration condition timed out",condition())
    }
    internal class Relay(private val legacyMetadata:Boolean=false, private val presence:Boolean=false):AutoCloseable {
        val server=MockWebServer()
        val sockets=ConcurrentHashMap<String,CopyOnWriteArrayList<WebSocket>>()
        val stored=ConcurrentHashMap<String,List<JSONObject>>()
        val members=ConcurrentHashMap<WebSocket,JSONObject>()
        private fun roster(room:String) {
            val list=sockets[room] ?: return
            val message=JSONObject().put("type","presence").put("members",JSONArray(list.mapNotNull {members[it]})).toString()
            list.forEach {it.send(message)}
        }
        val certificate=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        init {
            val tls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
            server.useHttps(tls.sslSocketFactory(),false)
            server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    val room=request.path!!.substringAfterLast('/')
                    return MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                        override fun onOpen(socket:WebSocket,response:Response) {
                            sockets.getOrPut(room) {CopyOnWriteArrayList()}.add(socket)
                            socket.send(JSONObject().put("type","room_snapshot").put("strokes",JSONArray(stored[room] ?: emptyList<JSONObject>())).toString())
                        }
                        override fun onMessage(socket:WebSocket,text:String) {
                            val message=JSONObject(text)
                            if(presence) when(message.optString("type")) {
                                "join" -> {
                                    val id=java.util.UUID.randomUUID().toString()
                                    members[socket]=JSONObject().put("id",id).put("userId",message.getString("userId"))
                                        .put("label","Artist ${id.take(4).uppercase()}").put("colorArgb",0xff2563ebL).put("activity","idle").put("updatedAt",System.currentTimeMillis())
                                    socket.send(JSONObject().put("type","joined").put("memberId",id).put("capabilities",JSONArray(listOf("presence","stroke_preview"))).toString())
                                    roster(room);return
                                }
                                "presence" -> {
                                    val member=members[socket] ?: return
                                    member.put("activity",message.optString("activity","idle")).put("cursor",message.opt("cursor") ?: JSONObject.NULL).put("updatedAt",System.currentTimeMillis())
                                    val update=JSONObject().put("type","presence_update").put("member",member).toString()
                                    sockets[room]?.forEach {it.send(update)};return
                                }
                                "stroke_preview" -> {
                                    val member=members[socket] ?: return
                                    val preview=JSONObject().put("type","stroke_preview").put("memberId",member.getString("id")).put("stroke",message.opt("stroke") ?: JSONObject.NULL).toString()
                                    sockets[room]?.forEach {it.send(preview)};return
                                }
                            }
                            if(legacyMetadata && message.optString("type")=="stroke") {
                                val stroke=message.getJSONObject("stroke")
                                stroke.remove("pageId");stroke.getJSONObject("toolConfig").remove("settings")
                                val points=stroke.getJSONArray("points")
                                repeat(points.length()) {index->points.getJSONObject(index).apply {
                                    remove("timestamp");remove("tilt");remove("orientation")
                                }}
                            }
                            when(message.getString("type")) {
                                "stroke"->{val stroke=message.getJSONObject("stroke");stored.compute(room) {_,old->(old ?: emptyList()).filterNot {it.getString("id")==stroke.getString("id")}+stroke}}
                                "canvas_state"->{val array=message.getJSONObject("state").getJSONArray("strokes");stored[room]=(0 until array.length()).map {array.getJSONObject(it)}}
                                else->return
                            }
                            sockets[room]?.forEach {it.send(message.toString())}
                            if(presence && message.optString("type")=="stroke") members[socket]?.let {member->
                                val clear=JSONObject().put("type","stroke_preview").put("memberId",member.getString("id")).put("stroke",JSONObject.NULL).toString()
                                sockets[room]?.forEach {it.send(clear)}
                            }
                        }
                        override fun onClosing(socket:WebSocket,code:Int,reason:String) {sockets[room]?.remove(socket);members.remove(socket);if(presence) roster(room);socket.close(code,reason)}
                        override fun onClosed(socket:WebSocket,code:Int,reason:String) {sockets[room]?.remove(socket);members.remove(socket);if(presence) roster(room)}
                    })
                }
            }
            server.start()
        }
        fun repository():CollaborationRepository {
            val client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
            return CollaborationRepository(server.url("/ws").toString().replace("https:","wss:"),client)
        }
        override fun close() {sockets.values.flatten().forEach {it.close(1000,"Test complete")};server.shutdown()}
    }
    @Test fun livePresencePreviewsCancelAndLeaveNeverChangeSavedInk() {
        Relay(presence=true).use {relay->
            val a=DrawingViewModel(relay.repository());val b=DrawingViewModel(relay.repository())
            try {
                main {a.createCollaborationRoom()};await {a.uiState.value.collaborationConnected}
                main {b.joinCollaborationRoom(a.uiState.value.collaborationRoomCode!!)}
                await {a.presence.value.members.size==2 && b.presence.value.members.size==2}
                assertEquals(1,b.presence.value.members.count {it.isSelf})
                main {a.startStroke(100f,200f);a.addStrokePoint(250f,300f)}
                await {b.presence.value.previews.isNotEmpty() && b.presence.value.members.any {!it.isSelf && it.activity=="drawing"}}
                assertTrue(b.snapshot().strokes.isEmpty())
                assertFalse(b.uiState.value.canUndo)
                main {a.endStroke()}
                await {b.uiState.value.strokes.size==1 && b.presence.value.previews.isEmpty()}
                main {a.startStroke(300f,400f)};await {b.presence.value.previews.isNotEmpty()}
                main {a.cancelStroke()};await {b.presence.value.previews.isEmpty()}
                assertEquals(1,b.snapshot().strokes.size)
                main {a.startStroke(350f,450f)};await {b.presence.value.previews.isNotEmpty()}
                await {b.presence.value.previews.isEmpty() && b.presence.value.members.none {!it.isSelf && it.activity=="drawing"}}
                assertEquals(1,b.snapshot().strokes.size)
                main {a.cancelStroke();b.leaveCollaborationRoom()}
                await {a.presence.value.members.size==1}
                assertTrue(b.presence.value.members.isEmpty())
            } finally {main {a.leaveCollaborationRoom();b.leaveCollaborationRoom()}}
        }
    }
    @Test fun legacyRelayWithoutStylusMetadataProducesInkThatCanBeSavedAndReopened() {
        Relay(legacyMetadata=true).use {relay->
            val a=DrawingViewModel(relay.repository());val b=DrawingViewModel(relay.repository())
            try {
                main {a.createCollaborationRoom()};await {a.uiState.value.collaborationConnected}
                main {b.joinCollaborationRoom(a.uiState.value.collaborationRoomCode!!)}
                await {b.uiState.value.collaborationConnected}
                main {a.startStroke(100f,200f,0.4f,timestamp=100,tilt=0.5f);a.addStrokePoint(300f,250f,0.8f);a.endStroke()}
                await {b.uiState.value.strokes.size==1}
                val received=b.uiState.value.strokes.single()
                assertTrue(received.points.all {it.tilt==0f && it.orientation==0f})
                val storage=`in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
                val restored=storage.decodeSession(storage.encodeSession(b.uiState.value.savedSession()))
                assertEquals(received.id,restored.strokes.single().id)
                assertEquals(received.points,restored.strokes.single().points)
                main {b.leaveCollaborationRoom();b.restoreSession(restored);b.joinCollaborationRoom(a.uiState.value.collaborationRoomCode!!)}
                await {b.uiState.value.collaborationConnected}
                // Ensure the replay has arrived, rather than asserting before the snapshot.
                main {a.startStroke(400f,500f);a.addStrokePoint(500f,500f);a.endStroke()}
                await {b.uiState.value.strokes.any {it.points.first().x==400f}}
                assertEquals(2,b.uiState.value.strokes.size)
            } finally {main {a.leaveCollaborationRoom();b.leaveCollaborationRoom()}}
        }
    }
    @Test fun twoClientsShareExistingInkNewPagesTransformsEraserUndoAndReconnect() {
        Relay().use {relay->
            val a=DrawingViewModel(relay.repository());val b=DrawingViewModel(relay.repository())
            try {
                main {a.startStroke(100f,200f,0.3f);a.addStrokePoint(300f,200f,0.9f);a.endStroke();a.createCollaborationRoom()}
                await {a.uiState.value.collaborationConnected && relay.stored[a.uiState.value.collaborationRoomCode]?.size==1}
                val room=a.uiState.value.collaborationRoomCode!!
                main {b.joinCollaborationRoom(" https://drawly.smartattendance.xyz/r/$room?source=test ")}
                await {b.uiState.value.strokes.size==1}
                main {b.addPage();b.startStroke(200f,300f,0.25f,timestamp=100,tilt=0.4f);b.addStrokePoint(400f,300f,0.8f,timestamp=200,tilt=0.6f);b.endStroke()}
                await {a.uiState.value.strokes.size==2}
                val remote=a.uiState.value.strokes.last()
                assertEquals(b.uiState.value.currentPageId,remote.pageId);assertEquals(0.25f,remote.points.first().pressure,0.001f)
                assertEquals(0.4f,remote.points.first().tilt,0.001f);assertTrue(a.uiState.value.pages.any {it.id==remote.pageId})
                main {b.selectAll();b.nudgeSelection(35f,20f)}
                await {a.uiState.value.strokes.last().points.first().x==235f}
                main {b.setTool(ToolType.ERASER);b.setToolConfig(b.uiState.value.currentTool.copy(eraserMode=EraserMode.STROKE));b.startStroke(300f,300f);b.addStrokePoint(300f,340f);b.endStroke()}
                await {a.uiState.value.strokes.size==1}
                main {b.undo()};await {a.uiState.value.strokes.size==2}
                // A broken transport reconnects; local edits made while disconnected are retained and sent.
                relay.sockets[room]!!.first().close(1012,"Reconnect test")
                await {!a.uiState.value.collaborationConnected}
                main {a.startStroke(500f,600f);a.addStrokePoint(600f,650f);a.endStroke()}
                await {a.uiState.value.collaborationConnected && b.uiState.value.strokes.size==3}
                main {a.leaveCollaborationRoom()}
                assertNull(a.uiState.value.collaborationRoomCode);assertTrue(b.uiState.value.collaborationConnected)
            } finally {main {a.leaveCollaborationRoom();b.leaveCollaborationRoom()}}
        }
    }
    @Test fun removingOldRoomSubscriptionCannotCloseNewRoom() {
        Relay().use {relay->
            val repo=relay.repository()
            var connected=0;var received=0
            val old=repo.listenToStrokes("first",{},{},{connected++},{})
            await {connected==1}
            val current=repo.listenToStrokes("second",{received+=it.size},{},{connected++},{})
            try {
                await {connected==2};old.remove()
                runBlocking {repo.uploadStroke("second",RemoteStroke(id="new-room-ink",userId="test",points=listOf(RemoteStrokePoint(10.0,20.0))))}
                await {received==1}
            } finally {current.remove()}
        }
    }
    @Test fun undoDoesNotDiscardAnotherParticipantsInk() {
        Relay().use {relay->
            val a=DrawingViewModel(relay.repository());val b=DrawingViewModel(relay.repository())
            try {
                main {a.createCollaborationRoom()};await {a.uiState.value.collaborationConnected}
                main {b.joinCollaborationRoom(a.uiState.value.collaborationRoomCode!!)};await {b.uiState.value.collaborationConnected}
                main {a.startStroke(100f,100f);a.addStrokePoint(200f,100f);a.endStroke()};await {b.uiState.value.strokes.size==1}
                main {b.startStroke(300f,300f);b.addStrokePoint(400f,300f);b.endStroke()};await {a.uiState.value.strokes.size==2}
                val peerId=b.uiState.value.strokes.last().id
                main {a.undo()};await {b.uiState.value.strokes.size==1}
                assertEquals(peerId,a.uiState.value.strokes.single().id)
                main {b.undo()};await {a.uiState.value.strokes.isEmpty()}
                main {a.redo()};await {b.uiState.value.strokes.size==1}
                assertNotEquals(peerId,b.uiState.value.strokes.single().id)
            } finally {main {a.leaveCollaborationRoom();b.leaveCollaborationRoom()}}
        }
    }
}
