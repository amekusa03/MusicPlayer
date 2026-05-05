package com.kusa.musicplayer.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kusa.musicplayer.R
import com.kusa.musicplayer.adapter.SongListAdapter
import com.kusa.musicplayer.viewmodel.PlayerViewModel

class SongListFragment : Fragment(R.layout.fragment_list) {
    private val vm: PlayerViewModel by lazy {
        ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        
        val adapter = SongListAdapter(viewLifecycleOwner.lifecycleScope) { song ->
            vm.playSong(song)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        vm.songs.observe(viewLifecycleOwner) { songs ->
            adapter.submitList(songs)
        }

        vm.currentSong.observe(viewLifecycleOwner) { song ->
            adapter.setCurrentSong(song)
            // 再生中の曲までスクロール
            val index = adapter.currentList.indexOf(song)
            if (index != -1) {
                recyclerView.smoothScrollToPosition(index)
            }
        }
    }
}