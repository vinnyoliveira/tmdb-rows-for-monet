package com.ptvin.tmdbrows.data

import java.time.LocalDate

/**
 * Verdadeiro quando [date] (ISO `yyyy-MM-dd`) cai entre [today]-[days] e [today].
 * Datas futuras, nulas ou inválidas dão falso (nunca anunciar o que ainda não saiu).
 */
fun isRecent(date: String?, days: Long, today: LocalDate = LocalDate.now()): Boolean {
    val parsed = date?.takeIf { it.length >= 10 }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return false
    return !parsed.isBefore(today.minusDays(days)) && !parsed.isAfter(today)
}
