package com.kusa.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.kusa.musicplayer.model.MusicRepository
import com.kusa.musicplayer.model.PlayMode
import com.kusa.musicplayer.model.Song
import com.kusa.musicplayer.service.MusicPlaybackService
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MusicRepository(application)
    private val collator = Collator.getInstance(Locale.JAPANESE)
    private var browser: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var allSongs: List<Song> = emptyList()
    private var currentQuery: String = ""
    private var currentArtistFilter: String? = null
    private var currentAlbumFilter: String? = null
    private var currentGenreFilter: String? = null

    private val _artists = MutableLiveData<List<String>>(emptyList())
    val artists: LiveData<List<String>> = _artists
    private val _albums = MutableLiveData<List<String>>(emptyList())
    val albums: LiveData<List<String>> = _albums
    private val _genres = MutableLiveData<List<String>>(emptyList())
    val genres: LiveData<List<String>> = _genres

    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs
    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong
    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _audioSessionId = MutableLiveData<Int?>(null)
    val audioSessionId: LiveData<Int?> = _audioSessionId
    private val _playMode = MutableLiveData(PlayMode.SEQUENTIAL)
    val playMode: LiveData<PlayMode> = _playMode
    private val _progress = MutableLiveData(0L)
    val progress: LiveData<Long> = _progress
    private val _duration = MutableLiveData(0L)
    val duration: LiveData<Long> = _duration
    private var lastDuration = -1L
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    @OptIn(UnstableApi::class)
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.postValue(isPlaying)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentSong()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateProgress()
            _audioSessionId.postValue(browser?.sessionExtras?.getInt("audio_session_id"))
            if (playbackState == Player.STATE_ENDED) {
                skipNext()
            }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playMode.postValue(if (shuffleModeEnabled) PlayMode.SHUFFLE else PlayMode.SEQUENTIAL)
        }
    }

    @OptIn(UnstableApi::class)
    fun connectToService() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            browser = controller
            controller.addListener(playerListener)
            _audioSessionId.postValue(controller.sessionExtras.getInt("audio_session_id"))
            // 接続時の状態を反映
            _playMode.postValue(if (controller.shuffleModeEnabled) PlayMode.SHUFFLE else PlayMode.SEQUENTIAL)
            updateCurrentSong()
        }, MoreExecutors.directExecutor())
    }

    fun loadFolder(folderUri: Uri, forceRefresh: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val loaded = repo.loadSongsFromFolder(folderUri, forceRefresh) { batch ->
                    _songs.postValue(batch.sortedWith { a, b -> collator.compare(a.displayTitle, b.displayTitle) })
                }
                val sorted = loaded.sortedWith { a, b -> collator.compare(a.displayTitle, b.displayTitle) }
                allSongs = sorted
                currentQuery = ""
                currentArtistFilter = null
                currentAlbumFilter = null
                currentGenreFilter = null
                _songs.postValue(sorted)
                updateGroupLists(sorted)
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

    private fun updateGroupLists(songList: List<Song>) {
        _artists.postValue(songList.map { it.displayArtist }.distinct().sortedWith { a, b -> collator.compare(a, b) })
        _albums.postValue(songList.map { it.displayAlbum }.distinct().sortedWith { a, b -> collator.compare(a, b) })
        _genres.postValue(songList.map { it.displayGenre }.distinct().sortedWith { a, b -> collator.compare(a, b) })
    }

    fun playSong(song: Song, list: List<Song>? = null) {
        val playList = list ?: _songs.value ?: return
        val index = playList.indexOf(song)
        browser?.let { ctrl ->
            val startIdx = if (index >= 0) index else 0
            val items = playList.map { s ->
                MediaItem.Builder()
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
        browser?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() {
        browser?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        browser?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        browser?.seekTo(positionMs)
    }

    fun togglePlayMode() {
        val next = if (_playMode.value == PlayMode.SEQUENTIAL) PlayMode.SHUFFLE else PlayMode.SEQUENTIAL
        // UI側の状態変更はリスナー（onShuffleModeEnabledChanged）経由で行われるため、ここではブラウザへ指示を出すのみ
        browser?.let {
            when (next) {
                PlayMode.SEQUENTIAL -> {
                    it.shuffleModeEnabled = false
                    it.repeatMode = Player.REPEAT_MODE_OFF
                }
                PlayMode.SHUFFLE -> {
                    it.shuffleModeEnabled = true
                    it.repeatMode = Player.REPEAT_MODE_ALL
                }
            }
        }
    }

    fun filterSongs(query: String) {
        currentQuery = query
        applyFilters()
    }

    fun filterByArtist(artistName: String?) {
        currentArtistFilter = artistName
        currentAlbumFilter = null
        applyFilters()
    }

    fun filterByAlbum(albumName: String?) {
        currentAlbumFilter = albumName
        currentArtistFilter = null
        currentGenreFilter = null
        applyFilters()
    }

    fun filterByGenre(genreName: String?) {
        currentGenreFilter = genreName
        currentArtistFilter = null
        currentAlbumFilter = null
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = allSongs
        if (currentArtistFilter != null) {
            filtered = filtered.filter { it.displayArtist == currentArtistFilter }
        }
        if (currentAlbumFilter != null) {
            filtered = filtered.filter { it.displayAlbum == currentAlbumFilter }
        }
        if (currentGenreFilter != null) {
            filtered = filtered.filter { it.displayGenre == currentGenreFilter }
        }
        if (currentQuery.isNotBlank()) {
            val q = currentQuery.trim().lowercase()
            filtered = filtered.filter {
                it.displayTitle.lowercase().contains(q) ||
                it.displayArtist.lowercase().contains(q) ||
                it.displayAlbum.lowercase().contains(q)
            }
        }
        _songs.value = filtered
    }

    fun playArtist(artistName: String) {
        val artistSongs = allSongs.filter { it.displayArtist == artistName }
        if (artistSongs.isNotEmpty()) {
            playSong(artistSongs[0], artistSongs)
        }
    }

    fun playAlbum(albumName: String) {
        val albumSongs = allSongs.filter { it.displayAlbum == albumName }
        if (albumSongs.isNotEmpty()) {
            playSong(albumSongs[0], albumSongs)
        }
    }

    fun playGenre(genreName: String) {
        val genreSongs = allSongs.filter { it.displayGenre == genreName }
        if (genreSongs.isNotEmpty()) {
            playSong(genreSongs[0], genreSongs)
        }
    }

    fun handleVoiceSearch(query: String, focus: String?) {
        when (focus) {
            MediaStore.Audio.Artists.CONTENT_TYPE -> {
                val artistSongs = allSongs.filter { it.displayArtist.contains(query, ignoreCase = true) }
                if (artistSongs.isNotEmpty()) playSong(artistSongs[0], artistSongs)
                else _error.value = "アーティストが見つかりませんでした: $query"
            }
            MediaStore.Audio.Albums.CONTENT_TYPE -> {
                val albumSongs = allSongs.filter { it.displayAlbum.contains(query, ignoreCase = true) }
                if (albumSongs.isNotEmpty()) playSong(albumSongs[0], albumSongs)
                else _error.value = "アルバムが見つかりませんでした: $query"
            }
            else -> {
                val match = allSongs.find { song ->
                    song.displayTitle.contains(query, ignoreCase = true) ||
                    song.displayArtist.contains(query, ignoreCase = true) ||
                    song.displayAlbum.contains(query, ignoreCase = true)
                }
                match?.let { playSong(it) } ?: run {
                    _error.value = "一致する曲が見つかりませんでした: $query"
                }
            }
        }
    }

    private fun updateCurrentSong() {
        val mediaId = browser?.currentMediaItem?.mediaId ?: return
        val song = allSongs.find { it.id.toString() == mediaId }
        _currentSong.postValue(song)
        _duration.postValue(browser?.duration?.takeIf { it > 0 } ?: 0L)
    }

    private fun updateProgress() {
        _progress.postValue(browser?.currentPosition ?: 0L)
        // 変化がない場合は不要な再描画を避ける
        val dur = browser?.duration?.takeIf { it > 0 } ?: 0L
        if (dur != lastDuration) {
            lastDuration = dur
            _duration.postValue(dur)
        }
    }

    fun refreshProgress() {
        updateProgress()
    }

    override fun onCleared() {
        browser?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
