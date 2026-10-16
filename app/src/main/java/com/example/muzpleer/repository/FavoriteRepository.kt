package com.example.muzpleer.repository

import com.example.muzpleer.room.entity.FavoriteSong
import com.example.muzpleer.model.Song
import com.example.muzpleer.room.dao.FavoriteDao
import com.example.muzpleer.room.dao.SongDao
import com.example.muzpleer.room.entity.SongFile
import com.example.muzpleer.room.utils.fromSongFileListToSongList
import com.example.muzpleer.room.utils.fromSongFileToSong
import com.example.muzpleer.util.UserMessenger

class FavoriteRepository(
    private val favoriteDao: FavoriteDao,
    private val songDao: SongDao,
    private val messenger: UserMessenger
) {
    suspend fun addToFavorites(songId: Long) {
        val favorite: FavoriteSong = FavoriteSong(songId = songId)
        favoriteDao.insert(favorite)
    }

    suspend fun removeFromFavorites(songId: Long) {
        favoriteDao.deleteBySongId(songId)
    }

    suspend fun isFavorite(songId: Long): Boolean {
        return favoriteDao.isFavorite(songId)
    }

    suspend fun getAllFavorites(): List<Song> {
        val favorites = favoriteDao.getAllFavorites()
        return favorites.mapNotNull { favorite ->
            val favoriteFile: SongFile? = songDao.getById(favorite.songId)
            favoriteFile?. let{
                fromSongFileToSong(it)
            }
        }
    }

    suspend fun getAllFavoriteSongs(): List<FavoriteSong> {
        return favoriteDao.getAllFavorites()
    }

    suspend fun toggleFavorite(songId: Long): Boolean {
        val isCurrentlyFavorite = isFavorite(songId)
        if (isCurrentlyFavorite) {
            removeFromFavorites(songId)
            messenger.show("Удалено из избранного")
            return false
        } else {
            addToFavorites(songId)
            messenger.show("Добавлено в избранное")
            return true
        }
    }

    suspend fun clearAllFavorites() {
        favoriteDao.deleteAll()
    }

    suspend fun updateFavoriteOrder(favorites: List<FavoriteSong>) {
        favorites.forEachIndexed { index, favorite ->
            favoriteDao.updateSortOrder(favorite.id, index)
        }
    }

    suspend fun getOrderedFavorites(): List<Song> {
        val favorites = favoriteDao.getAllOrdered()
        val songsFile = favorites.mapNotNull { favorite ->
            songDao.getById(favorite.songId)
        }
        return fromSongFileListToSongList(songsFile)
    }

    suspend  fun updateFavoriteAtrUri(favoriteId: Long, newArtUriString:String){
        favoriteDao.updateCoverPath(favoriteId, newArtUriString)
    }
}