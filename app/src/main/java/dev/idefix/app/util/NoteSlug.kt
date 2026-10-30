package dev.idefix.app.util

/**
 * Turns a human note title into a safe Markdown file name, matching the
 * Obsidian/Neovim workflow: whitespace becomes an underscore and German
 * umlauts are transliterated. The `.md` extension is added by the vault layer.
 */
object NoteSlug {

    private val umlauts = mapOf(
        'ä' to "ae",
        'ö' to "oe",
        'ü' to "ue",
        'ß' to "ss",
        'Ä' to "Ae",
        'Ö' to "Oe",
        'Ü' to "Ue",
    )

    private val forbidden = Regex("""[\\/:*?"<>|]""")
    private val whitespace = Regex("""\s+""")
    private val repeatedUnderscores = Regex("_+")

    fun slugify(title: String): String {
        val transliterated = buildString(title.trim().length) {
            for (char in title.trim()) {
                append(umlauts[char] ?: char.toString())
            }
        }
        var slug = transliterated
            .replace(whitespace, "_")
            .replace(forbidden, "_")
            .replace(repeatedUnderscores, "_")
        slug = when {
            slug.endsWith(".markdown", ignoreCase = true) -> slug.dropLast(".markdown".length)
            slug.endsWith(".md", ignoreCase = true) -> slug.dropLast(".md".length)
            else -> slug
        }
        return slug.trim('_', '.', ' ')
    }
}
