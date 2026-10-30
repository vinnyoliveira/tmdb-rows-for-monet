package com.ptvin.tmdbrows.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/** Linhas salvas. São poucas: JSON em prefs basta, sem banco. */
object Rows {
    private const val KEY = "rows_json"
    private const val KEY_ART_16_9 = "art_16_9"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var context: Context
    private val _items = MutableStateFlow<List<RowConfig>>(emptyList())
    val items: StateFlow<List<RowConfig>> = _items.asStateFlow()

    fun init(context: Context) {
        this.context = context.applicationContext
        _items.value = load()
    }

    fun current(): List<RowConfig> = _items.value

    fun find(id: String): RowConfig? = _items.value.firstOrNull { it.id == id }

    fun upsert(row: RowConfig) {
        val next = _items.value.toMutableList()
        val index = next.indexOfFirst { it.id == row.id }
        if (index >= 0) next[index] = row else next += row
        save(next)
    }

    fun update(id: String, transform: (RowConfig) -> RowConfig) {
        val row = find(id) ?: return
        upsert(transform(row))
    }

    fun delete(id: String) {
        save(_items.value.filterNot { it.id == id })
    }

    private fun load(): List<RowConfig> {
        val prefs = context.getSharedPreferences("rows", Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)
        if (stored.isNullOrBlank()) {
            val seeds = DefaultRows.all(context)
            persist(seeds)
            return seeds
        }
        val rows = runCatching {
            json.decodeFromString(ListSerializer(RowConfig.serializer()), stored)
        }.getOrDefault(DefaultRows.all(context))

        // Versões anteriores publicavam pôster 2:3, que o Monet mostra com barras.
        if (!prefs.getBoolean(KEY_ART_16_9, false)) {
            prefs.edit().putBoolean(KEY_ART_16_9, true).apply()
            val migrated = rows.map {
                if (it.art == ArtStyle.POSTER) it.copy(art = ArtStyle.BACKDROP, lastPublishedAt = null) else it
            }
            persist(migrated)
            return migrated
        }
        return rows
    }

    private fun save(rows: List<RowConfig>) {
        persist(rows)
        _items.value = rows
    }

    private fun persist(rows: List<RowConfig>) {
        context.getSharedPreferences("rows", Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.encodeToString(ListSerializer(RowConfig.serializer()), rows))
            .apply()
    }
}
