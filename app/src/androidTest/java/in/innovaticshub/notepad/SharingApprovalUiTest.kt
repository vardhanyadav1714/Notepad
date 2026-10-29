package `in`.innovaticshub.notepad

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.collab.SharingSafetyStore
import `in`.innovaticshub.notepad.ui.canvas.screens.SharingApprovalDialog
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SharingApprovalUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recognitionApprovalRequiresPinAndExplicitAcceptanceWithoutEnablingSharing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SharingSafetyStore(context)
        store.configure("483927")
        var approved = false
        val open = mutableStateOf(true)
        try {
            compose.setContent { NotepadTheme { if(open.value) SharingApprovalDialog(forRecognition=true,onDismiss={open.value=false},onApproved={approved=true;open.value=false}) } }
            compose.onNodeWithText("Approve this session").assertIsNotEnabled()
            compose.onNodeWithText("Family PIN").performScrollTo().performTextInput("000000")
            compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
            compose.onNodeWithText("Approve this session").performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("PIN not accepted", substring=true)).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(approved)
            compose.onNodeWithText("Family PIN").performScrollTo().performTextReplacement("483927")
            compose.onNodeWithText("Approve this session").performClick()
            compose.waitUntil(10000) { approved }
            assertFalse(store.enabled)
        } finally { store.resetPin() }
    }
    @Test fun cancelDoesNotApproveOrEnableSharing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SharingSafetyStore(context)
        store.resetPin()
        var approved = false
        var dismissed = false
        compose.setContent { NotepadTheme { SharingApprovalDialog(onDismiss={dismissed=true},onApproved={approved=true}) } }
        compose.onNodeWithText("Keep drawing offline").performClick()
        assertTrue(dismissed)
        assertFalse(approved)
        assertFalse(store.enabled)
    }
}
