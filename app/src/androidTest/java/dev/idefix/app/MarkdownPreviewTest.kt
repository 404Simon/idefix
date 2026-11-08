package dev.idefix.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.idefix.app.domain.model.ThemeMode
import dev.idefix.app.ui.editor.MarkdownDocument
import dev.idefix.app.ui.editor.MarkdownPreview
import dev.idefix.app.ui.theme.IdefixTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sample = """
        ---
        title: Preview demo
        tags: [design, markdown, frontmatter]
        date: 2026-10-30
        publish: false
        ---
        # Primary heading

        Body text.

        ## Secondary heading
    """.trimIndent()

    private val document = runBlocking { MarkdownDocument.parse(sample) }

    @Test
    fun frontmatterIsRenderedAsMetadataAndHiddenFromMarkdown() {
        composeRule.setContent {
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                MarkdownPreview(document = document, onInternalLink = {})
            }
        }
        composeRule.onNodeWithText("Frontmatter").assertIsDisplayed()
        composeRule.onNodeWithText("Preview demo").assertIsDisplayed()
        composeRule.onNodeWithText("design").assertIsDisplayed()
        composeRule.onNodeWithText("2026-10-30").assertIsDisplayed()
        composeRule.onNodeWithText("Primary heading").assertIsDisplayed()
        composeRule.onNodeWithText("Secondary heading").assertIsDisplayed()
        composeRule.onAllNodesWithText("---").assertCountEquals(0)
        composeRule.onAllNodesWithText("title: Preview demo").assertCountEquals(0)
    }

    @Test
    fun frontmatterCanBeCollapsed() {
        composeRule.setContent {
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                MarkdownPreview(document = document, onInternalLink = {})
            }
        }
        composeRule.onNodeWithContentDescription("Collapse frontmatter").performClick()
        composeRule.onAllNodesWithText("Preview demo").assertCountEquals(0)
        composeRule.onNodeWithText("design").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Expand frontmatter").assertIsDisplayed()
    }
}
