package com.ptvin.tmdbrows.data

import android.content.Context
import com.ptvin.tmdbrows.R
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

@Serializable
enum class MediaType(val tmdb: String, val deepLink: String) {
    MOVIE("movie", "movie"),
    TV("tv", "series"),
}

/** Como a linha escolhe os títulos. */
@Serializable
enum class RowMode { FILTER, IDS, PLATFORM }

/** Título escolhido à mão. [type] nulo = ainda não se sabe se é filme ou série. */
@Serializable
data class RowItem(
    val id: Int,
    val type: MediaType? = null,
    val title: String = "",
)

@Serializable
enum class Section {
    TRENDING_DAY,
    TRENDING_WEEK,
    POPULAR,
    TOP_RATED,
    UPCOMING,
    NOW_PLAYING,
    ON_THE_AIR,
    AIRING_TODAY,
    NEW_SERIES,
    DISCOVER,
}

@Serializable
enum class ArtStyle { POSTER, BACKDROP }

@Serializable
data class RowConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val mode: RowMode = RowMode.FILTER,
    val mediaType: MediaType = MediaType.MOVIE,
    val section: Section = Section.POPULAR,
    val genreIds: List<Int> = emptyList(),
    val minRating: Double = 0.0,
    val yearFrom: Int? = null,
    val sortBy: String = "popularity.desc",
    val itemCount: Int = 20,
    /** [RowMode.IDS]: títulos escolhidos à mão. */
    val items: List<RowItem> = emptyList(),
    /** [RowMode.PLATFORM]: ids de plataformas do TMDB (Netflix = 8). */
    val providers: List<Int> = emptyList(),
    /** Abre no Nuvio/Stremio só nesta linha. Nulo = seguir o ajuste geral. */
    val opener: Opener? = null,
    /** Primeiro cartão com o nome da linha na vertical. */
    val showCover: Boolean = true,
    // O Monet desenha os cartões em 16:9 e ignora o aspect ratio do canal:
    // com pôster 2:3 ele põe barras desfocadas dos lados.
    val art: ArtStyle = ArtStyle.BACKDROP,
    val lastPublishedAt: Long? = null,
    val lastError: String? = null,
) {
    fun defaultTitle(context: Context): String = when (mode) {
        RowMode.IDS -> context.getString(R.string.row_custom_title)
        RowMode.PLATFORM -> providers.take(2).joinToString(" · ") { Providers.name(it) }
            .ifBlank { context.getString(R.string.row_platform_fallback) }
        RowMode.FILTER -> (genreIds.mapNotNull { Genres.name(context, it) }.take(2) +
            section.label(context)).joinToString(" · ")
    }

    fun summary(context: Context): String = buildList {
        when (mode) {
            RowMode.IDS -> {
                add(itemsText(context, items.size))
            }
            RowMode.PLATFORM -> {
                add(mediaType.label(context))
                providers.forEach { add(Providers.name(it)) }
                addFilters(context)
            }
            RowMode.FILTER -> {
                add(mediaType.label(context))
                add(section.label(context))
                genreIds.mapNotNull { Genres.name(context, it) }.take(2).forEach { add(it) }
                addFilters(context)
            }
        }
        if (mode != RowMode.IDS) add(itemsText(context))
        add(art.label(context))
        opener?.let { add(it.label) }
    }.filter { it.isNotBlank() }.joinToString(" · ")

    private fun MutableList<String>.addFilters(context: Context) {
        if (mode == RowMode.FILTER && section != Section.DISCOVER) return
        add(sortLabel(context, sortBy))
        if (minRating > 0.0) add(context.getString(R.string.summary_rating, minRating.toString().replace('.', ',')))
        yearFrom?.let { add(context.getString(R.string.summary_year, it)) }
    }

    private fun itemsText(context: Context, count: Int = itemCount): String =
        context.resources.getQuantityString(R.plurals.summary_items, count, count)

    companion object {
        const val RANDOM = "random"
        const val LAST_EPISODE = "last_episode.desc"

        val SORTS = listOf(
            "popularity.desc",
            "vote_average.desc",
            "primary_release_date.desc",
            RANDOM,
        )

        fun sortsFor(type: MediaType): List<String> =
            if (type == MediaType.TV) SORTS + LAST_EPISODE else SORTS

        fun sectionsFor(type: MediaType): List<Section> = when (type) {
            MediaType.MOVIE -> listOf(
                Section.POPULAR,
                Section.TRENDING_DAY,
                Section.TRENDING_WEEK,
                Section.TOP_RATED,
                Section.NOW_PLAYING,
                Section.UPCOMING,
                Section.DISCOVER,
            )
            MediaType.TV -> listOf(
                Section.POPULAR,
                Section.TRENDING_DAY,
                Section.TRENDING_WEEK,
                Section.TOP_RATED,
                Section.ON_THE_AIR,
                Section.AIRING_TODAY,
                Section.NEW_SERIES,
                Section.DISCOVER,
            )
        }
    }
}

