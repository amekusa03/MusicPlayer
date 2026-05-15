package com.kusa.musicplayer

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.kusa.musicplayer.adapter.MainPagerAdapter
import com.kusa.musicplayer.model.PlayMode
import com.kusa.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "music_player_prefs"
        private const val KEY_FOLDER = "last_folder_uri"
    }

    private lateinit var vm: PlayerViewModel

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
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var btnSelectFolder: Button
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var searchView: androidx.appcompat.widget.SearchView

    private var artworkJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            vm.refreshProgress()
            handler.postDelayed(this, 1000)
        }
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER, it.toString()).apply()
            vm.loadFolder(it, forceRefresh = true)
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
        setupPager()
        observeViewModel()
        setupControls()

        val savedFolder = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER, null)
        savedFolder?.let { vm.loadFolder(Uri.parse(it)) }

        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (vm.isPlaying.value == true) handler.post(progressRunnable)
    }

    override fun onStop() {
        handler.removeCallbacks(progressRunnable)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY) ?: return
            val focusedField = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
            vm.handleVoiceSearch(query, focusedField)
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
        viewPager      = findViewById(R.id.viewPager)
        tabLayout      = findViewById(R.id.tabLayout)
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        pbLoading      = findViewById(R.id.pbLoading)
        tvEmpty        = findViewById(R.id.tvEmpty)
        searchView     = findViewById(R.id.searchView)
    }

    private fun setupPager() {
        val pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "全曲"
                1 -> "アーティスト"
                2 -> "アルバム"
                3 -> "ジャンル"
                else -> ""
            }
        }.attach()
    }

    private fun observeViewModel() {
        vm.songs.observe(this) { songs ->
            tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.currentSong.observe(this) { song ->
            artworkJob?.cancel()
            if (song != null) {
                tvTitle.text  = song.displayTitle
                tvArtist.text = song.displayArtist
                tvAlbum.text  = song.displayAlbum
                ivArtwork.setImageResource(R.drawable.ic_album_placeholder)
                artworkJob = lifecycleScope.launch {
                    val bmp = AlbumArtLoader.load(this@MainActivity, song.uri)
                    if (bmp != null) ivArtwork.setImageBitmap(bmp)
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
            if (playing && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                handler.post(progressRunnable)
            }
        }

        vm.playMode.observe(this) { mode ->
            val isShuffle = mode == PlayMode.SHUFFLE
            btnShuffle.setImageResource(if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)

            // プロジェクトのアクセントカラーを使用して視認性を向上させます
            val tintColor = if (isShuffle) {
                ContextCompat.getColor(this, R.color.accent) // ON: 明るい緑
            } else {
                ContextCompat.getColor(this, R.color.on_surface_secondary) // OFF: グレー
            }
            btnShuffle.imageTintList = ColorStateList.valueOf(tintColor)
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
        btnPlayPause.setOnClickListener {
            if (vm.currentSong.value == null) {
                val currentSongs = vm.songs.value
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
        if (ms < 0) return "0:00"
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
