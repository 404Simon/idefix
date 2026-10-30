package dev.idefix.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSlugTest {

    @Test
    fun replacesWhitespaceWithUnderscores() {
        assertEquals("Mein_neuer_Zettel", NoteSlug.slugify("Mein neuer Zettel"))
    }

    @Test
    fun transliteratesGermanUmlauts() {
        assertEquals("Ueber_Groesse_Aerger", NoteSlug.slugify("Über Größe Ärger"))
    }

    @Test
    fun collapsesRunsOfWhitespaceAndSeparators() {
        assertEquals("a_b", NoteSlug.slugify("  a   b  "))
        assertEquals("a_b", NoteSlug.slugify("a / b"))
    }

    @Test
    fun stripsPathSeparatorsAndUnsafeCharacters() {
        assertEquals("Notes_Plan", NoteSlug.slugify("Notes/Plan"))
        assertEquals("a_b_c", NoteSlug.slugify("a:b?c"))
    }

    @Test
    fun keepsMarkdownExtensionOutOfTheSlug() {
        assertEquals("Plan", NoteSlug.slugify("Plan.md"))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", NoteSlug.slugify("   "))
        assertEquals("", NoteSlug.slugify("___"))
    }
}
