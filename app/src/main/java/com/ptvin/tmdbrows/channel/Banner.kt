package com.ptvin.tmdbrows.channel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.data.ArtStyle
import com.ptvin.tmdbrows.data.Genres
import com.ptvin.tmdbrows.data.MediaType
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.Tmdb
import com.ptvin.tmdbrows.data.TmdbItem
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Desenha o banner 16:9 que o Monet mostra: backdrop + logo do título.
 * O resultado é servido por [BannerProvider] em `content://com.ptvin.tmdbrows.art/<arquivo>`.
 */
object Banner {
    private const val AUTHORITY = "com.ptvin.tmdbrows.art"
    private const val W = 1280
    private const val H = 720
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p"

    private data class Ribbon(val label: String, val start: Int, val end: Int)

    /**
     * Gera o banner se ainda não existir.
     * [category] só é usada no primeiro item: escreve o nome da linha na vertical,
     * como uma "capa" da categoria.
     * shortcut: um download + composição por título, sequencial;
     * se a publicação ficar lenta, gerar em paralelo.
     */
    fun ensure(
        context: Context,
        key: String,
        row: RowConfig,
        item: TmdbItem,
        category: String? = null,
        coverPath: String? = null,
    ): Uri? {
        val dir = File(context.cacheDir, "banners").apply { mkdirs() }
        val file = File(dir, fileName(row, item, category, coverPath, posterTag(context, item)))
        if (!file.exists()) {
            val type = item.type ?: row.mediaType
            if (row.art == ArtStyle.POSTER) {
                // Cartaz: BetterPosters (opcional) ou o póster do TMDB. Guarda-se o ficheiro
                // original: o canal fica a apontar para content://, sem depender do servidor.
                val bytes = posterBytes(context, item) ?: return null
                runCatching { FileOutputStream(file).use { it.write(bytes) } }
                if (!file.exists()) return null
                return Uri.parse("content://$AUTHORITY/${file.name}")
            }
            val backdropUrl = if (!category.isNullOrBlank()) coverPath ?: item.backdropPath else item.backdropPath
            val backdrop = backdropUrl?.let { download("$IMAGE_BASE/w1280$it") }?.let { decode(it) }
                ?: return null
            val withTitle = Prefs.cardTitle(context)
            val logo = if (category.isNullOrBlank() && withTitle) {
                Tmdb.logoPath(context, key, type, item.id, Prefs.posterLanguage(context).substringBefore('-'))
                    ?.let { download("$IMAGE_BASE/w500$it") }
                    ?.let { decode(it) }
            } else {
                null
            }
            val title = item.displayTitle.ifBlank { context.getString(R.string.untitled) }
            val banner = render(
                backdrop = backdrop,
                logo = logo,
                title = title,
                badges = if (Prefs.cardTags(context)) badges(context, item) else emptyList(),
                ribbon = if (category.isNullOrBlank()) ribbon(context, row, item) else null,
                category = category,
                withTitle = withTitle,
            ).also {
                backdrop.recycle()
                logo?.recycle()
            }
            runCatching { FileOutputStream(file).use { banner.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
            banner.recycle()
            if (!file.exists()) return null
        }
        return Uri.parse("content://$AUTHORITY/${file.name}")
    }

    /** Cartaz em bytes: BetterPosters quando ligado, com o póster do TMDB por baixo. */
    private fun posterBytes(context: Context, item: TmdbItem): ByteArray? {
        val tmdb = item.posterPath?.let { download("$IMAGE_BASE/w500$it") }
        if (!Prefs.betterPosters(context)) return tmdb
        val imdb = item.imdbId ?: return tmdb
        val url = "https://btttr.cc/poster/imdb/poster-default/$imdb.jpg" +
            "?lang=${Prefs.btttrLanguage(context)}&rs=TR"
        // Uma página de erro passa por "descarregado": só aceita mesmo uma imagem.
        return download(url)?.takeIf { isImage(it) } ?: tmdb
    }

    private fun isImage(bytes: ByteArray): Boolean = bytes.size > 2000 && when {
        bytes.size < 12 -> false
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> true
        bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> true
        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" -> true
        else -> false
    }

    /** Nome do ficheiro; capa, fundo e cartaz entram no nome para o banner ser refeito quando mudam. */
    fun fileName(
        row: RowConfig,
        item: TmdbItem,
        category: String? = null,
        coverPath: String? = null,
        posterTag: String = "",
    ): String {
        val suffix = category?.takeIf { it.isNotBlank() }?.let { "-c${it.hashCode()}" }.orEmpty()
        val art = coverPath?.let { "-a${it.hashCode()}" }.orEmpty()
        // Muda com o desenho do cartão: garante que os antigos são refeitos uma vez.
        return "${row.id}-${item.id}$suffix$art$posterTag-d2.jpg"
    }

    /** Marca do cartaz: muda com o id do IMDb e com o idioma, para não servir o antigo. */
    fun posterTag(context: Context, item: TmdbItem): String {
        if (!Prefs.betterPosters(context)) return ""
        val imdb = item.imdbId ?: return ""
        return "-b$imdb-${Prefs.btttrLanguage(context)}"
    }

    /** Apaga banners da linha que já não fazem parte desta publicação. */
    fun cleanup(context: Context, rowId: String, keep: Set<String>) {
        val dir = File(context.cacheDir, "banners")
        dir.listFiles()?.forEach { file ->
            if (!file.name.startsWith("$rowId-")) return@forEach
            if (file.name !in keep) file.delete()
        }
    }

    /** Etiquetas neutras em baixo: nota, ano e género (os selos são à parte, em cima). */
    private fun badges(context: Context, item: TmdbItem): List<String> {
        val out = ArrayList<String>(3)
        if (item.voteAverage > 0.0) {
            val rating: String = String.format(Locale.getDefault(), "%.1f", item.voteAverage)
            out.add("★ $rating")
        }
        item.year?.let { out.add(it.toString()) }
        item.genreIds.firstOrNull()?.let { id ->
            val name = Genres.name(context, id).orEmpty()
            if (name.isNotBlank()) out.add(if (name.length > 20) name.take(19) + "…" else name)
        }
        return out
    }

    /** Selo em cima: novidade no cinema (15 dias) ou episódio novo (3 dias). */
    private fun ribbon(context: Context, row: RowConfig, item: TmdbItem): Ribbon? {
        val type = item.type ?: row.mediaType
        val today = LocalDate.now()
        val date = { raw: String? ->
            raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        }
        if (type == MediaType.TV && Prefs.badgeEpisodes(context)) {
            val at = date(item.newEpisodeAt)
            if (at != null && !at.isBefore(today.minusDays(3)) && !at.isAfter(today)) {
                return Ribbon(
                    context.getString(R.string.ribbon_new_episode),
                    0xFFC9D6FF.toInt(),
                    0xFF8FE3FF.toInt(),
                )
            }
        }
        if (type == MediaType.MOVIE && Prefs.badgeNew(context)) {
            val at = date(item.date)
            if (at != null && !at.isBefore(today.minusDays(15)) && !at.isAfter(today)) {
                return Ribbon(context.getString(R.string.ribbon_new), 0xFFFFD783.toInt(), 0xFFFFA94D.toInt())
            }
        }
        return null
    }

    private fun render(
        backdrop: Bitmap,
        logo: Bitmap?,
        title: String,
        badges: List<String>,
        ribbon: Ribbon?,
        category: String?,
        withTitle: Boolean,
    ): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // backdrop em cover
        val scale = max(W / backdrop.width.toFloat(), H / backdrop.height.toFloat())
        val dw = backdrop.width * scale
        val dh = backdrop.height * scale
        canvas.drawBitmap(
            backdrop,
            null,
            RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f),
            paint,
        )

        // Scrim: garante contraste para o texto sem tapar a arte.
        paint.shader = LinearGradient(
            0f, 0f, W * 0.64f, 0f,
            Color.argb(220, 6, 8, 14), Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W * 0.64f, H.toFloat(), paint)
        paint.shader = LinearGradient(
            0f, H * 0.42f, 0f, H.toFloat(),
            Color.TRANSPARENT, Color.argb(230, 6, 8, 14),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, H * 0.42f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        val left = 78f

        if (!category.isNullOrBlank()) {
            // Capa: nome da linha na vertical, como marca d'água.
            val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(240, 234, 239, 255)
                textSize = 64f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            val label = category.uppercase()
            val maxLength = H - 150f
            while (name.measureText(label) > maxLength && name.textSize > 26f) name.textSize *= 0.94f
            canvas.save()
            canvas.rotate(-90f, 150f, H / 2f)
            canvas.drawText(label, 150f, H / 2f + name.textSize / 3f, name)
            canvas.restore()
        }

        val textLeft = if (category.isNullOrBlank()) left else 236f

        if (withTitle && !category.isNullOrBlank()) {
            // Capa: também mostra o filme do primeiro cartão.
            val movie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(236, 246, 248, 255)
                textSize = 54f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val lines = wrap(title, movie, W * 0.6f - textLeft, 2)
            var y = H - 158f - (lines.size - 1) * movie.textSize * 1.12f
            lines.forEach { line ->
                canvas.drawText(line, textLeft, y, movie)
                y += movie.textSize * 1.12f
            }
        } else if (withTitle && logo != null) {
            val maxW = W * 0.5f
            val s = min(maxW / logo.width, 200f / logo.height)
            val lw = logo.width * s
            val lh = logo.height * s
            canvas.drawBitmap(logo, null, RectF(left, H * 0.46f - lh / 2f, left + lw, H * 0.46f + lh / 2f), paint)
        } else if (withTitle) {
            // Sem logo: título grande, com ar de marca d'água.
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(226, 240, 243, 250)
                textSize = 86f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val lines = wrap(title, text, W * 0.6f - left, 2)
            var y = H - 158f - (lines.size - 1) * text.textSize * 1.1f
            lines.forEach { line ->
                canvas.drawText(line, left, y, text)
                y += text.textSize * 1.1f
            }
        }

        drawBadges(canvas, badges, textLeft)
        if (ribbon != null) drawRibbon(canvas, ribbon, left)
        return out
    }

    private fun drawBadges(canvas: Canvas, badges: List<String>, left: Float) {
        if (badges.isEmpty()) return
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            color = Color.argb(238, 232, 236, 255)
        }
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 10, 13, 22) }
        val top = H - 98f
        val bottom = H - 46f
        var x = left
        badges.forEach { label ->
            val width = text.measureText(label) + 40f
            canvas.drawRoundRect(RectF(x, top, x + width, bottom), 26f, 26f, box)
            canvas.drawText(label, x + 20f, bottom - 15f, text)
            x += width + 12f
        }
    }

    /** Selo de destaque no canto: pílula com gradiente, brilho e um ponto. */
    private fun drawRibbon(canvas: Canvas, ribbon: Ribbon, left: Float) {
        val top = 50f
        val bottom = 108f
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            color = Color.argb(245, 20, 16, 8)
        }
        val width = text.measureText(ribbon.label) + 68f
        val rect = RectF(left, top, left + width, bottom)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, Color.red(ribbon.start), Color.green(ribbon.start), Color.blue(ribbon.start))
        }
        canvas.drawRoundRect(RectF(left - 6f, top - 6f, left + width + 6f, bottom + 6f), 34f, 34f, glow)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(left, top, left + width, bottom, ribbon.start, ribbon.end, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, 29f, 29f, fill)
        fill.shader = null
        canvas.drawCircle(left + 28f, (top + bottom) / 2f, 7f, text)
        canvas.drawText(ribbon.label, left + 46f, bottom - 17f, text)
    }

    /** Quebra o título em até [maxLines] linhas, reduzindo a letra se preciso. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        var size = paint.textSize
        while (true) {
            val lines = ArrayList<String>()
            val line = StringBuilder()
            text.split(' ').forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    line.clear().append(candidate)
                } else {
                    if (line.isNotEmpty()) lines += line.toString()
                    line.clear().append(word)
                }
            }
            if (line.isNotEmpty()) lines += line.toString()
            if (lines.size <= maxLines || size <= 40f) return lines.take(maxLines)
            size *= 0.9f
            paint.textSize = size
        }
    }
    private fun download(url: String): ByteArray? = runCatching {
        Tmdb.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
        })
    }.getOrNull()
}
