package com.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musicplayer.model.MusicRepository
import com.musicplayer.model.PlayMode
import com.musicplayer.model.Song
import com.musicplayer.service.MusicPlaybackService
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MusicRepository(application)
    // フィルタリング前の全楽曲リストを保持する変数
    private var allSongs: List<Song> = emptyList()

    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _playMode = MutableLiveData(PlayMode.SEQUENTIAL)
    val playMode: LiveData<PlayMode> = _playMode

    private val _progress = MutableLiveData(0L)
    val progress: LiveData<Long> = _progress          // ms

    private val _duration = MutableLiveData(0L)
    val duration: LiveData<Long> = _duration          // ms

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // MediaController (client-side handle to the service)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.postValue(isPlaying)
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            updateCurrentSong()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateProgress()
        }
    }

    fun connectToService() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            updateCurrentSong()
        }, MoreExecutors.directExecutor())
    }

    fun loadFolder(folderUri: Uri, forceRefresh: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val loaded = repo.loadSongsFromFolder(folderUri, forceRefresh) { batch ->
                    // スキャン途中のリストを暫定的にUIに反映（必要ならソート）
                    _songs.postValue(batch.sortedBy { it.displayTitle })
                }
                val sorted = loaded.sortedBy { it.displayTitle }
                allSongs = sorted // 全曲リストを保存
                _songs.postValue(sorted)
                if (loaded.isEmpty()) {
                    _error.postValue("MP3ファイルが見つかりませんでした")
                }
            } catch (e: Exception) {
                _error.postValue("フォルダの読み込みに失敗しました: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun playSong(song: Song) {
        val list = _songs.value ?: return
        val index = list.indexOf(song)
        // We send the playlist to the service via a broadcast / intent extra
        // For simplicity, use the service directly via controller commands.
        // The service will rebuild the playlist; we pass the index via seekTo.
        controller?.let { ctrl ->
            val startIdx = if (index >= 0) index else 0
            // Build MediaItems
            val items = list.map { s ->
                androidx.media3.common.MediaItem.Builder()
                    .setUri(s.uri)
                    .setMediaId(s.id.toString())
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(s.albumArtUri)
                            .build()
                    )
                    .build()
            }
            ctrl.setMediaItems(items, startIdx, 0L)
            ctrl.prepare()
            ctrl.play()
        }
        _currentSong.value = song
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrevious() { controller?.seekToPreviousMediaItem() }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun togglePlayMode() {
        val next = if (_playMode.value == PlayMode.SEQUENTIAL) PlayMode.SHUFFLE else PlayMode.SEQUENTIAL
        _playMode.value = next
        controller?.let {
            when (next) {
                PlayMode.SEQUENTIAL -> { it.shuffleModeEnabled = false; it.repeatMode = Player.REPEAT_MODE_ALL }
                PlayMode.SHUFFLE    -> { it.shuffleModeEnabled = true;  it.repeatMode = Player.REPEAT_MODE_ALL }
            }
        }
    }

    fun filterSongs(query: String) {
        _songs.value = if (query.isBlank()) {
            allSongs
        } else {
            val q = query.trim().lowercase()
            allSongs.filter {
                it.displayTitle.lowercase().contains(q) ||
                it.displayArtist.lowercase().contains(q) ||
                it.displayAlbum.lowercase().contains(q)
            }
        }
    }

    /** Voice search from Google Assistant intent */
    fun handleVoiceSearch(query: String, focusedField: String?) {
        val songs = _songs.value ?: return
        val type = when (focusedField) {
            "vnd.android.cursor.item/artist" -> MusicRepository.SearchType.ARTIST
            "vnd.android.cursor.item/album"  -> MusicRepository.SearchType.ALBUM
            "vnd.android.cursor.item/audio"  -> MusicRepository.SearchType.TITLE
            else -> MusicRepository.SearchType.ANY
        }
        val results = repo.searchSongs(songs, query, type)
        if (results.isNotEmpty()) playSong(results.first())
    }

    private fun updateCurrentSong() {
        val mediaId = controller?.currentMediaItem?.mediaId ?: return
        val song = _songs.value?.find { it.id.toString() == mediaId }
        _currentSong.postValue(song)
        _duration.postValue(controller?.duration?.takeIf { it > 0 } ?: 0L)
    }

    private fun updateProgress() {
        _progress.postValue(controller?.currentPosition ?: 0L)
        _duration.postValue(controller?.duration?.takeIf { it > 0 } ?: 0L)
    }

    fun refreshProgress() { updateProgress() }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
