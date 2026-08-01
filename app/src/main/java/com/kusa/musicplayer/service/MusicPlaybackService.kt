package com.kusa.musicplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.kusa.musicplayer.AlbumArtLoader
import com.kusa.musicplayer.MainActivity
import com.kusa.musicplayer.model.MusicRepository
import com.kusa.musicplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class MusicPlaybackService : MediaLibraryService() {

    companion object {
        private val COMMAND_TOGGLE_SHUFFLE = SessionCommand("com.kusa.musicplayer.TOGGLE_SHUFFLE", Bundle.EMPTY)
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private val repo by lazy { MusicRepository(this) }
    private var songs: List<Song> = emptyList()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastParentId: String = "all_songs"

    private val shuffleStateListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (::mediaLibrarySession.isInitialized) {
                mediaLibrarySession.setCustomLayout(ImmutableList.of(buildShuffleButton()))
            }
        }
    }

    private val customBitmapLoader = object : BitmapLoader {
        override fun supportsMimeType(mimeType: String): Boolean = true
        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
            Futures.immediateFuture(BitmapFactory.decodeByteArray(data, 0, data.size))
        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            serviceScope.launch {
                // AlbumArtProvider URI の場合はクエリパラメータから元の曲URIを取り出す。
                // そのまま渡すと MediaMetadataRetriever が JPEG を音声ファイルとして扱い
                // embeddedPicture が null になるため、Android Auto にアートワークが届かない。
                val targetUri = if (uri.authority == "com.kusa.musicplayer.albumart") {
                    val songUriStr = uri.getQueryParameter("uri") ?: run {
                        future.setException(RuntimeException("No URI param"))
                        return@launch
                    }
                    Uri.parse(songUriStr)
                } else {
                    uri
                }
                val bitmap = AlbumArtLoader.load(this@MusicPlaybackService, targetUri)
                if (bitmap != null) future.set(bitmap) else future.setException(RuntimeException("Artwork load failed"))
            }
            return future
        }
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
        player.addListener(shuffleStateListener)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionExtras = Bundle().apply {
            putBoolean("com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR_SHUFFLE_MODE", true)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(pendingIntent)
            .setExtras(sessionExtras)
            .setBitmapLoader(customBitmapLoader)
            .build()

        mediaLibrarySession.setCustomLayout(ImmutableList.of(buildShuffleButton()))
        
        // Update session extras with audio session ID
        val updatedExtras = sessionExtras.deepCopy()
        updatedExtras.putInt("audio_session_id", player.audioSessionId)
        mediaLibrarySession.setSessionExtras(updatedExtras)

        getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
            .getString("last_folder_uri", null)
            ?.let { uriStr ->
                repo.loadSongsFromCacheSync(Uri.parse(uriStr))?.let { cached ->
                    songs = cached
                }
            }
    }

    private fun buildShuffleButton(): CommandButton =
        CommandButton.Builder(
            if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setSessionCommand(COMMAND_TOGGLE_SHUFFLE)
            .setDisplayName("シャッフル")
            .build()

    inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .build()
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(COMMAND_TOGGLE_SHUFFLE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand == COMMAND_TOGGLE_SHUFFLE) {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?) =
            Futures.immediateFuture(LibraryResult.ofItem(buildBrowsableItem("root", "Music Player"), null))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            lastParentId = parentId
            val items = when (parentId) {
                "root" -> listOf(
                    buildBrowsableItem("all_songs", "すべての曲"),
                    buildBrowsableItem("artists", "アーティスト"),
                    buildBrowsableItem("albums", "アルバム"),
                    buildBrowsableItem("genres", "ジャンル")
                )
                "all_songs" -> songs.map { it.toMediaItem("all_songs") }
                "artists" -> {
                    val c = Collator.getInstance(Locale.JAPANESE)
                    songs.map { it.displayArtist }.distinct().sortedWith { a, b -> c.compare(a, b) }.map { buildBrowsableItem("artist/$it", it) }
                }
                "albums" -> {
                    val c = Collator.getInstance(Locale.JAPANESE)
                    songs.map { it.displayAlbum }.distinct().sortedWith { a, b -> c.compare(a, b) }.map { buildBrowsableItem("album/$it", it) }
                }
                "genres" -> {
                    val c = Collator.getInstance(Locale.JAPANESE)
                    songs.map { it.displayGenre }.distinct().sortedWith { a, b -> c.compare(a, b) }.map { buildBrowsableItem("genre/$it", it) }
                }
                else -> {
                    if (parentId.startsWith("artist/")) {
                        val artist = parentId.removePrefix("artist/")
                        songs.filter { it.displayArtist == artist }.map { it.toMediaItem(parentId) }
                    } else if (parentId.startsWith("album/")) {
                        val album = parentId.removePrefix("album/")
                        songs.filter { it.displayAlbum == album }.map { it.toMediaItem(parentId) }
                    } else if (parentId.startsWith("genre/")) {
                        val genre = parentId.removePrefix("genre/")
                        songs.filter { it.displayGenre == genre }.map { it.toMediaItem(parentId) }
                    } else emptyList()
                }
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), null))
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
            // URIつきのアイテムはスマホUIから直接送られたもの。そのまま返す
            if (mediaItems.isNotEmpty() && mediaItems.all { it.localConfiguration?.uri != null }) {
                return Futures.immediateFuture(mediaItems)
            }
            // mediaIdに埋め込まれたコンテキストからキューを解決する
            // 例: "album/Rock Hits:42" → context="album/Rock Hits"
            // 曲IDは数値なので最後の':'がコンテキストとIDの区切り
            val firstMediaId = mediaItems.firstOrNull()?.mediaId ?: ""
            val lastColon = firstMediaId.lastIndexOf(':')
            val context = if (lastColon >= 0) firstMediaId.substring(0, lastColon) else lastParentId
            // mediaIdのみのアイテム（Android Auto経由）はlastParentIdで解決する（fallback）
