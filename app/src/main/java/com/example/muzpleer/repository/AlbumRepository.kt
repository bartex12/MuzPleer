package com.example.muzpleer.repository

import android.util.Log
import androidx.core.net.toUri
import com.example.muzpleer.model.Album
import com.example.muzpleer.model.Song
import com.example.muzpleer.room.dao.AlbumDao
import com.example.muzpleer.room.dao.SongDao
import com.example.muzpleer.room.entity.AlbumFile
import com.example.muzpleer.room.entity.SongFile
import com.example.muzpleer.room.utils.fromSongFileListToSongList
import com.example.muzpleer.room.utils.toArtistList

class AlbumRepository(
    private val albumDao: AlbumDao,
    private val mediaDao: SongDao
) {
    companion object{
        const val TAG = "33333"
    }

    suspend fun syncAlbumsFromMediaFiles() {
        Log.d(TAG, "# AlbumRepository Начало синхронизации альбомов...")

        // СОХРАНЯЕМ КАСТОМНЫЕ ОБЛОЖКИ ПЕРЕД ОЧИСТКОЙ
        val existingAlbums = albumDao.getAllOrderedAlbums()
        val customCoversMap = mutableMapOf<Long, String>() // albumId -> coverPath
        val customMovedMap = mutableMapOf<Long, Int>()  //albumId -> sortOrder

        //сохраняем обложки
        existingAlbums.forEach { album ->
            if (!album.coverPath.isNullOrEmpty()) {
                customCoversMap[album.albumId] = album.coverPath
            }
        }
        Log.d(TAG, "# Сохранено ${customCoversMap.size} кастомных обложек альбомов ")

        //сохраняем порядок следования альбомов в списке
        existingAlbums.forEach { album ->
            if (album.sortOrder >= 0) {
                customMovedMap[album.albumId] = album.sortOrder
            }
        }

        // Очищаем и пересоздаем альбомы
        albumDao.deleteAll()

        val mediaFiles = mediaDao.getAllFiles()

        val albumsMap = mediaFiles
            .groupBy { it.albumId } // только по ID! альбома
            .mapValues { (albumId, songs) ->
                val albumName = songs.firstOrNull()?.album ?: "Неизвестный альбом"
                val artists = songs.map { it.artist ?: "Unknown" }.distinct()
                val mainArtist = if (artists.size > 1) "Разные исполнители" else artists.first()

                val albumCoverPath = customCoversMap[albumId] ?: ""  //todo заменить на getDefaultAlbumCover(albumId)
                val albumMovedMap = customMovedMap[albumId] ?: 0

                AlbumFile(
                    albumId = albumId,
                    title =albumName,
                    artist = mainArtist,
                    allArtists = artists.joinToString(";"),
                    songCount = songs.size,
                    coverPath = albumCoverPath,
                    sortOrder = albumMovedMap
                )
            }

        Log.d(TAG, "# AlbumRepository Создано ${albumsMap.size} альбомов")

        try {
            // Сохраняем в базу
            albumDao.insertAll(albumsMap.values.toList())
            Log.d(TAG, "# AlbumRepository Альбомы успешно сохранены в базу")
        }catch (e: Exception){
            Log.d(TAG, "# AlbumRepository syncAlbumsFromMediaFiles Exception = ${e.message}")
        }
    }

    suspend fun getAllAlbumsWithSongs(): List<Album> {
        val albums = albumDao.getAllOrderedAlbums() //получаем упорядоченный список альбомов из базы
        return albums.map { albumFile ->
            val songFileList = mediaDao.getFilesByAlbumId(albumFile.albumId)
            //Log.d(TAG, "*AlbumRepository getAllAlbumsWithSongs songFileList size = ${songFileList.size}")
            Album(
                id =albumFile.id,
                title = albumFile.title,
                artist =albumFile.artist ,
                artists = albumFile.allArtists.toArtistList(),
                artworkUri =(albumFile.coverPath)?.toUri(),
                albumId = albumFile.albumId,
                songs = fromSongFileListToSongList (songFileList)
            )
        }
    }

    suspend fun getAllAlbumSongs(): List<AlbumFile> {
        return albumDao.getAllOrderedAlbums()
    }

    suspend fun updateAlbumsOrder(albums: List<AlbumFile>) {
        albums.forEachIndexed { index, album ->
            albumDao.updateAlbumsSortOrder(album.id, index)
        }
    }

    suspend fun getAlbumSongList(albumId: Long): List<Song> {
        var songFileList: List<SongFile> = listOf()
        val allAlbums = albumDao.getAllOrderedAlbums()
        Log.d(TAG, "*AlbumRepository getAlbumSongList allAlbums size   = ${allAlbums.size} ")
        val allIds = albumDao. getAllIds()
        Log.d(TAG, "*AlbumRepository getAlbumSongList getAllIds = $allIds ")
         val albumFile: AlbumFile? = albumDao.getAlbumById(albumId)
        Log.d(TAG, "*AlbumRepository getAlbumSongList albumFile  = $albumFile")
        if (albumFile!= null){
            songFileList = mediaDao.getFilesByAlbumId(albumFile.albumId)
            Log.d(TAG, "*AlbumRepository getAlbumSongList songFileList size = ${songFileList.size}")
        }
        return fromSongFileListToSongList(songFileList)
    }

    suspend fun updateAlbumArtUri(albumId: Long, artUri: String?) {
        albumDao.updateAlbumArtUri(albumId, artUri)
    }

    suspend fun updateAlbumArtUriByAlbumId(albumId: Long, artUri: String?){
        albumDao.updateAlbumArtUriByAlbumId(albumId, artUri)
    }

    suspend fun getAlbumById(albumId:Long): AlbumFile?{
        return albumDao.getAlbumById(albumId)
    }

}