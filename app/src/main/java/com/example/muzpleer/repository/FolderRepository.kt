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
    /**
     * Пошаговая синхронизация папок с таблицей песен: новые добавляются, изменившиеся
     * обновляются, исчезнувшие удаляются. Обложка и порядок существующих папок сохраняются,
     * внутренний id больше не меняется при каждом сканировании.
     */
    suspend fun syncFoldersFromMediaFiles() {
        Log.d(TAG, "# FolderRepository Начало синхронизации папок...")

        val existingByPath = folderDao.getAllOrderedFolders().associateBy { it.folderPath }
        val mediaFiles = songDao.getAllFiles()
        Log.d(TAG, "# FolderRepository syncFolderFromMediaFiles Получено ${mediaFiles.size} медиафайлов")
        val now = System.currentTimeMillis()

        val toWrite = mutableListOf<FolderFile>() //новые и изменившиеся
        val actualPaths = HashSet<String>()

        // Группируем по папкам
        mediaFiles
            .groupBy { it.folderPath ?: "Unknown" }
            .forEach { (folderPath, songs) ->
                actualPaths.add(folderPath)
                val existing = existingByPath[folderPath]

                //обложку папки (в том числе заданную пользователем) не трогаем
                val folderCoverPath = existing?.coverPath?.takeIf { it.isNotEmpty() }

                val candidate = FolderFile(
                    id = existing?.id ?: 0,
                    folderPath = folderPath,
                    folderName = getFolderNameFromPath(folderPath),
                    songCount = songs.size,
                    coverPath = folderCoverPath,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = existing?.updatedAt ?: now,
                    sortOrder = existing?.sortOrder?.takeIf { it >= 0 } ?: 0
                )
                //пишем в базу только то, что действительно изменилось
                if (existing == null || candidate != existing) {
                    toWrite.add(candidate.copy(updatedAt = now))
                }
            }

        val toDeleteIds = existingByPath.values
            .filter { it.folderPath !in actualPaths }
            .map { it.id }

        Log.d(TAG, "# FolderRepository папок в песнях = ${actualPaths.size}; " +
                "записать = ${toWrite.size}; удалить = ${toDeleteIds.size}")

        try {
            if (toWrite.isNotEmpty()) folderDao.insertAll(toWrite)
            toDeleteIds.chunked(500).forEach { folderDao.deleteByIds(it) }
            Log.d(TAG, "# FolderRepository syncFolderFromMediaFiles Папки успешно синхронизированы")
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