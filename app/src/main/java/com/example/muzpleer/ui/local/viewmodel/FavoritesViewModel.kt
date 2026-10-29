package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzpleer.model.Song
import com.example.muzpleer.repository.FavoriteRepository
import com.example.muzpleer.room.entity.FavoriteSong
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.launch

/**
 * Всё, что относится к избранному: список избранных песен, поиск, добавление и удаление,
 * порядок, позиция прокрутки. Раньше это жило в SharedViewModel.
 */
class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository,
    private val helper: IPreferenceHelper
) : ViewModel() {

    private val _favoriteSongs = MutableLiveData<List<Song>>(listOf())
    val favoriteSongs: LiveData<List<Song>> = _favoriteSongs

    private var _filteredFavoriteSongs = MutableLiveData<List<Song>>()
    val filteredFavoriteSongs: LiveData<List<Song>> = _filteredFavoriteSongs

    //id избранных треков - по ним в списках рисуется значок сердечка на обложке
    private val _favoriteIds = MutableLiveData<Set<Long>>(emptySet())
    val favoriteIds: LiveData<Set<Long>> = _favoriteIds

    init { refreshFavoriteIds() }

    fun refreshFavoriteIds() {
        viewModelScope.launch {
            try {
                _favoriteIds.value = favoriteRepository.getAllFavoriteSongs().map { it.songId }.toSet()
            } catch (e: Exception) {
                Log.d(TAG, "***FavoritesViewModel refreshFavoriteIds error = ${e.message}")
            }
        }
    }

    fun getFavoriteSongs():List<Song> {
        return favoriteSongs.value
    }

    internal fun filterFavoriteSongs(query: String) {
        _filteredFavoriteSongs.value = (favoriteSongs.value ?: listOf()).filterSongsByQuery(query)
    }

    fun getPositionFavoriteSong(): Int{  return helper.getPositionFavoriteSong() }

    fun savePositionFavoriteSong(position: Int){helper.savePositionFavoriteSong(position)}

    fun loadFavoriteSongs() {
        viewModelScope.launch {
            try {
                val songs = favoriteRepository.getOrderedFavorites()
                //val songs = favoriteRepository.getAllFavorites()
                _favoriteSongs.value = songs
                _filteredFavoriteSongs.value = songs
                refreshFavoriteIds()
            } catch (e: Exception) {
                Log.d(TAG, "***FavoritesViewModel loadFavoriteSongs error = ${e.message}")
            }
        }
    }

    fun getAllFavoriteSongs():List<Song>{
        return favoriteSongs.value
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val isNowFavorite = favoriteRepository.toggleFavorite(song.id)
            // Обновляем список избранного
            val songs = favoriteRepository.getAllFavorites()
            _favoriteSongs.value = songs
            _filteredFavoriteSongs.value = songs
            refreshFavoriteIds()
        }
    }

    fun checkIsFavoriteWithCallback (songId: Long, callback:(Boolean)->Unit ) {
        viewModelScope.launch {
            val isFavorite = favoriteRepository.isFavorite(songId)
            callback(isFavorite)
        }
    }

    fun clearFavorites() {
        viewModelScope.launch {
            favoriteRepository.clearAllFavorites()
            loadFavoriteSongs() // Обновляем список
        }
    }

    fun updateFavoritesOrder(orderedSongs: List<Song>) {
        viewModelScope.launch {
            val favorites:List<FavoriteSong> = favoriteRepository.getAllFavoriteSongs()
            val updatedFavorites: MutableList<FavoriteSong> = mutableListOf<FavoriteSong>()

            orderedSongs.forEachIndexed { index, song ->
                val favorite = favorites.find { it.songId == song.id }
                favorite?.let {
                    updatedFavorites.add(it.copy(sortOrder = index))
                }
            }
            favoriteRepository.updateFavoriteOrder(updatedFavorites)

            // Обновляем LiveData
            _favoriteSongs.value = orderedSongs
            _filteredFavoriteSongs.value = orderedSongs
        }
    }

    fun getOrderedFavoriteSong() {
        viewModelScope.launch {
            val songs= favoriteRepository.getOrderedFavorites()
            _favoriteSongs.value=songs
            _filteredFavoriteSongs.value=songs
            refreshFavoriteIds()
        }
    }

    fun loadFavoritesSongsForAdding() {
        viewModelScope.launch {
            _favoriteSongs.value = favoriteRepository.getOrderedFavorites()
        }
    }

    fun updateFavoriteArtUri(context: Context, track: Song){
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveDefaultCoverToCache(context)
            track.artUri = newArtUriString
            // Обновляем  избранное
            _favoriteSongs.value = _favoriteSongs.value?.map {fav->
                if (fav.id == track.id) fav.copy(artUri = newArtUriString) else fav
            }
            // Обновляем  фильтрованное избранное
            _filteredFavoriteSongs.value = _filteredFavoriteSongs.value?. map{ff->
                if (ff.id == track.id) ff.copy(artUri =newArtUriString) else ff
            }
            Log.d(TAG, " **??** FavoritesViewModel updateFavoriteArtUri newArtUriString $newArtUriString")
            try {
                newArtUriString?. let{
                    favoriteRepository.updateFavoriteAtrUri(track.id, it)
                }
                Log.d(TAG, "✅FavoritesViewModel updateFavoriteArtUri Обновлён artUri для папки  ${track.title}  id = ${track.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌FavoritesViewModel updateFavoriteArtUri Ошибка обновления artUri для папки  ${track.title} : ${e.message}")
            }
        }
    }

    companion object {
        const val TAG = "33333"
    }
}
