package com.example.muzpleer.model

import android.net.Uri

/**
 * @property id - Внутренний ID Room (автоинкремент, не использовать для сравнения)
 * @property artistId - Стабильный ID на основе хэша имени (использовать для связей)
 */

data class Artist(
    val id: Long,
    val name: String,
    val artistId:Long = name.hashCode().toLong(),
    val songs: List<Song>,
    var artworkUri: Uri? = null,
    val albums: List<Album> // Добавляем список альбомов
)