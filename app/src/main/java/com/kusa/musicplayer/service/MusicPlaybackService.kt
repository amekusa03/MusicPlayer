package com.kusa.musicplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kusa.musicplayer.MainActivity
import com.kusa.musicplayer.model.MusicRepository
import com.kusa.musicplayer.model.Song

class MusicPlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private val repo by lazy { MusicRepository(this) }
    private var songs: List<Song> = emptyList()

    companion object {
        private const val PREFS = "music_player_prefs"
        private const val KEY_FOLDER = "last_folder_uri"

        const val ROOT_ID = "root"
        private const val SONGS_ID = "songs"
        private const val ARTISTS_ID = "artists"
        private const val ALBUMS_ID = "albums"
        private const val ARTIST_PREFIX = "artist/"
        private const val ALBUM_PREFIX = "album/"
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(pendingIntent)
            .build()

        // キャッシュが存在すればサービス起動直後からブラウズ可能にする
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER, null)
            ?.let { uriStr ->
                repo.loadSongsFromCacheSync(Uri.parse(uriStr))?.let { cached ->
                    songs = cached
                }
            }
    }

    // ---- MediaLibrarySession.Callback ----

    inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(buildBrowsableItem(ROOT_ID, "Music Player"), null)
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items: List<MediaItem> = when {
                parentId == ROOT_ID -> listOf(
                    buildBrowsableItem(SONGS_ID, "すべての曲"),
                    buildBrowsableItem(ARTISTS_ID, "アーティスト"),
                    buildBrowsableItem(ALBUMS_ID, "アルバム")
                )
                parentId == SONGS_ID ->
                    songs.map { it.toMediaItem() }
                parentId == ARTISTS_ID ->
                    songs.map { it.displayArtist }.distinct().sorted()
                        .map { a -> buildBrowsableItem("$ARTIST_PREFIX$a", a) }
                parentId.startsWith(ARTIST_PREFIX) ->
                    songs.filter { it.displayArtist == parentId.removePrefix(ARTIST_PREFIX) }
                        .map { it.toMediaItem() }
                parentId == ALBUMS_ID ->
                    songs.map { it.displayAlbum }.distinct().sorted()
                        .map { al -> buildBrowsableItem("$ALBUM_PREFIX$al", al) }
                parentId.startsWith(ALBUM_PREFIX) ->
                    songs.filter { it.displayAlbum == parentId.removePrefix(ALBUM_PREFIX) }
                        .map { it.toMediaItem() }
                else -> emptyList()
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val song = songs.find { it.id.toString() == mediaId }
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            return Futures.immediateFuture(LibraryResult.ofItem(song.toMediaItem(), null))
        }

        // onSearch: 検索を受け付け件数を通知する（Android Auto / アシスタント向け）
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val count = repo.searchSongs(songs, query, MusicRepository.SearchType.ANY).size
            session.notifySearchResultChanged(browser, query, count, null)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        // onGetSearchResult: 検索結果を返す
        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val results = repo.searchSongs(songs, query, MusicRepository.SearchType.ANY)
                .map { it.toMediaItem() }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(results), null)
            )
        }

        // onAddMediaItems: ブラウズ / 音声操作で ID のみのアイテムが渡された場合に URI を解決する
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) item
                else songs.find { it.id.toString() == item.mediaId }?.toMediaItem() ?: item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        // onSetMediaItems: ViewModel がプレイリストを送ってきたタイミングでキャッシュを再読み込みする
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FOLDER, null)
                ?.let { uriStr ->
                    repo.loadSongsFromCacheSync(Uri.parse(uriStr))?.let { songs = it }
                }
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
    }

    // ---- MediaSessionService overrides ----

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    // ---- Helpers ----

    private fun buildBrowsableItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(title)
                    .build()
            )
            .build()
}

private fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
