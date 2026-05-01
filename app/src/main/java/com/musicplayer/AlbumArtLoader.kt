package com.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * 楽曲のURIからアルバムアートを読み込み、指定されたサイズにリサイズするユーティリティ。
 * Android Autoでの表示互換性を高めるために、画像を適切なサイズに調整します。
 */
object AlbumArtLoader {
    // Android Autoで推奨されるアルバムアートの最大サイズ（ピクセル）
    private const val TARGET_ARTWORK_SIZE_PX = 512

    /**
     * 楽曲のURIからアルバムアートを抽出し、指定サイズにリサイズして返します。
     *
     * @param context アプリケーションコンテキスト
     * @param songUri 楽曲ファイルのURI
     * @return リサイズされたBitmap、またはアートワークが見つからない場合はnull
     */
    suspend fun load(context: Context, songUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        try {
            retriever.setDataSource(context, songUri)
            val embeddedPic = retriever.embeddedPicture // 楽曲ファイルに埋め込まれたアートワークを取得

            if (embeddedPic != null) {
                // 1. まずは画像のサイズ情報のみをデコード
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size, options)

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight

                // 2. ターゲットサイズに合わせてinSampleSizeを計算し、メモリ効率良くデコード
                var inSampleSize = 1
                if (originalHeight > TARGET_ARTWORK_SIZE_PX || originalWidth > TARGET_ARTWORK_SIZE_PX) {
                    val halfHeight = originalHeight / 2
                    val halfWidth = originalWidth / 2

                    // ターゲットサイズより大きくなるまでinSampleSizeを2倍していく
                    while (halfHeight / inSampleSize >= TARGET_ARTWORK_SIZE_PX &&
                           halfWidth / inSampleSize >= TARGET_ARTWORK_SIZE_PX) {
                        inSampleSize *= 2
                    }
                }

                // 3. 計算したinSampleSizeで画像をデコード
                val finalOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888 // 高品質な画像形式を指定
                }
                bitmap = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size, finalOptions)

                // 4. inSampleSizeで正確なサイズにならなかった場合、最終的にターゲットサイズにスケーリング
                if (bitmap != null && (bitmap.width > TARGET_ARTWORK_SIZE_PX || bitmap.height > TARGET_ARTWORK_SIZE_PX)) {
                    val scaleFactor = Math.min(
                        TARGET_ARTWORK_SIZE_PX.toFloat() / bitmap.width,
                        TARGET_ARTWORK_SIZE_PX.toFloat() / bitmap.height
                    )
                    val newWidth = (bitmap.width * scaleFactor).toInt()
                    val newHeight = (bitmap.height * scaleFactor).toInt()
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace() // エラーログを出力
        } finally {
            retriever.release() // MediaMetadataRetrieverのリソースを解放
        }
        return@withContext bitmap
    }
}