package com.ptvin.tmdbrows.data

import android.content.Context

enum class Opener(val label: String) {
    NUVIO("Nuvio"),
    STREMIO("Stremio"),
}

object Prefs {
    private const val FILE = "rows_prefs"
    private const val KEY_TMDB = "tmdb_key"
    private const val KEY_OPENER = "opener"
    private const val KEY_HOURS = "refresh_hours"
    private const val KEY_AUTO_SYNC = "auto_sync"
    private const val KEY_LANGUAGE = "poster_lang"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_LANGUAGES = "languages_json"
    private const val KEY_BETTER_POSTERS = "better_posters"
    private const val KEY_IMDB = "imdb_"
    private const val KEY_CHANNEL_PREFIX = "channel_"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Só a chave que a pessoa colou: o app não traz nenhuma embutida. */
    fun tmdbKey(context: Context): String = sp(context).getString(KEY_TMDB, "") ?: ""

    fun setTmdbKey(context: Context, value: String?) {
        sp(context).edit().apply {
            if (value.isNullOrBlank()) remove(KEY_TMDB) else putString(KEY_TMDB, value.trim())
        }.apply()
    }

    fun opener(context: Context): Opener {
        val raw = sp(context).getString(KEY_OPENER, null) ?: return Opener.NUVIO
        return runCatching { Opener.valueOf(raw) }.getOrDefault(Opener.NUVIO)
    }

    fun setOpener(context: Context, opener: Opener) {
        sp(context).edit().putString(KEY_OPENER, opener.name).apply()
    }

    fun refreshHours(context: Context): Int = sp(context).getInt(KEY_HOURS, 12)

    fun setRefreshHours(context: Context, hours: Int) {
        sp(context).edit().putInt(KEY_HOURS, hours).apply()
    }

    /** Sincronização em segundo plano: desligada por omissão. */
    fun autoSync(context: Context): Boolean = sp(context).getBoolean(KEY_AUTO_SYNC, false)

    fun setAutoSync(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    /** Vazio = automático (idioma do aparelho). */
    fun languageChoice(context: Context): String =
        sp(context).getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguageChoice(context: Context, code: String?) {
        sp(context).edit().putString(KEY_LANGUAGE, code?.trim().orEmpty()).apply()
    }

    /** Idioma da interface. Vazio = seguir o sistema. */
    fun appLanguage(context: Context): String =
        sp(context).getString(KEY_APP_LANGUAGE, "") ?: ""

    fun setAppLanguage(context: Context, code: String?) {
        sp(context).edit().putString(KEY_APP_LANGUAGE, code?.trim().orEmpty()).apply()
    }

    fun posterLanguage(context: Context): String =
        languageChoice(context).ifBlank { deviceLanguage() }

    /** Lista de idiomas do TMDB, já descarregada. Vazia = buscar ou usar o recurso. */
    fun cachedLanguages(context: Context): String =
        sp(context).getString(KEY_LANGUAGES, "") ?: ""

    fun setCachedLanguages(context: Context, json: String) {
        sp(context).edit().putString(KEY_LANGUAGES, json).apply()
    }

    /** Cartazes do BetterPosters (opcional, desligado por omissão). */
    fun betterPosters(context: Context): Boolean =
        sp(context).getBoolean(KEY_BETTER_POSTERS, false)

    fun setBetterPosters(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_BETTER_POSTERS, enabled).apply()
    }

    fun imdbId(context: Context, cacheKey: String): String? =
        sp(context).getString(KEY_IMDB + cacheKey, null)

    fun setImdbId(context: Context, cacheKey: String, value: String) {
        sp(context).edit().putString(KEY_IMDB + cacheKey, value).apply()
    }

    /** Último episódio de uma série: "data|quando foi buscado". */
    fun episodeCache(context: Context, id: Int): String? =
        sp(context).getString("ep_$id", null)

    fun setEpisodeCache(context: Context, id: Int, value: String) {
        sp(context).edit().putString("ep_$id", value).apply()
    }

    fun lastUpdateCheck(context: Context): Long = sp(context).getLong("update_check", 0L)

    fun setLastUpdateCheck(context: Context, value: Long) {
        sp(context).edit().putLong("update_check", value).apply()
    }

    /** Versão que a pessoa escolheu não instalar agora. */
    fun ignoredVersion(context: Context): String = sp(context).getString("update_ignored", "") ?: ""

    fun setIgnoredVersion(context: Context, value: String?) {
        sp(context).edit().putString("update_ignored", value?.trim().orEmpty()).apply()
    }

    /** Cartões: ligar/desligar o que é desenhado por cima da arte. */
    fun cardTitle(context: Context): Boolean = sp(context).getBoolean("card_title", true)

    fun setCardTitle(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean("card_title", enabled).apply()
    }

    fun cardTags(context: Context): Boolean = sp(context).getBoolean("card_tags", true)

    fun setCardTags(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean("card_tags", enabled).apply()
    }

    fun badgeNew(context: Context): Boolean = sp(context).getBoolean("badge_new", true)

    fun setBadgeNew(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean("badge_new", enabled).apply()
    }

    fun badgeEpisodes(context: Context): Boolean = sp(context).getBoolean("badge_episodes", true)

    fun setBadgeEpisodes(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean("badge_episodes", enabled).apply()
    }

    /** Idioma no formato que o BetterPosters usa (`pt-BR`, `en-US`, …). */
    fun btttrLanguage(context: Context): String {
        val code = posterLanguage(context)
        val lang = code.substringBefore('-').lowercase()
        if (lang == "pt") return if (code.substringAfter('-', "").equals("PT", true)) "pt-PT" else "pt-BR"
        val region = code.substringAfter('-', "").uppercase().ifBlank { REGIONS[lang] ?: "US" }
        return "$lang-$region"
    }

    private val REGIONS = mapOf(
        "en" to "US", "es" to "ES", "fr" to "FR", "de" to "DE", "it" to "IT",
        "ja" to "JP", "ko" to "KR", "zh" to "CN", "ru" to "RU", "tr" to "TR",
        "ar" to "SA", "hi" to "IN", "nl" to "NL", "pl" to "PL", "sv" to "SE",
        "cs" to "CZ",
    )

    fun channelId(context: Context, rowId: String): Long =
        sp(context).getLong(KEY_CHANNEL_PREFIX + rowId, -1L)

    fun setChannelId(context: Context, rowId: String, channelId: Long) {
        sp(context).edit().putLong(KEY_CHANNEL_PREFIX + rowId, channelId).apply()
    }

    fun clearChannelId(context: Context, rowId: String) {
        sp(context).edit().remove(KEY_CHANNEL_PREFIX + rowId).apply()
    }

    /** Marca de migração já aplicada (ex.: canais de versões antigas apagados). */
    fun migrated(context: Context, name: String): Boolean =
        sp(context).getBoolean("migrated_$name", false)

    fun setMigrated(context: Context, name: String) {
        sp(context).edit().putBoolean("migrated_$name", true).apply()
    }
}
