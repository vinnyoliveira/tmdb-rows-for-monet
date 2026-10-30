package com.ptvin.tmdbrows

import com.ptvin.tmdbrows.data.isRecent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DatesTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun janelaDeQuinzeDiasParaFilmes() {
        assertTrue(isRecent("2026-10-30", 15, today))
        assertTrue(isRecent("2026-10-16", 15, today))
        assertTrue(isRecent("2026-10-15", 15, today))
        assertFalse(isRecent("2026-10-14", 15, today))
        // Ainda não estreou: não é novidade.
        assertFalse(isRecent("2026-10-31", 15, today))
    }

    @Test
    fun janelaDeTresDiasParaEpisodios() {
        assertTrue(isRecent("2026-10-28", 3, today))
        assertTrue(isRecent("2026-10-27", 3, today))
        assertFalse(isRecent("2026-10-26", 3, today))
    }

    @Test
    fun datasNulasOuInvalidasNaoSaoNovidade() {
        assertFalse(isRecent(null, 15, today))
        assertFalse(isRecent("", 15, today))
        assertFalse(isRecent("ontem", 15, today))
        assertFalse(isRecent("2026-13-45", 15, today))
    }
}
