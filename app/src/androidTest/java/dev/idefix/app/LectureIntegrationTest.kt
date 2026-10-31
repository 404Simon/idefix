package dev.idefix.app

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.idefix.app.data.lectures.SafLectureRepository
import dev.idefix.app.domain.model.LectureEntryKind
import dev.idefix.app.testing.TestDocumentsProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LectureIntegrationTest {

    private lateinit var context: Context
    private lateinit var lectureDir: File
    private lateinit var repository: SafLectureRepository

    @Before
    fun setUp() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        TestDocumentsProvider.reset(context)
        lectureDir = TestDocumentsProvider.vaultDir(context)
        seedLectures()
        repository = SafLectureRepository(context, FakeSettingsRepository())
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            TestDocumentsProvider.AUTHORITY,
            TestDocumentsProvider.ROOT_ID,
        )
        repository.adoptLecture(treeUri.toString(), "Vorlesungen")
        Unit
    }

    @After
    fun tearDown() {
        if (::lectureDir.isInitialized) lectureDir.deleteRecursively()
    }

    @Test
    fun topLevelFoldersBecomeLecturesAndFilesKeepTheirKind() = runBlocking {
        val lectures = repository.listLectures()
        assertEquals(listOf("Analysis 1"), lectures.map { it.name })
        assertEquals(LectureEntryKind.DIRECTORY, lectures.single().kind)

        val children = repository.listChildren(lectures.single())
        val pdf = children.single { it.name == "Sheet 01.pdf" }
        assertEquals(LectureEntryKind.PDF, pdf.kind)
        assertTrue(children.none { it.name == ".stfolder" })

        val codeFolder = children.single { it.name == "code" }
        val code = repository.listChildren(codeFolder).single { it.name == "solution.kt" }
        assertEquals(LectureEntryKind.CODE, code.kind)
        assertEquals("fun main() = Unit\n", repository.readText(code))
    }

    private fun seedLectures() {
        File(lectureDir, "Analysis 1/code").mkdirs()
        File(lectureDir, "Analysis 1/Sheet 01.pdf").writeText("%PDF-1.4 fake")
        File(lectureDir, "Analysis 1/code/solution.kt").writeText("fun main() = Unit\n")
        File(lectureDir, ".stfolder").mkdirs()
        File(lectureDir, "loose-note.md").writeText("root files are not lectures")
    }
}
