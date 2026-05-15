package com.kusa.musicplayer.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.kusa.musicplayer.R
import com.kusa.musicplayer.viewmodel.PlayerViewModel

class GenreListFragment : Fragment(R.layout.fragment_list_simple) {
    private val vm: PlayerViewModel by lazy {
        ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = view.findViewById<ListView>(R.id.listView)

        vm.genres.observe(viewLifecycleOwner) { genres ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, genres)
            listView.adapter = adapter
        }

        listView.setOnItemClickListener { parent, _, position, _ ->
            val name = parent.getItemAtPosition(position) as String
            vm.playGenre(name)
        }
    }
}
