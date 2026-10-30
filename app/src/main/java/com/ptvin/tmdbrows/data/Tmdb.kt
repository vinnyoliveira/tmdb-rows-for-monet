package com.ptvin.tmdbrows.data

import android.content.Context
import android.util.Log
import com.ptvin.tmdbrows.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class TmdbItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    /** Só é preenchido quando os cartazes BetterPosters estão ligados. */
    @SerialName("imdb_id") val imdbId: String? = null,
    /** Data (ISO) do último episódio já transmitido; só nas séries e quando o selo está ligado. */
    val newEpisodeAt: String? = null,
) {
    val displayTitle: String get() = title ?: name ?: ""
    val date: String? get() = (releaseDate ?: firstAirDate)?.takeIf { it.isNotBlank() }
    val year: Int? get() = date?.take(4)?.toIntOrNull()
    val type: MediaType? get() = when (mediaType) {
        "movie" -> MediaType.MOVIE
        "tv" -> MediaType.TV
        else -> null
    }
}

/** Idioma aceito pelo TMDB (`configuration/languages`). */
@Serializable
data class TmdbLanguage(
    @SerialName("iso_639_1") val code: String = "",
    val name: String = "",
    @SerialName("english_name") val englishName: String = "",
) {
    val label: String get() = name.ifBlank { englishName.ifBlank { code } }
}

/** Plataforma de streaming disponível numa região. */
@Serializable
data class TmdbProvider(
    @SerialName("provider_id") val id: Int = 0,
    @SerialName("provider_name") val name: String = "",
    @SerialName("display_priority") val priority: Int = 999,
    @SerialName("logo_path") val logoPath: String? = null,
) {
    val logoUrl: String? get() = logoPath?.let { "https://image.tmdb.org/t/p/w92$it" }
}

@Serializable
private data class ProviderPage(val results: List<TmdbProvider> = emptyList())

@Serializable
private data class TmdbPage(
    val results: List<TmdbItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
private data class TmdbImages(
    val posters: List<TmdbImage> = emptyList(),
    val logos: List<TmdbLogo> = emptyList(),
)

@Serializable
private data class TmdbLogo(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val width: Int = 0,
)

@Serializable
private data class TmdbImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val width: Int = 0,
)

@Serializable
private data class ExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
private data class TvDetail(
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("last_episode_to_air") val lastEpisode: Episode? = null,
)

@Serializable
private data class Episode(@SerialName("air_date") val airDate: String? = null)

class TmdbException(message: String, val status: Int? = null) : Exception(message)

