# Idefix

Idefix is a native Android app for searching, reading, editing and managing tasks in an existing Obsidian vault. It is built for the Google Pixel 10 Pro Fold: a single-pane, bottom-navigation layout on the cover display and a navigation rail with list/detail panes on the inner display.

The vault is accessed through Android's Storage Access Framework. Markdown files remain the source of truth; the app database only stores an index, file metadata and recent-activity state.

## Requirements

- JDK 21
- Android SDK 37 (compile SDK), build-tools 36/37
- An Android device or emulator with API 29+
- `adb` for installation

The project uses the Gradle wrapper; a machine-wide Gradle installation is not required.

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If `ANDROID_HOME` and `ANDROID_SDK_ROOT` both point to different SDK copies, unset
one or make them identical before invoking Gradle.

## Tests

Unit tests (JVM):

```bash
./gradlew testDebugUnitTest
```

Instrumentation tests (device/emulator must be attached):

```bash
./gradlew connectedDebugAndroidTest
```

The instrumentation suite includes a debug-only SAF-shaped document provider
(`app/src/debug/.../TestDocumentsProvider.kt`) that is used to exercise scanning,
reading, writing, indexing, FTS5, task editing and external-change reconciliation
without touching a real vault.

## Install and launch

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.idefix.app/.MainActivity
```

The first launch asks for a vault folder through `ACTION_OPEN_DOCUMENT_TREE`.
Read/write access is persisted with `takePersistableUriPermission`. No broad
storage permission is requested.

## Features

- Neovim-style first-key search commands when the field is empty: `f` filename,
  `g` full-text grep, `n` new note from a slugified title, `m` files starting
  with `Modul_`. The same commands are available as touch chips.
- Fuzzy filename search, ranked exact → prefix → contains → subsequence/fuzzy.
- FTS5 full-text search with relevance ranking and snippets, using the bundled
  SQLite driver (FTS5 support is verified by an instrumentation test).
- Grep-style literal search and JVM regex search with line numbers and context.
- Opening a result jumps to the matching line in source mode.
- Markdown preview with GFM support through
  `multiplatform-markdown-renderer-m3`; task lists, tables, code fences and links.
- YAML frontmatter is shown as a compact, collapsible metadata card with field rows,
  tag chips and long values; the raw block is preserved in source mode.
- Preview typography is tuned for phone reading instead of using display-size
  headings: H1 27sp, H2 22sp, H3 19sp, body 16sp.
- Plain-source editing with explicit save, find-in-note, cursor/scroll restoration
  and external-modification detection before overwrite.
- Task discovery from Markdown checkboxes (including nested tasks, uppercase
  completion markers and Obsidian `📅 YYYY-MM-DD` due dates), with safe
  one-character checkbox updates and task creation in any note.
- Recent files, cursor positions, scroll positions and recent searches stored in
  the local Room database, with a configurable history limit.
- Adaptive Material 3 layout: bottom navigation below 840dp, navigation rail and
  list/detail panes from 840dp; two panes from 600dp. Both folded/cover and
  unfolded/inner layouts are usable.
- Matcha dark sea theme by default (the btop palette), with a true-black canvas
  and near-black sea-glass surfaces for AMOLED displays; system, light and the
  original dark theme, plus dynamic Material You color, are selectable.
- Keyboard shortcuts: `Ctrl+K` search, `Ctrl+S` save, `Ctrl+F` find in note,
  `Escape` close/back, arrow keys and `Enter` in search results.

## Architecture

```text
app/src/main/java/dev/idefix/app/
├── data/
│   ├── database/     Room entities, DAOs, manually created FTS5 table
│   ├── index/        scanning/reconciliation, rename detection
│   ├── recents/      recent files, positions, searches
│   ├── search/       filename, FTS5, literal and regex search
│   ├── settings/     DataStore preferences
│   ├── tasks/        task extraction and safe checkbox updates
│   └── vault/        SAF recursive scanner and file operations
├── domain/
│   ├── model/        immutable models
│   ├── repository/   UI-independent repository interfaces
│   ├── search/       pure filename/FTS/content search helpers
│   ├── tasks/        Markdown checkbox parser/editor
│   └── editor/       external-change save guard
├── ui/               Compose screens, view models, theme, components
└── util/             line preservation, hashing, MIME helpers
```

`AppContainer` performs manual dependency injection. All file access, scanning,
indexing, search and task work runs off the main thread with coroutines.

### Data integrity

- The database never replaces the Markdown file as the authoritative content.
- Task toggles modify exactly one checkbox character at the recorded line after
  verifying that the line still contains the expected task and state.
- Saves read the file from the provider first; an external change produces a
  conflict dialog with **Reload** and explicit **Overwrite** options.
- A failed scan does not drop the previous index. Stale records are removed only
  after a scan completes successfully.
- The index can be rebuilt from the vault from Search or Settings.

## Known limitations

- Files larger than 16 MB cannot be opened/indexed; regex search examines at most
  20,000 characters per line to bound pathological expressions.
- Conflict resolution is reload-or-overwrite; there is no line-based three-way
  merge.
- Full-text and regex search query the stored index. If Obsidian/Vim changed a
  file, open the result or rebuild the index; opening a note refreshes its entry.
- There is no continuous file-system observer and no WorkManager background job.
  Indexing runs in a coroutine while the app is alive and is resumable/rebuildable.
- SAF providers vary in rename/delete/write support; unsupported operations are
  reported in the UI. Writes are not necessarily atomic across all providers.
- Internal navigation follows standard Markdown links. Obsidian `[[wiki links]]`
  and heading anchors are not resolved.
- The adaptive splitter is not draggable and there is no third pane.
- The preview renders GFM; editing is plain Markdown source by design.
