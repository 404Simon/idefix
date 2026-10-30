package dev.idefix.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinesTest {

    @Test
    fun splitsUnixLines() {
        val lines = MarkdownLines.split("one\ntwo\n")
        assertEquals(2, lines.size)
        assertEquals("one", lines[0].text)
        assertEquals("two", lines[1].text)
    }

    @Test
    fun splitsWindowsLines() {
        val lines = MarkdownLines.split("one\r\ntwo\r\nthree")
        assertEquals(3, lines.size)
        assertEquals("one", lines[0].text)
        assertEquals("three", lines[2].text)
    }

    @Test
    fun detectsNewlineConvention() {
        assertEquals("\r\n", MarkdownLines.detectNewline("a\r\nb"))
        assertEquals("\n", MarkdownLines.detectNewline("a\nb"))
        assertEquals("\n", MarkdownLines.detectNewline("single"))
    }

    @Test
    fun offsetsAndLinesRoundTrip() {
        val text = "first\nsecond\r\nthird"
        val offset = MarkdownLines.offsetForLine(text, 2)
        assertEquals(6, offset)
        assertEquals(2, MarkdownLines.lineNumberForOffset(text, offset))
        assertEquals("second", MarkdownLines.lineText(text, 2))
    }

    @Test
    fun offsetBeyondEndClampsToEnd() {
        val text = "one\ntwo"
        assertEquals(text.length, MarkdownLines.offsetForLine(text, 99))
    }

    @Test
    fun findsFirstLineContainingNeedle() {
        val text = "# Title\n\nAlpha beta\nGamma alpha"
        assertEquals(3, MarkdownLines.findLine(text, "alpha"))
        assertEquals(4, MarkdownLines.findLine(text, "alpha", ignoreCase = false))
        assertNull(MarkdownLines.findLine(text, "missing"))
    }

    @Test
    fun lineCountHandlesEmpty() {
        assertEquals(1, MarkdownLines.lineCount(""))
        assertEquals(2, MarkdownLines.lineCount("a\nb"))
    }

    @Test
    fun ensureTrailingNewlineRespectsConvention() {
        assertTrue(MarkdownLines.ensureTrailingNewline("a\r\nb").endsWith("b\r\n"))
        assertTrue(MarkdownLines.ensureTrailingNewline("a\nb\n").endsWith("b\n"))
    }
}