object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p"
    private const val MAX_PAGES = 5
    private const val MAX_SEARCH = 10

    internal val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val posterCache = HashMap<String, String>()
    private val logoCache = HashMap<String, String>()
    private val providerCache = HashMap<String, List<TmdbProvider>>()
    private val imdbCache = ConcurrentHashMap<String, String>()
    private val episodeCache = HashMap<Int, Pair<String?, Long>>()
    private const val EPISODE_TTL_MS = 6L * 60 * 60 * 1000

    fun fetchItems(
        context: Context,
        key: String,
        row: RowConfig,
        limit: Int = row.itemCount,
        language: String = "",
    ): List<TmdbItem> {
        if (key.isBlank()) throw TmdbException(context.getString(R.string.error_no_key))
        if (row.mode == RowMode.IDS) return fetchByIds(context, key, row, limit, language)

        val unique = LinkedHashMap<Int, TmdbItem>()
        val serverFiltered = row.mode == RowMode.PLATFORM || row.section == Section.DISCOVER
        val random = row.sortBy == RowConfig.RANDOM
        val byEpisodes = row.mediaType == MediaType.TV && row.sortBy == RowConfig.LAST_EPISODE
        // Reserva maior: para sortear ou para ordenar pelos episódios mais recentes.
        val target = if (random || byEpisodes) (limit * 3).coerceAtMost(MAX_PAGES * 20) else limit
        val withEpisodes = byEpisodes || (row.mediaType == MediaType.TV && Prefs.badgeEpisodes(context))

        for (page in 1..MAX_PAGES) {
            if (unique.size >= target) break
            val pageItems = request(context, key, row, page, language)
            pageItems.forEach { item ->
                if (unique.size >= target) return@forEach
                if (item.id in unique) return@forEach
                val resolved = when (row.art) {
                    ArtStyle.POSTER -> localizedPoster(context, key, row.mediaType, item, language)
                        ?.let { item.copy(posterPath = it) } ?: item
                    ArtStyle.BACKDROP -> item
                }
                val ready = withPosterSource(context, key, row, resolved)
                if (!hasArt(row, ready)) return@forEach
                if (!serverFiltered && !matches(row, item)) return@forEach
                unique[item.id] = if (withEpisodes) {
                    ready.copy(newEpisodeAt = latestEpisode(context, key, item.id))
                } else {
                    ready
                }
            }
            if (pageItems.size < 20) break
        }
        val all = unique.values.toList()
        return when {
            random -> all.shuffled().take(limit)
            byEpisodes -> all.filter { it.newEpisodeAt != null }
                .sortedByDescending { it.newEpisodeAt }
                .take(limit)
            else -> all
        }
    }

    /** Lista feita à mão: cada id custa 1 chamada (ou 2 quando o tipo é desconhecido). */
    private fun fetchByIds(
        context: Context,
        key: String,
        row: RowConfig,
        limit: Int,
        language: String,
    ): List<TmdbItem> {
        if (row.items.isEmpty()) throw TmdbException(context.getString(R.string.error_ids_empty))
        val out = ArrayList<TmdbItem>()
        for (entry in row.items) {
            if (out.size >= limit) break
            val item = resolveItem(context, key, entry, language, row.art) ?: continue
            val ready = withPosterSource(context, key, row, item)
            if (!hasArt(row, ready)) continue
            out += ready
        }
        if (out.isEmpty()) throw TmdbException(context.getString(R.string.error_ids_none))
        return out
    }

    /**
     * Título pelo id. Sem [RowItem.type] tenta filme e depois série
     * (os ids do TMDB não se repetem entre os dois).
     */
    fun resolveItem(
        context: Context,
        key: String,
        entry: RowItem,
        language: String = "",
        art: ArtStyle = ArtStyle.BACKDROP,
    ): TmdbItem? {
        val types = entry.type?.let { listOf(it) } ?: listOf(MediaType.MOVIE, MediaType.TV)
        for (type in types) {
            val item = try {
                detail(context, key, type, entry.id, language)
            } catch (error: TmdbException) {
                if (error.status == 404) continue else throw error
            }
            return when (art) {
                ArtStyle.POSTER -> localizedPoster(context, key, type, item, language)
                    ?.let { item.copy(posterPath = it, mediaType = type.tmdb) }
                    ?: item.copy(mediaType = type.tmdb)
                ArtStyle.BACKDROP -> item.copy(mediaType = type.tmdb)
            }
        }
        return null
    }

    /** Completa tipo e nome de um item colado à mão, antes de ele entrar na lista. */
    fun fillEntry(context: Context, key: String, entry: RowItem, language: String = ""): RowItem? {
        val types = entry.type?.let { listOf(it) } ?: listOf(MediaType.MOVIE, MediaType.TV)
        for (type in types) {
            val item = try {
                detail(context, key, type, entry.id, language)
            } catch (error: TmdbException) {
                if (error.status == 404) continue else throw error
            }
            return entry.copy(type = type, title = item.displayTitle)
        }
        return null
    }

    fun search(context: Context, key: String, query: String, language: String = ""): List<TmdbItem> {
        val url = "$BASE/search/multi".toHttpUrl().newBuilder().apply {
            auth(key)
            addQueryParameter("query", query)
            addQueryParameter("language", languageOrDefault(language))
            addQueryParameter("include_adult", "false")
        }.build()
        val body = execute(context, key, url)
        return runCatching { json.decodeFromString(TmdbPage.serializer(), body).results }
            .getOrElse { throw TmdbException(context.getString(R.string.error_bad_response)) }
            .filter { it.type != null }
            .take(MAX_SEARCH)
    }

    /** Lista completa de idiomas do TMDB; fica em cache e cai para uma lista curta offline. */
    fun languages(context: Context, key: String): List<TmdbLanguage> {
        val cached = Prefs.cachedLanguages(context)
        if (cached.isNotBlank()) {
            runCatching {
                json.decodeFromString(ListSerializer(TmdbLanguage.serializer()), cached)
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        if (key.isBlank()) return DEFAULT_LANGUAGES
        val url = "$BASE/configuration/languages".toHttpUrl().newBuilder().apply { auth(key) }.build()
        val fetched = runCatching {
            json.decodeFromString(ListSerializer(TmdbLanguage.serializer()), execute(context, key, url))
        }.getOrNull().orEmpty().filter { it.code.isNotBlank() }.sortedBy { it.label.lowercase() }
        if (fetched.isEmpty()) return DEFAULT_LANGUAGES
        Prefs.setCachedLanguages(context, json.encodeToString(ListSerializer(TmdbLanguage.serializer()), fetched))
        return fetched
    }

    /** Plataformas realmente disponíveis na região (os ids mudam de país para país). */
    fun providers(context: Context, key: String, type: MediaType, region: String): List<TmdbProvider> {
        val cacheKey = "${type.tmdb}:$region"
        providerCache[cacheKey]?.let { return it }
        if (key.isBlank()) return DEFAULT_PROVIDERS
        val url = "$BASE/watch/providers/${type.tmdb}".toHttpUrl().newBuilder().apply {
            auth(key)
            addQueryParameter("watch_region", region)
        }.build()
        val fetched = runCatching {
            json.decodeFromString(ProviderPage.serializer(), execute(context, key, url)).results
        }.getOrNull().orEmpty()
            .filter { it.id > 0 && it.name.isNotBlank() }
            .sortedBy { it.priority }
        val result = fetched.ifEmpty { DEFAULT_PROVIDERS }
        Providers.remember(result)
        providerCache[cacheKey] = result
        return result
    }

    private fun detail(
        context: Context,
        key: String,
        type: MediaType,
        id: Int,
        language: String,
    ): TmdbItem {
        val url = "$BASE/${type.tmdb}/$id".toHttpUrl().newBuilder().apply {
            auth(key)
            addQueryParameter("language", languageOrDefault(language))
        }.build()
        return runCatching { json.decodeFromString(TmdbItem.serializer(), execute(context, key, url)) }
            .getOrElse { throw TmdbException(context.getString(R.string.error_bad_response)) }
    }

    private fun hasArt(row: RowConfig, item: TmdbItem): Boolean =
        if (row.art == ArtStyle.POSTER) !item.posterPath.isNullOrBlank() else !item.backdropPath.isNullOrBlank()

    /**
     * Os endpoints de lista devolvem só o pôster principal (quase sempre sem idioma),
     * então um pôster localizado custa uma chamada /images por título.
     * shortcut: uma chamada por título/idioma, cacheada em memória;
     * se publicar centenas de itens ficar lento, buscar em paralelo.
     */
    private fun localizedPoster(
        context: Context,
        key: String,
        type: MediaType,
        item: TmdbItem,
        language: String,
    ): String? {
        if (language.isBlank()) return null
        val cacheKey = "${type.tmdb}:${item.id}:$language"
        posterCache[cacheKey]?.let { return it }
        val path = runCatching {
            val url = "$BASE/${type.tmdb}/${item.id}/images".toHttpUrl().newBuilder().apply {
                auth(key)
                addQueryParameter("include_image_language", language)
            }.build()
            json.decodeFromString(TmdbImages.serializer(), execute(context, key, url)).posters
                .filter { it.language == language && !it.filePath.isNullOrBlank() }
                .maxWithOrNull(compareBy({ it.voteAverage }, { it.width }))
                ?.filePath
        }.getOrNull()
        if (path != null) posterCache[cacheKey] = path
        return path
    }

    /**
     * Logo do título (PNG transparente) para desenhar no banner gerado.
     * Uma chamada por título, cacheada em memória.
     */
    fun logoPath(context: Context, key: String, type: MediaType, id: Int, language: String = "pt"): String? {
        val cacheKey = "${type.tmdb}:$id:$language"
        logoCache[cacheKey]?.let { return it }
        val path = runCatching {
            val url = "$BASE/${type.tmdb}/$id/images".toHttpUrl().newBuilder().apply {
                auth(key)
                addQueryParameter("include_image_language", "pt,en,null")
            }.build()
            val logos = json.decodeFromString(TmdbImages.serializer(), execute(context, key, url)).logos
                .filter { it.filePath?.endsWith(".png") == true }
            val rank = { lang: String? ->
                val code = lang?.lowercase().orEmpty()
                when {
                    code == language.lowercase() -> 0
                    code == "en" -> 1
                    code.isEmpty() || code == "null" -> 2
                    else -> 3
                }
            }
            logos.minWithOrNull(compareBy({ rank(it.language) }, { -it.voteAverage }))?.filePath
        }.getOrNull()
        if (path != null) logoCache[cacheKey] = path
        return path
    }

    fun validate(context: Context, key: String): Boolean = runCatching {
        val url = "$BASE/configuration".toHttpUrl().newBuilder().apply { auth(key) }.build()
        execute(context, key, url).isNotBlank()
    }.getOrElse { false }

    /** Com os cartazes BetterPosters ligados, os pósteres levam o id do IMDb. */
    private fun withPosterSource(
        context: Context,
        key: String,
        row: RowConfig,
        item: TmdbItem,
    ): TmdbItem {
        if (row.art != ArtStyle.POSTER || !Prefs.betterPosters(context)) return item
        val imdb = imdbId(context, key, item.type ?: row.mediaType, item.id) ?: return item
        return item.copy(imdbId = imdb)
    }

    private fun imdbId(context: Context, key: String, type: MediaType, id: Int): String? {
        val cacheKey = "${type.tmdb}:$id"
        imdbCache[cacheKey]?.let { return it }
        Prefs.imdbId(context, cacheKey)?.let {
            imdbCache[cacheKey] = it
            return it
        }
        val url = "$BASE/${type.tmdb}/$id/external_ids".toHttpUrl().newBuilder().apply { auth(key) }.build()
        val found = runCatching {
            json.decodeFromString(ExternalIds.serializer(), execute(context, key, url)).imdbId
        }.getOrNull()?.takeIf { it.startsWith("tt") }
        if (found != null) {
            imdbCache[cacheKey] = found
            Prefs.setImdbId(context, cacheKey, found)
        }
        return found
    }

    /** Data do último episódio transmitido (cache de 6 h por série). */
    private fun latestEpisode(context: Context, key: String, id: Int): String? {
        val now = System.currentTimeMillis()
        episodeCache[id]?.let { (date, at) -> if (now - at < EPISODE_TTL_MS) return date }
        Prefs.episodeCache(context, id)?.let { stored ->
            val at = stored.substringAfter('|', "0").toLongOrNull() ?: 0L
            if (now - at < EPISODE_TTL_MS) {
                val date = stored.substringBefore('|').takeIf { s -> s.isNotBlank() && s != "-" }
                episodeCache[id] = date to at
                return date
            }
        }
        val url = "$BASE/tv/$id".toHttpUrl().newBuilder().apply { auth(key) }.build()
        val date = runCatching {
            val detail = json.decodeFromString(TvDetail.serializer(), execute(context, key, url))
            detail.lastEpisode?.airDate ?: detail.lastAirDate
        }.getOrNull()?.takeIf { it.length >= 10 }
        episodeCache[id] = date to now
        Prefs.setEpisodeCache(context, id, "${date.orEmpty()}|$now")
        return date
    }

    fun artUrl(
        row: RowConfig,        item: TmdbItem,
        preview: Boolean = false,
        language: String? = null,
        small: Boolean = false,
    ): String? {
        // BetterPosters: cartaz com o título no idioma escolhido (só 2:3).
        if (row.art == ArtStyle.POSTER && language != null) {
            item.imdbId?.let { return "https://btttr.cc/poster/imdb/poster-default/$it.jpg?lang=$language&rs=TR" }
        }
        val path = if (row.art == ArtStyle.POSTER) item.posterPath else item.backdropPath
        if (path.isNullOrBlank()) return null
        // Miniaturas (prévia e cartões da home) não precisam da resolução toda.
        val size = when {
            row.art == ArtStyle.POSTER -> if (preview || small) "w342" else "w780"
            preview || small -> "w300"
            else -> "w1280"
        }
        return "$IMAGE_BASE/$size$path"
    }

    private fun matches(row: RowConfig, item: TmdbItem): Boolean {
        if (row.genreIds.isNotEmpty() && item.genreIds.none { it in row.genreIds }) return false
        if (item.voteAverage < row.minRating) return false
        val yearFrom = row.yearFrom
        if (yearFrom != null && (item.year ?: 0) < yearFrom) return false
        return true
    }

    private fun request(context: Context, key: String, row: RowConfig, page: Int, language: String): List<TmdbItem> {
        val url = buildUrl(row, page, key, language = language)
        val body = execute(context, key, url)
        val items = runCatching { json.decodeFromString(TmdbPage.serializer(), body).results }
            .getOrElse { throw TmdbException(context.getString(R.string.error_bad_response)) }
        if (items.isEmpty()) Log.d("TmdbRows", "sem itens: $url")
        return items
    }

    internal fun buildUrl(
        row: RowConfig,
        page: Int,
        key: String,
        region: String = deviceRegion(),
        language: String = "",
    ): HttpUrl =
        "$BASE${endpoint(row)}".toHttpUrl().newBuilder().apply {
            auth(key)
            addQueryParameter("language", languageOrDefault(language))
            addQueryParameter("include_adult", "false")
            addQueryParameter("page", page.toString())
            when (row.mediaType) {
                MediaType.MOVIE -> if (row.section in MOVIE_REGION_SECTIONS) addQueryParameter("region", region)
                MediaType.TV -> Unit
            }
            if (row.mode == RowMode.PLATFORM) {
                if (row.providers.isNotEmpty()) {
                    addQueryParameter("with_watch_providers", row.providers.joinToString("|"))
                }
                addQueryParameter("watch_region", region)
                addQueryParameter("with_watch_monetization_types", "flatrate")
            }
            if (row.mode != RowMode.FILTER || row.section == Section.DISCOVER ||
                row.section == Section.NEW_SERIES || row.section == Section.AIRING_TODAY
            ) {
                // Aleatório e "novos episódios" não têm suporte no TMDB: a ordem é feita no aparelho.
                val random = sortBy(row) == RowConfig.RANDOM || sortBy(row) == RowConfig.LAST_EPISODE
                if (row.mediaType == MediaType.TV && row.section == Section.AIRING_TODAY) {
                    // O mesmo que "Hoje na TV" no site do TMDB.
                    addQueryParameter("air_date.gte", today)
                    addQueryParameter("air_date.lte", today)
                }
                if (row.mediaType == MediaType.TV && row.section == Section.NEW_SERIES) {
                    addQueryParameter("first_air_date.gte", LocalDate.now().minusDays(30).toString())
                    addQueryParameter("first_air_date.lte", today)
                }
                if (!random) {
                    val sort = if (row.section == Section.NEW_SERIES) "first_air_date.desc" else sortBy(row)
                    addQueryParameter("sort_by", sort)
                }
                if (row.genreIds.isNotEmpty()) addQueryParameter("with_genres", row.genreIds.joinToString("|"))
                if (row.minRating > 0.0) addQueryParameter("vote_average.gte", row.minRating.toString())
                if (sortBy(row) == "vote_average.desc") addQueryParameter("vote_count.gte", "50")
                row.yearFrom?.let {
                    val param = if (row.mediaType == MediaType.MOVIE) "primary_release_date.gte" else "first_air_date.gte"
                    addQueryParameter(param, "$it-01-01")
                }
            }
        }.build()

    private fun execute(context: Context, key: String, url: HttpUrl): String {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (key.startsWith("eyJ")) builder.header("Authorization", "Bearer $key")
        val request = builder.build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return response.body?.string().orEmpty()
                throw TmdbException(
                    message = when (response.code) {
                        401 -> context.getString(R.string.error_invalid_key)
                        429 -> context.getString(R.string.error_rate)
                        else -> context.getString(R.string.error_http, response.code)
                    },
                    status = response.code,
                )
            }
        } catch (error: IOException) {
            throw TmdbException(context.getString(R.string.error_offline))
        }
    }

    /** Read token v4 vai no header; a API key v3 vai na query. */
    private fun HttpUrl.Builder.auth(key: String) {
        if (!key.startsWith("eyJ")) addQueryParameter("api_key", key)
    }

    private val today: String get() = LocalDate.now().toString()

    private fun languageOrDefault(language: String): String = language.ifBlank { "en-US" }

    private fun sortBy(row: RowConfig): String =
        if (row.mediaType == MediaType.TV && row.sortBy == "primary_release_date.desc") "first_air_date.desc"
        else row.sortBy

    private fun endpoint(row: RowConfig): String = when {
        row.mode != RowMode.FILTER -> "/discover/${row.mediaType.tmdb}"
        else -> when (row.section) {
            Section.TRENDING_DAY -> "/trending/${row.mediaType.tmdb}/day"
            Section.TRENDING_WEEK -> "/trending/${row.mediaType.tmdb}/week"
            Section.POPULAR -> "/${row.mediaType.tmdb}/popular"
            Section.TOP_RATED -> "/${row.mediaType.tmdb}/top_rated"
            Section.UPCOMING -> "/movie/upcoming"
            Section.NOW_PLAYING -> "/movie/now_playing"
            Section.ON_THE_AIR -> "/tv/on_the_air"
            Section.AIRING_TODAY -> "/tv/airing_today"
            Section.NEW_SERIES -> "/discover/tv"
            Section.DISCOVER -> "/discover/${row.mediaType.tmdb}"
        }
    }

    private val MOVIE_REGION_SECTIONS = setOf(Section.NOW_PLAYING, Section.UPCOMING)

    private val DEFAULT_LANGUAGES = listOf(
        TmdbLanguage("pt", "Português", "Portuguese"),
        TmdbLanguage("en", "English", "English"),
        TmdbLanguage("es", "Español", "Spanish"),
        TmdbLanguage("fr", "Français", "French"),
        TmdbLanguage("de", "Deutsch", "German"),
        TmdbLanguage("it", "Italiano", "Italian"),
        TmdbLanguage("ja", "日本語", "Japanese"),
        TmdbLanguage("ko", "한국어", "Korean"),
        TmdbLanguage("zh", "中文", "Chinese"),
        TmdbLanguage("ru", "Русский", "Russian"),
        TmdbLanguage("hi", "हिन्दी", "Hindi"),
        TmdbLanguage("ar", "العربية", "Arabic"),
        TmdbLanguage("nl", "Nederlands", "Dutch"),
        TmdbLanguage("pl", "Polski", "Polish"),
        TmdbLanguage("tr", "Türkçe", "Turkish"),
        TmdbLanguage("sv", "Svenska", "Swedish"),
        TmdbLanguage("id", "Bahasa Indonesia", "Indonesian"),
    )

    private val DEFAULT_PROVIDERS = Providers.ALL.map { TmdbProvider(it.id, it.name, 0) }
}
