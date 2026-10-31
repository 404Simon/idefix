# Implementation Plan: Idefix — Obsidian Vault Productivity App

## 1. Project objective

Build a complete, installable native Android application called **Idefix** for browsing, searching, reading, editing, and managing tasks in an existing Obsidian vault.

The primary target is the Google Pixel 10 Pro Fold. The app must use the inner display efficiently while remaining fully usable on the cover display.

The intended workflow resembles using Vim and grep on a PC:

- Search filenames without requiring an exact match.
- Search the full contents of Markdown files.
- Search for literal strings, phrases, and regular expressions.
- Open a result directly at the matching line.
- Edit Markdown files in place.
- Extract tasks from existing Markdown files and update their original checkboxes.
- Keep using Obsidian and Vim against the same files.

The vault is not expected to have a predefined folder hierarchy. It may contain a large, mostly unstructured collection of Markdown files.

### Definition of done

The project is complete only when:

1. The Android project builds successfully from the command line.
2. The app has working vault selection and persistent access permissions.
3. Filename and full-text search work against real Markdown files.
4. Search results open the correct file and matching line.
5. Markdown files can be read and edited without destroying unrelated content.
6. Tasks can be discovered, completed, and added through the app.
7. The UI adapts between the cover and inner displays.
8. Automated tests pass.
9. A debug APK is generated.
10. The APK installs and launches successfully on the connected Android device through ADB.

Do not stop after producing a mockup or a project skeleton.

---

## 2. Technology stack

Use a single-module native Android project initially. Avoid unnecessary architecture complexity or a premature multi-module setup.

| Component                     | Technology                                                      |
| ----------------------------- | --------------------------------------------------------------- |
| Language                      | Kotlin                                                          |
| UI                            | Jetpack Compose                                                 |
| Component system              | Material 3                                                      |
| Foldable layouts              | Material 3 Adaptive                                             |
| Application architecture      | MVVM with repository interfaces                                 |
| State management              | Kotlin Coroutines and StateFlow                                 |
| Dependency injection          | Manual dependency injection initially                           |
| Persistent metadata and index | Room                                                            |
| Full-text search              | SQLite FTS5, with verified driver support                       |
| Markdown preview              | Multiplatform Markdown Renderer with Material 3                 |
| File access                   | Storage Access Framework                                        |
| Background indexing           | Coroutines initially; WorkManager if persistent jobs are needed |
| Unit tests                    | JUnit                                                           |
| UI tests                      | Compose UI testing                                              |
| Build                         | Gradle Kotlin DSL and version catalog                           |

Prefer current stable, mutually compatible library versions. Avoid alpha or beta dependencies unless a required capability is unavailable in stable releases.

Use the official documentation to verify dependency coordinates and APIs before implementation:

- Compose: https://developer.android.com/develop/ui/compose
- Adaptive layouts: https://developer.android.com/develop/adaptive-apps/guides/list-detail
- Room: https://developer.android.com/training/data-storage/room
- Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
- Markdown Renderer: https://github.com/mikepenz/multiplatform-markdown-renderer

Do not add a large third-party component kit. Material 3 is the base design system. Build the search palette, result rows, task rows, editor and file preview as custom Compose components.

### Project configuration

Use the following initial configuration:

- Application ID: `dev.idefix.app`
- Display name: `Idefix`
- Minimum SDK: API 29 or higher unless an implementation requirement justifies a different minimum.
- Compile SDK and target SDK: current stable SDK supported by the selected Android Gradle Plugin.
- Kotlin: stable version compatible with the chosen Compose compiler/plugin.
- Build variants: at least `debug`.
- Theme: dynamic Material 3 color support, dark mode and light mode.
- Permissions: request only those required by the implementation.

If an existing repository already contains a valid Android project, inspect it first and adapt it instead of replacing it unnecessarily.

---

## 3. Architecture

Separate UI, indexing, search, task parsing and file operations.

Suggested package layout:

```text
app/src/main/java/dev/idefix/app/
├── IdefixApplication.kt
├── MainActivity.kt
├── navigation/
│   ├── AppNavigation.kt
│   └── AppDestination.kt
├── ui/
│   ├── theme/
│   ├── components/
│   │   ├── SearchField.kt
│   │   ├── SearchResultRow.kt
│   │   ├── TaskRow.kt
│   │   ├── FilePathLabel.kt
│   │   └── EmptyState.kt
│   ├── search/
│   ├── editor/
│   ├── tasks/
│   ├── recents/
│   └── settings/
├── domain/
│   ├── model/
│   ├── search/
│   ├── tasks/
│   └── repository/
├── data/
│   ├── vault/
│   ├── search/
│   ├── database/
│   ├── indexing/
│   └── tasks/
└── util/
```

