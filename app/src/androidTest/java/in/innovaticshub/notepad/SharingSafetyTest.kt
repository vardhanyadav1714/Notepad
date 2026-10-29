package `in`.innovaticshub.notepad

import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.collab.SharingSafetyStore
import `in`.innovaticshub.notepad.ui.canvas.storage.SavedSession
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import org.junit.Assert.*
import org.junit.Test

class SharingSafetyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    @Test fun handwritingSdkDoesNotHaveAnAutomaticStartupProvider() {
        val info = instrumentation.targetContext.packageManager.getPackageInfo(instrumentation.targetContext.packageName, android.content.pm.PackageManager.GET_PROVIDERS)
        assertFalse(info.providers.orEmpty().any { it.name == "com.google.mlkit.common.internal.MlKitInitProvider" })
    }

    @Test fun savedRoomsAndInvitesNeverConnectWithoutAdultApproval() {
        CollaborationNetworkTest.Relay().use { relay ->
            val model = DrawingViewModel(relay.repository())
            main {
                model.restoreSession(SavedSession(emptyList(), null, null, collaborationRoom = "saved-room"))
                model.joinCollaborationRoom("saved-room")
                assertFalse(model.uiState.value.sharingApproved)
                assertFalse(model.uiState.value.isCollaborationBusy)
                model.offerCollaborationInvite("incoming-invite")
                assertEquals("incoming-invite", model.uiState.value.collaborationRoomCode)
                assertFalse(model.uiState.value.collaborationConnected)
                model.createCollaborationRoom()
                assertFalse(model.uiState.value.isCollaborationBusy)
            }
            assertTrue(relay.sockets.isEmpty())
        }
    }

    @Test fun pausingAConnectedSessionRequiresFreshApprovalAndStopsReconnect() {
        CollaborationNetworkTest.Relay().use { relay ->
            val model = DrawingViewModel(relay.repository())
            try {
                main { model.approveSharingForSession(); model.createCollaborationRoom() }
                val deadline = System.currentTimeMillis() + 15_000
                while (!model.uiState.value.collaborationConnected && System.currentTimeMillis() < deadline) Thread.sleep(25)
                assertTrue(model.uiState.value.collaborationConnected)
                val room = model.uiState.value.collaborationRoomCode!!
                main {
                    model.pauseCollaboration()
                    assertFalse(model.uiState.value.sharingApproved)
                    assertFalse(model.uiState.value.collaborationConnected)
                    assertEquals(room, model.uiState.value.collaborationRoomCode)
                    model.joinCollaborationRoom(room)
                    assertFalse(model.uiState.value.isCollaborationBusy)
                }
            } finally { main { model.leaveCollaborationRoom() } }
        }
    }

    @Test fun pinControlsStartOffRejectWrongPinsAndPersistBlockedRooms() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences(SharingSafetyStore.FILE, 0)
        // Preview test package only: never touch another app's preferences.
        val previous = preferences.all
        try {
            assertTrue(preferences.edit().clear().commit())
            val store = SharingSafetyStore(context)
            assertFalse(store.enabled)
            assertFalse(store.configured)
            store.configure("483927")
            assertFalse(store.enabled)
            assertFalse(store.verify("000000"))
            assertTrue(store.verify("483927"))
            store.setEnabled(true)
            store.block("blocked-test-room")
            val reopened = SharingSafetyStore(context)
            assertTrue(reopened.enabled)
            assertTrue(reopened.isBlocked("blocked-test-room"))
            assertFalse(reopened.isBlocked("another-room"))
            assertFalse(preferences.all.values.any { it == "483927" })
            repeat(3) { assertFalse(reopened.verify("000000")) }
            assertFalse(reopened.verify("483927")) // cooldown cannot be bypassed with the correct PIN
            reopened.setEnabled(false)
            assertFalse(reopened.enabled)
        } finally {
            val editor = preferences.edit().clear()
            previous.forEach { (key, value) -> when(value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            assertTrue(editor.commit())
        }
    }
}
