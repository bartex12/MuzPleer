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

    /**
     * Пошаговая синхронизация исполнителей с таблицей песен: новые добавляются, изменившиеся
     * обновляются, исчезнувшие удаляются. Обложка и порядок существующих сохраняются,
     * внутренний id больше не меняется при каждом сканировании.
     */
    suspend fun syncArtistsFromMediaFiles() {
        Log.d(TAG, "# ArtistsRepository Начало синхронизации артистов...")

        val existingByName = artistDao.getAllOrderedArtists().associateBy { it.name }
        val mediaFiles = songDao.getAllFiles()
        Log.d(TAG, "# ArtistsRepository syncArtistsFromMediaFiles Получено ${mediaFiles.size} медиафайлов")
        val now = System.currentTimeMillis()

        val toWrite = mutableListOf<ArtistFile>() //новые и изменившиеся
        val actualNames = HashSet<String>()

        // Группируем по артистам
        mediaFiles
            .groupBy { it.artist ?: "Unknown Artist" }
            .forEach { (artistName, songs) ->
                actualNames.add(artistName)
                val existing = existingByName[artistName]
                val albumIds = songs.map { it.albumId }.distinct()

                val candidate = ArtistFile(
                    id = existing?.id ?: 0,
                    artistId = generateArtistId(artistName),  // это хэш-код  не путать с id!!!
                    name = artistName,
                    allAlbumIds = albumIds.joinToString(";"),
                    songCount = songs.size,
                    coverPath = existing?.coverPath?.takeIf { it.isNotEmpty() } ?: "", //todo
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = existing?.updatedAt ?: now,
                    sortOrder = existing?.sortOrder?.takeIf { it >= 0 } ?: 0
                )
                //пишем в базу только то, что действительно изменилось
                if (existing == null || candidate != existing) {
                    toWrite.add(candidate.copy(updatedAt = now))
                }
            }

        val toDeleteIds = existingByName.values
            .filter { it.name !in actualNames }
            .map { it.id }

        Log.d(TAG, "# ArtistsRepository артистов в песнях = ${actualNames.size}; " +
                "записать = ${toWrite.size}; удалить = ${toDeleteIds.size}")

        try {
            if (toWrite.isNotEmpty()) artistDao.insertAll(toWrite)
            toDeleteIds.chunked(500).forEach { artistDao.deleteByIds(it) }
            Log.d(TAG, "# ArtistsRepository syncArtistsFromMediaFiles Артисты успешно синхронизированы")
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

    suspend fun getArtistFileById(id: Long): ArtistFile? = artistDao.getArtistById(id)

    suspend fun updateArtistArtUriById(id: Long, artUri: String?) {
        artistDao.updateArtistArtUriById(id, artUri)
    }

    suspend fun updateArtistsOrder(artists: List<ArtistFile>) {
        artists.forEachIndexed { index, artist ->
            artistDao.updateArtistsSortOrder(artist.id, index)
        }
    }

}