package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzpleer.model.Folder
import com.example.muzpleer.model.Song
import com.example.muzpleer.repository.FolderRepository
import com.example.muzpleer.room.entity.FolderFile
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.launch

/**
 * Всё, что относится к вкладке "Папки" и списку песен папки.
 * Раньше это жило в SharedViewModel.
 */
class FoldersViewModel(
    private val folderRepository: FolderRepository,
    private val helper: IPreferenceHelper
) : ViewModel() {

    private val _folders = MutableLiveData<List<Folder>>()
    val folders: LiveData<List<Folder>> = _folders

    private val _currentFolder = MutableLiveData<Folder>()
    val currentFolder: LiveData<Folder> = _currentFolder

    private val _filteredFolders = MutableLiveData<List<Folder>>()
    val filteredFolders: LiveData<List<Folder>> = _filteredFolders

    private val _listFolderSong = MutableLiveData<List<Song>>()
    val listFolderSong: LiveData<List<Song>> = _listFolderSong

    private val _filteredListFolderSong = MutableLiveData<List<Song>>()
    val filteredListFolderSong: LiveData<List<Song>> = _filteredListFolderSong

    fun getPositionFolder(): Int { return helper.getPositionFolder() }
    fun savePositionFolder(position: Int) { helper.savePositionFolder(position) }

    fun loadFolders() {
        viewModelScope.launch {
            try {
                val foldersWithSongs = folderRepository.getAllFoldersWithSongs()
                _folders.value = foldersWithSongs
                _filteredFolders.value = foldersWithSongs //todo убрать?
            } catch (e: Exception) {
                Log.d(TAG, " FoldersViewModel loadFolders Error loading Folders ${e.message}")
            }
        }
    }

    fun syncFolders() {
        viewModelScope.launch {
            try {
                folderRepository.syncFoldersFromMediaFiles()
                loadFolders() // Перезагружаем после синхронизации
            } catch (e: Exception) {
                Log.e("FolderViewModel", "Error syncing folders", e)
            }
        }
    }

    fun getSongsByFolder(folderPath: String) {
        viewModelScope.launch {
            val listFolderSong: List<Song> = folderRepository.getFolderSongList(folderPath)
            Log.d(TAG, " * FoldersViewModel getSongsByFolder listFolderSong size = ${listFolderSong.size}")
            _listFolderSong.value = listFolderSong
            _filteredListFolderSong.value = listFolderSong
        }
    }

    fun loadFolderSongsForAdding(folderPath: String) {
        viewModelScope.launch {
            _listFolderSong.value = folderRepository.getFolderSongList(folderPath)
        }
    }

    /**
     * Находит папку по пути и делает её текущей. Обложку уровня (SharedViewModel.coverImageUriLevel)
     * выставляет вызывающий код в [onLoaded], когда папка найдена.
     */
    fun setCurrentFolderByFolderPath(folderPath: String, onLoaded: (Folder) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val currentFolder: Folder = folderRepository.getFolderByPath(folderPath)
                Log.d(TAG, "@ FoldersViewModel setCurrentFolderByFolderPath " +
                        " currentFolder name =  = ${currentFolder.name}")
                _currentFolder.value = currentFolder
                onLoaded(currentFolder)
            } catch (e: Exception) {
                Log.d(TAG, "@ FoldersViewModel setCurrentFolderByFolderPath  error = ${e.message}")
            }
        }
    }

    internal fun filterFolders(query: String) {
        //данные ещё не загружены: фильтровать нечего, пустой результат принимался за "список пуст"
        if (folders.value == null) return
        _filteredFolders.value = (folders.value ?: listOf()).filterByQuery(query, { it.name })
    }

    internal fun filterFolderSongs(query: String) {
        _filteredListFolderSong.value = (listFolderSong.value ?: listOf()).filterSongsByQuery(query)
    }

    fun updateFoldersOrder(orderedFolders: List<Folder>) {
        viewModelScope.launch {
            val folders: List<FolderFile> = folderRepository.getAllFolderSongs()
            val updatedFolders: MutableList<FolderFile> = mutableListOf<FolderFile>()

            orderedFolders.forEachIndexed { index, folder ->
                val folder1 = folders.find { it.folderPath == folder.path }
                folder1?.let {
                    updatedFolders.add(it.copy(sortOrder = index))
                }
            }
            Log.d(TAG, "--#**# FoldersViewModel updateFoldersOrder orderedFolders size = ${orderedFolders.size} " +
                    "orderedFolders ids = ${orderedFolders.map { it.id }} orderedFolders paths= ${orderedFolders.map { it.path }}")
            Log.d(TAG, "--#**# FoldersViewModel updateFoldersOrder updatedFolders size = ${updatedFolders.size} " +
                    "updatedFolders sortOrder = ${updatedFolders.map { it.sortOrder }}")

            folderRepository.updateFoldersOrder(updatedFolders)

            // Обновляем LiveData
            _folders.value = orderedFolders
            _filteredFolders.value = orderedFolders
        }
    }

    /**
     * Сохраняет обложку папки. Выбранную картинку сначала копируем в память приложения:
     * временный content-URI позже может перестать читаться, и обложка пропадала бы.
     */
    fun saveCoverFolderToDatabase(context: Context, uri: Uri, folderId: Long) {
        Log.d(TAG, "6--- FoldersViewModel saveCoverFolderToDatabase uri = $uri folderId = $folderId")
        viewModelScope.launch {
            val savedPath = copyCoverOrKeepUri(context, uri, "folder", folderId)
            folderRepository.updateFolderArtUri(folderId, savedPath)
            loadFolders() // Обновляем список папок
            //проверка
            val folderFile = folderRepository.getFolderFileById(folderId)
            Log.d(TAG, "6-1--- проверка FoldersViewModel saveCoverFolderToDatabase savedPath = $savedPath " +
                    "folderFile folderName = ${folderFile?.folderName}  coverPath =${folderFile?.coverPath}")
        }
    }

    fun updateFolderArtUri(context: Context, folder: Folder) {
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveDefaultCoverToCache(context)
            val newArtUri = newArtUriString?.toUri()
            folder.artworkUri = newArtUri
            // Обновляем папку
            _folders.value = _folders.value?.map { f ->
                if (f.id == folder.id) f.copy(artworkUri = newArtUri) else f
            }
            // Обновляем папку
            _filteredFolders.value = _filteredFolders.value?.map { ff ->
                if (ff.id == folder.id) ff.copy(artworkUri = newArtUri) else ff
            }

            // Обновляем текущую папку если нужно
            _currentFolder.value?.let { current ->
                if (current.id == folder.id) {
                    _currentFolder.value = current.copy(artworkUri = newArtUri)
                }
            }
            Log.d(TAG, " **??** FoldersViewModel updateFolderArtUri newArtUri $newArtUri")
            try {
                folderRepository.updateFolderArtUri(folder.id, newArtUriString)
                Log.d(TAG, "✅FoldersViewModel updateFolderArtUri Обновлён artUri для папки  ${folder.name}  id = ${folder.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌FoldersViewModel updateFolderArtUri Ошибка обновления artUri для папки ${folder.name}: ${e.message}")
            }
        }
    }

    companion object {
        const val TAG = "33333"
    }
}