Keep the following interfaces independent of the UI:

- `VaultRepository`: discover, read, write, list and resolve vault files.
- `SearchRepository`: filename, full-text and regex search.
- `IndexRepository`: scan, index, update, remove and rebuild entries.
- `TaskRepository`: extract tasks, update checkboxes and create tasks.
- `RecentFilesRepository`: persist recent files and navigation history.

Use immutable domain models and expose asynchronous operations through suspend functions or Flow.

The database must never become the authoritative copy of Markdown content. The actual files remain the source of truth.

---

## 4. User interface and interaction design

### 4.1 Overall visual design

Build a clean, dense, keyboard-friendly interface inspired by modern code editors and native Material 3 applications.

Requirements:

- Dark mode as the initial appearance, with light mode supported.
- Strong typography hierarchy and restrained use of color.
- Compact but accessible file lists.
- Large enough touch targets for mobile use.
- Minimal decorative elements.
- No mandatory dashboard, project hierarchy or folder organization.
- Consistent Material 3 controls and system bars.
- Loading, empty, error and permission-denied states for every major screen.

Do not stretch the cover-screen layout across the inner display. Use a genuinely adaptive layout.

### 4.2 Adaptive navigation

Use Material 3 Adaptive list-detail navigation.

Cover display:

- One primary content pane at a time.
- Search results replace the search landing screen when a query is entered.
- Selecting a result opens the document.
- Back returns to the previous results and restores the query and scroll position.

Inner display:

- Left pane: navigation and search results.
- Right pane: selected document, preview or editor.
- Optional third pane only if the actual available width makes it useful.
- Resizable or otherwise sensible pane proportions where practical.

Use available window size and adaptive layout APIs rather than hardcoding behavior based on a particular device model.

### 4.3 Primary navigation

Keep navigation deliberately small:

1. Tasks
2. Notes
3. Settings

The file browser should be accessible from Notes and Settings. Do not make folder navigation the primary way to find notes.

### 4.4 Tasks-first landing screen

When a vault has been selected, launch directly into the Tasks tab.

The Notes screen should include:

- A prominent, automatically focusable search field.
- Search mode selection: Filename, Full text, Regex.
- A scrollable result list.
- Recent notes when the search field is empty.
- Index status and an action to rebuild the index.
- A visible action to create a new Markdown file.
- An accessible action to open or switch vaults.

Do not automatically open the software keyboard if doing so obscures a useful first-run screen. After the vault is configured, favor immediate keyboard input.

### 4.5 Search results

Each result should display:

- Filename.
- Relative path.
- Matching text snippet, when applicable.
- Matching line number, when applicable.
- Highlighted query terms.
- Optional file modification time.

Selecting a full-text result opens the document at the relevant line. Selecting a filename result opens the document at its previous cursor position, or at the beginning if no position is saved.

Maintain search text, mode, result selection and scroll position across navigation.

### 4.6 Document reader and editor

Implement two document modes:

**Preview mode**

- Render Markdown using the selected Compose Markdown renderer.
- Support headings, paragraphs, links, lists, code blocks, blockquotes, tables and task lists.
- Provide an explicit action to switch to source editing.
- Navigate internal links when the target file can be resolved within the vault.

**Source mode**

- Edit the original Markdown text.
- Preserve the original file's content and formatting as much as possible.
- Support multiline text, selection, copy/paste, undo where practical, and keyboard shortcuts.
- Save changes explicitly or through a clearly defined autosave mechanism.
- Display save status and errors.
- Warn about external changes before overwriting modified content.

Do not build a complex rich-text editor for the first version. Plain Markdown source editing is sufficient and better aligned with Vim compatibility.

---

## 5. Vault selection and file access

Use Android's Storage Access Framework.

### Initial setup

1. Show a welcome screen explaining that the user must select their existing vault directory.
2. Launch `ACTION_OPEN_DOCUMENT_TREE`.
3. Allow the user to select the vault.
4. Persist the returned URI permission using the flags granted by the picker.
5. Save the URI and display the selected vault name.
6. Verify that the selected directory can actually be read.
7. Start indexing only after successful validation.

