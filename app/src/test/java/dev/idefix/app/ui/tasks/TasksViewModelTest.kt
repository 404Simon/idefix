package dev.idefix.app.ui.tasks

import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.domain.model.TodoList
import dev.idefix.app.domain.model.TodoUpdateResult
import dev.idefix.app.domain.repository.TodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addingATodoShowsTheRowBeforeTheWriteFinishes() = runTest(dispatcher.scheduler) {
        val viewModel = TasksViewModel(FakeTodoRepository())
        advanceUntilIdle()
        viewModel.selectList(viewModel.state.value.lists.first())
        advanceUntilIdle()

        viewModel.saveTask(null, TodoDraft("New todo"))

        val optimistic = viewModel.state.value.tasks.first { it.title == "New todo" }
        assertTrue(optimistic.startLine < 0)
        assertTrue(viewModel.isPending(optimistic))

        advanceUntilIdle()

        val persisted = viewModel.state.value.tasks.first { it.title == "New todo" }
        assertTrue(persisted.startLine > 0)
        assertTrue(viewModel.state.value.tasks.none { it.startLine < 0 })
    }

    @Test
    fun creatingAListShowsItBeforeTheFileExists() = runTest(dispatcher.scheduler) {
        val viewModel = TasksViewModel(FakeTodoRepository())
        advanceUntilIdle()

        viewModel.createList("job")

        val provisional = viewModel.state.value.lists.first { it.relativePath == "job.TODO.md" }
        assertEquals("Job", provisional.name)
        assertEquals("job.TODO.md", viewModel.state.value.creatingListPath)

        advanceUntilIdle()

        assertNull(viewModel.state.value.creatingListPath)
        assertEquals("job.TODO.md", viewModel.state.value.selectedList?.relativePath)
    }

    private class FakeTodoRepository : TodoRepository {
        private var lists = listOf(
            TodoList(
                fileId = 1,
                uri = "content://1",
                relativePath = "TODO.md",
                filename = "TODO.md",
                name = "Main",
            ),
        )
        private val tasks = mutableMapOf<String, MutableList<TodoItem>>()

        override suspend fun loadLists(): List<TodoList> = lists

        override suspend fun loadTasks(list: TodoList): List<TodoItem> =
            tasks[list.relativePath].orEmpty()

        override suspend fun addTask(list: TodoList, draft: TodoDraft): TodoUpdateResult {
            val current = tasks.getOrPut(list.relativePath) { mutableListOf() }
            val line = current.size + 1
            current += todo(list, line, draft.title)
            return TodoUpdateResult.Success("added")
        }

        override suspend fun updateTask(
            list: TodoList,
            task: TodoItem,
            draft: TodoDraft,
        ): TodoUpdateResult {
            val current = tasks.getOrPut(list.relativePath) { mutableListOf() }
            current.replaceAll {
                if (it.startLine == task.startLine) {
                    it.copy(title = draft.title, body = draft.body, due = draft.due)
                } else {
                    it
                }
            }
            return TodoUpdateResult.Success("updated")
        }

        override suspend fun setCompleted(
            list: TodoList,
            task: TodoItem,
            completed: Boolean,
        ): TodoUpdateResult {
            val current = tasks.getOrPut(list.relativePath) { mutableListOf() }
            current.replaceAll {
                if (it.startLine == task.startLine) it.copy(completed = completed) else it
            }
            return TodoUpdateResult.Success("toggled")
        }

        override suspend fun deleteTask(
            list: TodoList,
            task: TodoItem,
        ): TodoUpdateResult {
            tasks[list.relativePath]?.removeAll { it.startLine == task.startLine }
            return TodoUpdateResult.Success("deleted")
        }

        override suspend fun createList(name: String): TodoUpdateResult {
            val filename = TodoList.fileNameFor(name)
            lists = lists + TodoList(
                fileId = lists.size + 1L,
                uri = "content://${lists.size + 1}",
                relativePath = filename,
                filename = filename,
                name = TodoList.displayName(filename),
            )
            tasks[filename] = mutableListOf()
            return TodoUpdateResult.Success("created", filename)
        }

        private fun todo(list: TodoList, line: Int, title: String) = TodoItem(
            fileId = list.fileId,
            uri = list.uri,
            relativePath = list.relativePath,
            filename = list.filename,
            startLine = line,
            endLine = line,
            title = title,
            body = "",
            completed = false,
            due = null,
        )
    }
}
