package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzpleer.model.Artist
import com.example.muzpleer.model.Song
import com.example.muzpleer.repository.ArtistRepository
import com.example.muzpleer.room.entity.ArtistFile
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.copyCoverToAppStorage
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Всё, что относится к вкладке "Исполнители" и списку песен исполнителя.
 * Раньше это жило в SharedViewModel.
 */
class ArtistsViewModel(
    private val artistsRepository: ArtistRepository,
    private val helper: IPreferenceHelper
) : ViewModel() {

    private val _artists = MutableLiveData<List<Artist>>()
    val artists: LiveData<List<Artist>> = _artists

    private val _currentArtist = MutableLiveData<Artist?>()
    val currentArtist: LiveData<Artist?> = _currentArtist

    private val _filteredArtists = MutableLiveData<List<Artist>>()
    val filteredArtists: LiveData<List<Artist>> = _filteredArtists

    private val _listArtistSong = MutableLiveData<List<Song>>()
    val listArtistSong: LiveData<List<Song>> = _listArtistSong

    private val _filteredListArtistSong = MutableLiveData<List<Song>>()
    val filteredListArtistSong: LiveData<List<Song>> = _filteredListArtistSong

    fun getPositionArtist(): Int { return helper.getPositionArtist() }
    fun savePositionArtist(position: Int) { helper.savePositionArtist(position) }

    //загрузка артистов
    fun loadArtists() {
        viewModelScope.launch {
            try {
                val artistsWithSongsAndAlbums = artistsRepository.getAllArtistsWithSongsAndAlbums()
                Log.d(TAG, " * ArtistsViewModel loadArtists artistsWithSongsAndAlbums size = ${artistsWithSongsAndAlbums.size}")
                _artists.value = artistsWithSongsAndAlbums
                _filteredArtists.value = artistsWithSongsAndAlbums  //todo убрать?
            } catch (e: Exception) {
                Log.d(TAG, " ArtistsViewModel loadArtists Error loading Artists ${e.message}")
            }
        }
    }

    //получение артистов из песен и загрузка артистов
    fun syncArtist() {
        viewModelScope.launch {
            try {
                Log.d(TAG, " * ArtistsViewModel syncArtist artistsRepository.syncArtistsFromMediaFiles()")
                artistsRepository.syncArtistsFromMediaFiles()
                loadArtists() // Перезагружаем после синхронизации
            } catch (e: Exception) {
                Log.d(TAG, " ArtistsViewModel syncArtist Error loading Artist error = ${e.message}")
            }
        }
    }

    fun getSongsByArtist(artistId: Long) {
        viewModelScope.launch {
            val listArtistSong: List<Song> = artistsRepository.getArtistSongList(artistId)
            Log.d(TAG, " * ArtistsViewModel getSongsByArtist listArtistSong size = ${listArtistSong.size}")
            _listArtistSong.value = listArtistSong
            _filteredListArtistSong.value = listArtistSong
        }
    }

    fun loadArtistSongsForAdding(artistId: Long) {
        viewModelScope.launch {
            _listArtistSong.value = artistsRepository.getArtistSongList(artistId)
        }
    }

    /** Обложку уровня (SharedViewModel.coverImageUriLevel) вызывающий код выставляет отдельно. */
    fun setCurrentArtist(artist: Artist) {
        _currentArtist.value = artist  //todo не нужно?
    }

    /** Последний поисковый запрос вкладки. Нужен, чтобы после смены вкладки поле поиска
     *  показывало тот же запрос, по которому отфильтрован список. */
    var lastSearchQuery: String = ""
        private set

    internal fun filterArtists(query: String) {
        lastSearchQuery = query
        val originalArtistList: MutableList<Artist> = (artists.value ?: listOf()).toMutableList()
        val filteredArtistList: MutableList<Artist> = (filteredArtists.value ?: listOf()).toMutableList()
        filteredArtistList.clear()
        if (query.isEmpty()) {
            filteredArtistList.addAll(originalArtistList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (artist in originalArtistList) {
                if (artist.name.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredArtistList.add(artist)
                }
            }
        }
        _filteredArtists.value = filteredArtistList
    }

    internal fun filterArtistSongs(query: String) {
        val originalArtistSongList = (listArtistSong.value ?: listOf()).toMutableList()
        val filteredArtistSongList: MutableList<Song> = (filteredListArtistSong.value ?: listOf()).toMutableList()
        filteredArtistSongList.clear()
        if (query.isEmpty()) {
            filteredArtistSongList.addAll(originalArtistSongList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (artistSong in originalArtistSongList) {
                if (artistSong.title.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    artistSong.artist.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredArtistSongList.add(artistSong)
                }
            }
        }
        _filteredListArtistSong.value = filteredArtistSongList
    }

    fun updateArtistsOrder(orderedArtist: List<Artist>) {
        viewModelScope.launch {
            val artists: List<ArtistFile> = artistsRepository.getAllArtistsSongs()
            val updatedArtists: MutableList<ArtistFile> = mutableListOf<ArtistFile>()

            orderedArtist.forEachIndexed { index, orderedArtist ->
                val artist1 = artists.find { it.artistId == orderedArtist.artistId }
                artist1?.let {
                    updatedArtists.add(it.copy(sortOrder = index))
                }
            }
            val artistArtistIds = artists.map { it.artistId } //это хэши имени

            Log.d(TAG, "--#**# ArtistsViewModel updateArtistsOrder orderedArtist artistId = ${orderedArtist.map { it.artistId }}")
            Log.d(TAG, "--#**# ArtistsViewModel updateArtistsOrder artistArtistIds = $artistArtistIds")
            Log.d(TAG, "--#**# ArtistsViewModel updateArtistsOrder updatedArtists size = ${updatedArtists.size} " +
                    "updatedArtists sortOrder = ${updatedArtists.map { it.sortOrder }}")

            artistsRepository.updateArtistsOrder(updatedArtists)

            // Обновляем LiveData
            _artists.value = orderedArtist
            _filteredArtists.value = orderedArtist
        }
    }

    /**
     * Сохраняет обложку исполнителя. Выбранную картинку сначала копируем в память приложения:
     * временный content-URI позже может перестать читаться, и обложка пропадала бы.
     */
    fun saveCoverArtistToDatabase(context: Context, uri: Uri, artistId: Long) {
        Log.d(TAG, "6--- ArtistsViewModel saveCoverArtistToDatabase uri = $uri artistId = $artistId")
        val appContext = context.applicationContext
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                copyCoverToAppStorage(appContext, uri, "artist", artistId)
            }
            // если скопировать не удалось, сохраняем как раньше - сам URI
            artistsRepository.updateArtistArtUri(artistId, savedPath ?: uri.toString())
            loadArtists() // Обновляем список артистов
        }
    }

    fun updateArtistArtUri(context: Context, artist: Artist) {
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveDefaultCoverToCache(context)
            val newArtUri = newArtUriString?.toUri()
            artist.artworkUri = newArtUri
            // Обновляем артиста
            _artists.value = _artists.value?.map { a ->
                if (a.id == artist.id) a.copy(artworkUri = newArtUri) else a
            }
            // Обновляем артиста
            _filteredArtists.value = _filteredArtists.value?.map { fa ->
                if (fa.id == artist.id) fa.copy(artworkUri = newArtUri) else fa
            }

            // Обновляем текущего артиста если нужно
            _currentArtist.value?.let { current ->
                if (current.id == artist.id) {
                    _currentArtist.value = current.copy(artworkUri = newArtUri)
                }
            }
            Log.d(TAG, " **??** ArtistsViewModel updateArtistArtUri newArtUri $newArtUri")
            try {
                artistsRepository.updateArtistArtUriById(artist.id, newArtUriString)
                Log.d(TAG, "✅ArtistsViewModel updateArtistArtUri Обновлён artUri для артиста с ${artist.name}  id =  ${artist.id} }")
            } catch (e: Exception) {
                Log.e(TAG, "❌ArtistsViewModel updateArtistArtUri Ошибка обновления artUri: ${e.message}")
            }
        }
    }

    companion object {
        const val TAG = "33333"
    }
}
