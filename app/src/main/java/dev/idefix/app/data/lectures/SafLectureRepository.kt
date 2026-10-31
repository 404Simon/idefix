package dev.idefix.app.data.lectures

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.idefix.app.domain.model.LectureEntry
import dev.idefix.app.domain.model.LectureEntryKind
import dev.idefix.app.domain.model.LectureInfo
import dev.idefix.app.domain.model.LectureKinds
import dev.idefix.app.domain.repository.LectureRepository
import dev.idefix.app.domain.repository.SettingsRepository
import dev.idefix.app.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LectureAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SafLectureRepository(
    context: Context,
    private val settings: SettingsRepository,
) : LectureRepository {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val _lectureInfo = MutableStateFlow<LectureInfo?>(null)
    override val lectureInfo: StateFlow<LectureInfo?> = _lectureInfo.asStateFlow()

    @Volatile
    private var treeUri: Uri? = null

    @Volatile
    private var rootDocumentId: String? = null

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_SIZE,
    )

    override suspend fun restoreLecture(uri: String, displayName: String?): LectureInfo? =
        withContext(Dispatchers.IO) {
            val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
            if (parsed.scheme != ContentResolver.SCHEME_CONTENT) return@withContext null
            tryTakePermission(parsed)
            val info = readLectureInfo(parsed, displayName) ?: return@withContext null
            if (!info.readable) return@withContext null
            treeUri = parsed
            rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(parsed) }.getOrNull()
            if (rootDocumentId == null) return@withContext null
            _lectureInfo.value = info
            settings.setLecture(parsed.toString(), info.displayName)
            info
        }

    override suspend fun adoptLecture(uri: String, displayName: String?): LectureInfo =
        withContext(Dispatchers.IO) {
            val parsed = Uri.parse(uri)
            tryTakePermission(parsed)
            val info = readLectureInfo(parsed, displayName)
                ?: throw LectureAccessException("The selected lectures folder cannot be read")
            treeUri = parsed
            rootDocumentId = DocumentsContract.getTreeDocumentId(parsed)
            _lectureInfo.value = info
            settings.setLecture(parsed.toString(), info.displayName)
            info
        }

    override suspend fun clearLecture() = withContext(Dispatchers.IO) {
        val current = treeUri
        if (current != null) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    current,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        treeUri = null
        rootDocumentId = null
        _lectureInfo.value = null
        settings.clearLecture()
    }

    override suspend fun validateAccess(): Boolean = withContext(Dispatchers.IO) {
        val tree = treeUri ?: return@withContext false
        val id = rootDocumentId ?: return@withContext false
        runCatching {
            resolver.query(childrenUri(tree, id), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { true } ?: false
        }.getOrDefault(false)
    }

    override suspend fun listLectures(): List<LectureEntry> = withContext(Dispatchers.IO) {
        val tree = ensureTree()
        children(tree, ensureRootId(tree), "").filter { it.isDirectory }
    }

    override suspend fun listChildren(folder: LectureEntry): List<LectureEntry> = withContext(Dispatchers.IO) {
        if (!folder.isDirectory) return@withContext emptyList()
        children(ensureTree(), folder.documentId, folder.relativePath)
    }

    override suspend fun readText(file: LectureEntry): String = withContext(Dispatchers.IO) {
        if (file.isDirectory) throw LectureAccessException("${file.name} is a folder")
        try {
            val bytes = resolver.openInputStream(file.uriObject)?.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_TEXT_BYTES) {
                        throw LectureAccessException(
                            "${file.name} is larger than ${MAX_TEXT_BYTES / (1024 * 1024)} MB. Open it in another app.",
                        )
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: throw LectureAccessException("The provider returned no data for ${file.name}")
            if (bytes.take(8 * 1024).any { it == 0.toByte() }) {
                throw LectureAccessException("${file.name} does not look like a text file")
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: LectureAccessException) {
            throw e
        } catch (e: SecurityException) {
            throw LectureAccessException("Access to ${file.name} was revoked", e)
        } catch (e: Exception) {
            throw LectureAccessException("Could not read ${file.name}: ${e.message}", e)
        }
    }

    private fun children(tree: Uri, parentId: String, parentPath: String): List<LectureEntry> {
        val cursor = try {
            resolver.query(childrenUri(tree, parentId), projection, null, null, null)
        } catch (e: SecurityException) {
            throw LectureAccessException("Permission for the lectures folder was revoked", e)
        } catch (e: Exception) {
            throw LectureAccessException("Could not list ${parentPath.ifBlank { "the lectures folder" }}: ${e.message}", e)
        } ?: throw LectureAccessException("The provider returned no folder listing for ${parentPath.ifBlank { "the lectures folder" }}")

        val entries = ArrayList<LectureEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getStringOrNull(0) ?: continue
                val name = it.getStringOrNull(1) ?: continue
                if (MimeTypes.isHiddenName(name)) continue
                val mime = it.getStringOrNull(2)
                val isDirectory = mime == Document.MIME_TYPE_DIR
                val relativePath = if (parentPath.isBlank()) name else "$parentPath/$name"
                entries += LectureEntry(
                    documentId = id,
                    name = name,
                    relativePath = relativePath,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                    isDirectory = isDirectory,
                    mimeType = mime,
                    lastModified = it.getLongOrNull(3),
                    size = it.getLongOrNull(4),
                    kind = if (isDirectory) {
                        LectureEntryKind.DIRECTORY
                    } else {
                        LectureKinds.classify(name, mime)
                    },
                )
            }
        }
        return entries.sortedWith(
            compareBy<LectureEntry> { if (it.isDirectory) 0 else 1 }
                .thenBy { it.name.lowercase() },
        )
    }

    private fun tryTakePermission(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun readLectureInfo(uri: Uri, preferredName: String?): LectureInfo? {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val metadata = queryDocument(uri, rootId)
        if (metadata == null && preferredName == null) return null
        val name = metadata?.displayName?.takeIf { it.isNotBlank() } ?: preferredName ?: "Lectures"
        return LectureInfo(uri = uri.toString(), displayName = name, readable = metadata != null)
    }

    private fun ensureTree(): Uri = treeUri ?: throw LectureAccessException("No lectures folder selected")

    private fun ensureRootId(tree: Uri): String =
        rootDocumentId ?: runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?.also { rootDocumentId = it }
        ?: throw LectureAccessException("The lectures folder URI is invalid")

    private fun childrenUri(tree: Uri, documentId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)

    private fun queryDocument(tree: Uri, documentId: String): CursorMetadata? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        val cursor = try {
            resolver.query(uri, projection, null, null, null)
        } catch (_: Exception) {
            return null
        } ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return CursorMetadata(
                displayName = it.getStringOrNull(1) ?: return null,
            )
        }
    }

    private data class CursorMetadata(val displayName: String)

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    companion object {
        const val MAX_TEXT_BYTES = 8L * 1024 * 1024
    }
}