Do not request broad storage permissions as a substitute for the document picker.

### File abstraction

Implement a `VaultFile` abstraction containing at least:

- Stable internal document identifier, when available.
- Relative path.
- Display name.
- Document URI.
- MIME type, if provided.
- Modification time, if available.
- Size, if available.

Do not assume a document URI is a regular filesystem path.

### Recursive scanning

Recursively traverse the selected directory and discover Markdown files, including `.md` and `.markdown` extensions.

Exclude:

- Hidden folders by default, including `.obsidian`.
- Known transient files and files the app cannot read.
- The app's own cache and database, which must never be stored inside the vault.

Provide an option to include hidden folders later.

Handle:

- Empty directories.
- Permission revocation.
- Unreadable documents.
- Duplicate display names in different folders.
- Providers that do not expose modification times.
- Large files.
- Interrupted scans.
- Deleted or renamed files.

Use relative paths as human-readable identifiers, but do not assume they are permanently stable. Detect renames and update index entries appropriately.

Some document providers do not support efficient recursive traversal or reliable change notifications. Design scanning with progress reporting, cancellation and recoverable failures.

### File operations

Implement:

- Read document.
- Write document.
- Create Markdown file.
- Rename file where supported.
- Delete file only through an explicit user action.
- Refresh metadata.
- Resolve a relative path to a document.
- Report access and write failures clearly.

Never silently fall back to storing modified note content only in the application database.

---

## 6. Local search index

Build an index optimized for quick, repeated searches without requiring a complete scan for every query.

### Database schema

Create Room entities for file metadata and recent activity.

A file record should contain:

- Database ID.
- Document URI or provider identifier.
- Relative path.
- Filename.
- Last known modification time.
- Last indexed size.
- Content fingerprint or hash.
- Index status.
- Last successful indexing timestamp.

Create a full-text virtual table with fields such as:

- File ID.
- Filename.
- Relative path.
- Full text content.

Use FTS5 if the selected Room version and SQLite driver support it. Verify actual support through compilation and a database integration test. If the chosen setup cannot provide FTS5 reliably, choose a supported bundled SQLite driver or another compatible implementation rather than silently assuming FTS5 exists.

### Indexing workflow

On initial setup:

1. Recursively discover candidate files.
2. Display scan progress.
3. Read each Markdown file.
4. Compute a content fingerprint.
5. Store metadata and indexable text.
6. Update the full-text index.
7. Remove stale index records after a successful reconciliation.
8. Display the number of indexed files and any failures.

Do not delete the old index before a replacement scan succeeds.

On subsequent launches:

- Reuse the existing index.
- Perform a lightweight reconciliation.
- Reindex files whose fingerprint or metadata indicates a change.
- Remove records for files that no longer exist.
- Allow manual full reindexing.

The index must be rebuildable from the vault at any time.

### Index performance

Run file reading, scanning and indexing off the main thread.

Use bounded concurrency so large vaults do not exhaust memory or overload document providers.

Do not load every file's contents into memory simultaneously.

Use transactions or batches for index updates. Make indexing cancellable and safe to restart.

### Index correctness

Add tests for:

- New file.
- Modified file.
- Deleted file.
- Renamed file.
- Empty file.
- Very large file.
- Unreadable file.
- Duplicate filename.
- File with Unicode characters.
- File with Windows and Unix newline conventions.
- File changed by another application.

---

## 7. Search implementation

Implement the search engine behind a `SearchRepository` interface.

### 7.1 Filename search

Support fuzzy filename matching so that partial and imperfect queries can find likely files.

Requirements:

- Case-insensitive by default.
- Prefer filename matches over path-only matches.
- Rank exact matches first.
- Rank prefix matches above weaker fuzzy matches.
- Display the full relative path for disambiguation.
- Support searching with or without the `.md` extension.
- Return results quickly as the query changes.

Use a small, well-tested fuzzy-matching implementation or a maintained library if one provides a clear advantage. Avoid expensive fuzzy matching over full document contents.

### 7.2 Full-text search

Use FTS5 for token-based full-text search.

Support:

- Multiple search terms.
- Phrase queries.
- Ranking by relevance.
- Result snippets.
- Highlighted matching terms.
- Filtering by filename or path where practical.
- Cancellation of obsolete queries as the user continues typing.

FTS5 syntax can differ from plain grep. The UI should explain the selected search mode and report invalid query syntax without crashing.

