package com.ptvin.tmdbrows.channel

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.ptvin.tmdbrows.data.MediaType
import com.ptvin.tmdbrows.data.Opener
import com.ptvin.tmdbrows.data.Prefs

/** Abre o título no app escolhido pelo usuário. Chamado pelo card do canal. */
class OpenTitleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        var media = runCatching {
            MediaType.valueOf(intent.getStringExtra(EXTRA_MEDIA) ?: MediaType.MOVIE.name)
        }.getOrDefault(MediaType.MOVIE)

        if (id <= 0) {
            finish()
            return
        }

        val opener = intent.getStringExtra(EXTRA_OPENER)
            ?.let { runCatching { Opener.valueOf(it) }.getOrNull() }
            ?: Prefs.opener(this)
        val uri: Uri = when (opener) {
            Opener.NUVIO -> Uri.parse("nuvio://meta?type=${media.deepLink}&id=tmdb:$id")
            Opener.STREMIO -> Uri.parse("stremio:///search")
                .buildUpon()
                .appendQueryParameter("search", title)
                .build()
        }

        val base = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Fixar o pacote evita o seletor quando há duas apps a responder ao esquema.
        val target = when (opener) {
            Opener.NUVIO -> "com.nuvio.tv"
            Opener.STREMIO -> "com.stremio.one"
        }
        val opened = start(Intent(base).setPackage(target)) || start(base)
        if (!opened) {
            Toast.makeText(this, "${opener.label} não está instalado", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun start(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    }

    companion object {
        const val EXTRA_ID = "tmdb_id"
        const val EXTRA_MEDIA = "media"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OPENER = "opener"
    }
}
