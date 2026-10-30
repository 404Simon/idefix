package dev.idefix.app.domain.search

import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_END
import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_START
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSearchEngineTest {

    private fun doc(
        id: Long = 1,
        path: String = "notes/Test.md",
        content: String,
    ) = ContentDocument(
        fileId = id,
        uri = "content://test/$id",
        relativePath = path,
        filename = path.substringAfterLast('/'),
        content = content,
    )

    @Test
    fun literalSearchFindsAllLines() = runTest {
        val document = doc(content = "alpha beta\ngamma\nALPHA again\ndelta alpha")
        val outcome = ContentSearchEngine.literal(listOf(document), "alpha", caseSensitive = false)
        assertEquals(3, outcome.hits.size)
        assertEquals(listOf(1, 3, 4), outcome.hits.map { it.lineNumber })
        assertTrue(outcome.hits.first().snippet.contains(SNIPPET_HIGHLIGHT_START))
        assertTrue(outcome.hits.first().snippet.contains(SNIPPET_HIGHLIGHT_END))
    }

    @Test
    fun literalSearchRespectsCaseSensitivity() = runTest {
        val document = doc(content = "Alpha\nalpha")
        val insensitive = ContentSearchEngine.literal(listOf(document), "alpha", caseSensitive = false)
        val sensitive = ContentSearchEngine.literal(listOf(document), "alpha", caseSensitive = true)
        assertEquals(2, insensitive.hits.size)
        assertEquals(1, sensitive.hits.size)
        assertEquals(2, sensitive.hits.first().lineNumber)
    }

    @Test
    fun literalEmptyQueryFails() = runTest {
        assertNotNull(ContentSearchEngine.literal(listOf(doc(content = "x")), "", caseSensitive = false).error)
    }

    @Test
    fun regexReturnsLineNumbers() = runTest {
        val document = doc(content = "Task 12\nnothing\nTask 345\nTask abc")
        val outcome = ContentSearchEngine.regex(listOf(document), "Task \\d+", caseSensitive = false)
        assertEquals(2, outcome.hits.size)
        assertEquals(listOf(1, 3), outcome.hits.map { it.lineNumber })
    }

    @Test
    fun invalidRegexReportsError() = runTest {
        val outcome = ContentSearchEngine.regex(listOf(doc(content = "x")), "[unclosed", caseSensitive = false)
        assertNotNull(outcome.error)
        assertTrue(outcome.error!!.contains("Invalid regex"))
    }

    @Test
    fun regexCaseSensitivity() = runTest {
        val document = doc(content = "Alpha\nalpha")
        val insensitive = ContentSearchEngine.regex(listOf(document), "alpha", caseSensitive = false)
        val sensitive = ContentSearchEngine.regex(listOf(document), "alpha", caseSensitive = true)
        assertEquals(2, insensitive.hits.size)
        assertEquals(1, sensitive.hits.size)
    }

    @Test
    fun multipleFilesAreSearched() = runTest {
        val docs = listOf(
            doc(1, "a.md", "needle"),
            doc(2, "b.md", "nothing"),
            doc(3, "c.md", "a needle here"),
        )
        val outcome = ContentSearchEngine.literal(docs, "needle", caseSensitive = false)
        assertEquals(2, outcome.hits.size)
        assertEquals(setOf(1L, 3L), outcome.hits.map { it.document.fileId }.toSet())
    }

    @Test
    fun limitBoundsResults() = runTest {
        val content = (1..50).joinToString("\n") { "match $it" }
        val outcome = ContentSearchEngine.literal(listOf(doc(content = content)), "match", caseSensitive = false, limit = 3)
        assertEquals(3, outcome.hits.size)
    }

    @Test
    fun cancellationStopsWork() = runTest {
        val huge = (1..20_000).joinToString("\n") { "line $it with some text" }
        val docs = (1..100L).map { doc(it, "n$it.md", huge) }
        val job = launch(Dispatchers.Default) {
            ContentSearchEngine.literal(docs, "zzz-not-present", caseSensitive = false)
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun missingMatchReturnsEmpty() = runTest {
        val outcome = ContentSearchEngine.literal(listOf(doc(content = "hello")), "world", caseSensitive = false)
        assertTrue(outcome.hits.isEmpty())
        assertNull(outcome.error)
    }
}
