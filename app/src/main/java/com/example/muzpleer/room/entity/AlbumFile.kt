package com.example.muzpleer.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [Index(value = ["albumId"], unique = true)] // Уникальность только в таблице albums
)
data class AlbumFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val albumId: Long, //  val albumId: Long, // Уникальный только в рамках таблицы albums
    val title: String,
    val artist: String,
    val allArtists: String, // JSON или разделенный список
    val songCount: Int,
    val coverPath: String?,  //путь к обложке
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    var sortOrder: Int = 0 // Новое поле для порядка сортировки
)