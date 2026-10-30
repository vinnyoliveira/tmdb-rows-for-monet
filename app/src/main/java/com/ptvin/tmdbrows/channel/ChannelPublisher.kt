package com.ptvin.tmdbrows.channel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.data.ArtStyle
import com.ptvin.tmdbrows.data.MediaType
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.Rows
import com.ptvin.tmdbrows.data.Tmdb
import com.ptvin.tmdbrows.data.TmdbItem
import com.ptvin.tmdbrows.data.label
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Publica cada linha como um canal do Android TV (o Monet lê por aqui). */
object ChannelPublisher {
    private const val MAX_PROGRAMS = 50

    fun publish(context: Context, row: RowConfig, items: List<TmdbItem>): Int {
        if (items.isEmpty()) throw IOException(context.getString(R.string.error_no_items))
        val helper = PreviewChannelHelper(context)
        val channelTitle = row.title.ifBlank { row.defaultTitle(context) }
        val channelId = Channels.upsert(
            context,
            helper,
            row.id,
            channelTitle,
            context.getString(R.string.channel_description, row.art.label(context)),
        )

        runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
        Channels.clearPrograms(context, channelId)
        val banner = row.art == ArtStyle.BACKDROP
        val cover = banner && row.showCover
        val bannerNames = items.take(MAX_PROGRAMS).mapIndexed { index, item ->
            Banner.fileName(
                row,
                item,
                if (index == 0 && cover) channelTitle else null,
                Banner.posterTag(context, item),
            )
        }.toSet()
        Banner.cleanup(context, row.id, bannerNames)

        val key = Prefs.tmdbKey(context)
        val banners = if (banner || Prefs.betterPosters(context)) {
            generateBanners(context, key, row, items, channelTitle)
        } else {
            emptyMap()
        }
        var published = 0
        items.take(MAX_PROGRAMS).forEachIndexed { index, item ->
            val program = buildProgram(context, row, item, channelId, index, banners[item.id])
                ?: return@forEachIndexed
            if (helper.publishPreviewProgram(program) != -1L) published++
        }
        if (published == 0) throw IOException(context.getString(R.string.error_publish_empty))
        Log.i(
            "TmdbRows",
            "canal $channelId · $published programas · " +
                items.take(4).joinToString(" | ") { it.displayTitle },
        )
        return published
    }

    /**
     * Remove canais deste app que já não correspondem a nenhuma linha
     * (linhas apagadas, reinstalações, publicações duplicadas). Sem isto
     * o Monet mostra canais antigos com conteúdo de outra linha.
     */
    fun reconcile(context: Context) {
        val valid = Rows.current().map { it.id }.toSet()
        val channels = ownedChannels(context)
        val seen = HashSet<String>()
        var removed = 0
        channels.forEach { (channelId, key) ->
            if (key.isNullOrBlank()) return@forEach
            val duplicate = !seen.add(key)
            if (key !in valid || duplicate) {
                runCatching { Channels.clearPrograms(context, channelId) }
                runCatching {
                    context.contentResolver.delete(TvContractCompat.buildChannelUri(channelId), null, null)
                }
                Prefs.clearChannelId(context, key)
                removed++
            }
        }
        Log.i("TmdbRows", "canais do app: ${channels.size} · limpos: $removed")
    }

    /** Canais publicados por este app (o TvProvider limita a consulta aos nossos). */
    private fun ownedChannels(context: Context): List<Pair<Long, String?>> {
        val projection: Array<String> = arrayOf(
            TvContractCompat.Channels._ID,
            TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID,
        )
        val found = ArrayList<Pair<Long, String?>>()
        runCatching {
            context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) found += cursor.getLong(0) to cursor.getString(1)
            }
        }.onFailure { Log.i("TmdbRows", "consulta de canais falhou: ${it.message}") }
        return found
    }

    fun delete(context: Context, rowId: String) {
        val channelId = Channels.resolveId(context, PreviewChannelHelper(context), rowId) ?: return
        Channels.clearPrograms(context, channelId)
        context.contentResolver.delete(TvContractCompat.buildChannelUri(channelId), null, null)
        Prefs.clearChannelId(context, rowId)
    }

    /** Apaga todos os canais deste app. Usado uma vez ao migrar de versões antigas. */
    fun deleteAll(context: Context) {
        ownedChannels(context).forEach { (channelId, key) ->
            runCatching { Channels.clearPrograms(context, channelId) }
            runCatching {
                context.contentResolver.delete(TvContractCompat.buildChannelUri(channelId), null, null)
            }
            if (!key.isNullOrBlank()) Prefs.clearChannelId(context, key)
        }
    }

    /** Banners em paralelo: são downloads + composição, um por título. */
    private fun generateBanners(
        context: Context,
        key: String,
        row: RowConfig,
        items: List<TmdbItem>,
        category: String,
    ): Map<Int, Uri> {
        val pool = Executors.newFixedThreadPool(3)
        return try {
            val tasks = items.take(MAX_PROGRAMS).mapIndexed { index, item ->
                // A capa da linha só existe nos banners 16:9.
                val label = if (index == 0 && row.art == ArtStyle.BACKDROP) category else null
                Callable { item.id to Banner.ensure(context, key, row, item, label) }
            }
            pool.invokeAll(tasks)
                .mapNotNull { runCatching { it.get() }.getOrNull() }
                .mapNotNull { (id, uri) -> uri?.let { id to it } }
                .toMap()
        } finally {
            pool.shutdown()
        }
    }

    private fun buildProgram(
        context: Context,
        row: RowConfig,
        item: TmdbItem,
        channelId: Long,
        index: Int,
        banner: Uri? = null,
    ): PreviewProgram? {
        val artUri = banner?.toString()
            ?: Tmdb.artUrl(
                row,
                item,
                language = if (Prefs.betterPosters(context)) Prefs.btttrLanguage(context) else null,
            )
            ?: return null
        val ratio = if (row.art == ArtStyle.POSTER) {
            TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
        } else {
            TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
        }

        val title = item.year?.let { "${titleOf(context, item)} ($it)" } ?: titleOf(context, item)
        val type = item.type ?: row.mediaType
        val intent = Intent(context, OpenTitleActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("tmdbrows://open/${type.tmdb}/${item.id}")
            putExtra(OpenTitleActivity.EXTRA_ID, item.id)
            putExtra(OpenTitleActivity.EXTRA_MEDIA, type.name)
            putExtra(OpenTitleActivity.EXTRA_TITLE, titleOf(context, item))
            row.opener?.let { putExtra(OpenTitleActivity.EXTRA_OPENER, it.name) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(
                if (type == MediaType.MOVIE) TvContractCompat.PreviewPrograms.TYPE_MOVIE
                else TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
            )
            .setTitle(title)
            .setDescription(item.overview.ifBlank { context.getString(R.string.from_tmdb) }.take(400))
            .setPosterArtUri(Uri.parse(artUri))
            .setThumbnailUri(Uri.parse(artUri))
            .setPosterArtAspectRatio(ratio)
            .setThumbnailAspectRatio(ratio)
            .setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
            .setBrowsable(true)
            .setContentId("tmdb:${item.id}")
            .setInternalProviderId("${row.id}:${item.id}")
            // O Monet ordena do maior peso para o menor: o primeiro item tem de ter o maior.
            .setWeight(MAX_PROGRAMS - index)
            .build()
    }

    private fun titleOf(context: Context, item: TmdbItem): String =
        item.displayTitle.ifBlank { context.getString(R.string.untitled) }
}
