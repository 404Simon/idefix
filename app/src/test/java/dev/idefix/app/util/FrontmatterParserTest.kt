package dev.idefix.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontmatterParserTest {

    @Test
    fun parsesScalarsAndBody() {
        val content = """
            ---
            title: My note
            date: 2026-10-30
            publish: false
            ---
            # Heading

            Body text.
        """.trimIndent()
        val parsed = FrontmatterParser.parse(content)!!
        assertEquals(listOf("title", "date", "publish"), parsed.fields.map { it.key })
        assertEquals("My note", parsed.fields[0].displayValue)
        assertEquals("false", parsed.fields[2].displayValue)
        assertTrue(parsed.body.startsWith("# Heading"))
    }

    @Test
    fun parsesInlineTags() {
        val parsed = FrontmatterParser.parse(
            """
            ---
            tags: [work, obsidian, "long tag"]
            ---
            body
            """.trimIndent(),
        )!!
        assertEquals(listOf("work", "obsidian", "long tag"), parsed.tags)
    }

    @Test
    fun parsesBlockTags() {
        val parsed = FrontmatterParser.parse(
            """
            ---
            tags:
              - work
              - personal
            ---
            body
            """.trimIndent(),
        )!!
        assertEquals(listOf("work", "personal"), parsed.tags)
    }

    @Test
    fun handlesCrlf() {
        val parsed = FrontmatterParser.parse("---\r\ntitle: Windows\r\n---\r\nBody\r\n")!!
        assertEquals("Windows", parsed.fields.single().displayValue)
        assertEquals("Body\r\n", parsed.body)
    }

    @Test
    fun ignoresUnclosedFrontmatter() {
        assertNull(FrontmatterParser.parse("---\ntitle: Missing close\n# Heading"))
    }

    @Test
    fun ignoresFrontmatterLaterInDocument() {
        assertNull(FrontmatterParser.parse("# Heading\n\n---\ntitle: nope\n---"))
    }

    @Test
    fun handlesBom() {
        val parsed = FrontmatterParser.parse("\uFEFF---\ntitle: BOM\n---\nBody")!!
        assertEquals("BOM", parsed.fields.single().displayValue)
        assertEquals("Body", parsed.body)
    }

    @Test
    fun nestedValuesAreShownSafely() {
        val parsed = FrontmatterParser.parse(
            """
            ---
            cover:
              image: cover.png
              width: 200
            aliases:
              - First
              - Second
            ---
            """.trimIndent(),
        )!!
        val cover = parsed.fields.first { it.key == "cover" }
        assertTrue(cover.displayValue.contains("image: cover.png"))
        assertEquals(listOf("First", "Second"), parsed.fields.first { it.key == "aliases" }.values)
    }

    @Test
    fun emptyFrontmatterHasNoFieldsButRemovesBlock() {
        val parsed = FrontmatterParser.parse("---\n---\n# Body")!!
        assertTrue(parsed.fields.isEmpty())
        assertEquals("# Body", parsed.body)
    }

    @Test
    fun indentedDelimiterInsideBlockDoesNotCloseFrontmatter() {
        val parsed = FrontmatterParser.parse(
            """
            ---
            description: |
              first
              ---
              still metadata
            ---
            # Body
            """.trimIndent(),
        )!!
        assertTrue(parsed.fields.first { it.key == "description" }.displayValue.contains("still metadata"))
        assertTrue(parsed.body.startsWith("# Body"))
    }

    @Test
    fun longHashValuesAreMarkedForCompactRendering() {
        val parsed = FrontmatterParser.parse(
            """
            ---
            body_sha256: ${"a".repeat(64)}
            ---
            """.trimIndent(),
        )!!
        assertTrue(parsed.fields.single().isLongValue)
    }
}
