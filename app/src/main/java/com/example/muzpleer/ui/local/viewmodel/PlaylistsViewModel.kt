package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.model.Song
import com.example.muzpleer.repository.PlaylistRepository
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.UserMessenger
import com.example.muzpleer.util.isExistingLocalFile
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Всё, что относится к плейлистам: список плейлистов, текущий плейлист и его песни,
 * создание/переименование/удаление, добавление и удаление песен, порядок, обложка.
 * Раньше это жило в SharedViewModel.
 */
class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val helper: IPreferenceHelper,
    private val messenger: UserMessenger
) : ViewModel() {

    private val _playlists = MutableLiveData<List<Playlist>>() //все плейлисты в списке плейлистов,
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _filteredPlaylists = MutableLiveData<List<Playlist>>() //все плейлисты в списке плейлистов, отфильтрованные поиском
    val filteredPlaylists: LiveData<List<Playlist>> = _filteredPlaylists

    private val _currentPlaylist = MutableLiveData<Playlist?>()  //конкретный плейлист
    val currentPlaylist: LiveData<Playlist?> = _currentPlaylist

    private val _currentPlaylistSongs = MutableLiveData<List<Song>?>()  //список песен конкретного плейлиста
    val currentPlaylistSongs: LiveData<List<Song>?> = _currentPlaylistSongs

    private val _currentFilteredPlaylistSongs = MutableLiveData<List<Song>?>()  //отфильтрованный поиском список песен конкретного плейлиста
    val currentFilteredPlaylistSongs: LiveData<List<Song>?> = _currentFilteredPlaylistSongs

    internal fun filterPlaylists(query: String) {
        //данные ещё не загружены: фильтровать нечего, пустой результат принимался за "список пуст"
        if (playlists.value == null) return
        _filteredPlaylists.value = (playlists.value ?: listOf()).filterByQuery(query, { it.playlistName })
    }

    internal fun filterPlaylistSongs(query: String) {
        _currentFilteredPlaylistSongs.value = (currentPlaylistSongs.value ?: listOf()).filterSongsByQuery(query)
    }

    fun getPositionPlaylist(): Int{  return helper.getPositionPlaylist() }

    fun savePositionPlaylist(position: Int){helper.savePositionPlaylist(position)}

    //грузим все плейлисты для отображения списком
    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val playlists = playlistRepository.getAllPlaylists()
                Log.d(TAG,"#**# PlaylistsViewModel loadPlaylists playlists size = ${playlists.size}")
                _playlists.value = playlists
                _filteredPlaylists.value = playlists
            } catch (e: Exception) {
                Log.d(TAG,"#**# PlaylistsViewModel loadPlaylists Error loading playlists error = ${e.message}")
            }
        }
    }

    fun createPlaylist(name: String, onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val playlistId = playlistRepository.createPlaylist(name)
                loadPlaylists() // Перезагружаем список
                onSuccess(playlistId)
            } catch (e: Exception) {
                Log.d(TAG,"PlaylistsViewModel createPlaylist Error creating playlists error = ${e.message}")
            }
        }
    }

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            try {
                playlistRepository.addSongsToPlaylist(playlistId, songIds)
                loadPlaylists() // Перезагружаем список
            } catch (e: Exception) {
                Log.d(TAG,"PlaylistsViewModel addToPlaylist Error adding songs to playlist error = ${e.message}")
            }
        }
    }

    fun loadCurrentPlaylistForSongs(playlistId: Long) {
        viewModelScope.launch {
            try {
                val playlist = playlistRepository.getPlaylistWithSongs(playlistId)
                _currentPlaylist.value = playlist
                _currentPlaylistSongs.value =playlist?.playlistSongs
                _currentFilteredPlaylistSongs.value = playlist?.playlistSongs
            } catch (e: Exception) {
                Log.d(TAG,"PlaylistsViewModel loadPlaylist Error loading playlist error = ${e.message}")
            }
        }
    }

    /**
     * Находит плейлист по id и делает его текущим. Обложку уровня (SharedViewModel.coverImageUriLevel)
     * выставляет вызывающий код в [onLoaded].
     */
    fun setCurrentPlaylistByPlaylistId(playlistId: Long, onLoaded: (Playlist?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val playlist = playlistRepository.getPlaylistWithSongs(playlistId)
                _currentPlaylist.value = playlist
                onLoaded(playlist)
            } catch (e: Exception) {
                Log.d(TAG, "PlaylistsViewModel getPlaylistByPlaylistId Error loading playlist error = ${e.message}")
            }
        }
    }

    fun getCurrentPlaylist():Playlist?{
        return currentPlaylist.value
    }

    fun updatePlaylistOrder(playlists: List<Playlist>) {
        viewModelScope.launch {
            try {
                playlistRepository.updatePlaylistOrder(playlists)
                loadPlaylists()
            } catch (e: Exception) {
                Log.d(TAG,"PlaylistsViewModel updatePlaylistOrder Error updating playlist order error = ${e.message}")
            }
        }
    }

    /** Вызывается после удаления плейлиста: MainActivity по нему останавливает музыку из удалённого плейлиста. */
    var onPlaylistDeleted: ((Long) -> Unit)? = null

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(playlistId)
                loadPlaylists()
                onPlaylistDeleted?.invoke(playlistId)
            } catch (e: Exception) {
                Log.d(TAG,"PlaylistsViewModel deletePlaylist Error deleting playlist error = ${e.message}")
            }
        }
    }

    fun addSongsToPlaylistWhithSongs(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch {
            playlistRepository.addSongsToPlaylistWithSongs(playlistId, songs)
            //перезагружаем плейлисты для обновления количества песен в каждом плейлисте
            loadPlaylists()
        }
    }

    fun loadPlaylistSongsForAdding(playlistId:Long){
        viewModelScope.launch {
            _currentPlaylistSongs.value = playlistRepository.getPlaylistSongsOnly(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
            // Автоматически обновим список через LiveData
            loadPlaylists()
        }
    }

    fun setCurrentPlaylist(playlist: Playlist){
        _currentPlaylist.value = playlist
    }

    // Удаление песни из плейлиста
    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            try {
                val success = playlistRepository.removeSongFromPlaylist(playlistId, songId)
                if (success) {
                    // Обновляем текущий плейлист если он открыт
                    if (currentPlaylist.value?.id == playlistId) {
                        loadCurrentPlaylistForSongs(playlistId)
                    }

                    // Обновляем список всех плейлистов (для счетчика песен)
                    loadPlaylists()

                    // Показываем уведомление
                    messenger.show("Песня удалена из плейлиста")
                } else {
                    messenger.show("Ошибка удаления песни")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing song from playlist: ${e.message}", e)
                messenger.show("Ошибка: ${e.message}")
            }
        }
    }

    // Удаление нескольких песен из плейлиста
    fun removeSongsFromPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            try {
                val removedCount = playlistRepository.removeSongsFromPlaylist(playlistId, songIds)
                if (removedCount > 0) {
                    // Обновляем текущий плейлист если он открыт
                    if (currentPlaylist.value?.id == playlistId) {
                        loadCurrentPlaylistForSongs(playlistId)
                    }

                    // Обновляем список всех плейлистов
                    loadPlaylists()

                    // Показываем уведомление
                    messenger.show("Удалено песен: $removedCount")
                } else {
                    messenger.show("Не удалось удалить песни")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing songs from playlist: ${e.message}", e)
                messenger.show("Ошибка: ${e.message}")
            }
        }
    }

    // Получение только песен плейлиста (без загрузки всего объекта Playlist)
    fun getPlaylistSongsOnly(playlistId: Long, callback: (List<Song>) -> Unit) {
        viewModelScope.launch {
            try {
                val songs = playlistRepository.getPlaylistSongsOnly(playlistId)
                withContext(Dispatchers.Main) {
                    callback(songs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting playlist songs: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    fun deleteSongFromPlaylist(songId: Long) {
        viewModelScope.launch {
            try {
                // Получаем текущий плейлист
                val playlist = getCurrentPlaylist()?: return@launch
                val playlistId =playlist.id
                // Удаляем песню из плейлиста в БД
                playlistRepository.deleteSongFromPlaylist(playlistId, songId)

                // Обновляем локальные данные
                val updatedSongs = currentFilteredPlaylistSongs.value?.filter { it.id != songId }
                _currentFilteredPlaylistSongs.value = updatedSongs

                // Обновляем текущий плейлист  (для обновления счетчика)
                val newCurrentPlaylist = _currentPlaylist.value?.copy(
                    playlistSongs = updatedSongs ?: listOf())
                _currentPlaylist.value = newCurrentPlaylist

                _currentPlaylistSongs.value =newCurrentPlaylist?.playlistSongs

                //Все плейлисты, чтобы при возврате на списки плейлистов кол-во песен отображалось правильно
                val playlists = playlistRepository.getAllPlaylists()
                _playlists.value = playlists
                _filteredPlaylists.value = playlists

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при удалении песни из плейлиста", e)
            }
        }
    }

    fun addSongToPlaylist(songId: Long, playlistId: Long) {
        viewModelScope.launch {
            try {
                // Проверяем, нет ли уже этой песни в плейлисте
                val isAlreadyInPlaylist = playlistRepository.isSongInPlaylist(songId, playlistId)

                if (isAlreadyInPlaylist) {
                    // Можно показать сообщение, что песня уже есть
                    Log.d(TAG, "Песня уже есть в плейлисте")
                    return@launch
                }

                // Добавляем песню в плейлист
                playlistRepository.addSongToPlaylist(songId, playlistId)

                // Обновляем список плейлистов
                refreshPlaylists()

                // Если сейчас открыт этот плейлист, обновляем его песни
                val currentPlaylist = _currentPlaylist.value
                if (currentPlaylist?.id == playlistId) {
                    loadCurrentPlaylistForSongs(playlistId)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при добавлении песни в плейлист", e)
            }
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            val playlists = playlistRepository.getAllPlaylists()
            _playlists.value = playlists
            _filteredPlaylists.value = playlists

            // Также обновляем текущий плейлист, если он открыт
            _currentPlaylist.value?.let { currentPlaylist ->
                val updatedPlaylist = playlists.find { it.id == currentPlaylist.id }
                updatedPlaylist?.let { _currentPlaylist.value = it }
            }
        }
    }

    // запомнить новый порядок перен в плейлисте
    fun updatePlaylistSongsOrder(orderedSongs: List<Song>, callback: (Boolean) -> Unit ) {
        viewModelScope.launch {
            try {
                val playlistId = getCurrentPlaylist()?.id ?: return@launch
                playlistRepository.savePlaylistSongsOrder(playlistId, orderedSongs){
                    // Обновляем локальные данные
                    _currentFilteredPlaylistSongs.value = orderedSongs
                    _currentPlaylistSongs.value = orderedSongs

                    // Обновляем текущий плейлист
                    _currentPlaylist.value = _currentPlaylist.value?.copy(
                        playlistSongs = orderedSongs
                    )
                    Log.d(TAG, "--**-- PlaylistsViewModel updatePlaylistSongsOrder Порядок песен сохранен")
                }
                callback(true)
            } catch (e: Exception) {
                Log.e(TAG, "❌--**-- PlaylistsViewModel updatePlaylistSongsOrder Ошибка при сохранении порядка песен", e)
                callback(false)
            }
        }
    }

    fun updatePlaylistArtUri(context: Context,  playlist: Playlist){
        viewModelScope.launch {
            //Сюда попадаем, когда адаптеру не удалось загрузить обложку из своего (возможно, устаревшего) списка.
            //Если в базе лежит рабочий файл обложки, затирать его обложкой по умолчанию нельзя:
            //только обновляем объект в списках.
            val storedCover = playlistRepository.getPlaylistArtUriById(playlist.id)
            val keepStored = isExistingLocalFile(storedCover)
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = if (keepStored) storedCover else saveDefaultCoverToCache(context)
            val newArtUri = newArtUriString?.toUri()
            playlist.playlistArtUri = newArtUri
            // Обновляем плейлист
            _playlists.value = _playlists.value?.map {p->
                if (p.id == playlist.id) p.copy(playlistArtUri = newArtUri) else p
            }
            // Обновляем фильтрованный плейлист
            _filteredPlaylists.value = _filteredPlaylists.value?. map{fp->
                if (fp.id == playlist.id) fp.copy(playlistArtUri = newArtUri) else fp
            }

            // Обновляем текущий плейлист если нужно
            _currentPlaylist.value?.let { current ->
                if (current.id == playlist.id) {
                    _currentPlaylist.value = current.copy(playlistArtUri = newArtUri)
                }
            }
            Log.d(TAG, " **??** PlaylistsViewModel updatePlaylistArtUri newArtUri $newArtUri")
            try {
                if (!keepStored) playlistRepository.updatePlaylistArtUri(playlist.id, newArtUriString)
                Log.d(TAG, "✅PlaylistsViewModel updatePlaylistArtUri Обновлён artUri для плейлиста = ${playlist.playlistName}  id = ${playlist.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌PlaylistsViewModel updatePlaylistArtUri Ошибка обновления для плейлиста = ${playlist.playlistName}  artUri: ${e.message}")
            }
        }
    }

    fun getFilteredPlaylists() :List<Playlist>{
       return filteredPlaylists.value
    }

    /**
     * Сохраняет обложку плейлиста. Выбранную картинку сначала копируем в память приложения:
     * временный content-URI позже может перестать читаться, и обложка пропадала бы.
     */
    fun saveCoverPlaylistToDatabase(context: Context, uri: Uri, playlistId: Long) {
        Log.d(TAG, "6--- PlaylistsViewModel saveCoverPlaylistToDatabase uri = $uri playlistId = $playlistId")
        viewModelScope.launch {
            val savedPath = copyCoverOrKeepUri(context, uri, "playlist", playlistId)
            playlistRepository.updatePlaylistArtUri(playlistId, savedPath)
            loadPlaylists() // Обновляем список
        }
    }

    companion object {
        const val TAG = "33333"
    }
}
