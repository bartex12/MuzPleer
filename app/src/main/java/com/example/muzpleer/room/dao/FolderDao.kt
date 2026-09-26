package com.example.muzpleer.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.muzpleer.room.entity.ArtistFile
import com.example.muzpleer.room.entity.FolderFile

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderFile>)

    @Query("SELECT * FROM folders ORDER BY folderName COLLATE NOCASE")
    suspend fun getAllFolders(): List<FolderFile>

    @Query("SELECT * FROM folders WHERE folderPath = :folderPath LIMIT 1")
    suspend fun getFolderByPath(folderPath: String): FolderFile?

    @Query("SELECT coverPath FROM folders WHERE folderPath = :folderPath LIMIT 1")
    suspend fun getFolderCoverByPath(folderPath: String): String?

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: Long): FolderFile?

    @Query("SELECT * FROM folders WHERE folderName = :folderName")
    suspend fun getFolderByName(folderName: String): FolderFile?

    @Query("DELETE FROM folders WHERE folderPath  = :folderPath ")
    suspend fun deleteByPath(folderPath : String)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getCount(): Int

    @Query("UPDATE folders SET coverPath = :artUri WHERE id = :folderId")
    suspend fun updateFolderArtUri(folderId: Long, artUri: String?)

    @Query("SELECT * FROM folders ORDER BY sortOrder ASC")
    suspend fun getAllOrderedFolders(): List<FolderFile>

    @Query("UPDATE folders SET sortOrder = :newOrder WHERE id = :id")
    suspend fun updateFoldersSortOrder(id: Long, newOrder: Int)

}