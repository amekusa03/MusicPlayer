package com.musicplayer

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicplayer.adapter.SongListAdapter
import com.musicplayer.model.PlayMode
import com.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var vm: PlayerViewModel
    private lateinit var adapter: SongListAdapter

    private lateinit var ivArtwork: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var rvSongs: RecyclerView
    private lateinit var btnSelectFolder: Button
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var searchView: androidx.appcompat.widget.SearchView

    private val PREFS = "music_player_prefs"
    private val KEY_FOLDER = "last_folder_uri"

    private var artworkJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            vm.refreshProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER, it.toString()).apply()
            vm.loadFolder(it, forceRefresh = true)  // 新フォルダは必ず再スキャン
        } ?: run {
            Toast.makeText(this, "フォルダ選択がキャンセルされました", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vm = ViewModelProvider(this)[PlayerViewModel::class.java]
        vm.connectToService()

        bindViews()
        setupAdapter()
        observeViewModel()
        setupControls()

        val savedFolder = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER, null)
        savedFolder?.let { vm.loadFolder(Uri.parse(it)) }

        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // 画面が表示された際、再生中であれば更新ループを再開
        if (vm.isPlaying.value == true) handler.post(progressRunnable)
    }

    override fun onStop() {
        // 画面が非表示になったら更新ループを停止して負荷を下げる
        handler.removeCallbacks(progressRunnable)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        android.util.Log.d("MusicPlayer", "Intent received: ${intent.action}")
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY) ?: return
            val focusedField = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
            android.util.Log.d("MusicPlayer", "Voice search query: $query, focus: $focusedField")
            // 読み込み完了を待つ必要がある場合は、ViewModel側で適切にキューイングするか
            // songsのLiveDataが更新された後に実行する工夫が必要です
            lifecycleScope.launch {
                vm.handleVoiceSearch(query, focusedField)
            }
        }
    }

    private fun bindViews() {
        ivArtwork      = findViewById(R.id.ivArtwork)
        tvTitle        = findViewById(R.id.tvTitle)
        tvArtist       = findViewById(R.id.tvArtist)
        tvAlbum        = findViewById(R.id.tvAlbum)
        btnPlayPause   = findViewById(R.id.btnPlayPause)
        btnPrev        = findViewById(R.id.btnPrev)
        btnNext        = findViewById(R.id.btnNext)
        btnShuffle     = findViewById(R.id.btnShuffle)
        seekBar        = findViewById(R.id.seekBar)
        tvCurrentTime  = findViewById(R.id.tvCurrentTime)
        tvTotalTime    = findViewById(R.id.tvTotalTime)
        rvSongs        = findViewById(R.id.rvSongs)
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        pbLoading      = findViewById(R.id.pbLoading)
        tvEmpty        = findViewById(R.id.tvEmpty)
        searchView     = findViewById(R.id.searchView)
    }

    private fun setupAdapter() {
        adapter = SongListAdapter(lifecycleScope) { song -> vm.playSong(song) }
        rvSongs.layoutManager = LinearLayoutManager(this)
        rvSongs.adapter = adapter
    }

    private fun observeViewModel() {
        vm.songs.observe(this) { songs ->
            adapter.submitList(songs)
            tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.currentSong.observe(this) { song ->
            adapter.setCurrentSong(song)
            artworkJob?.cancel() // 前の読み込み処理があればキャンセル
            if (song != null) {
                tvTitle.text  = song.displayTitle
                tvArtist.text = song.displayArtist
                tvAlbum.text  = song.displayAlbum
                // Load artwork via coroutine
                artworkJob = lifecycleScope.launch {
                    val bmp = AlbumArtLoader.load(this@MainActivity, song.uri)
                    if (bmp != null) ivArtwork.setImageBitmap(bmp)
                    else ivArtwork.setImageResource(R.drawable.ic_album_placeholder)
                }
            } else {
                tvTitle.text  = getString(R.string.no_song)
                tvArtist.text = ""
                tvAlbum.text  = ""
                ivArtwork.setImageResource(R.drawable.ic_album_placeholder)
            }
        }

        vm.isPlaying.observe(this) { playing ->
            btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            handler.removeCallbacks(progressRunnable)
            // 画面が表示されている場合のみループを開始
            if (playing && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                handler.post(progressRunnable)
            }
        }

        vm.playMode.observe(this) { mode ->
            btnShuffle.setImageResource(
                if (mode == PlayMode.SHUFFLE) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
            )
        }

        vm.progress.observe(this) { pos ->
            seekBar.progress = pos.toInt()
            tvCurrentTime.text = formatTime(pos)
        }

        vm.duration.observe(this) { dur ->
            seekBar.max = dur.toInt()
            tvTotalTime.text = formatTime(dur)
        }

        vm.isLoading.observe(this) { loading ->
            pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }

        vm.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }
    }

    private fun setupControls() {
        btnSelectFolder.setOnClickListener { folderPicker.launch(null) }
        btnPlayPause.setOnClickListener   {
            if (vm.currentSong.value == null) {
                // 曲が選択されていない場合は、現在表示されているリストの最初の曲を再生する
                val currentSongs = adapter.currentList // 表示されているリストを直接参照
                if (!currentSongs.isNullOrEmpty()) {
                    vm.playSong(currentSongs[0])
                }
            } else {
                vm.togglePlayPause()
            }
        }
        btnPrev.setOnClickListener        { vm.skipPrevious() }
        btnNext.setOnClickListener        { vm.skipNext() }
        btnShuffle.setOnClickListener     { vm.togglePlayMode() }

        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                vm.filterSongs(newText ?: "")
                return true
            }
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) tvCurrentTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { vm.seekTo(sb.progress.toLong()) }
        })
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        super.onDestroy()
    }
}
