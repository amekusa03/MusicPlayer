package com.kusa.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AlbumArtLoader {
    private const val TAG = "AlbumArtLoader"
    private const val TARGET_ARTWORK_SIZE_PX = 512

    suspend fun load(context: Context, songUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        try {
            retriever.setDataSource(context, songUri)
            val embeddedPic = retriever.embeddedPicture

            if (embeddedPic != null) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size, options)

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight

                var inSampleSize = 1
                if (originalHeight > TARGET_ARTWORK_SIZE_PX || originalWidth > TARGET_ARTWORK_SIZE_PX) {
                    val halfHeight = originalHeight / 2
                    val halfWidth = originalWidth / 2
                    while (halfHeight / inSampleSize >= TARGET_ARTWORK_SIZE_PX &&
                           halfWidth / inSampleSize >= TARGET_ARTWORK_SIZE_PX) {
                        inSampleSize *= 2
                    }
                }

                val finalOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                bitmap = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size, finalOptions)

                if (bitmap != null && (bitmap.width > TARGET_ARTWORK_SIZE_PX || bitmap.height > TARGET_ARTWORK_SIZE_PX)) {
                    val scaleFactor = minOf(
                        TARGET_ARTWORK_SIZE_PX.toFloat() / bitmap.width,
                        TARGET_ARTWORK_SIZE_PX.toFloat() / bitmap.height
                    )
                    val newWidth = (bitmap.width * scaleFactor).toInt()
                    val newHeight = (bitmap.height * scaleFactor).toInt()
                    val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    bitmap.recycle()
                    bitmap = scaled
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "アルバムアートの読み込みに失敗しました", e)
        } finally {
            retriever.release()
        }
        return@withContext bitmap
    }
}
