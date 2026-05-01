package com.musicplayer.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val albumArtUri: Uri?,
    val duration: Long,       // milliseconds
    val filePath: String
) {
    val displayTitle: String get() = title.ifBlank { "不明な曲" }
    val displayArtist: String get() = artist.ifBlank { "不明なアーティスト" }
    val displayAlbum: String get() = album.ifBlank { "不明なアルバム" }

    val durationFormatted: String get() {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
