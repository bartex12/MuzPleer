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
import com.example.muzpleer.util.copyCoverToAppStorage
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
        val originalFolderList: MutableList<Folder> = (folders.value ?: listOf()).toMutableList()
        val filteredFolderList: MutableList<Folder> = (filteredFolders.value ?: listOf()).toMutableList()
        filteredFolderList.clear()
        if (query.isEmpty()) {
            filteredFolderList.addAll(originalFolderList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (folder in originalFolderList) {
                if (folder.name.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredFolderList.add(folder)
                }
            }
        }
        _filteredFolders.value = filteredFolderList
    }

    internal fun filterFolderSongs(query: String) {
        val originalFolderSongList: MutableList<Song> = (listFolderSong.value ?: listOf()).toMutableList()
        val filteredFolderSongList: MutableList<Song> = (filteredListFolderSong.value ?: listOf()).toMutableList()
        filteredFolderSongList.clear()
        if (query.isEmpty()) {
            filteredFolderSongList.addAll(originalFolderSongList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (folderSong in originalFolderSongList) {
                if (folderSong.title.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    folderSong.artist.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredFolderSongList.add(folderSong)
                }
            }
        }
        _filteredListFolderSong.value = filteredFolderSongList
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
        val appContext = context.applicationContext
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                copyCoverToAppStorage(appContext, uri, "folder", folderId)
            }
            // если скопировать не удалось, сохраняем как раньше - сам URI
            folderRepository.updateFolderArtUri(folderId, savedPath ?: uri.toString())
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
