package com.ptvin.tmdbrows.channel

import android.content.Context
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.Rows
import com.ptvin.tmdbrows.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChannelRefresher {
    private const val STALE_MS = 6 * 60 * 60 * 1000L

    data class Summary(val message: String)

    fun isStale(): Boolean {
        val now = System.currentTimeMillis()
        return Rows.current().any { it.lastPublishedAt == null || now - it.lastPublishedAt > STALE_MS }
    }

    suspend fun refreshAll(context: Context): Summary = withContext(Dispatchers.IO) {
        val key = Prefs.tmdbKey(context)
        // Sem chave nada é publicado e as linhas ficariam todas marcadas com erro.
        if (key.isBlank()) return@withContext Summary(context.getString(R.string.refresh_no_key))

        var ok = 0
        var failed = 0
        Rows.current().forEach { row ->
            val result = refreshRow(context, row, key)
            if (result.lastError == null) ok++ else failed++
        }
        Summary(
            when {
                ok > 0 && failed > 0 -> context.getString(R.string.refresh_mixed, ok, failed)
                ok > 0 -> context.getString(R.string.refresh_ok, ok)
                else -> context.getString(R.string.refresh_none)
            },
        )
    }

    /** Publica uma linha imediatamente e devolve o estado atualizado. */
    suspend fun publish(context: Context, row: RowConfig): RowConfig = withContext(Dispatchers.IO) {
        val updated = refreshRow(context, row, Prefs.tmdbKey(context))
        Rows.upsert(updated)
        updated
    }

    private fun refreshRow(context: Context, row: RowConfig, key: String): RowConfig {
        val updated = try {
            val items = Tmdb.fetchItems(context, key, row, row.itemCount, Prefs.posterLanguage(context))
            if (items.isEmpty()) error(context.getString(R.string.error_no_results))
            ChannelPublisher.publish(context, row, items)
            row.copy(lastPublishedAt = System.currentTimeMillis(), lastError = null)
        } catch (error: Exception) {
            // Falha não apaga o canal existente: só registra o motivo.
            row.copy(lastError = error.message ?: context.getString(R.string.error_bad_response))
        }
        Rows.upsert(updated)
        return updated
    }
}