### 7.3 Grep-style literal search

Provide an explicit literal search mode that matches the entered text without interpreting it as FTS syntax or a regular expression.

Return:

- File path.
- Matching line.
- Line number.
- Matching context.

Literal search should be case-insensitive by default, with a case-sensitive option if practical.

### 7.4 Regex search

Provide a separate regex mode using a compatible JVM regular-expression implementation or another suitable engine.

Requirements:

- Show a clear error for invalid expressions.
- Support case-sensitive and case-insensitive options.
- Return matching line numbers and snippets.
- Avoid running regex matching on the UI thread.
- Bound work and provide cancellation for expensive queries.

Do not pretend that every regex is supported by SQLite FTS5. Regex search is a separate operation against indexed or freshly read file content.

### 7.5 Search consistency

The index can be stale if Obsidian or Vim modifies a file outside the app.

Before opening a search result, verify that the document can still be read. If appropriate, refresh its index entry.

Provide a manual reindex command.

If stale results are detected, show a recoverable message rather than crashing or silently opening the wrong document.

### 7.6 Search shortcuts

Implement:

- `Ctrl+K`: focus global search.
- `Enter`: open the selected result.
- Arrow keys: navigate results.
- `Escape`: close the current overlay or return to the previous view.
- `Ctrl+F`: find text in the open document.
- `Ctrl+S`: save the open document if explicit saving is used.

Ensure keyboard events do not interfere with ordinary text editing.

---

## 8. Tasks extracted from Markdown

Tasks must be discovered from existing files, not from a new mandatory task database.

### Supported syntax

At minimum, parse standard Markdown checkboxes:

```markdown
- [ ] Incomplete task
- [x] Completed task
- [x] Also completed
  - [ ] Nested task
```

Also support Obsidian-style due-date markers where they can be parsed safely:

```markdown
- [ ] Prepare project plan 📅 2026-10-30
```

Do not assume every checkbox is a task with a due date or priority.

### Task model

Represent each extracted task with:

- File reference.
- Relative path.
- Line number.
- Task text.
- Completion state.
- Due date, if parsed.
- Indentation or nesting depth.
- Optional detected tags or priority markers.

The model should identify the original source line so the app can modify that line rather than regenerating the whole document.

### Task UI

Provide filters:

- All.
- Open.
- Completed.
- Due today.
- Overdue.
- Recently modified, if practical.

Display the source filename for each task.

Tapping a task's text opens the corresponding file at the task's line.

Tapping its checkbox updates the original Markdown checkbox.

### Safe task updates

When completing a task:

1. Read the current source document.
2. Locate the target line.
3. Verify that the expected checkbox and task text still match.
4. Change only the checkbox character.
5. Write the updated document.
6. Refresh the index and task list.

If the source line changed in another app, do not blindly apply an update to the wrong task. Refresh and retry matching the task or show a conflict message.

Adding a task should append or insert a normal Markdown checkbox in a user-selected file. The user must not be forced to use a predefined task file.

Do not build recurring tasks, subtasks with independent databases, kanban boards or complex project management in the MVP.

---

## 9. Recent files and navigation

Store recent activity in the application database, not in the vault.

Track:

- Recently opened files.
- Last known cursor position.
- Last opened matching line, if useful.
- Recent search queries.
- Last used search mode.

Requirements:

- Restore the previous search after returning from a document.
- Restore the result list's scroll position.
- Restore the document's cursor and scroll position.
- Handle renamed or deleted documents gracefully.
- Keep history bounded to a reasonable configurable limit.

Do not store full document copies in recent-history records.

---

## 10. File editing and concurrent modifications

This is a critical data-integrity requirement.

When opening a file for editing, retain a baseline fingerprint or the original text.

Before saving:

1. Read the current document from the provider.
2. Compare it with the baseline.
3. If it is unchanged, write the edited content.
4. If it changed externally, detect the conflict.
5. Offer to reload the current file or review conflicting versions.
6. Never silently overwrite an external modification.

For MVP, a simple conflict dialog with reload and explicit overwrite options is acceptable. A sophisticated line-based merge is not required.

Use the provider's supported write operation and handle failures. Do not assume writes are atomic across all providers.

Do not claim that changes are synchronized with Obsidian until the write succeeds. Obsidian Sync or another existing sync mechanism may then propagate the actual file changes.

---

## 11. Settings and first-run experience

Implement a settings screen containing:

