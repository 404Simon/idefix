package dev.idefix.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LectureKindsTest {

    @Test
    fun pdfsAreDetectedByExtensionOrMimeType() {
        assertEquals(LectureEntryKind.PDF, LectureKinds.classify("Chapter 01.pdf"))
        assertEquals(LectureEntryKind.PDF, LectureKinds.classify("slides", "application/pdf"))
    }

    @Test
    fun codeAndTextFilesUseTheBuiltInViewer() {
        assertTrue(LectureKinds.classify("solution.kt").isText)
        assertTrue(LectureKinds.classify("notes.md").isText)
        assertTrue(LectureKinds.classify("README", "text/plain").isText)
        assertEquals(LectureEntryKind.CODE, LectureKinds.classify("Makefile"))
        assertTrue(LectureKinds.classify("script.sh").isText)
    }

    @Test
    fun otherFilesAreOpenedExternally() {
        assertEquals(LectureEntryKind.IMAGE, LectureKinds.classify("diagram.png"))
        assertEquals(LectureEntryKind.ARCHIVE, LectureKinds.classify("exercises.zip"))
        assertEquals(LectureEntryKind.OTHER, LectureKinds.classify("data.bin"))
    }
}
