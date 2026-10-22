package `in`.innovaticshub.notepad.ui.canvas.collab

import `in`.innovaticshub.notepad.BuildConfig
import java.net.URI
import java.net.URLEncoder

object CollaborationEndpoint {
    val origin: String = BuildConfig.COLLABORATION_ORIGIN
    val host: String = URI(origin).host
    val websocketUrl: String = "wss://${URI(origin).rawAuthority}/ws"
    fun shareLink(room: String): String = "$origin/r/${URLEncoder.encode(room, "UTF-8").replace("+", "%20")}"
}
