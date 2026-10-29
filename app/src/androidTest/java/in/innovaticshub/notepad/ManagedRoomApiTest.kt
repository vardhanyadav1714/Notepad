package `in`.innovaticshub.notepad

import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.collab.CollaborationRepository
import `in`.innovaticshub.notepad.ui.canvas.collab.SharingSafetyStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ManagedRoomApiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun withServer(action: (MockWebServer, CollaborationRepository) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
            val repo = CollaborationRepository(server.url("/ws").toString().replace("https:", "wss:"), client, managedRooms=true)
            repo.initializeSafety(context)
            action(server, repo)
        }
    }
    @Test fun creatorSecretStaysOffTheInviteAndAuthorizesDeletion() = withServer { server, repo ->
        val room = "r-" + "c".repeat(48)
        val token = "d".repeat(64)
        var deletionAuthorized = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/config" -> MockResponse().setBody("{\"sharingAvailable\":true}")
                request.path == "/api/rooms" -> MockResponse().setResponseCode(201).setBody(JSONObject().put("roomId", room).put("ownerToken", token).toString())
                request.path == "/api/rooms/$room/reports" -> MockResponse().setResponseCode(201).setBody("{\"reportId\":\"saved-report\"}")
                request.method == "DELETE" && request.path == "/api/rooms/$room" -> {
                    deletionAuthorized = request.getHeader("Authorization") == "Bearer $token"
                    MockResponse().setBody("{\"deleted\":true}")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        try {
            runBlocking {
                assertEquals(room, repo.createRoom())
                assertTrue(repo.canEndRoom(room))
                assertFalse(room.contains(token))
                assertEquals("saved-report", repo.reportRoom(room, "bullying"))
                repo.endRoom(room)
                assertTrue(deletionAuthorized)
                assertFalse(repo.canEndRoom(room))
            }
        } finally { SharingSafetyStore(context).forgetOwner(room) }
    }
    @Test fun unapprovedServerPreventsCreationAndBlockedInvitesDoNotMakeAnyRequest() = withServer { server, repo ->
        server.enqueue(MockResponse().setBody("{\"sharingAvailable\":false}"))
        runBlocking {
            assertTrue(runCatching { repo.createRoom() }.isFailure)
            assertEquals(1, server.requestCount)
            repo.blockRoom("blocked-api-room")
            assertTrue(runCatching { repo.joinRoom("blocked-api-room") }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
