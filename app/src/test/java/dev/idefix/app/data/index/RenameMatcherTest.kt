package dev.idefix.app.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenameMatcherTest {

    @Test
    fun uniqueFingerprintIsDetectedAsRename() {
        val stale = listOf(RenameMatcher.Candidate(1, "old.md", "uri-old", "hash", 10))
        val fresh = listOf(RenameMatcher.Candidate(2, "new.md", "uri-new", "hash", 10))
        val result = RenameMatcher.detectRenames(stale, fresh)
        assertEquals(1, result.size)
        assertEquals(1, result.single().oldId)
        assertEquals(2, result.single().newId)
        assertEquals("new.md", result.single().relativePath)
    }

    @Test
    fun ambiguousFingerprintsAreNotMerged() {
        val stale = listOf(
            RenameMatcher.Candidate(1, "a.md", "uri-a", "same", 5),
            RenameMatcher.Candidate(2, "b.md", "uri-b", "same", 5),
        )
        val fresh = listOf(RenameMatcher.Candidate(3, "c.md", "uri-c", "same", 5))
        assertTrue(RenameMatcher.detectRenames(stale, fresh).isEmpty())
    }

    @Test
    fun differentSizeBreaksTheMatch() {
        val stale = listOf(RenameMatcher.Candidate(1, "a.md", "uri-a", "hash", 5))
        val fresh = listOf(RenameMatcher.Candidate(2, "b.md", "uri-b", "hash", 6))
        assertTrue(RenameMatcher.detectRenames(stale, fresh).isEmpty())
    }

    @Test
    fun sameIdIsIgnored() {
        val stale = listOf(RenameMatcher.Candidate(1, "a.md", "uri-a", "hash", 5))
        val fresh = listOf(RenameMatcher.Candidate(1, "a.md", "uri-a", "hash", 5))
        assertTrue(RenameMatcher.detectRenames(stale, fresh).isEmpty())
    }

    @Test
    fun noCandidatesNoRenames() {
        assertTrue(RenameMatcher.detectRenames(emptyList(), emptyList()).isEmpty())
    }
}
