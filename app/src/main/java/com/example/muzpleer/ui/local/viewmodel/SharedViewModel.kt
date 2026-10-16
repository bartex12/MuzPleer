package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.example.muzpleer.R
import com.example.muzpleer.di.App
import com.example.muzpleer.model.Album
import com.example.muzpleer.model.Artist
import com.example.muzpleer.model.Folder
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.model.Song
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.repository.AlbumRepository
import com.example.muzpleer.repository.ArtistRepository
import com.example.muzpleer.repository.FavoriteRepository
import com.example.muzpleer.repository.FolderRepository
import com.example.muzpleer.repository.MusicRepository
import com.example.muzpleer.repository.PlaylistRepository
import com.example.muzpleer.room.entity.AlbumFile
import com.example.muzpleer.room.entity.ArtistFile
import com.example.muzpleer.room.entity.FavoriteSong
import com.example.muzpleer.room.entity.FolderFile
import com.example.muzpleer.room.entity.SongFile
import com.example.muzpleer.room.utils.fromSongFileToSong
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.getSortedDataSong
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class SharedViewModel(
    var helper : IPreferenceHelper,
    private val repository: MusicRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _filteredSongs = MutableLiveData<List<Song>>()
    val filteredSongs: LiveData<List<Song>> = _filteredSongs

    private val _favoriteSongs = MutableLiveData<List<Song>>(listOf())
    val favoriteSongs: LiveData<List<Song>> = _favoriteSongs

    private var _filteredFavoriteSongs = MutableLiveData<List<Song>>()
    val filteredFavoriteSongs: LiveData<List<Song>> = _filteredFavoriteSongs

    private val _playlist = MutableLiveData<List<Song>>()
    val playlist: LiveData<List<Song>> = _playlist

    private val _songAndPlaylist = MutableLiveData<SongAndPlaylist>()
    val songAndPlaylist: LiveData<SongAndPlaylist> = _songAndPlaylist

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _coverImage = MutableLiveData<Bitmap?>()
    val coverImage: LiveData<Bitmap?> = _coverImage


    //выделенная строка в адаптере песни при щелчке по ней
    private val _selectedSongPosition = MutableLiveData<Int>(RecyclerView.NO_POSITION)
    val selectedSongPosition: LiveData<Int> = _selectedSongPosition

    private val _selectedAlbumPosition = MutableLiveData<Int>(RecyclerView.NO_POSITION)
    val selectedAlbumPosition: LiveData<Int> = _selectedAlbumPosition

    private val _selectedArtistPosition = MutableLiveData<Int>(RecyclerView.NO_POSITION)
    val selectedArtistPosition: LiveData<Int> = _selectedArtistPosition

    private val _selectedFolderPosition = MutableLiveData<Int>(RecyclerView.NO_POSITION)
    val selectedFolderPosition: LiveData<Int> = _selectedFolderPosition

    private val _selectedPlaylistPosition = MutableLiveData<Int>(RecyclerView.NO_POSITION)
    val selectedPlaylistPosition: LiveData<Int> = _selectedPlaylistPosition


    //индекс выбранной песни в отсортированном(!) списке песен
    private val _indexOfCurrentSong = MutableLiveData<Int>(-1)
    val indexOfCurrentSong: LiveData<Int> = _indexOfCurrentSong

    private val _selectedSong = MutableLiveData<Song?>()
    val selectedSong: LiveData<Song?> = _selectedSong

    private val _coverImageUri = MutableLiveData<Uri?>() //обложка песни
    val coverImageUri: LiveData<Uri?> = _coverImageUri

    private val _coverImageUriLevel = MutableLiveData<Uri?>()  //обложка плейлиста
    val coverImageUriLevel: LiveData<Uri?> = _coverImageUriLevel

    private val _coverImageUriFolder = MutableLiveData<Uri?>()  //обложка папки
    val coverImageUriFolder: LiveData<Uri?> = _coverImageUriFolder

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    //Число одновременно работающих загрузок. Нужен счётчик, а не просто true/false:
    //иначе загрузка, закончившаяся первой, гасит индикатор, пока остальные ещё идут
    private var activeLoads = 0

    private fun beginLoad() {
        activeLoads++
        _loading.value = true
    }

    private fun endLoad() {
        activeLoads = (activeLoads - 1).coerceAtLeast(0)
        _loading.value = activeLoads > 0
    }

    private val _coverPath = MutableLiveData<String>()
    val coverPath: LiveData<String> = _coverPath

    private val _appBarTitle = MutableLiveData<String>()  //отфильтрованный поиском список песен конкретного плейлиста
    val appBarTitle: LiveData<String> = _appBarTitle

    private val _listSelectedSong = MutableLiveData<List<Song>>()
    val listSelectedSong: LiveData<List<Song>> = _listSelectedSong

    private val _filteredListSelectedSong = MutableLiveData<List<Song>>()
    val filteredListSelectedSong: LiveData<List<Song>> = _filteredListSelectedSong

    fun setAppBarTitle(title:String){
        _appBarTitle.value = title
    }

     fun scanMedia(afterLoad:()->Unit) {
        viewModelScope.launch {
            beginLoad()
            initParamsSong(repository.loadMusicFromMemory())
            endLoad()
            afterLoad.invoke()
        }
    }

    /**
     * Полное обновление библиотеки: сначала сканируем телефон и собираем все треки в базе,
     * потом строим вкладки альбомов, исполнителей, папок и плейлистов.
     * [onScanned] - обновление вкладок, которые уже вынесены в свои ViewModel (передают вызывающие).
     * [afterRefresh] вызывается сразу после запуска построения вкладок, как и раньше
     * в местах, где эта цепочка вызывалась вручную.
     */
    fun refreshLibrary(onScanned: () -> Unit = {}, afterRefresh: () -> Unit = {}) {
        scanMedia {
            onScanned() //вкладки, вынесенные в отдельные ViewModel (сейчас альбомы, исполнители, папки и плейлисты)
            afterRefresh()
        }
    }

    private fun initParamsSong(songs:List<Song>){
        _songs.value = songs
        _filteredSongs.value  = _songs.value
        Log.d(TAG, "SharedViewModel initParamsSong songs.size = ${songs.size}")
    }

    fun setSongAndPlaylist(songAndPlaylist: SongAndPlaylist){
        _songAndPlaylist.value = songAndPlaylist
    }

    fun setPlaylist(songsList : List<Song>){
        _playlist.value = songsList
    }

    /** Вызывается из MainActivity, когда PlayerViewModel сообщает о смене трека. */
    fun onTrackChanged(track: Song) {
        //так не меняется автоматом- остаётся старый artUri из плейлиста
        _currentSong.value = track
        //считаем индекс выбранной песни в отсортированном списке песен,
        // чтобы при возврате на песни можно было перейти к этой песне по индексу
        val trackMediaUri = getNormalizedPath(track.mediaUri)
        val indexOfSong = getSortedDataSong(getSongs())
            .indexOfFirst { getNormalizedPath(it.mediaUri) == trackMediaUri }
        Log.d(TAG, " !@# !@# SharedViewModel onTrackChanged track title = ${track.title} " +
                "track mediaUri = ${track.mediaUri} trackMediaUri = $trackMediaUri")
        _indexOfCurrentSong.value = indexOfSong
        Log.d(TAG, " !@# !@# SharedViewModel onTrackChanged indexOfSong = $indexOfSong " +
                "  currentTrack  title = ${getCurrentSong()?.title}")
    }

    //функция для нормализации URI
    fun getNormalizedPath(uriString: String): String {
        return if (uriString.startsWith("file://")) {
            Uri.decode(uriString.substring(7))
        } else {
            uriString
        }
    }

    fun getAllSongs():List<Song>{
        return songs.value
    }

    fun setCurrentSong(song: Song) {
        Log.d(TAG, "SharedViewModel setCurrentSong song = $song")
        _currentSong.value = song
    }

    fun getCurrentSong() : Song?{
        //Log.d(TAG, "SharedViewModel getCurrentSong ")
         return currentSong.value
    }

    fun getFavoriteSongs():List<Song> {
        return favoriteSongs.value
    }

    companion object{
        const val TAG= "33333"
    }

    internal fun filterSongs(query: String) {
        val originalSongsList: MutableList<Song> =  (songs.value ?: listOf()).toMutableList()
        val filteredSongsList: MutableList<Song> = (filteredSongs.value ?: listOf()).toMutableList()
        filteredSongsList.clear()
        if (query.isEmpty()) {
            filteredSongsList.addAll(originalSongsList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (song in originalSongsList) {
                if (song.title.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    song.artist.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredSongsList.add(song)
                }
            }
        }
        _filteredSongs.value = filteredSongsList
    }

    internal fun filterFavoriteSongs(query: String) {
        val originalSongsList: MutableList<Song> =  (favoriteSongs.value ?: listOf()).toMutableList()
        val filteredSongsList: MutableList<Song> = (filteredFavoriteSongs.value ?: listOf()).toMutableList()
        filteredSongsList.clear()
        if (query.isEmpty()) {
            filteredSongsList.addAll(originalSongsList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (song in originalSongsList) {
                if (song.title.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    song.artist.lowercase(Locale.getDefault()).contains(searchQuery)) {
                    filteredSongsList.add(song)
                }
            }
        }
        _filteredFavoriteSongs.value = filteredSongsList
    }

    internal fun filterListSelectedSongs(query: String) {
        val originalListSelected: MutableList<Song> =  (listSelectedSong.value ?: listOf()).toMutableList()
        val filteredListSelected: MutableList<Song> = (filteredListSelectedSong.value ?: listOf()).toMutableList()
        filteredListSelected.clear()
        if (query.isEmpty()) {
            filteredListSelected.addAll(originalListSelected)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (selectedSong in originalListSelected) {
                if (selectedSong.title.lowercase(Locale.getDefault()).contains(searchQuery)||
                    selectedSong.artist.lowercase(Locale.getDefault()).contains(searchQuery))  {
                    filteredListSelected.add(selectedSong)
                }
            }
        }
        _filteredListSelectedSong.value = filteredListSelected
    }


    fun getSongs():List<Song> {
       return songs.value
    }

    fun getPlaylist():List<Song> {
        return playlist.value
    }

    fun getPositionSong(): Int{  return helper.getPositionSong() }
    fun savePositionSong(position: Int){helper.savePositionSong(position)}

    fun getTabsLocalPosition():Int = helper.getTabsLocalPosition()
    fun saveTabsLocalPosition(currentItem: Int) = helper.saveTabsLocalPosition(currentItem)

    fun setSelectedPosition(position: Int) { _selectedSongPosition.value = position }

    fun setSelectedAlbumPosition(position: Int) { _selectedAlbumPosition.value = position }
    fun getSelectedAlbumPosition():Int = selectedAlbumPosition.value?:0
    fun setSelectedArtistPosition(position: Int) { _selectedArtistPosition.value = position }
    fun setSelectedFolderPosition(position: Int) { _selectedFolderPosition.value = position }
    fun setSelectedPlaylistPosition(position: Int) { _selectedPlaylistPosition.value = position }

    fun setCurrentSongById(songId: Long) {
        songs.value?.find { it.id == songId }?.let { song ->
            _currentSong.value = song //передаём сохранённую песню из преференсис

            setSongAndPlaylist( SongAndPlaylist( //передаём плейлист и текущую песню
                song = song,  //текущая песня
                playlist =getSortedDataSong(getSongs()) //текущий плейлист
            ))
        }
    }
    fun getSongAndPlaylist(): SongAndPlaylist{
        return songAndPlaylist.value
    }

    fun getIndexOfCurrentSong():Int{
        return indexOfCurrentSong.value
    }

    fun getIndexOfSavedSong():Int{
        return helper.getIndexOfCurrentSong()
    }

    fun getPositionFavoriteSong(): Int{  return helper.getPositionFavoriteSong() }
    fun savePositionFavoriteSong(position: Int){helper.savePositionFavoriteSong(position)}

    fun loadFavoriteSongs() {
        viewModelScope.launch {
            beginLoad()
            try {
                val songs = favoriteRepository.getOrderedFavorites()
                //val songs = favoriteRepository.getAllFavorites()
                _favoriteSongs.value = songs
                _filteredFavoriteSongs.value = songs
            } catch (e: Exception) {
                Log.d(TAG, "***SharedViewModel loadFavoriteSongs error = ${e.message}")
            } finally {
                endLoad()
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

    fun setSelectedSong(song: Song) {
        _selectedSong.value = song
        if(song.artUri == null){
           val uri = getDefaultCoverUri()
            updateCoverImage(uri)
            Log.d(TAG, "***SharedViewModel setSelectedSong song.artUri == null DefaultCoverUri = $uri")
        }else{
            val uri = (song.artUri!!).toUri()
            //val uri = "content://com.android.providers.media.documents/document/image%3A135257".toUri()
            updateCoverImage(uri)
            Log.d(TAG, "***SharedViewModel setSelectedSong song.artUri = $uri song = ${song.title}")
        }
    }

    fun getSelectedSong():Song? {
        return selectedSong.value
    }

    fun updateCoverImage(uri: Uri) {
        Log.d(TAG, "***SharedViewModel updateCoverImage  uri= $uri ")
        _coverImageUri.value = uri
    }

    /** Выставляет обложку уровня (альбом/исполнитель/папка/плейлист), в том числе пустую. */
    fun setCoverImageUriLevel(uri: Uri?) {
        _coverImageUriLevel.value = uri
    }

    fun updateCoverImageLevel(uri: Uri) {
        Log.d(TAG, "***SharedViewModel updateCoverImageLevel  uri= $uri ")
        _coverImageUriLevel.value = uri
    }

    fun getCoverImageUri():Uri? {
        return coverImageUri.value
    }

    fun getCoverImageUriLevel():Uri? {
        return coverImageUriLevel.value
    }

    //для песен
    fun updateCoverImageAndSave(uri: Uri) {
        Log.d(TAG, "2*** SharedViewModel updateCoverImageAndSave uri = $uri")
        val currentSong = _selectedSong.value ?: return
        // Проверяем, не тот же ли это URI
        if (currentSong.artUri == uri.toString()) {
            Log.d(TAG, "  --##-- SharedViewModel updateCoverImageAndSave Same URI, skipping update")
            return // Не обновляем, если URI не изменился
        }
        _coverImageUri.value = uri  //обновляем на экране
        saveCoverToDatabase(uri)  //обновляем в базе
    }

    fun getDefaultCoverUri(): Uri {
        return "android.resource://${(App.instance).packageName}/${R.drawable.muz_player3}".toUri()
    }

    fun restoreDefaultCover() {
        _selectedSong.value?.let { song ->
            // Восстанавливаем обложку по умолчанию
            val defaultUri=getDefaultCoverUri()
            _coverImageUri.value=defaultUri
            song.artUri=defaultUri.toString()
        }
    }

    fun saveCoverToDatabase(uri: Uri) {
        viewModelScope.launch {
            _selectedSong.value?.let { selectedSong ->
                Log.d(TAG, "110*** ✅SharedViewModel saveCoverToDatabase uri = $uri")

                val coverPath =copyAlbumArtToCache( App.instance, uri)
                Log.d(TAG, "111*** ✅SharedViewModel saveCoverToDatabase coverPath = $coverPath  uri = $uri")

                //записываем путь к файлу обложки в базу
                coverPath?. let{path->
                    repository.updateCoverPath(selectedSong.id, path)
                }?: repository.updateCoverPath(selectedSong.id, uri.toString())

                _coverImageUri.value = coverPath?.toUri() //повторно? перезаписываем _coverImageUri

                // Обновляем песню в основном списке песен - нужны оба - _songs и _filteredSongs
                _songs.value = _songs.value?.map {s->
                    if (s.id == selectedSong.id) s.copy(artUri = coverPath) else s
                }
                _filteredSongs.value = _filteredSongs.value?. map{filteredSong->
                    if (filteredSong.id == selectedSong.id) filteredSong.copy(artUri = coverPath) else filteredSong
                }
                // Обновляем выбранную песню
                _selectedSong.value = selectedSong.copy(artUri = coverPath)

                // Обновляем текущую песню если нужно
                _currentSong.value?.let { current ->
                    if (current.id == selectedSong.id) {
                        _currentSong.value = current.copy(artUri = coverPath)
                    }
                }
            }
        }
    }

    fun copyAlbumArtToCache(context: Context, sourceUri: Uri): String? {
        try {
            // 1. Создаем директорию album_art в кэше, если её нет
            val cacheDir = File(context.cacheDir, "album_art")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            // 2. Генерируем уникальное имя файла на основе хэш-кода пути URI
            val fileName = "embedded_${sourceUri.toString().hashCode()}.jpg"
            val destinationFile = File(cacheDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                // Убеждаемся что файл создан
                if (!destinationFile.exists() && !destinationFile.createNewFile()) {
                    throw IOException("Failed to create cover file")
                }

                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                }
            }

            // 4. Возвращаем абсолютный путь для сохранения в БД
            val absolutePath = destinationFile.absolutePath
            Log.d(TAG, "SharedViewModel copyAlbumArtToCache Файл успешно сохранен в кэш: $absolutePath")
            return absolutePath

        } catch (e: Exception) {
            Log.e(TAG, " ❌SharedViewModel copyAlbumArtToCache Ошибка при копировании обложки в кэш", e)
            return null
        }
    }

    fun updateSongInfo(
        songId: Long,
        title: String,
        artist: String,
        album: String?,
        author: String?,
        genre: String?,
        year: Int?
    ) {
        viewModelScope.launch {
            try {
                // Обновляем в базе данных
                repository.updateSongInfo(songId, title, artist, album, author, genre, year)

                // Обновляем в текущих данных
                _songs.value = _songs.value?.map { song ->
                    if (song.id == songId) {
                        song.copy(
                            id = song.id,
                            title = title,
                            artist = artist,
                            artistId = song.artistId,
                            duration = song.duration,
                            mediaUri = song.mediaUri,
                            artUri = song.artUri,
                            isLocal = song.isLocal,
                            albumName = album,
                            albumId = song.albumId,
                            folderPath = song.folderPath,
                            author = author,
                            genre = genre,
                            year = year
                        )
                    } else {
                        song
                    }
                }

                _filteredSongs.value = _filteredSongs.value?. map{filteredSong->
                    if (filteredSong.id == songId) filteredSong.copy(
                        id = filteredSong.id,
                        title = title,
                        artist = artist,
                        artistId = filteredSong.artistId,
                        duration = filteredSong.duration,
                        mediaUri = filteredSong.mediaUri,
                        artUri = filteredSong.artUri,
                        isLocal = filteredSong.isLocal,
                        albumName = album,
                        albumId = filteredSong.albumId,
                        folderPath = filteredSong.folderPath,
                        author = author,
                        genre = genre,
                        year = year
                    ) else filteredSong
                }

                // Обновляем выбранную песню если нужно
                _selectedSong.value?.let { selected ->
                    if (selected.id == songId) {
                        _selectedSong.value = selected.copy(
                            id = selected.id,
                            title = title,
                            artist = artist,
                            artistId = selected.artistId,
                            duration = selected.duration,
                            mediaUri = selected.mediaUri,
                            artUri = selected.artUri,
                            isLocal = selected.isLocal,
                            albumName = album,
                            albumId = selected.albumId,
                            folderPath = selected.folderPath,
                            author = author,
                            genre = genre,
                            year = year
                        )
                    }
                }

                // Обновляем текущую песню если нужно
                _currentSong.value?.let { current ->
                    if (current.id == songId) {
                        _currentSong.value = current.copy(
                            id = current.id,
                            title = title,
                            artist = artist,
                            artistId = current.artistId,
                            duration = current.duration,
                            mediaUri = current.mediaUri,
                            artUri = current.artUri,
                            isLocal = current.isLocal,
                            albumName = album,
                            albumId = current.albumId,
                            folderPath = current.folderPath,
                            author = author,
                            genre = genre,
                            year = year
                        )
                    }
                }
                // Уведомляем об успешном обновлении
                //_message.value = "Информация обновлена"
                Toast.makeText(App.instance, "Информация обновлена", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                //_message.value = "Ошибка обновления: ${e.message}"
                Log.d(TAG, "SharedViewModel updateSongInfo: Ошибка обновления: ${e.message} ")
                //Toast.makeText(App.instance, "Ошибка обновления: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getSongDetails(songId: Long, callback: (SongFile?) -> Unit) {
        viewModelScope.launch {
            val songFile = repository.getSongFileById(songId)
            withContext(Dispatchers.Main) {
                callback(songFile)
            }
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
        }
    }

    fun loadAllSongsForAdding() {
        viewModelScope.launch {
            val allSongs =  repository.getSongsFromDatabase()
            _songs.value = allSongs
            _filteredSongs.value = allSongs
        }
    }

    fun loadFavoritesSongsForAdding() {
        viewModelScope.launch {
            _favoriteSongs.value = favoriteRepository.getOrderedFavorites()
        }
    }

    //принудительное обновление обложек
    fun refreshArtworks() {
        viewModelScope.launch {
            repository.refreshAllArtworks(App.instance)
            // Перезагружаем списки песен
            loadAllSongsForAdding()
        }
    }

    fun getSongListFromDatabase(autoLoadFromDb:()->Unit){
        viewModelScope.launch {
            val listSongs = repository.getSongsFromDatabase()
            if (listSongs.size > 0){
                _songs.value = listSongs
                _filteredSongs.value = listSongs
            }
            autoLoadFromDb.invoke()
        }
    }

    fun updateSongArtUri(context: Context, track:Song) {
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUri = saveResourceToCache(context)
            track.artUri = newArtUri
            // Обновляем песню
            _songs.value = _songs.value?.map {s->
                if (s.id == track.id) s.copy(artUri = newArtUri) else s
            }
            // Обновляем песню
            _filteredSongs.value = _filteredSongs.value?. map{filteredSong->
                if (filteredSong.id == track.id) filteredSong.copy(artUri = newArtUri) else filteredSong
            }
            // Обновляем выбранную песню
            _selectedSong.value?. let{selectedSong->
                if (selectedSong.id == track.id)
                    _selectedSong.value = selectedSong.copy(artUri = newArtUri)
            }

            // Обновляем текущую песню если нужно
            _currentSong.value?.let { current ->
                if (current.id == track.id) {
                    _currentSong.value = current.copy(artUri = newArtUri)
                }
            }

            Log.d(TAG, " **??** SharedViewModel updateSongArtUri newArtUri $newArtUri")
            try {
                repository.updateSongArtUri(track.id, newArtUri)
                Log.d(TAG, "✅SharedViewModel updateSongArtUri Обновлён artUri для песни ${track.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌SharedViewModel updateSongArtUri Ошибка обновления artUri: ${e.message}")
            }
        }
    }

    //для сохранения картинки из ресурсов в кэш диска
    fun saveResourceToCache(context: Context): String? = saveDefaultCoverToCache(context)

    fun updateFavoriteArtUri(context: Context, track: Song){
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveResourceToCache(context)
            track.artUri = newArtUriString
            // Обновляем  избранное
            _favoriteSongs.value = _favoriteSongs.value?.map {fav->
                if (fav.id == track.id) fav.copy(artUri = newArtUriString) else fav
            }
            // Обновляем  фильтрованное избранное
            _filteredFavoriteSongs.value = _filteredFavoriteSongs.value?. map{ff->
                if (ff.id == track.id) ff.copy(artUri =newArtUriString) else ff
            }
            Log.d(TAG, " **??** SharedViewModel updateFavoriteArtUri newArtUriString $newArtUriString")
            try {
                newArtUriString?. let{
                    favoriteRepository.updateFavoriteAtrUri(track.id, it)
                }
                Log.d(TAG, "✅SharedViewModel updateFavoriteArtUri Обновлён artUri для папки  ${track.title}  id = ${track.id} }")
            } catch (e: Exception) {
                Log.e(TAG, "❌SharedViewModel updateFavoriteArtUri Ошибка обновления artUri для папки  ${track.title} : ${e.message}")
            }
        }
    }

    fun getSongById(songId: Long, callback: (Song?) -> Unit) {
        viewModelScope.launch {
            val songFile = repository.getById(songId)
            songFile?. let{songFile->
                callback(fromSongFileToSong(songFile))
            }
        }
    }

    // Метод для обновления текущего плейлиста
    fun refreshCurrentPlaylist(playlistSongs: List<Song>) {
        //можно было взять и из currentFilteredPlaylistSongs
        Log.d(TAG, "!@#@ SongPlaylistFragment размер плейлиста = " +
                    "${playlistSongs.size} имя первой песни плейлиста " +
                    "= ${playlistSongs.first().title} "
        )
        val currentSong = getCurrentSong()

        if (currentSong!= null && currentSong in playlistSongs){
            setSongAndPlaylist(
                SongAndPlaylist(
                    song=currentSong,
                    playlist=playlistSongs
                )
            )
            setCurrentSong(currentSong)
        }
    }

    fun startSplash(startSplash: () -> Unit) {
        viewModelScope.launch {
            delay(1000)
            startSplash.invoke()
        }
    }

    fun setListSelectedSong(songs: List<Song>?) {
        songs?. let{
            _listSelectedSong.value =songs
            _filteredListSelectedSong.value = songs
        }
        Log.d(TAG, "✅SharedViewModel setListSelectedSong songs.size =" +
                songs?.size?.let { "songs.first = ${ if(it > 0) songs?.first()?.title ?: "" else "" }" })
    }

}
