package `in`.innovaticshub.notepad

import `in`.innovaticshub.notepad.ui.canvas.zoom.NavigationIntent
import org.junit.Assert.*
import org.junit.Test

class NavigationIntentTest {
    @Test fun unevenParallelFingersRemainAScroll() {
        val intent=NavigationIntent(16f)
        repeat(40) {assertFalse(intent.update(1.2f,18.75f))}
    }
    @Test fun stationaryPinchIsRecognizedAndStaysRecognized() {
        val intent=NavigationIntent(16f)
        repeat(10) {intent.update(-8f,0f)}
        assertTrue(intent.zooming);assertTrue(intent.update(0f,100f))
    }
    @Test fun pinchCanBeginAfterALongScroll() {
        val intent=NavigationIntent(16f)
        repeat(40) {intent.update(0f,20f)}
        repeat(10) {intent.update(-8f,0f)}
        assertTrue(intent.zooming)
    }
    @Test fun simultaneousTranslationAndPinchStillZooms() {
        val intent=NavigationIntent(16f)
        repeat(10) {intent.update(12f,10f)}
        assertTrue(intent.zooming)
    }
}
