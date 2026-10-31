package dev.idefix.app.domain.model

import android.net.Uri

/** A directory selected through the Storage Access Framework for lecture material. */
data class LectureInfo(
    val uri: String,
    val displayName: String,
    val readable: Boolean = true,
)

enum class LectureEntryKind(val label: String) {
    DIRECTORY("Folder"),
    PDF("PDF"),
    CODE("Code"),
    TEXT("Text"),
    IMAGE("Image"),
    AUDIO("Audio"),
    VIDEO("Video"),
    ARCHIVE("Archive"),
    OTHER("File"),
    ;

    val isText: Boolean get() = this == CODE || this == TEXT

    val defaultMimeType: String
        get() = when (this) {
            DIRECTORY -> "vnd.android.document/directory"
            PDF -> "application/pdf"
            CODE, TEXT -> "text/plain"
            IMAGE -> "image/*"
            AUDIO -> "audio/*"
            VIDEO -> "video/*"
            ARCHIVE -> "application/octet-stream"
            OTHER -> "application/octet-stream"
        }
}

/**
 * One child of the lectures directory. [relativePath] is relative to the
 * selected lectures folder and uses "/" separators.
 */
data class LectureEntry(
    val documentId: String,
    val name: String,
    val relativePath: String,
    val uri: String,
    val isDirectory: Boolean,
    val mimeType: String? = null,
    val lastModified: Long? = null,
    val size: Long? = null,
    val kind: LectureEntryKind = LectureEntryKind.OTHER,
) {
    val filename: String get() = name
    val parentPath: String get() = relativePath.substringBeforeLast('/', "")
    val uriObject: Uri get() = Uri.parse(uri)
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

/** Maps file names and provider MIME types to the way a lecture file is opened. */
object LectureKinds {
    private val codeExtensions = setOf(
        "kt", "kts", "java", "py", "pyw", "c", "h", "cc", "cpp", "cxx", "hpp", "hh",
        "cs", "fs", "vb", "js", "mjs", "cjs", "ts", "tsx", "jsx", "rs", "go", "rb",
        "php", "swift", "m", "mm", "scala", "sh", "bash", "zsh", "fish", "sql",
        "html", "htm", "css", "scss", "sass", "less", "json", "jsonc", "yaml", "yml",
        "toml", "xml", "gradle", "properties", "ini", "cfg", "conf", "tex", "sty",
        "cls", "r", "jl", "lua", "pl", "pm", "dart", "ex", "exs", "erl", "hrl",
        "hs", "lhs", "clj", "cljs", "cljc", "edn", "elm", "nim", "zig", "v", "sv",
        "vhd", "vhdl", "asm", "s", "gitignore", "dockerfile", "makefile", "cmake",
    )
    private val textExtensions = setOf(
        "txt", "md", "markdown", "rst", "org", "log", "csv", "tsv", "bib", "srt",
        "vtt", "adoc", "asciidoc", "text",
    )
    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "tif", "tiff")
    private val audioExtensions = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v")
    private val archiveExtensions = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar")

    private val codeNames = setOf(
        "makefile", "dockerfile", "cmakelists.txt", "rakefile", "gemfile", "procfile",
    )

    fun classify(name: String, mimeType: String? = null): LectureEntryKind {
        val lower = name.lowercase()
        val extension = lower.substringAfterLast('.', "")
        return when {
            lower == "pdf" || extension == "pdf" || mimeType == "application/pdf" -> LectureEntryKind.PDF
            lower in codeNames || extension in codeExtensions -> LectureEntryKind.CODE
            extension in textExtensions -> LectureEntryKind.TEXT
            extension in imageExtensions -> LectureEntryKind.IMAGE
            extension in audioExtensions -> LectureEntryKind.AUDIO
            extension in videoExtensions -> LectureEntryKind.VIDEO
            extension in archiveExtensions -> LectureEntryKind.ARCHIVE
            mimeType?.startsWith("text/") == true -> LectureEntryKind.TEXT
            mimeType?.startsWith("image/") == true -> LectureEntryKind.IMAGE
            mimeType?.startsWith("audio/") == true -> LectureEntryKind.AUDIO
            mimeType?.startsWith("video/") == true -> LectureEntryKind.VIDEO
            mimeType == "application/zip" || mimeType == "application/x-tar" -> LectureEntryKind.ARCHIVE
            else -> LectureEntryKind.OTHER
        }
    }
}
