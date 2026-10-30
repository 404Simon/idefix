package dev.idefix.app.data.tasks

import dev.idefix.app.data.database.FtsDao
import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.domain.model.TodoList
import dev.idefix.app.domain.model.TodoUpdateResult
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.repository.IndexRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.TodoRepository
import dev.idefix.app.domain.repository.VaultRepository
import dev.idefix.app.domain.tasks.TodoEditor
import dev.idefix.app.domain.tasks.TodoParser
import dev.idefix.app.domain.tasks.TodoWriteOutcome
import dev.idefix.app.sync.SyncCoordinator
import dev.idefix.app.util.MimeTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Reads todo lists from the indexed content cache instead of the Storage Access
 * Framework. The Markdown files remain authoritative: every write goes through
 * the vault and refreshes the index entry, so the cache catches up immediately.
 * Explicit index reconciliation handles changes made outside the app.
 */
class DefaultTodoRepository(
    private val searchRepository: SearchRepository,
    private val ftsDao: FtsDao,
    private val vaultRepository: VaultRepository,
    private val indexRepository: IndexRepository,
    private val syncCoordinator: SyncCoordinator,
) : TodoRepository {

    override suspend fun loadLists(): List<TodoList> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        searchRepository.browseFiles()
            .filter { TodoList.isTodoFile(it.filename) }
            .mapNotNull { summary ->
                val content = readIndexedContent(
                    fileId = summary.fileId,
                    relativePath = summary.relativePath,
                    uri = summary.uri,
                    filename = summary.filename,
                ) ?: return@mapNotNull null
                val tasks = TodoParser.parse(
                    content = content,
                    fileId = summary.fileId,
                    uri = summary.uri,
                    relativePath = summary.relativePath,
                    filename = summary.filename,
                )
                TodoList(
                    fileId = summary.fileId,
                    uri = summary.uri,
                    relativePath = summary.relativePath,
                    filename = summary.filename,
                    name = TodoList.displayName(summary.filename),
                    openCount = tasks.count { !it.completed },
                    overdueCount = tasks.count { it.isOverdue(now) },
                )
            }
            .sortedWith(
                compareBy<TodoList>(
                    { !it.filename.equals("TODO.md", ignoreCase = true) },
                    { it.filename.lowercase() },
                    { it.relativePath.lowercase() },
                ),
            )
    }

    override suspend fun loadTasks(list: TodoList): List<TodoItem> = withContext(Dispatchers.IO) {
        val content = readIndexedContent(
            fileId = list.fileId,
            relativePath = list.relativePath,
            uri = list.uri,
            filename = list.filename,
        ) ?: error("Could not read ${list.filename}")
        TodoParser.parse(
            content = content,
            fileId = list.fileId,
            uri = list.uri,
            relativePath = list.relativePath,
            filename = list.filename,
        )
    }

    override suspend fun addTask(list: TodoList, draft: TodoDraft): TodoUpdateResult =
        withContext(Dispatchers.IO) {
            editList(list, "Todo added") { content ->
                TodoEditor.appendTask(content, draft)
            }
        }

    override suspend fun updateTask(
        list: TodoList,
        task: TodoItem,
        draft: TodoDraft,
    ): TodoUpdateResult = withContext(Dispatchers.IO) {
        editList(list, "Todo updated") { content ->
            TodoEditor.updateTask(content, task, draft)
        }
    }

    override suspend fun setCompleted(
        list: TodoList,
        task: TodoItem,
        completed: Boolean,
    ): TodoUpdateResult = withContext(Dispatchers.IO) {
        editList(
            list = list,
            message = if (completed) "Todo completed" else "Todo reopened",
        ) { content ->
            TodoEditor.setCompleted(content, task, completed)
        }
    }

    override suspend fun deleteTask(list: TodoList, task: TodoItem): TodoUpdateResult =
        withContext(Dispatchers.IO) {
            editList(list, "Todo deleted") { content ->
                TodoEditor.deleteTask(content, task)
            }
        }

    override suspend fun createList(name: String): TodoUpdateResult =
        syncCoordinator.track {
            withContext(Dispatchers.IO) {
                try {
                    val filename = TodoList.fileNameFor(name)
                    if (vaultRepository.resolve(filename) != null) {
                        return@withContext TodoUpdateResult.Conflict(
                            "A list named ${TodoList.displayName(filename)} already exists",
                        )
                    }
                    val file = vaultRepository.createMarkdownFile(
                        parentRelativePath = null,
                        displayName = MimeTypes.normalizeMarkdownName(filename),
                        content = "",
                    )
                    val warning = runCatching { indexRepository.reindexFile(file) }.exceptionOrNull()
                    TodoUpdateResult.Success(
                        message = if (warning == null) {
                            "Created ${TodoList.displayName(file.displayName)}"
                        } else {
                            "List created; index refresh failed: ${warning.message}"
                        },
                        relativePath = file.relativePath,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    TodoUpdateResult.Error(e.message ?: "Could not create the list")
                }
            }
        }

    private suspend fun editList(
        list: TodoList,
        message: String,
        edit: (String) -> TodoWriteOutcome,
    ): TodoUpdateResult = syncCoordinator.track {
        try {
            val file = resolveListFile(list)
            val content = vaultRepository.readText(file)
            when (val outcome = edit(content)) {
                is TodoWriteOutcome.Conflict -> TodoUpdateResult.Conflict(outcome.message)
                is TodoWriteOutcome.Success -> {
                    vaultRepository.writeText(file, outcome.content)
                    val warning = runCatching { indexRepository.reindexFile(file) }.exceptionOrNull()
                    TodoUpdateResult.Success(
                        message = if (warning == null) {
                            message
                        } else {
                            "$message; index refresh failed: ${warning.message}"
                        },
                        relativePath = file.relativePath,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TodoUpdateResult.Error(e.message ?: "Could not update the todo list")
        }
    }

    private suspend fun readIndexedContent(
        fileId: Long,
        relativePath: String,
        uri: String,
        filename: String,
    ): String? {
        if (fileId > 0) {
            try {
                ftsDao.contentFor(fileId)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to the vault read.
            }
        }
        val file = vaultRepository.resolve(relativePath)
            ?: VaultFile(
                documentId = "",
                relativePath = relativePath,
                displayName = filename,
                uri = uri,
            )
        return try {
            vaultRepository.readText(file)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveListFile(list: TodoList): VaultFile =
        vaultRepository.resolve(list.relativePath)
            ?: VaultFile(
                documentId = "",
                relativePath = list.relativePath,
                displayName = list.filename,
                uri = list.uri,
            )
}
