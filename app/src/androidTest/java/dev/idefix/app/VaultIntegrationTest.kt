package dev.idefix.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.idefix.app.data.database.IdefixDatabase
import dev.idefix.app.data.index.DefaultIndexRepository
import dev.idefix.app.data.recents.DefaultRecentFilesRepository
import dev.idefix.app.data.search.DefaultSearchRepository
import dev.idefix.app.data.tasks.DefaultTodoRepository
import dev.idefix.app.data.vault.SafVaultRepository
import dev.idefix.app.domain.model.AppSettings
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoUpdateResult
import dev.idefix.app.domain.repository.IndexRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.TodoRepository
import dev.idefix.app.domain.repository.VaultRepository
import dev.idefix.app.testing.TestDocumentsProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VaultIntegrationTest {

    private lateinit var context: Context
    private lateinit var vaultDir: File
    private lateinit var db: IdefixDatabase
    private lateinit var settings: FakeSettingsRepository
    private lateinit var vault: VaultRepository
    private lateinit var index: IndexRepository
    private lateinit var search: SearchRepository
    private lateinit var todos: TodoRepository
    private lateinit var recents: DefaultRecentFilesRepository

    private val projectPath = "Notes/Project Plan.md"
    private val projectContent = "# Project Plan\n\nThe walrus dances at dawn.\n"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        TestDocumentsProvider.reset(context)
        vaultDir = TestDocumentsProvider.vaultDir(context)
        seedVault()
        settings = FakeSettingsRepository(AppSettings())
        db = TestDatabase.create(context)
        vault = SafVaultRepository(context, settings)
        val treeUri = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_ID)
        vault.adoptVault(treeUri.toString(), "Test vault")
        index = DefaultIndexRepository(db, vault)
        search = DefaultSearchRepository(db.fileDao(), db.ftsDao())
        todos = DefaultTodoRepository(search, db.ftsDao(), vault, index)
        recents = DefaultRecentFilesRepository(db.recentDao())
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        if (::context.isInitialized) TestDatabase.cleanup(context)
        if (::vaultDir.isInitialized) vaultDir.deleteRecursively()
    }

    private fun seedVault() {
        File(vaultDir, "Notes").mkdirs()
        File(vaultDir, "Other").mkdirs()
        File(vaultDir, ".hidden").mkdirs()
        File(vaultDir, projectPath).writeText(projectContent)
        File(vaultDir, "Notes/Alpha.md").writeText("# Alpha\n\nA quiet note.\n")
        File(vaultDir, "Notes/Beta.markdown").writeText("# Beta\n\nBeta content.\n")
        File(vaultDir, "Other/Dup.md").writeText("duplicate in other")
        File(vaultDir, "Notes/Dup.md").writeText("duplicate in notes")
        File(vaultDir, "Empty.md").writeText("")
        File(vaultDir, "Windows.md").writeText("line one\r\n- [ ] windows task\r\nline three\r\n")
        File(vaultDir, "Unicode.md").writeText("# Überblick\n\nDie Straße ist schön.\n")
        File(vaultDir, ".hidden/Secret.md").writeText("hidden walrus")
        File(vaultDir, "TODO.md").writeText(
            """
            # TODO

            - [ ] Open task
              due: 2026-10-30 18:00
              Remember the milk.

            - [x] Done task

            - [ ] Prepare plan
              due: 2026-10-31 09:30

            ```
            - [ ] fake task in code
            ```
            """.trimIndent() + "\n",
        )
        File(vaultDir, "job.TODO.md").writeText(
            "# Job\n\n- [ ] Write report\n  Body for the report.\n",
        )
        File(vaultDir, "Code.md").writeText("```\n- [ ] fake task in code\n```\n")
        File(vaultDir, "Large.md").writeText(buildString {
            append("# Large\n\n")
            repeat(5_000) { append("Filler line $it with walrus sometimes.\n") }
            append("unique-large-marker\n")
        })
    }

    @Test
    fun vaultAccessAndSafOperations() = runBlocking {
        assertTrue(vault.validateAccess())
        assertNotNull(vault.vaultInfo.value)

        val scanned = vault.scanMarkdownFiles(includeHidden = false)
        assertTrue(scanned.files.any { it.relativePath == projectPath })
        assertFalse(scanned.files.any { it.relativePath.startsWith(".hidden") })
        val scannedHidden = vault.scanMarkdownFiles(includeHidden = true)
        assertTrue(scannedHidden.files.any { it.relativePath.startsWith(".hidden") })

        val project = vault.resolve(projectPath)
        assertNotNull(project)
        assertEquals(projectContent, vault.readText(project!!))

        val newContent = projectContent + "\nAdded through SAF.\n"
        vault.writeText(project, newContent)
        assertEquals(newContent, File(vaultDir, projectPath).readText())

        val created = vault.createMarkdownFile(null, "Created", "seed")
        assertEquals("Created.md", created.displayName)
        assertEquals("seed", vault.readText(created))
        assertEquals("seed", File(vaultDir, "Created.md").readText())

        val renamed = vault.renameFile(created, "Renamed.md")
        assertEquals("Renamed.md", renamed.displayName)
        assertNull(vault.resolve("Created.md"))
        assertNotNull(vault.resolve("Renamed.md"))

        vault.deleteFile(renamed)
        assertNull(vault.resolve("Renamed.md"))
        assertFalse(File(vaultDir, "Renamed.md").exists())

        val empty = vault.resolve("Empty.md")!!
        assertEquals("", vault.readText(empty))
        val windows = vault.resolve("Windows.md")!!
        assertTrue(vault.readText(windows).contains("\r\n"))
    }

    @Test
    fun indexingSearchAndLineNumbers() = runBlocking {
        val result = index.rebuild(includeHidden = false)
        assertTrue("indexed=${result.indexed} failures=${result.failures}", result.indexed >= 8)
        assertEquals(0, result.failures.size)

        val filenames = search.search("proj", SearchMode.FILENAME, false)
        assertEquals(projectPath, filenames.results.first().relativePath)

        val fullText = search.search("walrus", SearchMode.TEXT, false)
        assertTrue(fullText.results.any { it.relativePath == projectPath && it.line == 3 })

        val phrase = search.search("\"walrus dances\"", SearchMode.TEXT, false)
        assertTrue(phrase.results.any { it.relativePath == projectPath })

        val literal = search.search("walrus", SearchMode.LITERAL, false)
        assertTrue(literal.results.any { it.relativePath == projectPath && it.line == 3 })
        assertTrue(literal.results.all { it.snippet != null })

        val regex = search.search("walrus\\s+dances", SearchMode.REGEX, false)
        assertTrue(regex.results.any { it.relativePath == projectPath && it.line == 3 })

        val invalidRegex = search.search("[broken", SearchMode.REGEX, false)
        assertNotNull(invalidRegex.error)

        val unicode = search.search("Straße", SearchMode.TEXT, false)
        assertTrue(unicode.results.any { it.relativePath == "Unicode.md" })

        val hidden = search.search("walrus", SearchMode.TEXT, false)
        assertFalse(hidden.results.any { it.relativePath.startsWith(".hidden") })

        val duplicates = search.search("dup", SearchMode.FILENAME, false)
        assertEquals(setOf("Notes/Dup.md", "Other/Dup.md"), duplicates.results.map { it.relativePath }.toSet())

        val large = search.search("unique-large-marker", SearchMode.LITERAL, false)
        assertTrue(large.results.any { it.relativePath == "Large.md" })

        val empty = search.search("   ", SearchMode.TEXT, false)
        assertTrue(empty.results.isEmpty())
        assertNull(empty.error)
    }

    @Test
    fun todoListsAreDiscoveredAndParsed() = runBlocking {
        index.rebuild(false)
        val lists = todos.loadLists()
        assertEquals(listOf("TODO.md", "job.TODO.md"), lists.map { it.relativePath })
        assertEquals("Main", lists.first().name)
        assertEquals("Job", lists.last().name)

        val main = lists.first()
        val items = todos.loadTasks(main)
        assertEquals(listOf("Open task", "Done task", "Prepare plan"), items.map { it.title })
        assertFalse(items.first().completed)
        assertTrue(items[1].completed)
        assertEquals("Remember the milk.", items.first().body)
        assertEquals(
            java.time.LocalDateTime.of(2026, 10, 30, 18, 0),
            items.first().due,
        )
        assertFalse(items.any { it.title.contains("fake task in code") })

        val job = lists.last()
        assertEquals("Body for the report.", todos.loadTasks(job).first().body)
    }

    @Test
    fun todoMutationsRoundTrip() = runBlocking {
        index.rebuild(false)
        val main = todos.loadLists().first { it.relativePath == "TODO.md" }
        val open = todos.loadTasks(main).first { it.title == "Open task" }

        assertTrue(todos.setCompleted(main, open, true) is TodoUpdateResult.Success)
        val toggled = File(vaultDir, "TODO.md").readText()
        assertTrue(toggled.contains("- [x] Open task"))
        assertTrue(toggled.contains("  due: 2026-10-30 18:00"))
        assertTrue(toggled.contains("  Remember the milk."))

        val refreshed = todos.loadTasks(main).first { it.title == "Open task" }
        val update = todos.updateTask(
            main,
            refreshed,
            TodoDraft(
                title = "Buy oat milk",
                body = "Check the fridge.",
                due = java.time.LocalDateTime.of(2026, 11, 1, 10, 30),
            ),
        )
        assertTrue(update is TodoUpdateResult.Success)
        assertTrue(
            File(vaultDir, "TODO.md").readText()
                .contains("- [x] Buy oat milk\n  due: 2026-11-01 10:30\n  Check the fridge."),
        )

        assertTrue(
            todos.addTask(
                main,
                TodoDraft("New todo", "New body", java.time.LocalDateTime.of(2026, 12, 24, 20, 0)),
            ) is TodoUpdateResult.Success,
        )
        val added = todos.loadTasks(main).first { it.title == "New todo" }
        assertEquals("New body", added.body)
        assertTrue(todos.deleteTask(main, added) is TodoUpdateResult.Success)
        assertFalse(todos.loadTasks(main).any { it.title == "New todo" })
    }

    @Test
    fun todoConflictWhenTheBlockChangesExternally() = runBlocking {
        index.rebuild(false)
        val main = todos.loadLists().first { it.relativePath == "TODO.md" }
        val task = todos.loadTasks(main).first { it.title == "Prepare plan" }
        val taskFile = File(vaultDir, "TODO.md")
        taskFile.writeText(taskFile.readText().replace("Prepare plan", "Renamed externally"))
        val result = todos.setCompleted(main, task, true)
        assertTrue(result is TodoUpdateResult.Conflict)
        assertTrue(taskFile.readText().contains("- [ ] Renamed externally"))
    }

    @Test
    fun creatingAListAddsTheTodoSuffix() = runBlocking {
        index.rebuild(false)
        assertTrue(todos.createList("personal") is TodoUpdateResult.Success)
        assertTrue(File(vaultDir, "personal.TODO.md").exists())
        assertTrue(todos.loadLists().any { it.relativePath == "personal.TODO.md" })
    }

    @Test
    fun externalChangesRenameAndDeletionAreReconciled() = runBlocking {
        index.rebuild(false)
        val before = search.search("walrus", SearchMode.TEXT, false).results.first { it.relativePath == projectPath }
        val oldId = before.fileId

        File(vaultDir, projectPath).writeText("# Project Plan\n\nA new narwhal paragraph.\n")
        index.reconcile(false)
        val updated = search.search("narwhal", SearchMode.TEXT, false)
        assertTrue(updated.results.any { it.relativePath == projectPath })
        assertTrue(search.search("walrus dances", SearchMode.LITERAL, false).results.none {
            it.relativePath == projectPath
        })

        val renamedFile = File(vaultDir, "Notes/Renamed Project.md")
        assertTrue(File(vaultDir, projectPath).renameTo(renamedFile))
        index.reconcile(false)
        val renamed = search.search("renamed", SearchMode.FILENAME, false)
        assertTrue(renamed.results.any { it.relativePath == "Notes/Renamed Project.md" })
        assertEquals(oldId, renamed.results.first { it.relativePath == "Notes/Renamed Project.md" }.fileId)

        assertTrue(renamedFile.delete())
        index.reconcile(false)
        assertTrue(search.search("renamed project", SearchMode.FILENAME, false).results.none {
            it.relativePath == "Notes/Renamed Project.md"
        })
    }

    @Test
    fun reconcileOnlyReindexesChangedFiles() = runBlocking {
        val initial = index.rebuild(false)
        assertTrue(initial.indexed > 0)

        val unchanged = index.reconcile(false)
        assertEquals(0, unchanged.indexed)
        assertEquals(0, unchanged.removed)

        File(vaultDir, projectPath).appendText("\nA fresh line.\n")
        val changed = index.reconcile(false)
        assertEquals(1, changed.indexed)
        assertEquals(0, changed.removed)
    }

    @Test
    fun indexCanBeRebuiltAfterDatabaseClear() = runBlocking {
        index.rebuild(false)
        assertTrue(search.summary().fileCount > 0)
        index.clearIndex()
        assertEquals(0, search.summary().fileCount)
        assertTrue(search.search("walrus", SearchMode.TEXT, false).results.isEmpty())
        index.rebuild(false)
        assertTrue(search.search("walrus", SearchMode.TEXT, false).results.isNotEmpty())
    }

    @Test
    fun recentHistoryAndPositionsPersist() = runBlocking {
        index.rebuild(false)
        val file = vault.resolve(projectPath)!!
        val id = search.fileIdForPath(projectPath)!!
        recents.recordOpen(file, id, 3, 22)
        val recent = recents.recentFiles.first().first()
        assertEquals(projectPath, recent.relativePath)
        assertEquals(3, recent.cursorLine)
        recents.updatePosition(id, 4, 30, 77, 3)
        val updated = recents.positionFor(id)!!
        assertEquals(4, updated.cursorLine)
        assertEquals(77, updated.scrollOffset)
        recents.clearHistory()
        assertTrue(recents.recentFiles.first().isEmpty())
    }

    @Test
    fun unreadableFileIsReportedWithoutBreakingTheIndex() = runBlocking {
        index.rebuild(false)
        File(vaultDir, "Unreadable.md").writeText("this file cannot be opened")
        val result = index.rebuild(false)
        assertTrue(result.failures.any { it.contains("Unreadable.md") })
        assertTrue(search.search("walrus", SearchMode.TEXT, false).results.isNotEmpty())
        index.clearIndex()
        assertTrue(search.summary().fileCount == 0)
    }

    @Test
    fun hiddenFoldersCanBeIncluded() = runBlocking {
        index.rebuild(true)
        val hidden = search.search("hidden walrus", SearchMode.LITERAL, false)
        assertTrue(hidden.results.any { it.relativePath.startsWith(".hidden") })
        index.rebuild(false)
        assertTrue(search.search("hidden walrus", SearchMode.LITERAL, false).results.none {
            it.relativePath.startsWith(".hidden")
        })
    }
}