fun MediaType.label(context: Context): String = context.getString(
    if (this == MediaType.MOVIE) R.string.media_movie else R.string.media_tv,
)

fun Section.label(context: Context): String = context.getString(
    when (this) {
        Section.TRENDING_DAY -> R.string.section_trending_day
        Section.TRENDING_WEEK -> R.string.section_trending_week
        Section.POPULAR -> R.string.section_popular
        Section.TOP_RATED -> R.string.section_top_rated
        Section.UPCOMING -> R.string.section_upcoming
        Section.NOW_PLAYING -> R.string.section_now_playing
        Section.ON_THE_AIR -> R.string.section_on_the_air
        Section.AIRING_TODAY -> R.string.section_airing_today
        Section.NEW_SERIES -> R.string.section_new_series
        Section.DISCOVER -> R.string.section_discover
    },
)

fun ArtStyle.label(context: Context): String = context.getString(
    if (this == ArtStyle.POSTER) R.string.art_poster else R.string.art_backdrop,
)

fun RowMode.label(context: Context): String = context.getString(
    when (this) {
        RowMode.FILTER -> R.string.row_mode_filter
        RowMode.IDS -> R.string.row_mode_ids
        RowMode.PLATFORM -> R.string.row_mode_platform
    },
)

/** Nome curto para as abas, onde o texto longo não cabe. */
fun RowMode.shortLabel(context: Context): String = context.getString(
    when (this) {
        RowMode.FILTER -> R.string.row_mode_filter_short
        RowMode.IDS -> R.string.row_mode_ids_short
        RowMode.PLATFORM -> R.string.row_mode_platform_short
    },
)

fun sortLabel(context: Context, code: String): String = context.getString(
    when (code) {
        "vote_average.desc" -> R.string.sort_rating
        "primary_release_date.desc" -> R.string.sort_newest
        RowConfig.RANDOM -> R.string.sort_random
        RowConfig.LAST_EPISODE -> R.string.sort_new_episodes
        else -> R.string.sort_popularity
    },
)

object Genres {
    private val movieIds = listOf(28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27, 10402, 9648, 10749, 878, 53, 10752, 37)
    private val tvIds = listOf(10759, 16, 35, 80, 99, 18, 10751, 10762, 9648, 10763, 10764, 10765, 10766, 10767, 10768, 37)

    private val names = mapOf(
        28 to R.string.genre_28,
        12 to R.string.genre_12,
        16 to R.string.genre_16,
        35 to R.string.genre_35,
        80 to R.string.genre_80,
        99 to R.string.genre_99,
        18 to R.string.genre_18,
        10751 to R.string.genre_10751,
        14 to R.string.genre_14,
        36 to R.string.genre_36,
        27 to R.string.genre_27,
        10402 to R.string.genre_10402,
        9648 to R.string.genre_9648,
        10749 to R.string.genre_10749,
        878 to R.string.genre_878,
        53 to R.string.genre_53,
        10752 to R.string.genre_10752,
        37 to R.string.genre_37,
        10759 to R.string.genre_10759,
        10762 to R.string.genre_10762,
        10763 to R.string.genre_10763,
        10764 to R.string.genre_10764,
        10765 to R.string.genre_10765,
        10766 to R.string.genre_10766,
        10767 to R.string.genre_10767,
        10768 to R.string.genre_10768,
    )