- Current vault.
- Switch vault.
- Rebuild index.
- Include hidden folders.
- Search case-sensitivity preference.
- Theme: system, dark or light.
- Index status and last successful scan.
- Version and diagnostic information.

First-run flow:

1. Welcome screen.
2. Select vault.
3. Validate access.
4. Build index.
5. Show progress and failures.
6. Enter search screen.

Allow the user to cancel indexing and resume later.

If the user has no vault selected, do not display a nonfunctional search screen.

---

## 12. Testing strategy

Use unit tests for domain logic and instrumentation tests for Android integration.

### Unit tests

**Filename search**

- Exact match.
- Prefix match.
- Fuzzy match.
- Duplicate filenames.
- Unicode names.

**Full-text search**

- Single term.
- Multiple terms.
- Phrase.
- Special characters.
- Empty query.
- Invalid FTS syntax.
- Relevance ranking.
- Correct snippets.

**Regex search**

- Valid expression.
- Invalid expression.
- Case sensitivity.
- Multiple matches in one file.
- Cancellation.

**Markdown tasks**

- Open checkbox.
- Completed checkbox.
- Uppercase completion marker.
- Nested tasks.
- Due dates.
- Checkbox-like text in code blocks.
- Task text containing unusual characters.

**File editing**

- Checkbox-only change.
- Preserve unrelated lines.
- Preserve newline conventions.
- External modification conflict.
- File read/write failure.

**Indexing**

- Initial scan.
- Incremental update.
- Deletion.
- Rename.
- Failed scan.
- Rebuild after database deletion.

### Instrumentation tests

Test:

- Vault selection through the document picker.
- Persistent URI permission handling where testable.
- Reading and writing files using a test document provider.
- Opening a result at a matching line.
- Completing a task and verifying the Markdown content.
- Navigation and adaptive layout behavior.

### UI tests

Verify:

- First-run experience.
- Search interactions.
- Empty results.
- Loading indicators.
- Error handling.
- Task completion.
- Document navigation.
- Dark and light themes.

Do not rely solely on screenshot tests. Functional tests must verify the actual file contents.

---

## 13. Build and install workflow

The coding agent must inspect the environment before selecting build commands.

### Step 1: Inspect the environment

Run and report:

```bash
pwd
git status --short
java -version
./gradlew --version
adb version
adb devices -l
```

If no Gradle wrapper exists yet, initialize a valid Android project and generate the wrapper.

Check whether Android SDK tools and a compatible JDK are installed. If not, document the missing prerequisite and use the available environment to resolve it when possible.

Do not replace existing user files or delete unrelated repository content.

### Step 2: Create the project

Generate a valid Android Gradle project containing:

- `settings.gradle.kts`
- Root `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradlew` and `gradlew.bat`
- `gradle/wrapper/`
- App module build file.
- Android manifest.
- Kotlin sources.
- Resources and themes.
- Tests.

Use the Gradle wrapper, not a machine-global Gradle installation.

### Step 3: Build incrementally

Run after each major milestone:

```bash
./gradlew assembleDebug
```

Run tests using:

```bash
./gradlew testDebugUnitTest
```

If an emulator or device is available:

```bash
./gradlew connectedDebugAndroidTest
```

Resolve compilation and test failures before adding more features.

Do not suppress errors, disable tests, or remove required functionality just to get a green build.

### Step 4: Inspect the generated APK

The expected default path is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Verify that the file exists and is a valid APK.

Use Android SDK tools when available:

```bash
$ANDROID_HOME/build-tools/<installed-version>/aapt dump badging \
  app/build/outputs/apk/debug/app-debug.apk
```

If `aapt` is unavailable, use the installed SDK's equivalent APK inspection tool.

Verify the package ID, version, minimum SDK and launch activity.

### Step 5: Install through ADB

First inspect the connected device:

```bash
adb devices -l
```

Confirm that the expected Pixel device appears as `device`, not `unauthorized` or `offline`.

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If a previous installation has a different signing key, diagnose the signature mismatch. Do not automatically uninstall the app if doing so would destroy its data.

### Step 6: Launch the app

Launch using the actual package and launcher activity identified from the manifest.

For example, if the launcher activity is `dev.idefix.app.MainActivity`:

```bash
adb shell am start -n dev.idefix.app/.MainActivity
```

Collect diagnostics if the app crashes:

```bash
adb logcat -d -t 2000
```

