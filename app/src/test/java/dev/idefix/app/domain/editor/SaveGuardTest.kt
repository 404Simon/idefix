package dev.idefix.app.domain.editor

import dev.idefix.app.util.Hashing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveGuardTest {

    @Test
    fun unchangedContentIsClean() {
        val content = "# Note\n\nBody\n"
        assertTrue(SaveGuard.check(Hashing.sha256(content), content) is SaveCheck.Clean)
    }

    @Test
    fun externalChangeIsDetected() {
        val baseline = "# Note\n\nBody\n"
        val changed = "# Note\n\nChanged elsewhere\n"
        val result = SaveGuard.check(Hashing.sha256(baseline), changed)
        assertTrue(result is SaveCheck.ExternalChange)
        assertEquals(changed, (result as SaveCheck.ExternalChange).currentContent)
    }

    @Test
    fun newlineOnlyChangeIsDetected() {
        val baseline = "a\nb\n"
        val changed = "a\r\nb\r\n"
        assertTrue(SaveGuard.check(Hashing.sha256(baseline), changed) is SaveCheck.ExternalChange)
    }
}
