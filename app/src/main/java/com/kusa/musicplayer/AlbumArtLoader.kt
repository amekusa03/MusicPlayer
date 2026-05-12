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
    // Android 8.0 ユニットのタイムアウトを防ぐため、サイズをさらに絞る
    private const val TARGET_ARTWORK_SIZE_PX = 256

    suspend fun load(context: Context, songUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, songUri)
            val embeddedPic = retriever.embeddedPicture

            if (embeddedPic != null) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size, options)
                
                if (bitmap != null) {
                    // 複雑なリサイズ処理を省き、単純なスケーリングで速度優先にする
                    if (bitmap.width > TARGET_ARTWORK_SIZE_PX || bitmap.height > TARGET_ARTWORK_SIZE_PX) {
                        val scale = TARGET_ARTWORK_SIZE_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
                        return@withContext Bitmap.createScaledBitmap(
                            bitmap, 
                            (bitmap.width * scale).toInt(), 
                            (bitmap.height * scale).toInt(), 
                            false
                        )
                    }
                    return@withContext bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "アートワーク抽出失敗: ${e.message}")
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
        return@withContext null
    }
}
