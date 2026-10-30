package dev.idefix.app.data.vault

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.idefix.app.domain.model.ScanProgress
import dev.idefix.app.domain.model.ScanResult
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.model.VaultInfo
import dev.idefix.app.domain.repository.SettingsRepository
import dev.idefix.app.domain.repository.VaultRepository
import dev.idefix.app.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VaultAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SafVaultRepository(
    context: Context,
    private val settings: SettingsRepository,
) : VaultRepository {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val _vaultInfo = MutableStateFlow<VaultInfo?>(null)
    override val vaultInfo: StateFlow<VaultInfo?> = _vaultInfo.asStateFlow()

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

    override suspend fun restoreVault(uri: String, displayName: String?): VaultInfo? =
        withContext(Dispatchers.IO) {
            val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
            if (parsed.scheme != ContentResolver.SCHEME_CONTENT) return@withContext null
            tryTakePermission(parsed)
            val info = readVaultInfo(parsed, displayName) ?: return@withContext null
            if (!info.readable) return@withContext null
            treeUri = parsed
            rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(parsed) }.getOrNull()
            if (rootDocumentId == null) return@withContext null
            _vaultInfo.value = info
            settings.setVault(parsed.toString(), info.displayName)
            info
        }

    override suspend fun adoptVault(uri: String, displayName: String?): VaultInfo =
        withContext(Dispatchers.IO) {
            val parsed = Uri.parse(uri)
            tryTakePermission(parsed)
            val info = readVaultInfo(parsed, displayName)
                ?: throw VaultAccessException("The selected folder cannot be read")
            treeUri = parsed
            rootDocumentId = DocumentsContract.getTreeDocumentId(parsed)
            _vaultInfo.value = info
            settings.setVault(parsed.toString(), info.displayName)
            info
        }

    override suspend fun clearVault() = withContext(Dispatchers.IO) {
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
        _vaultInfo.value = null
        settings.clearVault()
    }

    override suspend fun validateAccess(): Boolean = withContext(Dispatchers.IO) {
        val uri = treeUri ?: return@withContext false
        val id = rootDocumentId ?: return@withContext false
        runCatching {
            resolver.query(childrenUri(id), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { true } ?: false
        }.getOrDefault(false) && uri.scheme == ContentResolver.SCHEME_CONTENT
    }

    override suspend fun scanMarkdownFiles(
        includeHidden: Boolean,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val tree = ensureTree()
        val rootId = ensureRootId(tree)
        val files = ArrayList<VaultFile>()
        val failures = ArrayList<String>()
        val visited = HashSet<String>()

        suspend fun walk(documentId: String, path: String, depth: Int) {
            if (depth > 64) {
                failures += "Folder nesting limit reached at ${path.ifEmpty { "/" }}"
                return
            }
            if (!visited.add(documentId)) return
            currentCoroutineContext().ensureActive()
            val cursor = try {
                resolver.query(childrenUri(documentId), projection, null, null, null)
            } catch (e: SecurityException) {
                failures += "Permission denied: ${path.ifEmpty { "/" }}"
                return
            } catch (e: Exception) {
                failures += "Could not read ${path.ifEmpty { "/" }}: ${e.message}"
                return
            } ?: return
            cursor.use {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val childId = it.getStringOrNull(0) ?: continue
                    val name = it.getStringOrNull(1) ?: continue
                    val mime = it.getStringOrNull(2)
                    val modified = it.getLongOrNull(3)
                    val size = it.getLongOrNull(4)
                    if (!includeHidden && MimeTypes.isHiddenName(name)) continue
                    if (mime == Document.MIME_TYPE_DIR) {
                        val childPath = if (path.isEmpty()) name else "$path/$name"
                        walk(childId, childPath, depth + 1)
                    } else if (MimeTypes.isMarkdownName(name)) {
                        val relativePath = if (path.isEmpty()) name else "$path/$name"
                        files += VaultFile(
                            documentId = childId,
                            relativePath = relativePath,
                            displayName = name,
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId).toString(),
                            mimeType = mime,
                            lastModified = modified,
                            size = size,
                        )
                        onProgress(ScanProgress(files.size, relativePath))
                    }
                }
            }
        }

        walk(rootId, "", 0)
        ScanResult(files = files.sortedBy { it.relativePath.lowercase() }, failures = failures)
    }

    override suspend fun readText(file: VaultFile): String = withContext(Dispatchers.IO) {
        val uri = file.uriObject
        try {
            val bytes = resolver.openInputStream(uri)?.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_DOCUMENT_BYTES) {
                        throw VaultAccessException("File is larger than ${MAX_DOCUMENT_BYTES / (1024 * 1024)} MB")
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: throw VaultAccessException("The provider returned no data for ${file.displayName}")
            String(bytes, Charsets.UTF_8)
        } catch (e: VaultAccessException) {
            throw e
        } catch (e: SecurityException) {
            throw VaultAccessException("Access to ${file.displayName} was revoked", e)
        } catch (e: Exception) {
            throw VaultAccessException("Could not read ${file.displayName}: ${e.message}", e)
        }
    }

    override suspend fun writeText(file: VaultFile, content: String) = withContext(Dispatchers.IO) {
        val uri = file.uriObject
        try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            val stream = try {
                resolver.openOutputStream(uri, "wt")
            } catch (_: Exception) {
                resolver.openOutputStream(uri, "w")
            } ?: throw VaultAccessException("The provider cannot open ${file.displayName} for writing")
            stream.use {
                it.write(bytes)
                it.flush()
            }
        } catch (e: VaultAccessException) {
            throw e
        } catch (e: SecurityException) {
            throw VaultAccessException("Write permission for ${file.displayName} is missing", e)
        } catch (e: Exception) {
            throw VaultAccessException("Could not save ${file.displayName}: ${e.message}", e)
        }
    }

    override suspend fun createMarkdownFile(
        parentRelativePath: String?,
        displayName: String,
        content: String,
    ): VaultFile = withContext(Dispatchers.IO) {
        val tree = ensureTree()
        val parent = if (parentRelativePath.isNullOrBlank()) {
            VaultFile(
                documentId = ensureRootId(tree),
                relativePath = "",
                displayName = "",
                uri = DocumentsContract.buildDocumentUriUsingTree(tree, ensureRootId(tree)).toString(),
            )
        } else {
            resolve(parentRelativePath)
                ?: throw VaultAccessException("Folder not found: $parentRelativePath")
        }
        val desired = uniqueChildName(parent.documentId, MimeTypes.normalizeMarkdownName(displayName))
        val created = try {
            DocumentsContract.createDocument(
                resolver,
                parent.uriObject,
                MimeTypes.MARKDOWN,
                desired,
            )
        } catch (e: Exception) {
            throw VaultAccessException("Could not create $desired: ${e.message}", e)
        } ?: throw VaultAccessException("The provider refused to create $desired")

        val createdFile = fileFromUri(created, parent.relativePath)
            ?: throw VaultAccessException("Created file could not be read back")
        if (content.isNotEmpty()) {
            writeText(createdFile, content)
        }
        createdFile
    }

    override suspend fun renameFile(file: VaultFile, newDisplayName: String): VaultFile =
        withContext(Dispatchers.IO) {
            val target = MimeTypes.normalizeMarkdownName(newDisplayName)
            val renamed = try {
                DocumentsContract.renameDocument(resolver, file.uriObject, target)
            } catch (e: Exception) {
                throw VaultAccessException("Could not rename ${file.displayName}: ${e.message}", e)
            } ?: throw VaultAccessException("The provider does not support renaming this file")
            fileFromUri(renamed, file.parentPath)
                ?: throw VaultAccessException("Renamed file could not be read back")
        }

    override suspend fun deleteFile(file: VaultFile) = withContext(Dispatchers.IO) {
        try {
            if (!DocumentsContract.deleteDocument(resolver, file.uriObject)) {
                throw VaultAccessException("The provider refused to delete ${file.displayName}")
            }
        } catch (e: VaultAccessException) {
            throw e
        } catch (e: Exception) {
            throw VaultAccessException("Could not delete ${file.displayName}: ${e.message}", e)
        }
    }

    override suspend fun resolve(relativePath: String): VaultFile? = withContext(Dispatchers.IO) {
        val tree = treeUri ?: return@withContext null
        if (relativePath.isBlank()) return@withContext null
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        var currentId = ensureRootId(tree)
        var currentPath = ""
        var metadata: CursorMetadata? = null
        for ((index, segment) in segments.withIndex()) {
            currentCoroutineContext().ensureActive()
            val found = findChild(currentId, segment) ?: return@withContext null
            currentId = found.documentId
            currentPath = if (currentPath.isEmpty()) found.displayName else "$currentPath/${found.displayName}"
            metadata = found
            if (index < segments.lastIndex && found.mimeType != Document.MIME_TYPE_DIR) {
                return@withContext null
            }
        }
        val last = metadata ?: return@withContext null
        if (!MimeTypes.isMarkdownName(last.displayName)) return@withContext null
        VaultFile(
            documentId = last.documentId,
            relativePath = currentPath,
            displayName = last.displayName,
            uri = DocumentsContract.buildDocumentUriUsingTree(tree, last.documentId).toString(),
            mimeType = last.mimeType,
            lastModified = last.lastModified,
            size = last.size,
        )
    }

    override suspend fun refresh(file: VaultFile): VaultFile? = withContext(Dispatchers.IO) {
        fileFromUri(file.uriObject, file.parentPath)
    }

    override suspend fun exists(file: VaultFile): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(
                file.uriObject,
                arrayOf(Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
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

    private fun readVaultInfo(uri: Uri, preferredName: String?): VaultInfo? {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val metadata = queryDocument(DocumentsContract.buildDocumentUriUsingTree(uri, rootId))
        if (metadata == null && preferredName == null) return null
        val name = metadata?.displayName?.takeIf { it.isNotBlank() } ?: preferredName ?: "Vault"
        return VaultInfo(uri = uri.toString(), displayName = name, readable = metadata != null)
    }

    private fun ensureTree(): Uri = treeUri ?: throw VaultAccessException("No vault selected")

    private fun ensureRootId(tree: Uri): String =
        rootDocumentId ?: runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?.also { rootDocumentId = it }
        ?: throw VaultAccessException("The vault URI is invalid")

    private fun childrenUri(documentId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(ensureTree(), documentId)

    private fun findChild(parentDocumentId: String, name: String): CursorMetadata? {
        val cursor = try {
            resolver.query(childrenUri(parentDocumentId), projection, null, null, null)
        } catch (_: Exception) {
            return null
        } ?: return null
        cursor.use {
            var fallback: CursorMetadata? = null
            while (it.moveToNext()) {
                val displayName = it.getStringOrNull(1) ?: continue
                val meta = CursorMetadata(
                    documentId = it.getStringOrNull(0) ?: continue,
                    displayName = displayName,
                    mimeType = it.getStringOrNull(2),
                    lastModified = it.getLongOrNull(3),
                    size = it.getLongOrNull(4),
                )
                if (displayName == name) return meta
                if (fallback == null && displayName.equals(name, ignoreCase = true)) fallback = meta
            }
            return fallback
        }
    }

    private fun uniqueChildName(parentDocumentId: String, desired: String): String {
        val cursor = try {
            resolver.query(childrenUri(parentDocumentId), projection, null, null, null)
        } catch (_: Exception) {
            null
        }
        if (cursor == null) return desired
        val names = HashSet<String>()
        cursor.use {
            while (it.moveToNext()) {
                it.getStringOrNull(1)?.lowercase()?.let(names::add)
            }
        }
        if (desired.lowercase() !in names) return desired
        val base = MimeTypes.displayNameWithoutExtension(desired)
        val extension = desired.substring(base.length)
        var index = 2
        while (true) {
            val candidate = "$base $index$extension"
            if (candidate.lowercase() !in names) return candidate
            index++
        }
    }

    private fun fileFromUri(uri: Uri, parentPath: String): VaultFile? {
        val meta = queryDocument(uri) ?: return null
        if (!MimeTypes.isMarkdownName(meta.displayName)) return null
        val relativePath = if (parentPath.isBlank()) meta.displayName else "$parentPath/${meta.displayName}"
        return VaultFile(
            documentId = meta.documentId,
            relativePath = relativePath,
            displayName = meta.displayName,
            uri = uri.toString(),
            mimeType = meta.mimeType,
            lastModified = meta.lastModified,
            size = meta.size,
        )
    }

    private fun queryDocument(uri: Uri): CursorMetadata? {
        val cursor = try {
            resolver.query(uri, projection, null, null, null)
        } catch (_: Exception) {
            return null
        } ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return CursorMetadata(
                documentId = it.getStringOrNull(0) ?: return null,
                displayName = it.getStringOrNull(1) ?: return null,
                mimeType = it.getStringOrNull(2),
                lastModified = it.getLongOrNull(3),
                size = it.getLongOrNull(4),
            )
        }
    }

    private data class CursorMetadata(
        val documentId: String,
        val displayName: String,
        val mimeType: String?,
        val lastModified: Long?,
        val size: Long?,
    )

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    companion object {
        const val MAX_DOCUMENT_BYTES = 16L * 1024 * 1024
    }
}
