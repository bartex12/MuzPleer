package com.example.muzpleer.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.muzpleer.room.entity.AlbumFile

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<AlbumFile>)

    @Update
    suspend fun update(album: AlbumFile)

    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE")
    suspend fun getAllAlbums(): List<AlbumFile>

    @Query("SELECT * FROM albums WHERE albumId = :id LIMIT 1")
    suspend fun getAlbumById(id: Long): AlbumFile?

    @Query("SELECT albumId FROM albums")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM albums WHERE id = :mediaStoreId")
    suspend fun deleteByMediaStoreId(mediaStoreId: Long)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun getCount(): Int

    @Query("SELECT DISTINCT albumId FROM media_files WHERE albumId IS NOT NULL")
    suspend fun getAllAlbumIds(): List<Long>

    @Query("UPDATE albums SET coverPath = :artUri WHERE id = :albumId")
    suspend fun updateAlbumArtUri(albumId: Long, artUri: String?)

    @Query("UPDATE albums SET coverPath = :artUri WHERE albumId = :albumId")
    suspend fun updateAlbumArtUriByAlbumId(albumId: Long, artUri: String?)

    @Query("UPDATE albums SET sortOrder = :newOrder WHERE id = :id")
    suspend fun updateAlbumsSortOrder(id: Long, newOrder: Int)

    @Query("SELECT * FROM albums ORDER BY sortOrder ASC")
    suspend fun getAllOrderedAlbums(): List<AlbumFile>
}