package com.example.muzpleer.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.muzpleer.room.entity.LyricsEntity

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId LIMIT 1")
    suspend fun get(songId: Long): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun delete(songId: Long)
}
