package com.kusa.musicplayer.model

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

class MusicRepository(private val context: Context) {

    private val collator = Collator.getInstance(Locale.JAPANESE)

    private val cachePrefs get() =
        context.getSharedPreferences("music_cache", Context.MODE_PRIVATE)

    private fun cacheKey(folderUri: Uri) = "songs_${folderUri}"

    // ---- キャッシュ読み書き ----

    /** キャッシュから楽曲一覧を同期的に取得する（失敗時またはデータなしは null）。 */
    fun loadSongsFromCacheSync(folderUri: Uri): List<Song>? {
        val raw = cachePrefs.getString(cacheKey(folderUri), null) ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("songs")
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Song(
                    id        = o.getLong("id"),
                    title     = o.getString("title"),
                    artist    = o.getString("artist"),
                    album     = o.getString("album"),
                    uri       = Uri.parse(o.getString("uri")),
                    albumArtUri = o.optString("artUri").takeIf { it.isNotEmpty() }
                        ?.let { Uri.parse(it) },
                    duration  = o.getLong("duration"),
                    filePath  = o.getString("filePath")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveSongsToCache(folderUri: Uri, songs: List<Song>) {
        val arr = JSONArray()
        songs.forEach { s ->
            arr.put(JSONObject().apply {
                put("id",       s.id)
                put("title",    s.title)
                put("artist",   s.artist)
                put("album",    s.album)
                put("uri",      s.uri.toString())
                put("artUri",   s.albumArtUri?.toString() ?: "")
                put("duration", s.duration)
                put("filePath", s.filePath)
            })
        }
        val payload = JSONObject().apply {
            put("ts",    System.currentTimeMillis())
            put("songs", arr)
        }
        cachePrefs.edit().putString(cacheKey(folderUri), payload.toString()).apply()
    }

    /** すべてのキャッシュデータを削除する */
    fun clearCache() {
        cachePrefs.edit().clear().apply()
    }

    // ---- 楽曲読み込み ----

    /**
     * フォルダ URI から楽曲一覧を取得する。
     * forceRefresh = true のときは既存の全キャッシュをクリアしてから再スキャンする。
     */
    suspend fun loadSongsFromFolder(
        folderUri: Uri,
        forceRefresh: Boolean = false,
        onProgress: (suspend (List<Song>) -> Unit)? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            loadSongsFromCacheSync(folderUri)?.let { return@withContext it }
        } else {
            // フォルダ設定し直し（強制リフレッシュ）時は旧キャッシュを消去
            clearCache()
        }
        val songs = mutableListOf<Song>()
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext songs
        scanDirectory(folder, songs, onProgress)
        songs.sortWith { a, b -> collator.compare(a.displayTitle, b.displayTitle) }
        saveSongsToCache(folderUri, songs)
        songs
    }

    private suspend fun scanDirectory(
        dir: DocumentFile,
        songs: MutableList<Song>,
        onProgress: (suspend (List<Song>) -> Unit)? = null
    ) {
        dir.listFiles().forEach { file ->
            when {
                file.isDirectory -> scanDirectory(file, songs, onProgress)
                file.isFile && isMp3(file) -> {
                    readSong(file)?.let {
                        songs.add(it)
                        // 5曲ごとに進捗を通知し、少し休止（自己タイマー的な再開処理）
                        if (songs.size % 5 == 0) {
                            onProgress?.invoke(ArrayList(songs)) // コピーを渡す
                            delay(50) // 50ms待機してスレッドを解放
                        }
                    }
                }
            }
        }
    }

    private fun isMp3(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val mime = file.type ?: ""
        return name.endsWith(".mp3") || mime == "audio/mpeg"
    }

    private fun readSong(file: DocumentFile): Song? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)

            val title    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.name?.removeSuffix(".mp3") ?: "不明な曲"
            val artist   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            Song(
                id          = file.uri.hashCode().toLong(),
                title       = title,
                artist      = artist,
                album       = album,
                uri         = file.uri,
                albumArtUri = file.uri,
                duration    = duration,
                filePath    = file.uri.toString()
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // ---- 検索 ----

    fun searchSongs(
        songs: List<Song>,
        query: String,
        searchType: SearchType
    ): List<Song> {
        val q = query.trim().lowercase()
        return when (searchType) {
            SearchType.TITLE  -> songs.filter { it.title.lowercase().contains(q) }
            SearchType.ARTIST -> songs.filter { it.artist.lowercase().contains(q) }
            SearchType.ALBUM  -> songs.filter { it.album.lowercase().contains(q) }
            SearchType.ANY    -> songs.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    enum class SearchType { TITLE, ARTIST, ALBUM, ANY }
}
