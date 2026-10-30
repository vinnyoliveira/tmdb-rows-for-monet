package com.ptvin.tmdbrows

import com.ptvin.tmdbrows.data.MediaType
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.RowIdInput
import com.ptvin.tmdbrows.data.RowMode
import com.ptvin.tmdbrows.data.Section
import com.ptvin.tmdbrows.data.Tmdb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbUrlTest {

    @Test
    fun discoverSendsFiltersToServer() {
        val row = RowConfig(
            mediaType = MediaType.MOVIE,
            section = Section.DISCOVER,
            genreIds = listOf(27, 35),
            minRating = 7.0,
            yearFrom = 2000,
            sortBy = "vote_average.desc",
        )

        val url = Tmdb.buildUrl(row, page = 2, key = "abc", region = "BR", language = "pt-BR")

        assertEquals("https://api.themoviedb.org/3/discover/movie", url.toString().substringBefore("?"))
        assertEquals("abc", url.queryParameter("api_key"))
        assertEquals("27|35", url.queryParameter("with_genres"))
        assertEquals("7.0", url.queryParameter("vote_average.gte"))
        assertEquals("2000-01-01", url.queryParameter("primary_release_date.gte"))
        assertEquals("50", url.queryParameter("vote_count.gte"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("pt-BR", url.queryParameter("language"))
        assertNull(url.queryParameter("region"))
    }

    @Test
    fun trendingHasNoDiscoverFilters() {
        val row = RowConfig(mediaType = MediaType.TV, section = Section.TRENDING_DAY, genreIds = listOf(9648))

        val url = Tmdb.buildUrl(row, page = 1, key = "abc", language = "pt-BR")

        assertEquals("https://api.themoviedb.org/3/trending/tv/day", url.toString().substringBefore("?"))
        assertNull(url.queryParameter("with_genres"))
        assertNull(url.queryParameter("sort_by"))
        assertEquals("en-US", Tmdb.buildUrl(row, 1, "abc").queryParameter("language"))
    }

    @Test
    fun readTokenDoesNotLeakIntoQuery() {
        val row = RowConfig(mediaType = MediaType.MOVIE, section = Section.POPULAR)

        val url = Tmdb.buildUrl(row, page = 1, key = "eyJhbGciOiJIUzI1NiJ9.token")

        assertNull(url.queryParameter("api_key"))
        assertTrue(url.toString().startsWith("https://api.themoviedb.org/3/movie/popular?"))
    }

    @Test
    fun nowPlayingAsksForTheRegion() {
        val url = Tmdb.buildUrl(
            RowConfig(mediaType = MediaType.MOVIE, section = Section.NOW_PLAYING),
            1,
            "abc",
            region = "PT",
        )

        assertEquals("PT", url.queryParameter("region"))
    }

    @Test
    fun tvDiscoverUsesFirstAirDate() {
        val row = RowConfig(mediaType = MediaType.TV, section = Section.DISCOVER, yearFrom = 2015)

        val url = Tmdb.buildUrl(row, 1, "abc")

        assertEquals("2015-01-01", url.queryParameter("first_air_date.gte"))
        assertNull(url.queryParameter("primary_release_date.gte"))
    }

    @Test
    fun platformUsesDiscoverWithProviderAndRegion() {
        val row = RowConfig(
            mode = RowMode.PLATFORM,
            mediaType = MediaType.TV,
            providers = listOf(307),
            genreIds = listOf(10766),
        )

        val url = Tmdb.buildUrl(row, 1, "abc", region = "BR")

        assertEquals("https://api.themoviedb.org/3/discover/tv", url.toString().substringBefore("?"))
        assertEquals("307", url.queryParameter("with_watch_providers"))
        assertEquals("BR", url.queryParameter("watch_region"))
        assertEquals("flatrate", url.queryParameter("with_watch_monetization_types"))
        assertEquals("10766", url.queryParameter("with_genres"))
        assertEquals("popularity.desc", url.queryParameter("sort_by"))
    }

    @Test
    fun aleatorioNaoEnviaSortBy() {
        val row = RowConfig(mode = RowMode.PLATFORM, mediaType = MediaType.TV, providers = listOf(307), sortBy = RowConfig.RANDOM)

        val url = Tmdb.buildUrl(row, 1, "abc", region = "BR")

        assertNull(url.queryParameter("sort_by"))
        assertEquals("307", url.queryParameter("with_watch_providers"))
    }

    @Test
    fun plataformaHojeNaTvFiltraPelaData() {
        val row = RowConfig(
            mode = RowMode.PLATFORM,
            mediaType = MediaType.TV,
            section = Section.AIRING_TODAY,
            providers = listOf(307),
        )

        val url = Tmdb.buildUrl(row, 1, "abc", region = "BR")
        val today = java.time.LocalDate.now().toString()

        assertEquals("https://api.themoviedb.org/3/discover/tv", url.toString().substringBefore("?"))
        assertEquals(today, url.queryParameter("air_date.gte"))
        assertEquals(today, url.queryParameter("air_date.lte"))
        assertEquals("307", url.queryParameter("with_watch_providers"))
    }

    @Test
    fun novasSeriesUsamADataDeEstreia() {
        val row = RowConfig(mediaType = MediaType.TV, section = Section.NEW_SERIES)

        val url = Tmdb.buildUrl(row, 1, "abc")

        assertEquals("https://api.themoviedb.org/3/discover/tv", url.toString().substringBefore("?"))
        assertEquals("first_air_date.desc", url.queryParameter("sort_by"))
        assertEquals(
            java.time.LocalDate.now().minusDays(30).toString(),
            url.queryParameter("first_air_date.gte"),
        )
    }

    @Test
    fun cartazBetterPostersUsaOIdDoImdb() {
        val row = RowConfig(art = com.ptvin.tmdbrows.data.ArtStyle.POSTER)
        val item = com.ptvin.tmdbrows.data.TmdbItem(id = 862, imdbId = "tt0114709")

        assertEquals(
            "https://btttr.cc/poster/imdb/poster-default/tt0114709.jpg?lang=pt-BR&rs=TR",
            Tmdb.artUrl(row, item, language = "pt-BR"),
        )
        // Sem o IMDb cai para o TMDB.
        assertEquals(
            null,
            Tmdb.artUrl(row, com.ptvin.tmdbrows.data.TmdbItem(id = 862), language = "pt-BR"),
        )
    }

    @Test
    fun novosEpisodiosNaoVaoParaOServidor() {
        val row = RowConfig(
            mode = RowMode.PLATFORM,
            mediaType = MediaType.TV,
            providers = listOf(307),
            sortBy = RowConfig.LAST_EPISODE,
        )

        val url = Tmdb.buildUrl(row, 1, "abc", region = "BR")

        assertNull(url.queryParameter("sort_by"))
        assertEquals("307", url.queryParameter("with_watch_providers"))
    }

    @Test
    fun ordenarPorNovosEpisodiosSoEmSeries() {
        assertTrue(RowConfig.LAST_EPISODE in RowConfig.sortsFor(MediaType.TV))
        assertTrue(RowConfig.LAST_EPISODE !in RowConfig.sortsFor(MediaType.MOVIE))
    }

    @Test
    fun leIdsELinksDoSite() {
        val dois = RowIdInput.parse("550, 1399")
        assertEquals(listOf(550, 1399), dois.map { it.id })
        assertNull(dois[0].type)

        val comLink = RowIdInput.parse("https://www.themoviedb.org/tv/1399-game-of-thrones")
        assertEquals(1, comLink.size)
        assertEquals(1399, comLink[0].id)
        assertEquals(MediaType.TV, comLink[0].type)

        assertTrue(RowIdInput.parse("nada aqui").isEmpty())
    }
}
