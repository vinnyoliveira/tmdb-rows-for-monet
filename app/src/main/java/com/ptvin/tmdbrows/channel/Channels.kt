package com.ptvin.tmdbrows.channel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import com.ptvin.tmdbrows.MainActivity
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.data.Prefs
import java.io.IOException

/** Operações de canal partilhadas pelas linhas do TMDB e pelas linhas ao vivo. */
internal object Channels {
    private const val LOGO_SIZE = 320

    fun upsert(
        context: Context,
        helper: PreviewChannelHelper,
        rowId: String,
        displayName: String,
        description: String,
    ): Long {
        val existing = resolveId(context, helper, rowId)
        val channel = PreviewChannel.Builder()
            .setDisplayName(displayName)
            .setDescription(description)
            .setAppLinkIntent(Intent(context, MainActivity::class.java))
            .setInternalProviderId(rowId)
            .setLogo(logo())
            .build()

        if (existing != null) {
            runCatching {
                helper.updatePreviewChannel(existing, channel)
                return existing
            }
        }

        val channelId = helper.publishChannel(channel)
        if (channelId == -1L) throw IOException(context.getString(R.string.error_publish_channel))
        Prefs.setChannelId(context, rowId, channelId)
        return channelId
    }

    fun resolveId(context: Context, helper: PreviewChannelHelper, rowId: String): Long? {
        val saved = Prefs.channelId(context, rowId)
        if (saved != -1L && helper.getPreviewChannel(saved) != null) return saved
        val found = helper.allChannels.firstOrNull { it.internalProviderId == rowId }?.id
        if (found != null) Prefs.setChannelId(context, rowId, found)
        return found
    }

    fun clearPrograms(context: Context, channelId: Long) {
        // Consulta por URI do canal: o TvProvider recusa seleção SQL livre para apps comuns.
        context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            arrayOf(TvContractCompat.PreviewPrograms._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(TvContractCompat.PreviewPrograms._ID)
            while (cursor.moveToNext()) {
                context.contentResolver.delete(
                    TvContractCompat.buildPreviewProgramUri(cursor.getLong(idColumn)),
                    null,
                    null,
                )
            }
        }
    }

    fun logo(): Bitmap {
        val bitmap = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0D0F14"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B7C4FF")
            textSize = LOGO_SIZE * 0.42f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val y = LOGO_SIZE / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("TR", LOGO_SIZE / 2f, y, paint)
        return bitmap
    }
}
