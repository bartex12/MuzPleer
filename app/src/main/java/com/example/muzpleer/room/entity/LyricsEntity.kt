package com.example.muzpleer.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Текст песни, который пользователь добавил или исправил в приложении (в файле он не меняется). */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis()
)
