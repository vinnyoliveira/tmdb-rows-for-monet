package com.ptvin.tmdbrows.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prévia em memória das linhas. Poucas linhas, sem expiração. */
object PreviewCache {
    private val cache = LinkedHashMap<String, List<TmdbItem>>()

    private fun key(context: Context, row: RowConfig): String =
        row.copy(lastPublishedAt = null, lastError = null).hashCode().toString() +
            ":" + Prefs.posterLanguage(context)

    suspend fun load(context: Context, row: RowConfig, limit: Int): List<TmdbItem> {
        val key = key(context, row)
        cache[key]?.let { return it }
        val items = withContext(Dispatchers.IO) {
            Tmdb.fetchItems(context, Prefs.tmdbKey(context), row, limit, Prefs.posterLanguage(context))
        }
        cache[key] = items
        return items
    }
}
