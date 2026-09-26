package com.example.muzpleer.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistFile::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongFile::class,
            parentColumns = ["mediaStoreId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["songId"]),
        Index(value = ["sortOrder"])
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
