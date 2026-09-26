package com.example.muzpleer.room.entity


import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["id"], unique = true)] // Уникальность только в таблице playlists
)

data class PlaylistFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistName: String,  //имя плейлиста
    val playlistArtUri: String? = null, //обложка
    val songCount: Int,  //количество песен
    val createdAt: Long = System.currentTimeMillis(), //создано
    val updatedAt: Long = System.currentTimeMillis(),  //обновлено
    var sortOrder: Int = 0 // порядок сортировки
)