Filter logs as appropriate to the package and Android runtime.

Verify that the app reaches the first-run screen and remains running.

### Step 7: End-to-end smoke test

Using the connected device:

1. Open the app.
2. Select a disposable test vault.
3. Wait for indexing to complete.
4. Search for a known filename.
5. Search for a phrase in a known file.
6. Open a matching result.
7. Edit and save a disposable Markdown file.
8. Verify the resulting file content.
9. Complete a test task and verify the original checkbox changed.
10. Restart the app and verify that vault access and the index remain available.
11. Expand the inner display and verify the adaptive layout if the device or emulator supports it.

Do not modify or delete the user's real notes during automated smoke tests. Create a temporary test vault or use a disposable test directory.

If the physical device is unavailable, build the APK and run available emulator tests. Clearly distinguish a successful build from a verified physical-device installation.

---

## 14. Recommended implementation milestones

Work in this order. Each milestone must compile before proceeding.

### Milestone A — Buildable foundation

- Inspect the repository and toolchain.
- Create or repair the Android project.
- Configure Kotlin, Compose, Material 3 and adaptive dependencies.
- Implement the application theme and basic navigation.
- Produce the first debug APK.

Acceptance: the app builds, installs and launches.

### Milestone B — Vault access

- Implement vault selection.
- Persist document URI permissions.
- Implement file discovery and reading.
- Add error handling and vault settings.

Acceptance: the app can list and read Markdown files from a selected test vault.

### Milestone C — Index and filename search

- Implement Room schema.
- Implement recursive indexing.
- Implement filename search and result ranking.
- Add index progress and rebuild actions.

Acceptance: files can be found by partial filenames without reopening the entire vault for each query.

### Milestone D — Full-text and regex search

- Implement and verify the FTS5 setup.
- Implement snippets and relevance ranking.
- Add literal and regex modes.
- Add matching line navigation.

Acceptance: known search queries return the correct files, snippets and line numbers.

### Milestone E — Reader and source editor

- Integrate the Markdown renderer.
- Implement source editing.
- Implement safe writes and external-change detection.
- Add cursor and scroll restoration.

Acceptance: Markdown files can be read, modified and reopened without losing unrelated content.

### Milestone F — Task management

- Implement checkbox parsing.
- Extract tasks across the vault.
- Implement task filters.
- Update original Markdown checkboxes safely.
- Add task creation.

Acceptance: task operations change the actual source files.

### Milestone G — Foldable UI and polish

- Implement adaptive list-detail layout.
- Tune cover and inner display behavior.
- Add keyboard navigation and shortcuts.
- Complete theme, accessibility and error states.

Acceptance: both compact and expanded layouts are usable, including with a hardware keyboard.

### Milestone H — Release verification

- Run all unit tests.
- Run available instrumentation tests.
- Build the debug APK.
- Install through ADB.
- Launch and smoke-test.
- Fix all blocking issues.
- Report the APK path and the exact install command.

Acceptance: all required criteria in Definition of done are met.

---

## 15. Engineering constraints

The agent must follow these constraints throughout implementation:

1. Do not invent an organizational system for the vault.
2. Do not move or rename existing files automatically.
3. Do not store task state separately from the Markdown checkboxes.
4. Do not use a database copy as the authoritative document content.
5. Do not rewrite entire files merely to toggle a task.
6. Do not block the main thread during scanning, search or indexing.
7. Do not assume Storage Access Framework URIs are filesystem paths.
8. Do not silently overwrite concurrent external modifications.
9. Do not leave core features as placeholder buttons.
10. Do not report a successful device installation unless ADB confirms it.
11. Do not stop after the first successful compilation.
12. Do not introduce unnecessary network access or cloud dependencies.

Prefer simple, testable implementations over abstractions that are not needed yet.

---

## 16. Final deliverables

When finished, provide:

- Complete Android project source.
- Gradle wrapper and dependency configuration.
- Unit and instrumentation tests.
- README with setup and build instructions.
- APK at `app/build/outputs/apk/debug/app-debug.apk`.
- Exact ADB installation command.
- Build and test results.
- Device installation and launch verification status.
- A concise list of known limitations.

The final report must explicitly state which milestones were completed and which, if any, could not be verified.

**Start with Milestone A, then proceed through all remaining milestones without waiting for approval between milestones.** If a genuine environment blocker occurs, diagnose it, complete all work that remains possible, and report the precise blocker rather than claiming completion.