//            val queue = when {
//                lastParentId == "all_songs" -> songs
//                lastParentId.startsWith("artist/") -> {
//                    val artist = lastParentId.removePrefix("artist/")
//                    songs.filter { it.displayArtist == artist }
//                }
//                lastParentId.startsWith("album/") -> {
//                    val album = lastParentId.removePrefix("album/")
//                    songs.filter { it.displayAlbum == album }
//                }
//                else -> songs
//            }
            val queue = when {
                context == "all_songs" -> songs
                context.startsWith("artist/") -> {
                    val artist = context.removePrefix("artist/")
                    songs.filter { it.displayArtist == artist }
                }
                context.startsWith("album/") -> {
                    val album = context.removePrefix("album/")
                    songs.filter { it.displayAlbum == album }
                }
                context.startsWith("genre/") -> {
                    val genre = context.removePrefix("genre/")
                    songs.filter { it.displayGenre == genre }
                }
                else -> songs
            }
            // 返却アイテムはplainなmediaId（コンテキストなし）でPlayerViewModelから参照できるようにする
            return Futures.immediateFuture(queue.map { it.toMediaItem() }.toMutableList())
        }
    }

    private fun buildBrowsableItem(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).setTitle(title).build())
        .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaLibrarySession
    override fun onDestroy() {
        player.removeListener(shuffleStateListener)
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }
}

private fun Song.toMediaItem(contextId: String = ""): MediaItem {
    val artUri = Uri.parse("content://com.kusa.musicplayer.albumart")
        .buildUpon()
        .appendQueryParameter("uri", uri.toString())
        .build()
    // コンテキストがある場合はmediaIdに埋め込む（例: "album/Rock Hits:42"）
    val mediaId = if (contextId.isNotEmpty()) "$contextId:$id" else id.toString()

    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .setDisplayTitle(title)
                .setSubtitle(artist)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        ).build()
}
