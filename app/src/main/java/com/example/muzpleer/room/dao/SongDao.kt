package com.example.muzpleer.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.muzpleer.room.entity.SongFile

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: SongFile)

    // Вставка списка элементов
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<SongFile>)

    @Update
    suspend fun update(file: SongFile)

    @Update
    suspend fun updateAll(files: List<SongFile>)

    @Delete
    suspend fun delete(file: SongFile)

    @Query("SELECT * FROM media_files WHERE mediaStoreId = :id LIMIT 1")
    suspend fun getById(id: Long): SongFile?

    @Query("SELECT mediaStoreId FROM media_files")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT * FROM media_files")
    suspend fun getAllFiles(): List<SongFile>

    @Query("DELETE FROM media_files WHERE mediaStoreId = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media_files WHERE albumId = :albumId ORDER BY title")
    suspend fun getFilesByAlbumId(albumId: Long): List<SongFile>

    @Query("SELECT DISTINCT albumId FROM media_files WHERE albumId IS NOT NULL")
    suspend fun getAllAlbumIds(): List<Long>

    @Query("SELECT * FROM media_files WHERE artist = :artistName ORDER BY title")
    suspend fun getFilesByArtistName(artistName: String): List<SongFile>

    @Query("SELECT * FROM media_files WHERE artistId = :artistId ORDER BY title")
    suspend fun getFilesByArtistId(artistId: Long): List<SongFile>

    @Query("SELECT DISTINCT artist FROM media_files WHERE artist IS NOT NULL")
    suspend fun getAllArtistNames(): List<String>

    @Query("SELECT * FROM media_files WHERE folderPath = :folderPath ORDER BY title")
    suspend fun getFilesByFolderPath(folderPath: String): List<SongFile>

    @Query("SELECT DISTINCT folderPath FROM media_files WHERE folderPath IS NOT NULL")
    suspend fun getAllFolderPaths(): List<String>

    @Query("UPDATE media_files SET artUri = :coverPath WHERE mediaStoreId = :songId")
    suspend fun updateCoverPath(songId: Long, coverPath: String?)

    @Query("SELECT artUri FROM media_files WHERE mediaStoreId = :songId")
    suspend fun getCoverPath(songId: Long): String?

    @Query("UPDATE media_files SET title = :title, artist = :artist, album = :album, author = :author, genre = :genre, year = :year WHERE mediaStoreId = :id")
    suspend fun updateSongInfo(id: Long, title: String, artist: String, album: String, author: String?, genre: String?, year: Int?)

    // метод для получения всех полей
    @Query("SELECT * FROM media_files WHERE mediaStoreId = :id")
    suspend fun getSongWithDetails(id: Long): SongFile?

    @Query("SELECT * FROM media_files WHERE artist = :artistName ORDER BY title ASC")
    suspend fun getSongsByArtist(artistName: String): List<SongFile>
}

