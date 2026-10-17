package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzpleer.model.Album
import com.example.muzpleer.model.Song
import com.example.muzpleer.repository.AlbumRepository
import com.example.muzpleer.room.entity.AlbumFile
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.copyCoverToAppStorage
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Всё, что относится к вкладке "Альбомы" и списку песен альбома.
 * Раньше это жило в SharedViewModel.
 */
class AlbumsViewModel(
    private val albumRepository: AlbumRepository,
    private val helper: IPreferenceHelper
) : ViewModel() {

    private val _albums = MutableLiveData<List<Album>>()  //альбомы
    val albums: LiveData<List<Album>> = _albums

    private val _currentAlbum = MutableLiveData<Album?>() //текущий альбом
    val currentAlbum: LiveData<Album?> = _currentAlbum

    private val _filteredAlbums = MutableLiveData<List<Album>>()  //альбомы в ходе фильтрации при поиске
    val filteredAlbums: LiveData<List<Album>> = _filteredAlbums

    private val _listAlbumSong = MutableLiveData<List<Song>>()  //список песен конкретного альбома
    val listAlbumSong: LiveData<List<Song>> = _listAlbumSong

    private val _filteredListAlbumSong = MutableLiveData<List<Song>>()  //фильтрованный поиском список песен конкретного альбома
    val filteredListAlbumSong: LiveData<List<Song>> = _filteredListAlbumSong

    fun getPositionAlbum(): Int { return helper.getPositionAlbum() }
    fun savePositionAlbum(position: Int) { helper.savePositionAlbum(position) }

    //загрузка альбомов
    fun loadAlbums() {
        viewModelScope.launch {
            try {
                val albumsWithSongs = albumRepository.getAllAlbumsWithSongs()
                Log.d(TAG, " * AlbumsViewModel loadAlbums albumsWithSongs size = ${albumsWithSongs.size}")
                _albums.value = albumsWithSongs  //todo убрать?
                _filteredAlbums.value = albumsWithSongs
            } catch (e: Exception) {
                Log.d(TAG, " AlbumsViewModel loadAlbums Error loading albums ${e.message}")
            }
        }
    }

    //получение альбомов из песен и загрузка альбомов
    fun syncAlbums() {
        viewModelScope.launch {
            try {
                Log.d(TAG, " * AlbumsViewModel syncAlbums albumRepository.syncAlbumsFromMediaFiles()")
                albumRepository.syncAlbumsFromMediaFiles()
                loadAlbums() // Перезагружаем после синхронизации
            } catch (e: Exception) {
                Log.d(TAG, " AlbumsViewModel syncAlbums Error loading albums error = ${e.message}")
            }
        }
    }

    fun getSongsByAlbum(albumId: Long) {
        viewModelScope.launch {
            val listAlbumSong: List<Song> = albumRepository.getAlbumSongList(albumId)
            Log.d(TAG, " * AlbumsViewModel getSongsByAlbum listAlbumSong size = ${listAlbumSong.size}")
            _listAlbumSong.value = listAlbumSong
            _filteredListAlbumSong.value = listAlbumSong
        }
    }

    fun loadAlbumSongsForAdding(albumId: Long) {
        viewModelScope.launch {
            _listAlbumSong.value = albumRepository.getAlbumSongList(albumId)
        }
    }

    /** Обложку уровня (SharedViewModel.coverImageUriLevel) вызывающий код выставляет отдельно. */
    fun setCurrentAlbum(album: Album) {
        _currentAlbum.value = album  //todo не нужно?
    }

    internal fun filterAlbums(query: String) {
        //данные ещё не загружены: фильтровать нечего, пустой результат принимался за "список пуст"
        if (albums.value == null) return
        val originalAlbumList: MutableList<Album> = (albums.value ?: listOf()).toMutableList()
        val filteredAlbumList: MutableList<Album> = (filteredAlbums.value ?: listOf()).toMutableList()
        filteredAlbumList.clear()
        if (query.isEmpty()) {
            filteredAlbumList.addAll(originalAlbumList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (album in originalAlbumList) {
                if (album.title.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    album.artist.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredAlbumList.add(album)
                }
            }
        }
        _filteredAlbums.value = filteredAlbumList
    }

    fun updateAlbumsOrder(orderedAlbums: List<Album>) {
        viewModelScope.launch {
            val albums: List<AlbumFile> = albumRepository.getAllAlbumSongs() //альбомы из базы
            val updatedAlbums: MutableList<AlbumFile> = mutableListOf<AlbumFile>()

            orderedAlbums.forEachIndexed { index, album ->  //смотрим пришедшие альбомы
                //при совпадении id из базы и пришедших альбомов добавляем в список с обновлением индекса для сортировки
                val album1 = albums.find { it.albumId == album.albumId }
                album1?.let {
                    updatedAlbums.add(it.copy(sortOrder = index))
                }
            }
            Log.d(TAG, "--#**# AlbumsViewModel updateAlbumsOrder orderedAlbums size = ${orderedAlbums.size} " +
                    "orderedAlbums ids = ${orderedAlbums.map { it.albumId }} albums albumIds= ${albums.map { it.albumId }}")
            Log.d(TAG, "--#**# AlbumsViewModel updateAlbumsOrder updatedAlbums size = ${updatedAlbums.size} " +
                    "updatedAlbums sortOrder = ${updatedAlbums.map { it.sortOrder }}")

            albumRepository.updateAlbumsOrder(updatedAlbums)

            // Обновляем LiveData
            _albums.value = orderedAlbums
            _filteredAlbums.value = orderedAlbums
        }
    }

    /**
     * Сохраняет обложку альбома. Выбранную картинку сначала копируем в память приложения:
     * временный content-URI из picker позже перестаёт читаться, и обложка пропадала.
     * [albumId] здесь - значение, которое приходит из экрана замены обложки (id строки в базе).
     */
    fun saveCoverAlbumToDatabase(context: Context, uri: android.net.Uri, albumId: Long) {
        Log.d(TAG, "6--- AlbumsViewModel saveCoverAlbumToDatabase uri = $uri albumId = $albumId")
        val appContext = context.applicationContext
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                copyCoverToAppStorage(appContext, uri, "album", albumId)
            }
            // если скопировать не удалось, сохраняем как раньше - сам URI
            albumRepository.updateAlbumArtUri(albumId, savedPath ?: uri.toString())
            loadAlbums() // Обновляем список альбомов

            //проверка
            val albumFile = albumRepository.getAlbumById(albumId)
            Log.d(TAG, "6-1--- проверка AlbumsViewModel saveCoverAlbumToDatabase savedPath = $savedPath " +
                    " albumFile title = ${albumFile?.title} albumFile id = ${albumFile?.id} " +
                    "albumFile albumId = ${albumFile?.albumId} albumFile coverPath = ${albumFile?.coverPath}")
        }
    }

    fun updateAlbumArtUri(context: Context, album: Album) {
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveDefaultCoverToCache(context)
            val newArtUri = newArtUriString?.toUri()
            album.artworkUri = newArtUri
            // Обновляем альбом
            _albums.value = _albums.value?.map { a ->
                if (a.albumId == album.albumId) a.copy(artworkUri = newArtUri) else a
            }
            // Обновляем альбом
            _filteredAlbums.value = _filteredAlbums.value?.map { fa ->
                if (fa.albumId == album.albumId) fa.copy(artworkUri = newArtUri) else fa
            }

            // Обновляем текущий альбом если нужно
            _currentAlbum.value?.let { current ->
                if (current.albumId == album.albumId) {
                    _currentAlbum.value = current.copy(artworkUri = newArtUri)
                }
            }
            Log.d(TAG, " **??** AlbumsViewModel updateAlbumArtUri newArtUri $newArtUri")
            try {
                albumRepository.updateAlbumArtUriByAlbumId(album.albumId, newArtUriString)
                Log.d(TAG, "✅AlbumsViewModel updateAlbumArtUri Обновлён artUri для альбома с title = ${album.title}  id =  ${album.id} albumId = ${album.albumId}")
            } catch (e: Exception) {
                Log.e(TAG, "❌AlbumsViewModel updateAlbumArtUri Ошибка обновления artUri: ${e.message}")
            }
        }
    }

    internal fun filterAlbumSongs(query: String) {
        val originalAlbumSongList = (listAlbumSong.value ?: listOf()).toMutableList()
        val filteredAlbumSongList: MutableList<Song> = (filteredListAlbumSong.value ?: listOf()).toMutableList()
        filteredAlbumSongList.clear()
        if (query.isEmpty()) {
            filteredAlbumSongList.addAll(originalAlbumSongList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (albumSong in originalAlbumSongList) {
                if (albumSong.title.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    albumSong.artist.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredAlbumSongList.add(albumSong)
                }
            }
        }
        _filteredListAlbumSong.value = filteredAlbumSongList
    }

    companion object {
        const val TAG = "33333"
    }
}
