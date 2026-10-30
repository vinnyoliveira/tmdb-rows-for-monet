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
import com.ptvin.tmdbrows.data.isRecent
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

    // Escala de tipos: os mesmos corpos em todos os cartões da linha.
    private const val LEFT = 72f
    private const val ROW_NAME_X = 108f
    private const val ROW_NAME_SIZE = 40f
    private const val TITLE_SIZE = 76f
    private const val TAG_SIZE = 36f
    private const val LOGO_H = 210f
    private const val BAND_H = 88f

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
    ): Uri? {
        val dir = File(context.cacheDir, "banners").apply { mkdirs() }
        val file = File(dir, fileName(row, item, category, posterTag(context, item)))
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
            // O fundo é sempre o do próprio título: nunca mostrar a arte de outro filme.
            val backdrop = item.backdropPath
                ?.let { download("$IMAGE_BASE/w1280$it") }
                ?.let { decode(it) }
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

    /** Nome do ficheiro; capa e cartaz entram no nome para o banner ser refeito quando mudam. */
    fun fileName(
        row: RowConfig,
        item: TmdbItem,
        category: String? = null,
        posterTag: String = "",
    ): String {
        val suffix = category?.takeIf { it.isNotBlank() }?.let { "-c${it.hashCode()}" }.orEmpty()
        // Muda com o desenho do cartão: garante que os antigos são refeitos uma vez.
        return "${row.id}-${item.id}$suffix$posterTag-d4.jpg"
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
        if (type == MediaType.TV && Prefs.badgeEpisodes(context) && isRecent(item.newEpisodeAt, 3)) {
            return Ribbon(
                context.getString(R.string.ribbon_new_episode),
                0xFFC9D6FF.toInt(),
                0xFF8FE3FF.toInt(),
            )
        }
        if (type == MediaType.MOVIE && Prefs.badgeNew(context) && isRecent(item.date, 15)) {
            return Ribbon(context.getString(R.string.ribbon_new), 0xFFFFD783.toInt(), 0xFFFFA94D.toInt())
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
        // Pincel só para imagens: não herda cor/alpha dos scrims (senão o logo sai transparente).
        val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // backdrop em cover
        val scale = max(W / backdrop.width.toFloat(), H / backdrop.height.toFloat())
        val dw = backdrop.width * scale
        val dh = backdrop.height * scale
        canvas.drawBitmap(
            backdrop,
            null,
            RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f),
            art,
        )

        // Scrim da esquerda e faixa de baixo: iguais em todos os cartões.
        paint.shader = LinearGradient(
            0f, 0f, W * 0.62f, 0f,
            Color.argb(215, 6, 8, 14), Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W * 0.62f, H.toFloat(), paint)
        paint.shader = LinearGradient(
            0f, H - 230f, 0f, H - BAND_H,
            Color.TRANSPARENT, Color.argb(235, 6, 8, 14),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, H - 230f, W.toFloat(), H - BAND_H, paint)
        paint.shader = null
        paint.color = Color.argb(235, 6, 8, 14)
        canvas.drawRect(0f, H - BAND_H, W.toFloat(), H.toFloat(), paint)
        paint.color = Color.argb(30, 255, 255, 255)
        canvas.drawRect(0f, H - BAND_H, W.toFloat(), H - BAND_H + 1.5f, paint)

        val cover = !category.isNullOrBlank()

        if (cover) {
            // Capa: nome da linha na vertical, sempre no mesmo corpo.
            val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(235, 234, 239, 255)
                textSize = ROW_NAME_SIZE
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            val label = category.uppercase()
            val maxLength = H - BAND_H - 150f
            while (name.measureText(label) > maxLength && name.textSize > 24f) name.textSize *= 0.94f
            val axisY = (H - BAND_H) / 2f + 6f
            canvas.save()
            canvas.rotate(-90f, ROW_NAME_X, axisY)
            canvas.drawText(label, ROW_NAME_X, axisY + name.textSize / 3f, name)
            canvas.restore()
        }

        val textLeft = if (cover) 176f else LEFT
        val titleWidth = W - textLeft - LEFT

        if (withTitle) {
            if (logo != null) {
                // A arte do título também aparece na capa, no mesmo tamanho dos outros cartões.
                val s = min(titleWidth / logo.width, LOGO_H / logo.height)
                val lw = logo.width * s
                val lh = logo.height * s
                val centerY = (H - BAND_H) / 2f
                canvas.drawBitmap(
                    logo,
                    null,
                    RectF(textLeft, centerY - lh / 2f, textLeft + lw, centerY + lh / 2f),
                    art,
                )
            } else {
                // Sem logo: título grande, igual na capa e nos restantes.
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(250, 245, 247, 255)
                    textSize = TITLE_SIZE
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                val lines = wrap(title, text, titleWidth, 2)
                var y = H - BAND_H - 34f - (lines.size - 1) * text.textSize * 1.1f
                lines.forEach { line ->
                    canvas.drawText(line, textLeft, y, text)
                    y += text.textSize * 1.1f
                }
            }
        }

        drawTags(canvas, badges)
        if (ribbon != null) drawRibbon(canvas, ribbon, LEFT)
        return out
    }

    /**
     * Etiquetas na faixa de baixo: letras grandes, em maiúsculas, repartidas por toda a largura
     * (a primeira à esquerda, a última à direita).
     */
    private fun drawTags(canvas: Canvas, badges: List<String>) {
        if (badges.isEmpty()) return
        val labels = badges.map { it.uppercase() }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TAG_SIZE
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val available = W - LEFT * 2f
        var widths = labels.map { text.measureText(it) }
        var total = widths.sum()
        // Se não couber, reduz o corpo o mínimo necessário (nunca sobrepõe).
        if (total > available && labels.size > 1) {
            text.textSize = TAG_SIZE * (available / total)
            widths = labels.map { text.measureText(it) }
            total = widths.sum()
        }
        val baseline = H - 33f
        val gap = if (labels.size > 1) (available - total) / (labels.size - 1) else 0f
        var x = LEFT
        labels.forEachIndexed { index, label ->
            text.color = if (index == 0) {
                Color.parseColor("#DCE4FF")
            } else {
                Color.argb(232, 236, 240, 255)
            }
            canvas.drawText(label, x, baseline, text)
            x += widths[index] + gap
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
