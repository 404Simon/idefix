package dev.idefix.app.ui.editor

import androidx.compose.runtime.Immutable
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import dev.idefix.app.util.Frontmatter
import dev.idefix.app.util.FrontmatterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A note whose frontmatter and Markdown have already been parsed for rendering.
 *
 * The renderer's per-composition state helper parses its input on every entry
 * into composition, so a tab switch rebuilds the Markdown AST on the main thread.
 * Parsing once here, off the main thread, lets the preview render a cached result
 * and keeps tab switches free of Markdown parsing.
 */
@Immutable
data class MarkdownDocument(
    val content: String,
    val frontmatter: Frontmatter?,
    val markdown: State,
) {
    companion object {
        /** Parses [content] on [Dispatchers.Default], stripping frontmatter from the body. */
        suspend fun parse(content: String): MarkdownDocument = withContext(Dispatchers.Default) {
            val frontmatter = FrontmatterParser.parse(content)
            MarkdownDocument(
                content = content,
                frontmatter = frontmatter,
                markdown = parseMarkdown(frontmatter?.body ?: content),
            )
        }
    }
}
