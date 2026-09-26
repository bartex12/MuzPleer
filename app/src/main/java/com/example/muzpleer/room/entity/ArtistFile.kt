package com.example.muzpleer.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * @property id - Внутренний ID Room (автоинкремент, не использовать для сравнения)
 * @property artistId - Стабильный ID на основе хэша имени (использовать для связей)
 */

@Entity(
    tableName = "artists",
    indices = [Index(value = ["id"], unique = true)] // Уникальность только в таблице artists
)
data class ArtistFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artistId: Long, //   хэш имени
    val name: String,
    val allAlbumIds: String, // Список ID альбомов через ";"
    val songCount: Int,
    val coverPath: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    var sortOrder: Int = 0 // Новое поле для порядка сортировки
)
