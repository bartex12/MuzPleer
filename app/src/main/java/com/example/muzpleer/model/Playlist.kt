package com.example.muzpleer.model

import android.net.Uri

data class Playlist(
    val id: Long = 0,  //id
    val playlistName: String,  //имя плейлиста
    var playlistArtUri : Uri? = null, //обложка
    val playlistSongs: List<Song> = emptyList(),  //список песен
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
