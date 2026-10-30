package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchOutcome

interface SearchRepository {
    suspend fun search(
        query: String,
        mode: SearchMode,
        caseSensitive: Boolean,
        limit: Int = 100,
        prefixOnly: Boolean = false,
    ): SearchOutcome

    suspend fun browseFiles(query: String? = null): List<FileSummary>

    suspend fun summary(): dev.idefix.app.domain.model.IndexSummary

    suspend fun fileIdForPath(relativePath: String): Long?
}
