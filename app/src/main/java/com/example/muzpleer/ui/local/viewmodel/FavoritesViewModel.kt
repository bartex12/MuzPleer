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
/** По какому полю отсортирован список избранного; NONE - свой порядок, заданный перетаскиванием. */
enum class FavoriteSortField { NONE, TITLE, ALBUM, ARTIST, DURATION, DATE_ADDED }

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

    //сортировка списка: хранится здесь, переживает поворот экрана; свой порядок песен лежит в manualSongs
    var sortField = FavoriteSortField.NONE
        private set
    var sortAscending = true
        private set
    private var manualSongs: List<Song> = listOf()
    private var datesAdded: Map<Long, Long> = emptyMap()
    private var lastQuery = ""

    init { refreshFavoriteIds() }

    /** Включает сортировку (NONE - вернуть свой порядок) и перестраивает списки. */
    fun setSort(field: FavoriteSortField, ascending: Boolean) {
        sortField = field
        sortAscending = ascending
        viewModelScope.launch { setSongs(manualSongs) }
    }

    //все изменения списка проходят здесь: запоминаем свой порядок, показываем и играем в порядке сортировки
    private suspend fun setSongs(songs: List<Song>) {
        manualSongs = songs
        if (sortField == FavoriteSortField.DATE_ADDED) {
            try { datesAdded = favoriteRepository.getDatesAdded(songs.map { it.id }) } catch (e: Exception) { Log.d(TAG, "dates error ${e.message}") }
        }
        val sorted = sortSongs(songs)
        _favoriteSongs.value = sorted
        _filteredFavoriteSongs.value = if (lastQuery.isEmpty()) sorted else sorted.filterSongsByQuery(lastQuery)
    }

    private fun sortSongs(songs: List<Song>): List<Song> {
        val result = when (sortField) {
            FavoriteSortField.NONE -> return songs
            FavoriteSortField.TITLE -> sortByText(songs) { it.title }
            FavoriteSortField.ALBUM -> sortByText(songs) { it.albumName.orEmpty() }
            FavoriteSortField.ARTIST -> sortByText(songs) { it.artist }
            FavoriteSortField.DURATION -> songs.sortedBy { it.duration }
            FavoriteSortField.DATE_ADDED -> songs.sortedBy { datesAdded[it.id] ?: 0L }
        }
        return if (sortAscending) result else result.reversed()
    }

    //текст: сначала русские буквы, потом латинские, потом всё остальное (цифры и знаки); внутри групп по алфавиту
    private fun sortByText(songs: List<Song>, text: (Song) -> String): List<Song> =
        songs.map { song ->
            val t = text(song).trim()
            val first = t.firstOrNull()
            val group = when {
                first == null -> 3
                first in 'а'..'я' || first in 'А'..'Я' || first == 'ё' || first == 'Ё' -> 0
                first in 'a'..'z' || first in 'A'..'Z' -> 1
                else -> 2
            }
            Triple(group, t.lowercase(), song)
        }.sortedWith(compareBy<Triple<Int, String, Song>>({ it.first }, { it.second })).map { it.third }

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
        lastQuery = query
        _filteredFavoriteSongs.value = (favoriteSongs.value ?: listOf()).filterSongsByQuery(query)
    }

    fun getPositionFavoriteSong(): Int{  return helper.getPositionFavoriteSong() }

    fun savePositionFavoriteSong(position: Int){helper.savePositionFavoriteSong(position)}

    fun loadFavoriteSongs() {
        viewModelScope.launch {
            try {
                val songs = favoriteRepository.getOrderedFavorites()
                setSongs(songs)
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
            setSongs(songs)
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
            setSongs(orderedSongs)
        }
    }

    fun getOrderedFavoriteSong() {
        viewModelScope.launch {
            val songs= favoriteRepository.getOrderedFavorites()
            setSongs(songs)
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
            manualSongs = manualSongs.map { if (it.id == track.id) it.copy(artUri = newArtUriString) else it }
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
