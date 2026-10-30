package dev.idefix.app.util

/**
 * Line/offset helpers that never normalise content. Editing operations use
 * offsets into the original string so CRLF files and unrelated lines survive
 * untouched.
 */
object MarkdownLines {

    data class LineRange(
        val number: Int,
        val start: Int,
        val contentEnd: Int,
        val end: Int,
        val text: String,
    )

    fun split(text: String): List<LineRange> {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<LineRange>(text.count { it == '\n' } + 1)
        var start = 0
        var number = 1
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                val contentEnd = if (index > start && text[index - 1] == '\r') index - 1 else index
                result += LineRange(number, start, contentEnd, index + 1, text.substring(start, contentEnd))
                start = index + 1
                number++
            }
            index++
        }
        if (start < text.length) {
            val contentEnd = if (text.endsWith("\r")) text.length - 1 else text.length
            result += LineRange(number, start, contentEnd, text.length, text.substring(start, contentEnd))
        }
        return result
    }

    fun lineCount(text: String): Int = split(text).size.coerceAtLeast(if (text.isEmpty()) 1 else 1)

    fun lineRange(text: String, lineNumber: Int): LineRange? =
        split(text).firstOrNull { it.number == lineNumber }

    fun lineText(text: String, lineNumber: Int): String? =
        lineRange(text, lineNumber)?.text

    fun offsetForLine(text: String, lineNumber: Int): Int {
        val clamped = lineNumber.coerceAtLeast(1)
        return split(text).firstOrNull { it.number == clamped }?.start ?: text.length
    }

    fun lineNumberForOffset(text: String, offset: Int): Int {
        val clamped = offset.coerceIn(0, text.length)
        var line = 1
        var index = 0
        while (index < clamped) {
            if (text[index] == '\n') line++
            index++
        }
        return line
    }

    fun detectNewline(text: String): String = if (text.contains("\r\n")) "\r\n" else "\n"

    fun ensureTrailingNewline(text: String): String {
        if (text.isEmpty()) return text
        return if (text.endsWith("\n")) text else text + detectNewline(text)
    }

    /** Returns a one-based line number for the first occurrence of [needle]. */
    fun findLine(text: String, needle: String, ignoreCase: Boolean = true): Int? {
        if (needle.isEmpty()) return null
        val index = text.indexOf(needle, ignoreCase = ignoreCase)
        return if (index < 0) null else lineNumberForOffset(text, index)
    }
}
