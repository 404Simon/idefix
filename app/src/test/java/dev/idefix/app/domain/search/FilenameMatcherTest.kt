package dev.idefix.app.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameMatcherTest {

    @Test
    fun exactMatchRanksHighest() {
        val exact = FilenameMatcher.score("project plan", "Project Plan.md", "Project Plan.md")
        val prefix = FilenameMatcher.score("proj", "Project Plan.md", "Project Plan.md")
        val contains = FilenameMatcher.score("plan", "Project Plan.md", "Project Plan.md")
        assertTrue(exact > prefix)
        assertTrue(prefix > contains)
    }

    @Test
    fun matchesWithoutExtension() {
        val score = FilenameMatcher.score("daily note", "Daily Note.md", "journal/Daily Note.md")
        assertTrue(score >= FilenameMatcher.MIN_SCORE)
    }

    @Test
    fun matchesWithExtension() {
        val score = FilenameMatcher.score("Daily Note.md", "Daily Note.md", "journal/Daily Note.md")
        assertTrue(score >= FilenameMatcher.MIN_SCORE)
    }

    @Test
    fun fuzzyTypoFindsName() {
        val score = FilenameMatcher.score("proejct", "Project.md", "Project.md")
        assertTrue("Expected fuzzy match, score was $score", score >= FilenameMatcher.MIN_SCORE)
    }

    @Test
    fun subsequenceFindsAbbreviation() {
        val score = FilenameMatcher.score("pjm", "Project Management.md", "work/Project Management.md")
        assertTrue(score >= FilenameMatcher.MIN_SCORE)
    }

    @Test
    fun nameMatchOutranksPathOnlyMatch() {
        val name = FilenameMatcher.score("meeting", "Meeting notes.md", "work/Meeting notes.md")
        val path = FilenameMatcher.score("meeting", "Misc.md", "meeting/Misc.md")
        assertTrue(name > path)
    }

    @Test
    fun duplicateNamesBothMatch() {
        val first = FilenameMatcher.score("index", "index.md", "a/index.md")
        val second = FilenameMatcher.score("index", "index.md", "b/index.md")
        assertTrue(first >= FilenameMatcher.MIN_SCORE)
        assertEquals(first, second, 0.001)
    }

    @Test
    fun unicodeNamesMatchCaseInsensitively() {
        val score = FilenameMatcher.score("überblick", "Überblick.md", "Ideen/Überblick.md")
        assertTrue(score >= FilenameMatcher.MIN_SCORE)
    }

    @Test
    fun unrelatedNameIsRejected() {
        val score = FilenameMatcher.score("zzzqqq", "Project Plan.md", "Project Plan.md")
        assertTrue(score < FilenameMatcher.MIN_SCORE)
    }

    @Test
    fun levenshteinBasics() {
        assertEquals(0, FilenameMatcher.levenshtein("abc", "abc"))
        assertEquals(1, FilenameMatcher.levenshtein("abc", "abd"))
        assertEquals(3, FilenameMatcher.levenshtein("", "abc"))
        assertEquals(3, FilenameMatcher.levenshtein("kitten", "sitting"))
    }
}
