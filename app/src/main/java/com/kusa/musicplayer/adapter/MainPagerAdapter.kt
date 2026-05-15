package com.kusa.musicplayer.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.kusa.musicplayer.ui.SongListFragment
import com.kusa.musicplayer.ui.ArtistListFragment
import com.kusa.musicplayer.ui.AlbumListFragment
import com.kusa.musicplayer.ui.GenreListFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SongListFragment()    // 全曲リスト
            1 -> ArtistListFragment()  // アーティスト一覧
            2 -> AlbumListFragment()   // アルバム一覧
            3 -> GenreListFragment()   // ジャンル一覧
            else -> throw IllegalStateException()
        }
    }
}