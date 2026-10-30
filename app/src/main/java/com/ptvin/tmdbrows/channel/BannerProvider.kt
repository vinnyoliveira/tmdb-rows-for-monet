package com.ptvin.tmdbrows.channel

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serve os banners gerados em `cacheDir/banners`.
 * Exportado sem permissão de propósito: o launcher (Monet) não tem como pedir grant.
 * São imagens públicas de filmes, sem dados do utilizador.
 */
class BannerProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val dir = File(requireNotNull(context).cacheDir, "banners")
        val name = uri.lastPathSegment.orEmpty()
        val file = File(dir, name)
        if (name.isBlank() || !file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            throw FileNotFoundException(uri.toString())
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
