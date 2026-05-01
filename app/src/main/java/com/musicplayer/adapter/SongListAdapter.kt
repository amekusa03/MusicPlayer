package com.musicplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicplayer.AlbumArtLoader
import com.musicplayer.R
import com.musicplayer.model.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SongListAdapter(
    private val scope: LifecycleCoroutineScope,
    private val onSongClick: (Song) -> Unit
) : ListAdapter<Song, SongListAdapter.SongViewHolder>(DIFF_CALLBACK) {

    private var currentSongId: Long = -1L

    fun setCurrentSong(song: Song?) {
        val old = currentSongId
        currentSongId = song?.id ?: -1L
        currentList.forEachIndexed { i, s ->
            if (s.id == old || s.id == currentSongId) notifyItemChanged(i)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == currentSongId)
    }

    inner class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivArt: ImageView     = view.findViewById(R.id.ivAlbumArt)
        private val tvTitle: TextView    = view.findViewById(R.id.tvSongTitle)
        private val tvArtist: TextView   = view.findViewById(R.id.tvArtist)
        private val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        private val viewPlaying: View    = view.findViewById(R.id.viewPlayingIndicator)
        private var artJob: Job? = null

        fun bind(song: Song, isCurrent: Boolean) {
            tvTitle.text    = song.displayTitle
            tvArtist.text   = "${song.displayArtist} • ${song.displayAlbum}"
            tvDuration.text = song.durationFormatted
            viewPlaying.visibility = if (isCurrent) View.VISIBLE else View.INVISIBLE
            itemView.isSelected = isCurrent

            // Reset art while loading
            ivArt.setImageResource(R.drawable.ic_music_note)
            artJob?.cancel()
            artJob = scope.launch {
                val bmp = AlbumArtLoader.load(itemView.context, song.uri)
                if (bmp != null) ivArt.setImageBitmap(bmp)
                else ivArt.setImageResource(R.drawable.ic_music_note)
            }

            itemView.setOnClickListener { onSongClick(song) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
            override fun areContentsTheSame(a: Song, b: Song) = a == b
        }
    }
}
