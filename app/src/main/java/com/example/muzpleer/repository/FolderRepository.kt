package com.example.muzpleer.repository

import android.util.Log
import androidx.core.net.toUri
import com.example.muzpleer.model.Folder
import com.example.muzpleer.model.Song
import com.example.muzpleer.room.dao.FolderDao
import com.example.muzpleer.room.dao.SongDao
import com.example.muzpleer.room.entity.AlbumFile
import com.example.muzpleer.room.entity.FolderFile
import com.example.muzpleer.room.utils.fromSongFileListToSongList
import java.io.File

class FolderRepository (private val folderDao: FolderDao,
                        private val songDao: SongDao,
                       ){
    // Первый запуск - группировка и сохранение
    suspend fun syncFoldersFromMediaFiles() {
        Log.d(TAG, "# FolderRepository Начало синхронизации папок...")

        // СОХРАНЯЕМ КАСТОМНЫЕ ОБЛОЖКИ и порядок следования ПЕРЕД ОЧИСТКОЙ
        val existingFolders = folderDao.getAllOrderedFolders()
        val customCoversMap = mutableMapOf<String, String>() // path -> coverPath
        val customMovedMap = mutableMapOf<String, Int>()  //folderId -> sortOrder

        existingFolders.forEach { folder ->
            if (!folder.coverPath.isNullOrEmpty()) {
                customCoversMap[folder.folderPath] = folder.coverPath
            }
        }
        Log.d(TAG, "# Сохранено ${customCoversMap.size} кастомных обложек")

        //сохраняем порядок следования папок  в списке
        existingFolders.forEach { folder ->
            if (folder.sortOrder >= 0) {
                customMovedMap[folder.folderPath] = folder.sortOrder
            }
        }

        // Очищаем папки
        folderDao.deleteAll()

        val mediaFiles = songDao.getAllFiles()
        Log.d(TAG, "# FolderRepository syncFolderFromMediaFiles Получено ${mediaFiles.size} медиафайлов")

        // Группируем по папкам
        val foldersMap = mediaFiles
            .groupBy { it.folderPath ?: "Unknown" }
            .mapValues { (folderPath, songs) ->

                //получаем имя из пути
                val folderName = getFolderNameFromPath(folderPath)
                //получаем обложку - её могли изменить - но мы уже всё стёрли
                val folderCoverPath = customCoversMap[folderPath] ?: getFolderCoverByPath(folderPath)
                val folderMovedMap = customMovedMap[folderPath] ?: 0

                FolderFile(
                    folderPath = folderPath,
                    folderName = folderName,
                    songCount = songs.size,
                    coverPath = folderCoverPath,
                    sortOrder = folderMovedMap
                )
            }
        Log.d(TAG, "# FolderRepository syncFolderFromMediaFiles Создано ${foldersMap.size} папок")

        try {
            folderDao.insertAll(foldersMap.values.toList())
            Log.d(TAG, "# FolderRepository syncFolderFromMediaFiles  Папки успешно сохранены в базу")
            val folderSize = folderDao.getAllOrderedFolders().size
            Log.d(TAG, "# FolderRepository syncFolderFromMediaFiles из базы folderSize = $folderSize" )
        } catch (e: Exception) {
            Log.d(TAG, "# FolderRepository syncFolderFromMediaFiles Ошибка сохранения папок: ${e.message}")
        }
    }

    // Последующие запуски - получение из базы
    suspend fun getAllFoldersWithSongs(): List<Folder> {
        // Получаем все папки  из базы
        val folders = folderDao.getAllOrderedFolders()

        return folders.map { folder ->
            // Получаем все песни в папке
            val songFiles  = songDao.getFilesByFolderPath(folder.folderPath)
            val songs = fromSongFileListToSongList(songFiles)
            Folder(
                id = folder.id,
                path = folder.folderPath,
                name = folder.folderName,
                songs = songs,
                artworkUri =folder.coverPath?.toUri(),
            )
        }
    }

    suspend fun getFolderByPath(folderPath:String):Folder{

        val folderFile = folderDao.getFolderByPath(folderPath) ?: throw Exception("Папка не найдена")
        val songFiles = songDao.getFilesByFolderPath(folderPath)
        val songs = fromSongFileListToSongList(songFiles)
        return Folder(
            id = folderFile.id,
            path = folderFile.folderPath,
            name = folderFile.folderName,
            songs = songs,
            artworkUri =folderFile.coverPath?.toUri()
        )
    }

    suspend fun getFolderCoverByPath(folderPath:String):String?{
        return folderDao.getFolderCoverByPath(folderPath)
    }

    suspend fun updateFolderArtUri(folderId: Long, artUri: String?) {
        folderDao.updateFolderArtUri(folderId, artUri)
    }

    suspend fun getFolderFileById(folderId: Long):FolderFile?{
        return folderDao.getFolderById(folderId)
    }

    private fun getFolderNameFromPath(path: String): String {
        return if (path.isBlank()) {
            "Unknown"
        } else {
            File(path).name
        }
    }

    suspend fun getFolderSongList(folderPath: String): List<Song> {
        val songFiles = songDao.getFilesByFolderPath(folderPath)
       return fromSongFileListToSongList(songFiles)
    }

    suspend fun getAllFolderSongs(): List<FolderFile> {
        return folderDao.getAllOrderedFolders()
    }

    suspend fun updateFoldersOrder(folders: List<FolderFile>) {
        folders.forEachIndexed { index, folder ->
            folderDao.updateFoldersSortOrder(folder.id, index)
        }
    }

    companion object{
        const val TAG = "33333"
    }
}