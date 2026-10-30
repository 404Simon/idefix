package dev.idefix.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["relative_path"], unique = true),
        Index(value = ["document_uri"]),
        Index(value = ["fingerprint"]),
    ],
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_uri") val documentUri: String,
    @ColumnInfo(name = "document_id") val documentId: String?,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val filename: String,
    @ColumnInfo(name = "last_modified") val lastModified: Long?,
    val size: Long?,
    val fingerprint: String,
    @ColumnInfo(name = "index_status") val indexStatus: String = "ok",
    @ColumnInfo(name = "last_indexed_at") val lastIndexedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey @ColumnInfo(name = "file_id") val fileId: Long,
    @ColumnInfo(name = "document_uri") val documentUri: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val filename: String,
    @ColumnInfo(name = "opened_at") val openedAt: Long,
    @ColumnInfo(name = "cursor_line") val cursorLine: Int = 1,
    @ColumnInfo(name = "cursor_offset") val cursorOffset: Int = 0,
    @ColumnInfo(name = "scroll_offset") val scrollOffset: Int = 0,
    @ColumnInfo(name = "last_match_line") val lastMatchLine: Int? = null,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val mode: String,
    @ColumnInfo(name = "searched_at") val searchedAt: Long,
)
