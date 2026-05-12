package com.kusa.musicplayer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * Android Auto (特に古いユニット) がアートワークを確実に読み込めるようにするための
 * 画像専用コンテンツプロバイダー。
 */
class AlbumArtProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, p1: ContentValues?): Uri? = null
    override fun delete(uri: Uri, p1: String?, p2: Array<out String>?): Int = 0
    override fun update(uri: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val songUriStr = uri.getQueryParameter("uri") ?: return null
        val songUri = Uri.parse(songUriStr)
        val context = context ?: return null

        // キャッシュディレクトリ内に一時画像を作成
        val cacheFile = File(context.cacheDir, "art_${songUri.hashCode()}.jpg")
        
        if (!cacheFile.exists()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, songUri)
                val picture = retriever.embeddedPicture
                if (picture != null) {
                    FileOutputStream(cacheFile).use { it.write(picture) }
                } else {
                    return null
                }
            } catch (e: Exception) {
                return null
            } finally {
                retriever.release()
            }
        }

        return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
