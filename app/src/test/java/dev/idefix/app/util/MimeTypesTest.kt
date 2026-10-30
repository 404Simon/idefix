package dev.idefix.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypesTest {

    @Test
    fun detectsMarkdownExtensions() {
        assertTrue(MimeTypes.isMarkdownName("Note.md"))
        assertTrue(MimeTypes.isMarkdownName("NOTE.MD"))
        assertTrue(MimeTypes.isMarkdownName("note.markdown"))
        assertFalse(MimeTypes.isMarkdownName("note.txt"))
        assertFalse(MimeTypes.isMarkdownName("md"))
    }

    @Test
    fun normalisesCreatedNames() {
        assertEquals("Note.md", MimeTypes.normalizeMarkdownName("Note"))
        assertEquals("Note.md", MimeTypes.normalizeMarkdownName("Note.md"))
        assertEquals("Untitled.md", MimeTypes.normalizeMarkdownName("  "))
    }

    @Test
    fun hiddenAndTransientNames() {
        assertTrue(MimeTypes.isHiddenName(".obsidian"))
        assertTrue(MimeTypes.isHiddenName(".trash"))
        assertTrue(MimeTypes.isHiddenName("note.md~"))
        assertFalse(MimeTypes.isHiddenName("normal.md"))
    }

    @Test
    fun stripsKnownExtensions() {
        assertEquals("Note", MimeTypes.displayNameWithoutExtension("Note.md"))
        assertEquals("Note", MimeTypes.displayNameWithoutExtension("Note.markdown"))
    }
}
