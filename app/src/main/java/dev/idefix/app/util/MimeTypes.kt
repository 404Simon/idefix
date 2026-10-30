package dev.idefix.app.util

object MimeTypes {
    const val MARKDOWN = "text/markdown"

    fun isMarkdownName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".md") || lower.endsWith(".markdown")
    }

    fun isHiddenName(name: String): Boolean = name.startsWith(".") ||
        name == "__MACOSX" ||
        name.endsWith("~") ||
        name.endsWith(".swp") ||
        name.endsWith(".tmp")

    fun normalizeMarkdownName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Untitled.md"
        return if (isMarkdownName(trimmed)) trimmed else "$trimmed.md"
    }

    fun displayNameWithoutExtension(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".markdown") -> name.dropLast(".markdown".length)
            lower.endsWith(".md") -> name.dropLast(".md".length)
            else -> name
        }
    }
}
