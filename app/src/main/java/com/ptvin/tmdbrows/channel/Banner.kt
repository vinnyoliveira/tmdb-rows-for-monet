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
            val banner = if (!category.isNullOrBlank()) {
                // Capa: fundo aleatório da linha + nome na vertical.
                val art = (coverPath ?: item.backdropPath)
                    ?.let { download("$IMAGE_BASE/w1280$it") }?.let { decode(it) }
                    ?: return null
                renderCategory(art, category).also { art.recycle() }
            } else {
                val backdrop = item.backdropPath?.let { download("$IMAGE_BASE/w1280$it") }?.let { decode(it) }
                    ?: return null
                val logo = Tmdb.logoPath(context, key, type, item.id, Prefs.posterLanguage(context).substringBefore('-'))
                    ?.let { download("$IMAGE_BASE/w500$it") }
                    ?.let { decode(it) }
                val title = item.displayTitle.ifBlank { context.getString(R.string.untitled) }
                render(backdrop, logo, title, item.year, badges(context, item)).also {
                    backdrop.recycle()
                    logo?.recycle()
                }
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
        return "${row.id}-${item.id}$suffix$art$posterTag.jpg"
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

    /** Etiquetas por cima da arte: novidade, nota, ano e género (não há badges de qualidade no TMDB). */
    private fun badges(context: Context, item: TmdbItem): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>(4)
        val date = item.date
        val recent = date != null && runCatching {
            LocalDate.parse(date).isAfter(LocalDate.now().minusDays(60))
        }.getOrDefault(false)
        if (recent) out.add(context.getString(R.string.badge_new) to true)
        if (item.voteAverage > 0.0) {
            val rating: String = String.format(Locale.getDefault(), "%.1f", item.voteAverage)
            out.add("★ $rating" to false)
        }
        item.year?.let { out.add(it.toString() to false) }
        item.genreIds.firstOrNull()?.let { id ->
            val name = Genres.name(context, id).orEmpty()
            if (name.isNotBlank()) {
                out.add((if (name.length > 20) name.take(19) + "…" else name) to false)
            }
        }
        return out
    }

    private fun render(
        backdrop: Bitmap,
        logo: Bitmap?,
        title: String,
        year: Int?,
        badges: List<Pair<String, Boolean>>,
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

        // degradê à esquerda para o logo ter contraste
        paint.shader = LinearGradient(
            0f, 0f, W * 0.72f, 0f,
            Color.argb(235, 8, 8, 12), Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W * 0.72f, H.toFloat(), paint)
        paint.shader = LinearGradient(
            0f, H * 0.55f, 0f, H.toFloat(),
            Color.TRANSPARENT, Color.argb(150, 0, 0, 0),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, H * 0.55f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        val left = 78f

        if (logo != null) {
            val maxW = W * 0.44f
            val s = min(maxW / logo.width, 210f / logo.height)
            val lw = logo.width * s
            val lh = logo.height * s
            canvas.drawBitmap(logo, null, RectF(left, (H - lh) / 2f, left + lw, (H + lh) / 2f), paint)
        } else {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 62f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val maxW = W * 0.44f
            val width = text.measureText(title)
            if (width > maxW) text.textSize *= maxW / width
            val baseline = H / 2f + text.textSize / 3f
            canvas.drawText(title, left, baseline, text)
            if (year != null) {
                val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(200, 220, 220, 230)
                    textSize = 26f
                }
                canvas.drawText(year.toString(), left + 4f, baseline + 52f, small)
            }
        }
        drawBadges(canvas, badges, left)
        return out
    }

    private fun drawBadges(canvas: Canvas, badges: List<Pair<String, Boolean>>, left: Float) {
        if (badges.isEmpty()) return
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val box = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = H - 98f
        val bottom = H - 46f
        var x = left
        badges.forEach { (label, accent) ->
            box.color = if (accent) Color.parseColor("#E6BCC8FF") else Color.argb(200, 12, 16, 26)
            text.color = if (accent) Color.parseColor("#0E1C4F") else Color.parseColor("#E8ECFF")
            val width = text.measureText(label) + 40f
            canvas.drawRoundRect(RectF(x, top, x + width, bottom), 26f, 26f, box)
            canvas.drawText(label, x + 20f, bottom - 15f, text)
            x += width + 12f
        }
    }

    /** Capa da linha: fundo aleatório + nome na vertical. */
    private fun renderCategory(backdrop: Bitmap, category: String): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val scale = max(W / backdrop.width.toFloat(), H / backdrop.height.toFloat())
        val dw = backdrop.width * scale
        val dh = backdrop.height * scale
        canvas.drawBitmap(
            backdrop,
            null,
            RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f),
            paint,
        )
        paint.color = Color.argb(70, 0, 0, 0)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)

        // Painel vertical com o nome da linha.
        paint.color = Color.argb(238, 8, 8, 12)
        canvas.drawRect(0f, 0f, 300f, H.toFloat(), paint)
        paint.color = Color.parseColor("#BCC8FF")
        canvas.drawRect(296f, 0f, 300f, H.toFloat(), paint)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val text = category.uppercase()
        val maxLength = H - 120f
        while (title.measureText(text) > maxLength && title.textSize > 24f) title.textSize *= 0.94f
        canvas.save()
        canvas.rotate(-90f, 150f, H / 2f)
        canvas.drawText(text, 150f, H / 2f + title.textSize / 3f, title)
        canvas.restore()
        return out
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
