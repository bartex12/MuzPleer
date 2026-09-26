package com.example.muzpleer.repository

import android.util.Log
import androidx.core.net.toUri
import com.example.muzpleer.model.Album
import com.example.muzpleer.model.Artist
import com.example.muzpleer.model.Song
import com.example.muzpleer.room.dao.AlbumDao
import com.example.muzpleer.room.dao.ArtistDao
import com.example.muzpleer.room.dao.SongDao
import com.example.muzpleer.room.entity.ArtistFile
import com.example.muzpleer.room.entity.SongFile
import com.example.muzpleer.room.utils.fromSongFileListToSongList
import com.example.muzpleer.room.utils.toArtistList

class ArtistRepository (private val artistDao: ArtistDao,
                        private val songDao: SongDao,
                        private val albumDao: AlbumDao)
{

    // Первый запуск - группировка и сохранение
    suspend fun syncArtistsFromMediaFiles() {
        Log.d(TAG, "# ArtistsRepository Начало синхронизации артистов...")

    // СОХРАНЯЕМ КАСТОМНЫЕ ОБЛОЖКИ и порядок следования ПЕРЕД ОЧИСТКОЙ
        val existingArtists = artistDao.getAllOrderedArtists()
        val customCoversMap = mutableMapOf<String, String>() // artistName -> coverPath
        val customMovedMap = mutableMapOf<String, Int>()  //artistId -> sortOrder

        //сохраняем обложки
        existingArtists.forEach { artist ->
            if (!artist.coverPath.isNullOrEmpty()) {
                customCoversMap[artist.name] = artist.coverPath
            }
        }
        Log.d(TAG, "# Сохранено ${customCoversMap.size} кастомных обложек артистов")

        //сохраняем порядок следования артистов в списке
        existingArtists.forEach { artist ->
            if (artist.sortOrder >= 0) {
                customMovedMap[artist.name] = artist.sortOrder
            }
        }

        // Очищаем артистов
        artistDao.deleteAll()

        val mediaFiles = songDao.getAllFiles()
        Log.d(TAG, "# ArtistsRepository syncArtistsFromMediaFiles Получено ${mediaFiles.size} медиафайлов")

        // Группируем по артистам
        val artistsMap = mediaFiles
            .groupBy { it.artist ?: "Unknown Artist" }
            .mapValues { (artistName, songs) ->
                val artistId = generateArtistId(artistName)
                val albumIds = songs.map { it.albumId }.distinct()

                // ВОССТАНАВЛИВАЕМ КАСТОМНУЮ ОБЛОЖКУ
                val artistCoverPath = customCoversMap[artistName] ?: "" //todo
                val artistMovedMap = customMovedMap[artistName] ?: 0

                ArtistFile(
                    artistId = artistId,  // это хэш-код  не путать с id!!!
                    name = artistName,
                    allAlbumIds = albumIds.joinToString(";"),
                    songCount = songs.size,
                    coverPath = artistCoverPath,
                    sortOrder = artistMovedMap
                )
            }

        Log.d(TAG, "# ArtistsRepository syncArtistsFromMediaFiles Создано ${artistsMap.size} артистов")

        try {
            artistDao.insertAll(artistsMap.values.toList())
            Log.d(TAG, "# ArtistsRepository syncArtistsFromMediaFiles Артисты успешно сохранены в базу")
            val artistSize = artistDao.getAllOrderedArtists().size
            Log.d(TAG, "# ArtistsRepository syncArtistsFromMediaFiles из базы artistSize size = $artistSize" )
        } catch (e: Exception) {
            Log.d(TAG, "# ArtistsRepository syncArtistsFromMediaFiles Ошибка сохранения артистов: ${e.message}")
        }
    }

    suspend fun getAllArtistsWithSongsAndAlbums(): List<Artist> {
        // Получаем всех артистов из базы
        val artistFiles = artistDao.getAllOrderedArtists()

        return artistFiles.map { artistFile ->
            // Получаем все песни этого артиста
            val artistSongs = songDao.getFilesByArtistId(artistFile.artistId)
            val songList = fromSongFileListToSongList(artistSongs)

            // Получаем ID альбомов этого артиста
            val albumIds = artistFile.allAlbumIds.split(";").mapNotNull { it.toLongOrNull() }

            // Для каждого альбома получаем песни этого артиста
            val artistAlbums = albumIds.mapNotNull { albumId ->
                // Получаем данные альбома
                val albumFile = albumDao.getAlbumById(albumId) ?: return@mapNotNull null

                // Получаем все песни этого альбома
                val allAlbumSongs = songDao.getFilesByAlbumId(albumId)

                // Фильтруем только песни текущего артиста
                val artistSongsInAlbum = allAlbumSongs.filter {
                    it.artistId == artistFile.artistId
                }

                Album(
                    id = albumFile.albumId,
                    title = albumFile.title,
                    artist = albumFile.artist,
                    artists = albumFile.allArtists.toArtistList(),
                    artworkUri = albumFile.coverPath?.toUri(),
                    albumId = albumFile.albumId,
                    songs = fromSongFileListToSongList(artistSongsInAlbum)
                )
            }

            Artist(
                id = artistFile.id,
                name = artistFile.name,
                songs = songList,
                artworkUri = artistFile.coverPath?.toUri(),
                albums = artistAlbums
            )
        }
    }

    suspend fun getAllArtistsSongs(): List<ArtistFile> {
        return artistDao.getAllOrderedArtists()
    }

    suspend fun getArtistSongList(artistId: Long): List<Song> {
        var songFileList: List<SongFile> = listOf()
        val allArtists = artistDao.getAllOrderedArtists()
        Log.d(TAG, "# ArtistsRepository getAlbumSongList allArtists size   = ${allArtists.size} ")
        val allArtistIds = artistDao. getAllArtistIds()
        Log.d(TAG, "# ArtistsRepository getArtistSongList allArtistIds = $allArtistIds ")
        val artistFile: ArtistFile? = artistDao.getArtistById(artistId)
        Log.d(TAG, "# ArtistsRepository getArtistSongList artistFile  = $artistFile")
        if (artistFile!= null){
            songFileList = songDao.getFilesByArtistId(artistFile.artistId)
            Log.d(TAG, "# ArtistsRepository getArtistSongList songFileList size = ${songFileList.size}")
        }
        return fromSongFileListToSongList(songFileList)
    }

    private fun generateArtistId(artistName: String): Long {
        return artistName.hashCode().toLong()
    }

    companion object{
        const val TAG = "33333"
    }

    suspend fun updateArtistArtUri(artistId: Long, artUri: String?) {
        artistDao.updateArtistArtUri(artistId, artUri)
    }

    suspend fun updateArtistArtUriById(id: Long, artUri: String?) {
        artistDao.updateArtistArtUriById(id, artUri)
    }

    suspend fun updateArtistsOrder(artists: List<ArtistFile>) {
        artists.forEachIndexed { index, artist ->
            artistDao.updateArtistsSortOrder(artist.id, index)
        }
    }

}