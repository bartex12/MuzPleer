package com.example.muzpleer.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.muzpleer.room.entity.ArtistFile

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: ArtistFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<ArtistFile>)

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    suspend fun getAllArtists(): List<ArtistFile>

    @Query("SELECT * FROM artists WHERE id = :artistId LIMIT 1")
    suspend fun getArtistById(artistId: Long): ArtistFile?

    @Query("SELECT * FROM artists WHERE name = :artistName LIMIT 1")
    suspend fun getArtistByName(artistName: String): ArtistFile?

    @Query("DELETE FROM artists WHERE id = :artistId")
    suspend fun deleteByArtistId(artistId: Long)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun getCount(): Int

    @Query("SELECT id FROM artists")
    suspend fun getAllArtistIds(): List<Long>

    @Query("UPDATE artists SET coverPath = :artUri WHERE id = :artistId")
    suspend fun updateArtistArtUri(artistId: Long, artUri: String?)

    @Query("UPDATE artists SET coverPath = :artUri WHERE id = :id")
    suspend fun updateArtistArtUriById(id: Long, artUri: String?)

    @Query("UPDATE artists SET sortOrder = :newOrder WHERE id = :id")
    suspend fun updateArtistsSortOrder(id: Long, newOrder: Int)

    @Query("SELECT * FROM artists ORDER BY sortOrder ASC")
    suspend fun getAllOrderedArtists(): List<ArtistFile>
}