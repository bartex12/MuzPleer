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

    /**
     * Пошаговая синхронизация альбомов с таблицей песен: новые добавляются, изменившиеся
     * обновляются, исчезнувшие удаляются. Раньше таблица очищалась и создавалась заново:
     * внутренние id менялись при каждом сканировании, а при сбое вставки альбомы терялись.
     * Обложка и порядок существующих альбомов сохраняются.
     */
    suspend fun syncAlbumsFromMediaFiles() {
        Log.d(TAG, "# AlbumRepository Начало синхронизации альбомов...")

        val existingByAlbumId = albumDao.getAllOrderedAlbums().associateBy { it.albumId }
        val mediaFiles = mediaDao.getAllFiles()
        val now = System.currentTimeMillis()

        val toWrite = mutableListOf<AlbumFile>() //новые и изменившиеся
        val actualAlbumIds = HashSet<Long>()

        mediaFiles
            .groupBy { it.albumId } // только по ID! альбома
            .forEach { (albumId, songs) ->
                actualAlbumIds.add(albumId)
                val albumName = songs.firstOrNull()?.album ?: "Неизвестный альбом"
                val artists = songs.map { it.artist ?: "Unknown" }.distinct()
                val mainArtist = if (artists.size > 1) "Разные исполнители" else artists.first()
                val existing = existingByAlbumId[albumId]

                val candidate = AlbumFile(
                    id = existing?.id ?: 0,
                    albumId = albumId,
                    title = albumName,
                    artist = mainArtist,
                    allArtists = artists.joinToString(";"),
                    songCount = songs.size,
                    coverPath = existing?.coverPath?.takeIf { it.isNotEmpty() } ?: "",  //todo заменить на getDefaultAlbumCover(albumId)
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = existing?.updatedAt ?: now,
                    sortOrder = existing?.sortOrder?.takeIf { it >= 0 } ?: 0
                )
                //пишем в базу только то, что действительно изменилось
                if (existing == null || candidate != existing) {
                    toWrite.add(candidate.copy(updatedAt = now))
                }
            }

        val toDeleteIds = existingByAlbumId.values
            .filter { it.albumId !in actualAlbumIds }
            .map { it.id }

        Log.d(TAG, "# AlbumRepository альбомов в песнях = ${actualAlbumIds.size}; " +
                "записать = ${toWrite.size}; удалить = ${toDeleteIds.size}")

        try {
            if (toWrite.isNotEmpty()) albumDao.insertAll(toWrite)
            toDeleteIds.chunked(500).forEach { albumDao.deleteByIds(it) }
            Log.d(TAG, "# AlbumRepository Альбомы успешно синхронизированы")
        } catch (e: Exception) {
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