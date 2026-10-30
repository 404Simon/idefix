package dev.idefix.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY relative_path COLLATE NOCASE")
    suspend fun getAll(): List<FileEntity>

    @Query("SELECT * FROM files ORDER BY relative_path COLLATE NOCASE")
    fun observeAll(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FileEntity?

    @Query("SELECT * FROM files WHERE relative_path = :path LIMIT 1")
    suspend fun getByPath(path: String): FileEntity?

    @Query("SELECT * FROM files WHERE document_uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): FileEntity?

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity): Long

    @Update
    suspend fun update(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM files")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM files")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM files")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(last_indexed_at) FROM files")
    suspend fun lastIndexedAt(): Long?

    @Query("SELECT COUNT(*) FROM files WHERE index_status != 'ok'")
    suspend fun failedCount(): Int
}

data class FtsSearchRow(
    val fileId: Long,
    val snippet: String,
    val score: Double,
)

data class IndexedDocumentRow(
    val fileId: Long,
    val uri: String,
    val relativePath: String,
    val filename: String,
    val content: String,
)

data class FileFingerprintRow(
    val id: Long,
    val relativePath: String,
    val documentUri: String,
    val fingerprint: String,
    val size: Long?,
)

@Dao
interface FtsDao {

    @SkipQueryVerification
    @Query("INSERT INTO file_fts(rowid, filename, path, content) VALUES (:fileId, :filename, :path, :content)")
    suspend fun insert(fileId: Long, filename: String, path: String, content: String)

    @SkipQueryVerification
    @Query("DELETE FROM file_fts WHERE rowid = :fileId")
    suspend fun delete(fileId: Long)

    @SkipQueryVerification
    @Query("DELETE FROM file_fts")
    suspend fun deleteAll()

    @SkipQueryVerification
    @Query(
        """SELECT rowid AS fileId,
                  snippet(file_fts, 2, char(1), char(2), '…', 18) AS snippet,
                  bm25(file_fts) AS score
           FROM file_fts
           WHERE file_fts MATCH :matchQuery
           ORDER BY rank
           LIMIT :limit""",
    )
    suspend fun search(matchQuery: String, limit: Int): List<FtsSearchRow>

    @SkipQueryVerification
    @Query("SELECT content FROM file_fts WHERE rowid = :fileId LIMIT 1")
    suspend fun contentFor(fileId: Long): String?

    @SkipQueryVerification
    @Query(
        """SELECT f.id AS fileId, f.document_uri AS uri, f.relative_path AS relativePath,
                  f.filename AS filename, fts.content AS content
           FROM files f JOIN file_fts fts ON fts.rowid = f.id
           WHERE f.id > :afterId
           ORDER BY f.id
           LIMIT :limit""",
    )
    suspend fun contentPage(afterId: Long, limit: Int): List<IndexedDocumentRow>

    @SkipQueryVerification
    @Query("SELECT COUNT(*) FROM file_fts")
    suspend fun count(): Int

    @SkipQueryVerification
    @Query("INSERT INTO file_fts(rowid, filename, path, content) SELECT :newId, filename, path, content FROM file_fts WHERE rowid = :oldId")
    suspend fun copyRow(oldId: Long, newId: Long)

    @SkipQueryVerification
    @Query("SELECT id, relative_path, document_uri, fingerprint, size FROM files")
    suspend fun fingerprints(): List<FileFingerprintRow>
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_files ORDER BY opened_at DESC LIMIT :limit")
    fun observeRecentFiles(limit: Int): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files ORDER BY opened_at DESC LIMIT :limit")
    suspend fun recentFiles(limit: Int): List<RecentFileEntity>

    @Query("SELECT * FROM recent_files WHERE file_id = :fileId LIMIT 1")
    suspend fun getFile(fileId: Long): RecentFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: RecentFileEntity)

    @Query("UPDATE recent_files SET cursor_line = :line, cursor_offset = :offset, scroll_offset = :scrollOffset, last_match_line = :matchLine WHERE file_id = :fileId")
    suspend fun updatePosition(fileId: Long, line: Int, offset: Int, scrollOffset: Int, matchLine: Int?)

    @Query("DELETE FROM recent_files WHERE file_id = :fileId")
    suspend fun deleteFile(fileId: Long)

    @Query("DELETE FROM recent_files")
    suspend fun clearFiles()

    @Query("DELETE FROM recent_files WHERE file_id NOT IN (SELECT file_id FROM recent_files ORDER BY opened_at DESC LIMIT :keep)")
    suspend fun trimFiles(keep: Int)

    @Query("SELECT * FROM recent_searches ORDER BY searched_at DESC LIMIT :limit")
    fun observeRecentSearches(limit: Int): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY searched_at DESC LIMIT :keep)")
    suspend fun trimSearches(keep: Int)

    @Query("DELETE FROM recent_searches")
    suspend fun clearSearches()

    @Query("DELETE FROM recent_files WHERE file_id NOT IN (SELECT id FROM files)")
    suspend fun removeOrphanedFiles()
}
