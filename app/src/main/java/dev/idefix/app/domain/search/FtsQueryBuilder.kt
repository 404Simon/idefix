package dev.idefix.app.domain.search

/**
 * Converts a human query into safe FTS5 syntax. Plain terms and quoted phrases
 * are supported; explicit AND/OR/NOT operators are passed through. Everything
 * else is quoted so punctuation cannot become FTS syntax by accident.
 */
object FtsQueryBuilder {

    data class Result(
        val query: String? = null,
        val error: String? = null,
        val plainTerms: List<String> = emptyList(),
    )

    private val operators = setOf("AND", "OR", "NOT")

    fun build(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result(error = "Enter a search term")

        val tokens = ArrayList<String>()
        val plain = ArrayList<String>()
        var i = 0
        var expectTerm = true
        var lastWasOperator = false

        while (i < trimmed.length) {
            when {
                trimmed[i].isWhitespace() -> i++

                trimmed[i] == '"' -> {
                    val end = findClosingQuote(trimmed, i + 1)
                    if (end < 0) return Result(error = "Unterminated quoted phrase")
                    val phrase = trimmed.substring(i + 1, end).trim()
                    if (phrase.isEmpty()) return Result(error = "Empty quoted phrase")
                    tokens += "\"" + phrase.replace("\"", "\"\"") + "\""
                    plain += phrase
                    expectTerm = false
                    lastWasOperator = false
                    i = end + 1
                }

                else -> {
                    var end = i
                    while (end < trimmed.length && !trimmed[end].isWhitespace() && trimmed[end] != '"') end++
                    var word = trimmed.substring(i, end)
                    if (word.uppercase() in operators) {
                        if (expectTerm && word.uppercase() != "NOT") {
                            return Result(error = "Query cannot start with ${word.uppercase()}")
                        }
                        if (!expectTerm && lastWasOperator) {
                            return Result(error = "Two operators in a row")
                        }
                        tokens += word.uppercase()
                        lastWasOperator = true
                        expectTerm = true
                    } else {
                        if (lastWasOperator && !expectTerm) {
                            return Result(error = "Missing term after ${tokens.last()}")
                        }
                        val prefix = word.endsWith("*")
                        if (prefix) word = word.dropLast(1)
                        word = word.trim()
                        if (word.isNotEmpty()) {
                            val escaped = word.replace("\"", "\"\"")
                            tokens += if (prefix) "\"$escaped\"*" else "\"$escaped\""
                            plain += word
                        }
                        expectTerm = false
                        lastWasOperator = false
                    }
                    i = end
                }
            }
        }

        if (tokens.isEmpty()) return Result(error = "Enter a search term")
        if (tokens.last().uppercase() in operators) {
            return Result(error = "Query cannot end with ${tokens.last().uppercase()}")
        }
        return Result(query = tokens.joinToString(" "), plainTerms = plain.distinct())
    }

    private fun findClosingQuote(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            if (text[i] == '"') return i
            i++
        }
        return -1
    }

    /** Builds a prefix-friendly query used while the user is still typing. */
    fun buildPrefix(input: String): Result {
        val base = build(input)
        if (base.error != null) return base
        val words = base.plainTerms
        if (words.isEmpty()) return base
        val last = words.last()
        val quoted = words.dropLast(1).map { "\"" + it.replace("\"", "\"\"") + "\"" }
        val query = (quoted + "\"${last.replace("\"", "\"\"")}\"*").joinToString(" ")
        return base.copy(query = query)
    }
}
