package `in`.innovaticshub.notepad

import `in`.innovaticshub.notepad.ui.canvas.zoom.PageEndPull
import org.junit.Assert.*
import org.junit.Test

class PageEndPullTest {
    @Test fun ordinaryScrollingDoesNotGrowTheDocument() {
        val pull=PageEndPull(72f,1f)
        assertFalse(pull.update(-200f,1800f,1000f,1f))
        assertFalse(pull.update(-200f,1600f,1000f,1f))
    }
    @Test fun accumulatedUpwardPullAtTheEndCreatesOnePage() {
        val pull=PageEndPull(72f,1f)
        assertFalse(pull.update(-40f,1000f,1000f,1f))
        assertTrue(pull.update(-40f,1000f,1000f,1f))
        assertFalse(pull.update(-900f,1000f,1000f,1f))
    }
    @Test fun reversingDirectionUnwindsTheEndPullWithoutLosingSmallMovements() {
        val pull=PageEndPull(72f,1f)
        assertFalse(pull.update(-60f,1000f,1000f,1f));assertFalse(pull.update(20f,1000f,1000f,1f))
        assertTrue(pull.update(-60f,1000f,1000f,1f))
        val reversed=PageEndPull(72f,1f)
        reversed.update(-60f,1000f,1000f,1f);reversed.update(80f,1000f,1000f,1f)
        assertFalse(reversed.update(-60f,1000f,1000f,1f))
    }
    @Test fun pinchingCannotCreateAPageEvenAfterScaleReturnsToItsStart() {
        val pull=PageEndPull(72f,1f)
        assertFalse(pull.update(-200f,900f,1000f,0.75f));assertFalse(pull.update(-200f,900f,1000f,1f))
        val clamped=PageEndPull(72f,0.75f)
        assertFalse(clamped.update(-200f,900f,1000f,0.75f,zoomAttempt=true))
        val latePinch=PageEndPull(72f,1f)
        assertTrue(latePinch.update(-100f,1000f,1000f,1f));assertTrue(latePinch.canCommit)
        latePinch.update(-20f,900f,1000f,1f,zoomAttempt=true);assertFalse(latePinch.canCommit)
    }
    @Test fun existingBlankSpaceDoesNotCountAsAPull() {
        val pull=PageEndPull(72f,1f)
        assertFalse(pull.update(-1f,200f,1000f,1f));assertFalse(pull.update(-1f,200f,1000f,1f))
    }
    @Test fun pullingBackBeforeLiftCancelsTheNewPage() {
        val pull=PageEndPull(72f,1f)
        assertTrue(pull.update(-100f,1000f,1000f,1f));assertTrue(pull.canCommit)
        pull.update(80f,900f,1000f,1f);assertFalse(pull.canCommit)
    }
}
