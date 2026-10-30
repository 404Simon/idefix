package dev.idefix.app.data.tasks

import dev.idefix.app.data.database.FtsDao
import dev.idefix.app.domain.model.TaskItem
import dev.idefix.app.domain.model.TaskUpdateResult
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.repository.IndexRepository
import dev.idefix.app.domain.repository.TaskRepository
import dev.idefix.app.domain.repository.VaultRepository
import dev.idefix.app.domain.tasks.TaskEditor
import dev.idefix.app.domain.tasks.TaskParser
import dev.idefix.app.domain.tasks.ToggleOutcome
import dev.idefix.app.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DefaultTaskRepository(
    private val ftsDao: FtsDao,
    private val vaultRepository: VaultRepository,
    private val indexRepository: IndexRepository,
) : TaskRepository {

    override suspend fun loadTasks(): List<TaskItem> = withContext(Dispatchers.IO) {
        val tasks = ArrayList<TaskItem>()
        var afterId = 0L
        val pageSize = 25
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = ftsDao.contentPage(afterId, pageSize)
            if (page.isEmpty()) break
            afterId = page.last().fileId
            for (document in page) {
                tasks += TaskParser.parse(
                    content = document.content,
                    fileId = document.fileId,
                    uri = document.uri,
                    relativePath = document.relativePath,
                    filename = document.filename,
                )
            }
            if (page.size < pageSize) break
        }
        tasks.sortedWith(compareBy({ it.relativePath.lowercase() }, { it.lineNumber }))
    }

    override suspend fun setCompleted(task: TaskItem, completed: Boolean): TaskUpdateResult =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveTaskFile(task)
                    ?: return@withContext TaskUpdateResult.Error("The note no longer exists in the vault")
                val current = vaultRepository.readText(file)
                when (
                    val outcome = TaskEditor.setCompleted(
                        content = current,
                        lineNumber = task.lineNumber,
                        expectedText = task.text,
                        expectedCompleted = task.completed,
                        newCompleted = completed,
                    )
                ) {
                    is ToggleOutcome.Conflict -> TaskUpdateResult.Conflict(outcome.message)
                    is ToggleOutcome.NotATask -> TaskUpdateResult.Conflict(outcome.message)
                    is ToggleOutcome.Success -> {
                        vaultRepository.writeText(file, outcome.content)
                        val warning = runCatching { indexRepository.reindexFile(file) }.exceptionOrNull()
                        TaskUpdateResult.Success(
                            message = if (warning == null) {
                                if (completed) "Task completed" else "Task reopened"
                            } else {
                                "Saved; index refresh failed: ${warning.message}"
                            },
                            relativePath = file.relativePath,
                        )
                    }
                }
            } catch (e: Exception) {
                TaskUpdateResult.Error(e.message ?: "Could not update the task")
            }
        }

    override suspend fun addTask(
        relativePath: String,
        text: String,
        createIfMissing: Boolean,
    ): TaskUpdateResult = withContext(Dispatchers.IO) {
        try {
            val normalizedPath = relativePath.trim().trimStart('/')
            val target = vaultRepository.resolve(normalizedPath)
                ?: if (createIfMissing && normalizedPath.isNotBlank()) {
                    val parent = normalizedPath.substringBeforeLast('/', "")
                    val name = MimeTypes.normalizeMarkdownName(normalizedPath.substringAfterLast('/'))
                    vaultRepository.createMarkdownFile(parent, name, "")
                } else {
                    return@withContext TaskUpdateResult.Error("Choose an existing note, or create a new one")
                }
            val current = vaultRepository.readText(target)
            val updated = TaskEditor.appendTask(current, text)
            vaultRepository.writeText(target, updated)
            val warning = runCatching { indexRepository.reindexFile(target) }.exceptionOrNull()
            TaskUpdateResult.Success(
                message = if (warning == null) {
                    "Task added to ${target.displayName}"
                } else {
                    "Task saved; index refresh failed: ${warning.message}"
                },
                relativePath = target.relativePath,
            )
        } catch (e: Exception) {
            TaskUpdateResult.Error(e.message ?: "Could not add the task")
        }
    }

    private suspend fun resolveTaskFile(task: TaskItem): VaultFile? {
        vaultRepository.resolve(task.relativePath)?.let { return it }
        val fallback = VaultFile(
            documentId = "",
            relativePath = task.relativePath,
            displayName = task.filename,
            uri = task.uri,
        )
        return if (vaultRepository.exists(fallback)) fallback else null
    }
}
