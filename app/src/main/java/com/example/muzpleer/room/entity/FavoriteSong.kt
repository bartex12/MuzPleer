package com.example.muzpleer.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_songs",
    foreignKeys = [
        ForeignKey(
            entity=SongFile::class,
            parentColumns=["mediaStoreId"],
            childColumns=["songId"],
            onDelete=ForeignKey.Companion.CASCADE
        )
    ],
    indices = [Index(value=["songId"], unique=true)]
)
data class FavoriteSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long, // ID песни из таблицы songs
    val addedAt: Long = System.currentTimeMillis(),
    var sortOrder: Int = 0,  // Новое поле для порядка сортировки
    var artUri: String? = null  // путь к обложке для треков из local
)