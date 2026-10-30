package dev.idefix.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.idefix.app.data.database.FileEntity
import dev.idefix.app.data.database.IdefixDatabase
import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_END
import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_START
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FtsDatabaseTest {

    private lateinit var db: IdefixDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TestDatabase.create(context)
    }

    @After
    fun tearDown() {
        db.close()
        TestDatabase.cleanup(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun fts5IsAvailableAndReturnsRankedSnippets() = runBlocking {
        val file = insertFile("notes/Walrus.md", "Walrus.md")
        db.ftsDao().insert(
            file,
            "Walrus.md",
            "notes/Walrus.md",
            "The walrus dances.\nA second walrus appears.\nUnrelated text.",
        )
        val hits = db.ftsDao().search("\"walrus\"", 10)
        assertEquals(1, hits.size)
        assertEquals(file, hits.single().fileId)
        assertTrue(hits.single().snippet.contains(SNIPPET_HIGHLIGHT_START))
        assertTrue(hits.single().snippet.contains(SNIPPET_HIGHLIGHT_END))
    }

    @Test
    fun multipleTermsAndPhrasesWork() = runBlocking {
        val file = insertFile("a.md", "a.md")
        db.ftsDao().insert(file, "a.md", "a.md", "alpha beta gamma\nbeta alpha")
        assertEquals(1, db.ftsDao().search("\"alpha\" \"beta\"", 10).size)
        assertEquals(1, db.ftsDao().search("\"alpha beta\"", 10).size)
        assertEquals(0, db.ftsDao().search("\"alpha delta\"", 2).size)
    }

    @Test
    fun updateReplacesOldRow() = runBlocking {
        val file = insertFile("a.md", "a.md")
        db.ftsDao().insert(file, "a.md", "a.md", "old text")
        db.ftsDao().delete(file)
        db.ftsDao().insert(file, "a.md", "a.md", "new text")
        assertEquals(0, db.ftsDao().search("\"old\"", 10).size)
        assertEquals(1, db.ftsDao().search("\"new\"", 10).size)
        assertEquals("new text", db.ftsDao().contentFor(file))
    }

    @Test
    fun contentPagingReturnsFilesAndUris() = runBlocking {
        val first = insertFile("one.md", "one.md")
        val second = insertFile("two.md", "two.md")
        db.ftsDao().insert(first, "one.md", "one.md", "first")
        db.ftsDao().insert(second, "two.md", "two.md", "second")
        val page = db.ftsDao().contentPage(0, 10)
        assertEquals(2, page.size)
        assertTrue(page.all { it.uri.startsWith("content://test/") })
        assertEquals("first", page.first().content)
    }

    @Test
    fun deletingFromFtsRemovesSearchHits() = runBlocking {
        val file = insertFile("a.md", "a.md")
        db.ftsDao().insert(file, "a.md", "a.md", "find me")
        assertEquals(1, db.ftsDao().search("\"find\"", 10).size)
        db.ftsDao().delete(file)
        assertEquals(0, db.ftsDao().search("\"find\"", 10).size)
    }

    @Test
    fun unicodeContentIsSearchable() = runBlocking {
        val file = insertFile("u.md", "Überblick.md")
        db.ftsDao().insert(file, "Überblick.md", "u.md", "Der Überblick über die Straße")
        assertEquals(1, db.ftsDao().search("\"überblick\"", 10).size)
    }

    private suspend fun insertFile(path: String, name: String): Long {
        val id = db.fileDao().insert(
            FileEntity(
                documentUri = "content://test/${path.hashCode()}",
                documentId = path,
                relativePath = path,
                filename = name,
                lastModified = 1L,
                size = 10L,
                fingerprint = "hash-$path",
                indexStatus = "ok",
            ),
        )
        assertTrue(id > 0)
        assertNotNull(db.fileDao().getById(id))
        return id
    }
}