    fun namesFor(context: Context, type: MediaType): List<Pair<Int, String>> =
        (if (type == MediaType.MOVIE) movieIds else tvIds).map { id ->
            id to name(context, id).orEmpty()
        }

    fun name(context: Context, id: Int): String? = names[id]?.let(context::getString)
}

/** Plataformas de streaming mais comuns (ids do TMDB, usados só como recurso offline). */
data class Provider(val id: Int, val name: String)

object Providers {
    val ALL = listOf(
        Provider(8, "Netflix"),
        Provider(119, "Prime Video"),
        Provider(337, "Disney+"),
        Provider(1899, "Max"),
        Provider(307, "Globoplay"),
        Provider(350, "Apple TV+"),
        Provider(531, "Paramount+"),
        Provider(283, "Crunchyroll"),
        Provider(300, "Pluto TV"),
        Provider(47, "Looke"),
        Provider(484, "Claro tv+"),
        Provider(11, "MUBI"),
    )

    private val known = LinkedHashMap<Int, String>(ALL.associate { it.id to it.name })

    /** Guarda os nomes das plataformas da região para aparecerem nos resumos. */
    fun remember(list: List<TmdbProvider>) {
        list.forEach { known[it.id] = it.name }
    }

    fun name(id: Int): String = known[id] ?: "#$id"
}

/** Região usada nas consultas: segue o aparelho e, sem país, o idioma. */
fun deviceRegion(): String {
    val locale = Locale.getDefault()
    val country = locale.country.uppercase()
    if (country.isNotBlank()) return country
    return when (locale.language.lowercase()) {
        "pt" -> "BR"
        "es" -> "ES"
        "fr" -> "FR"
        "de" -> "DE"
        "it" -> "IT"
        "ja" -> "JP"
        "ko" -> "KR"
        "zh" -> "CN"
        "ru" -> "RU"
        else -> "US"
    }
}

/** Idioma do aparelho no formato do TMDB (`pt-BR`, `es-ES`, …). */
fun deviceLanguage(): String = Locale.getDefault().toLanguageTag()

object DefaultRows {
    fun all(context: Context): List<RowConfig> = listOf(
        RowConfig(
            id = "seed-trending-movie",
            title = context.getString(R.string.seed_trending_movie),
            mediaType = MediaType.MOVIE,
            section = Section.TRENDING_DAY,
        ),
        RowConfig(
            id = "seed-trending-tv",
            title = context.getString(R.string.seed_trending_tv),
            mediaType = MediaType.TV,
            section = Section.TRENDING_DAY,
        ),
        RowConfig(
            id = "seed-popular-movie",
            title = context.getString(R.string.seed_popular_movie),
            mediaType = MediaType.MOVIE,
            section = Section.POPULAR,
        ),
        RowConfig(
            id = "seed-popular-tv",
            title = context.getString(R.string.seed_popular_tv),
            mediaType = MediaType.TV,
            section = Section.POPULAR,
        ),
        RowConfig(
            id = "seed-top-rated",
            title = context.getString(R.string.seed_top_rated),
            mediaType = MediaType.MOVIE,
            section = Section.TOP_RATED,
        ),
        RowConfig(
            id = "seed-horror",
            title = context.getString(R.string.seed_horror),
            mediaType = MediaType.MOVIE,
            section = Section.DISCOVER,
            genreIds = listOf(27),
            minRating = 6.0,
        ),
    )
}
