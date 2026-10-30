package dev.idefix.app.util

/** A single top-level YAML frontmatter property. */
data class FrontmatterField(
    val key: String,
    val values: List<String>,
    val wasList: Boolean = false,
) {
    val displayValue: String
        get() = values.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "—" }

    val isLongValue: Boolean
        get() = displayValue.length > 48 ||
            key.contains("hash", ignoreCase = true) ||
            key.endsWith("_id", ignoreCase = true) ||
            key.contains("sha", ignoreCase = true)
}

data class Frontmatter(
    val fields: List<FrontmatterField>,
    val body: String,
    val raw: String,
) {
    val tags: List<String>
        get() = fields.firstOrNull { it.key.equals("tags", ignoreCase = true) || it.key.equals("tag", ignoreCase = true) }
            ?.values
            ?.filter { it.isNotBlank() }
            .orEmpty()
}

/**
 * A deliberately small, safe YAML frontmatter reader. It handles the common
 * Obsidian shape: top-level scalars, inline arrays and indented lists. Anything
 * more exotic is displayed as its raw scalar text instead of being interpreted.
 */
object FrontmatterParser {

    private const val MAX_LINES = 500

    fun parse(content: String): Frontmatter? {
        val lines = MarkdownLines.split(content)
        if (lines.isEmpty()) return null
        val firstText = lines.first().text.removePrefix("\uFEFF").trimEnd()
        if (firstText != "---") return null
        val closing = lines.drop(1).take(MAX_LINES).firstOrNull {
            val candidate = it.text.trimEnd()
            candidate == "---" || candidate == "..."
        } ?: return null
        val raw = content.substring(lines.first().end, closing.start)
        val body = content.substring(closing.end)
        return Frontmatter(
            fields = parseFields(raw),
            body = body,
            raw = raw,
        )
    }

    private fun parseFields(raw: String): List<FrontmatterField> {
        val fields = ArrayList<FrontmatterField>()
        var key: String? = null
        var values = ArrayList<String>()
        var blockScalar = false

        fun flush() {
            val current = key ?: return
            fields += FrontmatterField(
                key = current,
                values = values.toList(),
                wasList = values.size > 1,
            )
            key = null
            values = ArrayList()
            blockScalar = false
        }

        for (line in raw.lineSequence()) {
            if (line.isBlank()) continue
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) continue
            val indent = line.indexOfFirst { it != ' ' && it != '\t' }.coerceAtLeast(0)
            val isListItem = trimmed == "-" || trimmed.startsWith("- ")
            if (isListItem && key != null) {
                val item = unquote(trimmed.removePrefix("-").trim())
                if (item.isNotEmpty()) values += item
                continue
            }
            if (indent == 0) {
                val colon = line.indexOf(':')
                if (colon <= 0) {
                    if (key != null) values += unquote(trimmed)
                    continue
                }
                flush()
                key = line.substring(0, colon).trim().ifEmpty { continue }
                val rest = line.substring(colon + 1).trim()
                blockScalar = rest.startsWith("|") || rest.startsWith(">")
                if (rest.isNotEmpty() && !blockScalar) {
                    values.addAll(parseScalar(key!!, rest))
                }
            } else {
                if (key == null) continue
                // Indented continuation, nested map or block scalar line.
                val clean = unquote(trimmed)
                if (clean.isNotEmpty()) values += clean
            }
        }
        flush()
        return fields.take(100)
    }

    private fun parseScalar(key: String, raw: String): List<String> {
        val value = raw.trim()
        if (value.startsWith("[") && value.endsWith("]")) {
            return splitInlineList(value.substring(1, value.length - 1))
        }
        if (key.equals("tags", ignoreCase = true) || key.equals("tag", ignoreCase = true)) {
            if (value.contains(",")) return splitInlineList(value)
        }
        return listOf(unquote(value))
    }

    private fun splitInlineList(raw: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (char in raw) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                char == '\\' -> {
                    current.append(char)
                    escaped = true
                }

                quote != null -> {
                    if (char == quote) quote = null
                    current.append(char)
                }

                char == '"' || char == '\'' -> {
                    quote = char
                    current.append(char)
                }

                char == ',' -> {
                    result += unquote(current.toString().trim())
                    current.clear()
                }

                else -> current.append(char)
            }
        }
        if (current.isNotBlank()) result += unquote(current.toString().trim())
        return result.filter { it.isNotEmpty() }
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }
}
