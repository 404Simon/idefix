package dev.idefix.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCommandTest {

    @Test
    fun mapsTheFirstKeyToACommand() {
        assertEquals(SearchCommand.FILENAME, SearchCommand.fromKey('f'))
        assertEquals(SearchCommand.GREP, SearchCommand.fromKey('g'))
        assertEquals(SearchCommand.NEW_NOTE, SearchCommand.fromKey('n'))
        assertEquals(SearchCommand.MODUL, SearchCommand.fromKey('m'))
    }

    @Test
    fun acceptsUppercaseKeys() {
        assertEquals(SearchCommand.GREP, SearchCommand.fromKey('G'))
    }

    @Test
    fun ignoresOtherKeys() {
        assertNull(SearchCommand.fromKey('x'))
        assertNull(SearchCommand.fromKey('1'))
    }
}
