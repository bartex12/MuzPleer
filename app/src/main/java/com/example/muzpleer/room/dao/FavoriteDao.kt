package com.example.muzpleer.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.muzpleer.room.entity.FavoriteSong

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteSong)

    @Delete
    suspend fun delete(favorite: FavoriteSong)

    @Query("DELETE FROM favorite_songs WHERE songId = :songId")
    suspend fun deleteBySongId(songId: Long)

    @Query("SELECT * FROM favorite_songs ORDER BY addedAt DESC")
    suspend fun getAllFavorites(): List<FavoriteSong>

    @Query("SELECT * FROM favorite_songs WHERE songId = :songId LIMIT 1")
    suspend fun getFavoriteBySongId(songId: Long): FavoriteSong?

    @Query("SELECT COUNT(*) FROM favorite_songs WHERE songId = :songId")
    suspend fun isFavorite(songId: Long): Boolean

    @Query("DELETE FROM favorite_songs")
    suspend fun deleteAll()

    @Update
    suspend fun update(favorite: FavoriteSong)

    @Query("UPDATE favorite_songs SET sortOrder = :newOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, newOrder: Int)

    @Query("UPDATE favorite_songs SET artUri = :coverPath WHERE id = :songId")
    suspend fun updateCoverPath(songId: Long, coverPath: String?)

    @Query("SELECT * FROM favorite_songs ORDER BY sortOrder ASC")
    suspend fun getAllOrdered(): List<FavoriteSong>

    @Query("SELECT MAX(sortOrder) FROM favorite_songs")
    suspend fun getMaxSortOrder(): Int?
}