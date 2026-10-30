package dev.idefix.app.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException

/**
 * Debug-only SAF-shaped provider. It deliberately extends ContentProvider rather
 * than DocumentsProvider: Android only allows real DocumentsProviders to be
 * installed by the system document UI (MANAGE_DOCUMENTS), while the app under test
 * needs a provider it can install itself. All DocumentsContract query/open/call
 * paths used by [dev.idefix.app.data.vault.SafVaultRepository] are implemented.
 */
class TestDocumentsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "dev.idefix.app.testdocuments"
        const val ROOT_ID = "root"

        private val defaultProjection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
        )

        fun vaultDir(context: Context): File =
            File(context.cacheDir, "idefix-test-vault").apply { mkdirs() }

        fun reset(context: Context) {
            val dir = vaultDir(context)
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }

    private lateinit var appContext: Context

    override fun onCreate(): Boolean {
        appContext = context ?: return false
        return true
    }

    private fun rootDir(): File = vaultDir(appContext)

    private fun fileFor(documentId: String): File =
        if (documentId == ROOT_ID) rootDir() else File(rootDir(), documentId)

    private fun documentIdFor(file: File): String {
        val root = rootDir().canonicalFile
        val canonical = file.canonicalFile
        return if (canonical == root) {
            ROOT_ID
        } else {
            canonical.relativeTo(root).path.replace(File.separatorChar, '/')
        }
    }

    private fun documentIdFromUri(uri: Uri): String {
        val segments = uri.pathSegments
        val documentIndex = segments.indexOf("document")
        if (documentIndex >= 0 && documentIndex + 1 < segments.size) {
            return segments[documentIndex + 1]
        }
        return segments.lastOrNull() ?: ROOT_ID
    }

    private fun isChildrenUri(uri: Uri): Boolean = uri.pathSegments.lastOrNull() == "children"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: defaultProjection
        val cursor = MatrixCursor(columns)
        if (isChildrenUri(uri)) {
            val parent = fileFor(documentIdFromUri(uri))
            if (!parent.isDirectory) throw FileNotFoundException(parent.path)
            parent.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { addRow(cursor, it, columns) }
        } else {
            val file = fileFor(documentIdFromUri(uri))
            if (!file.exists()) throw FileNotFoundException(file.path)
            addRow(cursor, file, columns)
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileFor(documentIdFromUri(uri))
        if (file.name == "Unreadable.md") throw SecurityException("Simulated unreadable document")
        if (!file.exists()) throw FileNotFoundException(file.path)
        val flags = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND
            "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            else -> ParcelFileDescriptor.MODE_READ_WRITE
        }
        return ParcelFileDescriptor.open(file, flags)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        when (method) {
            "android:createDocument" -> {
                val parentUri = extras?.getParcelable("uri", Uri::class.java)
                    ?: throw IllegalArgumentException("Missing parent URI")
                val mime = extras.getString(Document.COLUMN_MIME_TYPE) ?: "application/octet-stream"
                val name = extras.getString(Document.COLUMN_DISPLAY_NAME)
                    ?: throw IllegalArgumentException("Missing display name")
                val created = createDocument(documentIdFromUri(parentUri), mime, name)
                return Bundle().apply {
                    putParcelable("uri", created)
                }
            }

            "android:renameDocument" -> {
                val uri = extras?.getParcelable("uri", Uri::class.java)
                    ?: throw IllegalArgumentException("Missing document URI")
                val name = extras.getString(Document.COLUMN_DISPLAY_NAME)
                    ?: throw IllegalArgumentException("Missing display name")
                val renamed = renameDocument(documentIdFromUri(uri), name)
                return Bundle().apply { putParcelable("uri", renamed) }
            }

            "android:deleteDocument" -> {
                val uri = extras?.getParcelable("uri", Uri::class.java)
                    ?: throw IllegalArgumentException("Missing document URI")
                deleteDocument(documentIdFromUri(uri))
                return Bundle.EMPTY
            }

            else -> return super.call(method, arg, extras)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = documentIdFromUri(uri)
        return if (deleteDocument(id)) 1 else 0
    }

    override fun getType(uri: Uri): String {
        val file = fileFor(documentIdFromUri(uri))
        return if (file.isDirectory) Document.MIME_TYPE_DIR else mimeFor(file)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val parentId = documentIdFromUri(uri)
        val name = values?.getAsString(Document.COLUMN_DISPLAY_NAME) ?: return null
        val mime = values.getAsString(Document.COLUMN_MIME_TYPE) ?: "text/markdown"
        return createDocument(parentId, mime, name)
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun createDocument(parentId: String, mimeType: String, displayName: String): Uri {
        val parent = fileFor(parentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentId)
        val file = File(parent, displayName)
        if (mimeType == Document.MIME_TYPE_DIR) {
            if (!file.mkdirs()) throw IllegalStateException("Could not create directory")
        } else {
            parent.mkdirs()
            if (!file.createNewFile()) throw IllegalStateException("File already exists")
        }
        return DocumentsContract.buildDocumentUri(AUTHORITY, documentIdFor(file))
    }

    private fun renameDocument(documentId: String, displayName: String): Uri {
        val source = fileFor(documentId)
        if (!source.exists()) throw FileNotFoundException(documentId)
        val target = File(source.parentFile, displayName)
        if (!source.renameTo(target)) throw IllegalStateException("Rename failed")
        return DocumentsContract.buildDocumentUri(AUTHORITY, documentIdFor(target))
    }

    private fun deleteDocument(documentId: String): Boolean {
        val file = fileFor(documentId)
        return file.deleteRecursively()
    }

    private fun addRow(cursor: MatrixCursor, file: File, columns: Array<out String>) {
        val row = cursor.newRow()
        val id = documentIdFor(file)
        val isDir = file.isDirectory
        for (column in columns) {
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> row.add(id)
                Document.COLUMN_DISPLAY_NAME -> row.add(if (id == ROOT_ID) "Idefix test vault" else file.name)
                Document.COLUMN_MIME_TYPE -> row.add(if (isDir) Document.MIME_TYPE_DIR else mimeFor(file))
                Document.COLUMN_LAST_MODIFIED -> row.add(file.lastModified())
                Document.COLUMN_SIZE -> row.add(if (isDir) 0L else file.length())
                Document.COLUMN_FLAGS -> row.add(
                    (if (isDir) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE) or
                        Document.FLAG_SUPPORTS_DELETE or
                        Document.FLAG_SUPPORTS_RENAME,
                )
                Document.COLUMN_SUMMARY -> row.add(null)
                Document.COLUMN_ICON -> row.add(android.R.drawable.ic_menu_agenda)
                else -> row.add(null)
            }
        }
    }

    private fun mimeFor(file: File): String = when {
        file.name.endsWith(".md", ignoreCase = true) ||
            file.name.endsWith(".markdown", ignoreCase = true) -> "text/markdown"

        else -> "application/octet-stream"
    }
}
