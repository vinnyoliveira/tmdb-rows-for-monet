package com.ptvin.tmdbrows

import com.ptvin.tmdbrows.data.Updates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun comparaVersoesPontoAPonto() {
        assertTrue(Updates.isNewer("1.3", "1.2"))
        assertTrue(Updates.isNewer("2.0", "1.9"))
        assertTrue(Updates.isNewer("1.10", "1.9"))
        assertTrue(Updates.isNewer("1.2.1", "1.2"))
        assertFalse(Updates.isNewer("1.2", "1.2"))
        assertFalse(Updates.isNewer("1.1", "1.2"))
        assertFalse(Updates.isNewer("1.9", "1.10"))
    }

    @Test
    fun versaoInvalidaNaoAtualiza() {
        assertFalse(Updates.isNewer("", "1.2"))
        assertFalse(Updates.isNewer("abc", "1.2"))
        assertFalse(Updates.isNewer("1.2", ""))
    }
}
