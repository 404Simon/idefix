package dev.idefix.app.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsQueryBuilderTest {

    @Test
    fun singleTermIsQuoted() {
        val result = FtsQueryBuilder.build("walrus")
        assertEquals("\"walrus\"", result.query)
        assertEquals(listOf("walrus"), result.plainTerms)
    }

    @Test
    fun multipleTermsAreImplicitAnd() {
        val result = FtsQueryBuilder.build("project plan")
        assertEquals("\"project\" \"plan\"", result.query)
        assertEquals(listOf("project", "plan"), result.plainTerms)
    }

    @Test
    fun quotedPhraseIsPreserved() {
        val result = FtsQueryBuilder.build("\"project plan\"")
        assertEquals("\"project plan\"", result.query)
        assertEquals(listOf("project plan"), result.plainTerms)
    }

    @Test
    fun explicitOperatorsPassThrough() {
        val result = FtsQueryBuilder.build("alpha OR beta")
        assertEquals("\"alpha\" OR \"beta\"", result.query)
    }

    @Test
    fun leadingOperatorIsRejected() {
        assertNotNull(FtsQueryBuilder.build("OR beta").error)
    }

    @Test
    fun trailingOperatorIsRejected() {
        assertNotNull(FtsQueryBuilder.build("alpha AND").error)
    }

    @Test
    fun unterminatedQuoteIsRejected() {
        assertNotNull(FtsQueryBuilder.build("\"alpha beta").error)
    }

    @Test
    fun specialCharactersAreQuoted() {
        val result = FtsQueryBuilder.build("C++")
        assertEquals("\"C++\"", result.query)
    }

    @Test
    fun emptyQueryIsRejected() {
        assertNotNull(FtsQueryBuilder.build("   ").error)
        assertNull(FtsQueryBuilder.build("   ").query)
    }

    @Test
    fun prefixStarIsSupported() {
        val result = FtsQueryBuilder.build("proj*")
        assertEquals("\"proj\"*", result.query)
        assertTrue(result.plainTerms.single() == "proj")
    }

    @Test
    fun punctuationCannotEscapeAQuote() {
        val result = FtsQueryBuilder.build("foo\"bar")
        assertTrue(result.error != null || result.query?.contains("\"\"") == true)
    }
}
