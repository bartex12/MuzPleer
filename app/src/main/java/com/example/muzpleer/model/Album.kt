package com.example.muzpleer.model

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,       // Основной исполнитель или "Various Artists"
    val artists: List<String> =  emptyList(), // Все исполнители в альбоме
    var artworkUri: Uri? = null,
    val albumId: Long, // Добавляем поле для albumId
    val songs: List<Song> = emptyList()
)