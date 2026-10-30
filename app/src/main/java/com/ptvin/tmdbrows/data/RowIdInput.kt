package com.ptvin.tmdbrows.data

/**
 * Lê ids colados à mão. Aceita `550`, `550, 1399` e links do site
 * (`themoviedb.org/movie/550-fight-club`), que já trazem o tipo.
 */
object RowIdInput {
    private val URL = Regex("themoviedb\\.org/(movie|tv)/(\\d+)")

    fun parse(text: String): List<RowItem> =
        text.split('\n', ',', ';', ' ', '\t')
            .mapNotNull(::parseOne)
            .distinctBy { it.id to it.type }

    private fun parseOne(raw: String): RowItem? {
        val token = raw.trim()
        if (token.isEmpty()) return null
        URL.find(token)?.let { match ->
            val id = match.groupValues[2].toIntOrNull() ?: return null
            val type = if (match.groupValues[1] == "movie") MediaType.MOVIE else MediaType.TV
            return RowItem(id = id, type = type)
        }
        val id = token.removePrefix("#").toIntOrNull() ?: return null
        return if (id > 0) RowItem(id = id) else null
    }
}
