package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.LectureEntry
import dev.idefix.app.domain.model.LectureInfo
import kotlinx.coroutines.flow.StateFlow

interface LectureRepository {
    val lectureInfo: StateFlow<LectureInfo?>

    /** Validate an already-granted tree URI and make it the active lectures folder. */
    suspend fun restoreLecture(uri: String, displayName: String?): LectureInfo?

    /** Persist a URI returned by ACTION_OPEN_DOCUMENT_TREE and validate it. */
    suspend fun adoptLecture(uri: String, displayName: String?): LectureInfo

    suspend fun clearLecture()

    suspend fun validateAccess(): Boolean

    /** Every top-level subfolder is one lecture. */
    suspend fun listLectures(): List<LectureEntry>

    /** List folders and files directly inside [folder]. */
    suspend fun listChildren(folder: LectureEntry): List<LectureEntry>

    suspend fun readText(file: LectureEntry): String
}
