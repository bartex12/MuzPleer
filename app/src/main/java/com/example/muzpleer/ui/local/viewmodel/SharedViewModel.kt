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
import com.example.muzpleer.service.MusicServiceHandler
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.getSortedDataSong
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
    private val albumRepository: AlbumRepository,
    private val artistsRepository: ArtistRepository,
    private val playerHandler: MusicServiceHandler,
    private val folderRepository: FolderRepository,
    private val favoriteRepository: FavoriteRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel(), MusicServiceHandler.PlayerCallback{

    init {
        playerHandler.callback = this  //иначе не работает
        Log.d(TAG, "PlayerViewModel init: playerHandler =$playerHandler ")
    }


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

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _coverImage = MutableLiveData<Bitmap?>()
    val coverImage: LiveData<Bitmap?> = _coverImage

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData<Long>(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _duration = MutableLiveData<Long>(0L)
    val duration: LiveData<Long> = _duration

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

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

    private val _playerVisibility = MutableLiveData<Boolean>(true)
    val playerVisibility: LiveData<Boolean> = _playerVisibility

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

    private val _coverPath = MutableLiveData<String>()
    val coverPath: LiveData<String> = _coverPath

    private val _playlists = MutableLiveData<List<Playlist>>() //все плейлисты в списке плейлистов,
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _filteredPlaylists = MutableLiveData<List<Playlist>>() //все плейлисты в списке плейлистов, отфильтрованные поиском
    val filteredPlaylists: LiveData<List<Playlist>> = _filteredPlaylists

    private val _currentPlaylist = MutableLiveData<Playlist?>()  //конкретный плейлист
    val currentPlaylist : LiveData<Playlist?> = _currentPlaylist

    private val _currentPlaylistSongs = MutableLiveData<List<Song>?>()  //список песен конкретного плейлиста
    val currentPlaylistSongs : LiveData<List<Song>?> = _currentPlaylistSongs

    private val _currentFilteredPlaylistSongs = MutableLiveData<List<Song>?>()  //отфильтрованный поиском список песен конкретного плейлиста
    val currentFilteredPlaylistSongs: LiveData<List<Song>?> = _currentFilteredPlaylistSongs

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
            _loading.value = true
            initParamsSong(repository.loadMusicFromMemory())
            _loading.value = false
            afterLoad.invoke()
        }
    }

    private fun initParamsSong(songs:List<Song>){
        _songs.value = songs
        _filteredSongs.value  = _songs.value
        Log.d(TAG, "SharedViewModel initParamsSong songs.size = ${songs.size}")
    }

    fun setPlayerVisibility(visible: Boolean) {
        _playerVisibility.value = visible
    }

    fun setSongAndPlaylist(songAndPlaylist: SongAndPlaylist){
        _songAndPlaylist.value = songAndPlaylist
    }

    fun setPlaylistForHandler(playlist: List<Song>, initialIndex: Int = 0) {
        playerHandler.setPlaylist(playlist, initialIndex)
    }

    fun setPlaylist(songsList : List<Song>){
        _playlist.value = songsList
    }

    fun togglePlayPause() {
        playerHandler.togglePlayPause()
    }

    fun playNext() {
        Log.d(TAG, "SharedViewModel playNext ")
        playerHandler.playNext()
    }

    fun playPrevious() {
        playerHandler.playPrevious()
    }

    fun seekTo(position: Long) {
        playerHandler.seekTo(position)
    }

    fun seekRelative(offsetMs: Long) {
        playerHandler.getCurrentPosition().let { currentPos ->
            val newPosition = (currentPos + offsetMs).coerceAtLeast(0)
            playerHandler.seekTo(newPosition)
        }
    }

    override fun onTrackChanged(track: Song) {
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

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    override fun onPositionChanged(position: Long, duration: Long) {
        _currentPosition.postValue(position)
        _duration.postValue(duration)
    }

    override fun onError(message: String) {
        _errorMessage.postValue(message)
    }

    //функция для нормализации URI
    fun getNormalizedPath(uriString: String): String {
        return if (uriString.startsWith("file://")) {
            Uri.decode(uriString.substring(7))
        } else {
            uriString
        }
    }

    fun clearError() {
        _errorMessage.value = null
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

    override fun onCleared() {
        super.onCleared()
        playerHandler.release()
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

    internal fun filterAlbums(query: String) {
        val originalAlbumList: MutableList<Album> =  (albums.value ?: listOf()).toMutableList()
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

    internal fun filterPlaylists(query: String) {
        val originalPlaylistList: MutableList<Playlist> =  (playlists.value ?: listOf()).toMutableList()
        val filteredPlaylistList: MutableList<Playlist> = (filteredPlaylists.value ?: listOf()).toMutableList()
        filteredPlaylistList.clear()
        if (query.isEmpty()) {
            filteredPlaylistList.addAll(originalPlaylistList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (playlist in originalPlaylistList) {
                if (playlist.playlistName.lowercase(Locale.getDefault()).contains(searchQuery) ) {
                    filteredPlaylistList.add(playlist)
                }
            }
        }
        _filteredPlaylists.value = filteredPlaylistList
    }

    internal fun filterAlbumSongs(query: String) {
        val originalAlbumSongList = (listAlbumSong.value?: listOf()).toMutableList()
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

    internal fun filterArtists(query: String) {
        val originalArtistList: MutableList<Artist> =  (artists.value ?: listOf()).toMutableList()
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
        val originalArtistSongList = (listArtistSong.value?: listOf()).toMutableList()
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

    internal fun filterFolders(query: String) {
        val originalFolderList: MutableList<Folder> =  (folders.value ?: listOf()).toMutableList()
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
        val originalFolderSongList: MutableList<Song> =  (listFolderSong.value ?: listOf()).toMutableList()
        val filteredFolderSongList: MutableList<Song> = (filteredListFolderSong.value ?: listOf()).toMutableList()
        filteredFolderSongList.clear()
        if (query.isEmpty()) {
            filteredFolderSongList.addAll(originalFolderSongList)
        } else {
            val searchQuery = query.lowercase(Locale.getDefault())
            for (folderSong in originalFolderSongList) {
                if (folderSong.title.lowercase(Locale.getDefault()).contains(searchQuery)||
                    folderSong.artist.lowercase(Locale.getDefault()).contains(searchQuery))  {
                    filteredFolderSongList.add(folderSong)
                }
            }
        }
        _filteredListFolderSong.value = filteredFolderSongList
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

    internal fun filterPlaylistSongs(query: String) {
        val playlistSongs =currentPlaylistSongs.value
        val originalSongsList: MutableList<Song> =playlistSongs?.toMutableList() ?: mutableListOf()
        val filteredPlaylistSong =currentFilteredPlaylistSongs.value
        val filteredSongsList: MutableList<Song> =filteredPlaylistSong?.toMutableList() ?: mutableListOf()
        filteredSongsList.clear( )
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
        _currentFilteredPlaylistSongs.value = filteredSongsList
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

    fun getPositionAlbum(): Int{  return helper.getPositionAlbum() }
    fun savePositionAlbum(position: Int){helper.savePositionAlbum(position)}

    fun getPositionPlaylist(): Int{  return helper.getPositionPlaylist() }
    fun savePositionPlaylist(position: Int){helper.savePositionPlaylist(position)}

    fun getPositionArtist(): Int{  return helper.getPositionArtist() }
    fun savePositionArtist(position: Int){helper.savePositionArtist(position)}

    fun getPositionFolder(): Int{  return helper.getPositionFolder() }
    fun savePositionFolder(position: Int){helper.savePositionFolder(position)}

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
            _loading.value = true
            try {
                val songs = favoriteRepository.getOrderedFavorites()
                //val songs = favoriteRepository.getAllFavorites()
                _favoriteSongs.value = songs
                _filteredFavoriteSongs.value = songs
            } catch (e: Exception) {
                Log.d(TAG, "***SharedViewModel loadFavoriteSongs error = ${e.message}")
            } finally {
                _loading.value = false
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

    //для плейлистов
    fun updateCoverImageLevelAndSave(uri: Uri, levelId:Long) {
        Log.d(TAG, "5--- SharedViewModel updateCoverImageAndSave uri = $uri levelId =$levelId")
        _coverImageUriLevel.value = uri
        saveCoverLevelToDatabase(uri, levelId)
    }

    //для альбомов
    fun updateCoverImageAlbumAndSave(uri: Uri, albumId:Long) {
        Log.d(TAG, "5--- SharedViewModel updateCoverImageAlbumAndSave uri = $uri albumId =$albumId")
        _coverImageUriLevel.value = uri
        saveCoverAlbumToDatabase(uri, albumId)
    }

    //для артистов
    fun updateCoverImageArtistsAndSave(uri: Uri, artistId:Long) {
        Log.d(TAG, "5--- SharedViewModel updateCoverImageArtistsAndSave uri = $uri artistId =$artistId")
        _coverImageUriLevel.value = uri
        saveCoverArtistToDatabase(uri, artistId)
    }

    //для папок
    fun updateCoverImageFolderAndSave(uri: Uri, folderId:Long) {
        Log.d(TAG, "5--- SharedViewModel updateCoverImageFolderAndSave uri = $uri folderId =$folderId")
        _coverImageUriLevel.value = uri
        saveCoverFolderToDatabase(uri, folderId)
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

    //для плейлиста
  fun  saveCoverLevelToDatabase(uri: Uri, levelId:Long){
       Log.d(TAG, "6--- SharedViewModel saveCoverLevelToDatabase uri = $uri levelId = $levelId")
       viewModelScope.launch {
           playlistRepository.updatePlaylistArtUri(levelId, uri.toString())
           loadPlaylists() // Обновляем список
       }
   }

    fun  saveCoverAlbumToDatabase(uri: Uri, albumId:Long){
        Log.d(TAG, "6--- SharedViewModel saveCoverAlbumToDatabase uri = $uri albumId = $albumId")
        viewModelScope.launch {
            albumRepository.updateAlbumArtUri(albumId, uri.toString())
            loadAlbums() // Обновляем список альбомов

            //проверка
            val albumFile = albumRepository.getAlbumById(albumId)
            Log.d(TAG, "6-1--- проверка SharedViewModel saveCoverFolderToDatabase " +
                    " albumFile title = ${albumFile?.title} albumFile id = ${albumFile?.id} " +
                    "albumFile albumId = ${albumFile?.albumId} albumFile coverPath = ${albumFile?.coverPath}")
        }
    }

    fun  saveCoverArtistToDatabase(uri: Uri, artistId:Long){
        Log.d(TAG, "6--- SharedViewModel saveCoverArtistToDatabase uri = $uri artistId = $artistId")
        viewModelScope.launch {
            artistsRepository.updateArtistArtUri(artistId, uri.toString())
            loadArtists() // Обновляем список артистов
        }
    }

    fun  saveCoverFolderToDatabase(uri: Uri, folderId:Long){
        Log.d(TAG, "6--- SharedViewModel saveCoverFolderToDatabase uri = $uri folderId = $folderId")
        viewModelScope.launch {
            folderRepository.updateFolderArtUri(folderId, uri.toString())
            loadFolders() // Обновляем список папок
            //проверка
            val folderFile = folderRepository.getFolderFileById(folderId)
            Log.d(TAG, "6-1--- проверка SharedViewModel saveCoverFolderToDatabase " +
                    "folderFile folderName = ${folderFile?.folderName}  coverPath =${folderFile?.coverPath}")
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

    //загрузка альбомов
    fun loadAlbums() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val albumsWithSongs = albumRepository.getAllAlbumsWithSongs()
                Log.d(TAG, " * SharedViewModel loadAlbums albumsWithSongs size = ${albumsWithSongs.size}")
                _albums.value = albumsWithSongs  //todo убрать?
                _filteredAlbums.value = albumsWithSongs
            } catch (e: Exception) {
                Log.d(TAG, " SharedViewModel loadAlbums Error loading albums ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    //загрузка артистов
    fun loadArtists() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val artistsWithSongsAndAlbums = artistsRepository.getAllArtistsWithSongsAndAlbums()
                Log.d(TAG, " * SharedViewModel loadArtists artistsWithSongsAndAlbums size = ${artistsWithSongsAndAlbums.size}")
                _artists.value = artistsWithSongsAndAlbums
                _filteredArtists.value = artistsWithSongsAndAlbums  //todo убрать?
            } catch (e: Exception) {
                Log.d(TAG, " SharedViewModel loadArtists Error loading Artists ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val foldersWithSongs = folderRepository.getAllFoldersWithSongs()
                _folders.value = foldersWithSongs
                _filteredFolders.value = foldersWithSongs //todo убрать?
            } catch (e: Exception) {
                Log.d(TAG, " SharedViewModel loadFolders Error loading Folders ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    //получение альбомов из песен и загрузка альбомов
    fun syncAlbums() {
        viewModelScope.launch {
            _loading.value = true
            try {
                Log.d(TAG, " * SharedViewModel syncAlbums albumRepository.syncAlbumsFromMediaFiles()")
                albumRepository.syncAlbumsFromMediaFiles()
                loadAlbums() // Перезагружаем после синхронизации
            } catch (e: Exception) {
                Log.d(TAG, " SharedViewModel syncAlbums Error loading albums error = ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    fun syncArtist (){
        viewModelScope.launch {
            _loading.value = true
            try {
                Log.d(TAG, " * SharedViewModel syncArtist artistsRepository.syncArtistsFromMediaFiles()")
                artistsRepository.syncArtistsFromMediaFiles()
                loadArtists() // Перезагружаем после синхронизации
            } catch (e: Exception) {
                Log.d(TAG, " SharedViewModel syncArtist Error loading Artist error = ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    fun syncFolders() {
        viewModelScope.launch {
            _loading.value = true
            try {
                folderRepository.syncFoldersFromMediaFiles()
                loadFolders() // Перезагружаем после синхронизации
            } catch (e: Exception) {
                Log.e("FolderViewModel", "Error syncing folders", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun getSongsByAlbum(albumId: Long){
        viewModelScope.launch {
            var listAlbumSong:List<Song> = albumRepository.getAlbumSongList(albumId)
            Log.d(TAG, " * SharedViewModel getSongsByAlbum listAlbumSong size = ${listAlbumSong.size}")
            _listAlbumSong.value = listAlbumSong
            _filteredListAlbumSong.value = listAlbumSong
        }
    }

    fun getSongsByArtist(artistId: Long){
        viewModelScope.launch {
            var listArtistSong:List<Song> = artistsRepository.getArtistSongList(artistId)
            Log.d(TAG, " * SharedViewModel getSongsByArtist listArtistSong size = ${listArtistSong.size}")
            _listArtistSong.value = listArtistSong
            _filteredListArtistSong.value = listArtistSong
        }
    }

    fun getSongsByFolder(folderPath: String) {
        viewModelScope.launch {
            var listFolderSong: List<Song> = folderRepository.getFolderSongList(folderPath)
            Log.d(TAG, " * SharedViewModel getSongsByFolder listFolderSong size = ${listFolderSong.size}")
             _listFolderSong.value = listFolderSong
            _filteredListFolderSong.value = listFolderSong
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
                            title = title,
                            artist = artist,
                            albumName = album,
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
                        title = title,
                        artist = artist,
                        albumName = album,
                        author = author,
                        genre = genre,
                        year = year
                    ) else filteredSong
                }

                // Обновляем выбранную песню если нужно
                _selectedSong.value?.let { selected ->
                    if (selected.id == songId) {
                        _selectedSong.value = selected.copy(
                            title = title,
                            artist = artist,
                            albumName = album,
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
                            title = title,
                            artist = artist,
                            albumName = album,
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

    fun updateFoldersOrder(orderedFolders: List<Folder>) {
        viewModelScope.launch {
            val folders:List<FolderFile> = folderRepository.getAllFolderSongs()
            val updatedFolders: MutableList<FolderFile> = mutableListOf<FolderFile>()

            orderedFolders.forEachIndexed { index, folder ->
                val folder1 = folders.find { it.folderPath == folder.path }
                folder1?.let {
                    updatedFolders.add(it.copy(sortOrder = index))
                }
            }
            Log.d(TAG,"--#**# SharedViewModel updateFoldersOrder orderedFolders size = ${orderedFolders.size} " +
                    "orderedFolders ids = ${orderedFolders.map{it.id}} orderedFolders paths= ${orderedFolders.map{it.path}}")
            Log.d(TAG,"--#**# SharedViewModel updateFoldersOrder updatedFolders size = ${updatedFolders.size} " +
                    "updatedFolders sortOrder = ${updatedFolders.map{it.sortOrder}  }")

            folderRepository.updateFoldersOrder(updatedFolders)

            // Обновляем LiveData
            _folders.value = orderedFolders
            _filteredFolders.value = orderedFolders
        }
    }

    fun updateAlbumsOrder(orderedAlbums: List<Album>) {
        viewModelScope.launch {
            val albums:List<AlbumFile> = albumRepository.getAllAlbumSongs() //альбомы из базы
            val updatedAlbums: MutableList<AlbumFile> = mutableListOf<AlbumFile>()

            orderedAlbums.forEachIndexed { index, album ->  //смотрим пришедшие альбомы
                //при совпадении id из базы и пришедших альбомов добавляем в список с обновлением индекса для сортировки
                val album1 = albums.find { it.albumId == album.albumId }
                album1?.let {
                    updatedAlbums.add(it.copy(sortOrder = index))
                }
            }
            Log.d(TAG,"--#**# SharedViewModel updateAlbumsOrder orderedAlbums size = ${orderedAlbums.size} " +
                    "orderedAlbums ids = ${orderedAlbums.map{it.albumId}} albums albumIds= ${albums.map{it.albumId}}")
            Log.d(TAG,"--#**# SharedViewModel updateAlbumsOrder updatedAlbums size = ${updatedAlbums.size} " +
                    "updatedAlbums sortOrder = ${updatedAlbums.map{it.sortOrder}  }")

            albumRepository.updateAlbumsOrder(updatedAlbums)

            // Обновляем LiveData
            _albums.value = orderedAlbums
            _filteredAlbums.value = orderedAlbums
        }
    }

    fun updateArtistsOrder(orderedArtist: List<Artist>) {
        viewModelScope.launch {
            val artists:List<ArtistFile> = artistsRepository.getAllArtistsSongs()
            val updatedArtists: MutableList<ArtistFile> = mutableListOf<ArtistFile>()

            orderedArtist.forEachIndexed { index, orderedArtist ->
                val artist1 = artists.find { it.artistId == orderedArtist.artistId }
                artist1?.let {
                    updatedArtists.add(it.copy(sortOrder = index))
                }
            }
            //val artistIds = artists.map{ it.id}
            val artistArtistIds = artists.map{ it.artistId} //это хэши имени

            Log.d(TAG,"--#**# SharedViewModel updateArtistsOrder orderedArtist artistId = ${orderedArtist.map{it.artistId}}")
            Log.d(TAG,"--#**# SharedViewModel updateArtistsOrder artistArtistIds = $artistArtistIds")
            Log.d(TAG,"--#**# SharedViewModel updateArtistsOrder updatedArtists size = ${updatedArtists.size} " +
                    "updatedArtists sortOrder = ${updatedArtists.map{it.sortOrder}  }")

            artistsRepository.updateArtistsOrder(updatedArtists)

            // Обновляем LiveData
            _artists.value = orderedArtist
            _filteredArtists.value = orderedArtist
        }
    }

    fun getOrderedFavoriteSong() {
        viewModelScope.launch {
            val songs= favoriteRepository.getOrderedFavorites()
            _favoriteSongs.value=songs
            _filteredFavoriteSongs.value=songs
        }
    }

    //грузим все плейлисты для отображения списком
    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val playlists = playlistRepository.getAllPlaylists()
                Log.d(TAG,"#**# SharedViewModel loadPlaylists playlists size = ${playlists.size}")
                _playlists.value = playlists
                _filteredPlaylists.value = playlists
            } catch (e: Exception) {
                Log.d(TAG,"#**# SharedViewModel loadPlaylists Error loading playlists error = ${e.message}")
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
                Log.d(TAG,"SharedViewModel createPlaylist Error creating playlists error = ${e.message}")
            }
        }
    }

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            try {
                playlistRepository.addSongsToPlaylist(playlistId, songIds)
                loadPlaylists() // Перезагружаем список
            } catch (e: Exception) {
                Log.d(TAG,"SharedViewModel addToPlaylist Error adding songs to playlist error = ${e.message}")
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
                Log.d(TAG,"SharedViewModel loadPlaylist Error loading playlist error = ${e.message}")
            }
        }
    }

    fun setCurrentPlaylistByPlaylistId(playlistId:Long){
        viewModelScope.launch {
            try {
            val playlist=playlistRepository.getPlaylistWithSongs(playlistId)
                _currentPlaylist.value = playlist
                _coverImageUriLevel.value =playlist?.playlistArtUri
            } catch (e: Exception) {
                Log.d(TAG,"SharedViewModel getPlaylistByPlaylistId Error loading playlist error = ${e.message}")
            }
        }
    }

    fun setCurrentAlbum(album: Album){
        viewModelScope.launch {
            try {
                _currentAlbum.value = album  //todo не нужно?
                _coverImageUriLevel.value = album.artworkUri
            } catch (e: Exception) {
                Log.d(TAG,"SharedViewModel setCurrentAlbum Error loading playlist error = ${e.message}")
            }
        }
    }

    fun setCurrentArtist(artist: Artist){
        viewModelScope.launch {
            try {
                _currentArtist.value = artist  //todo не нужно?
                _coverImageUriLevel.value =artist.artworkUri
            } catch (e: Exception) {
                Log.d(TAG,"SharedViewModel setCurrentArtist Error loading playlist error = ${e.message}")
            }
        }
    }

    fun setCurrentFolderByFolderPath(folderPath:String){
        viewModelScope.launch {
            try {
                var currentFolder:Folder =  folderRepository.getFolderByPath(folderPath)
                Log.d(TAG,"@ SharedViewModel setCurrentFolderByFolderPath " +
                        " currentFolder name =  = ${currentFolder.name}")
                _currentFolder.value = currentFolder
                _coverImageUriLevel.value =currentFolder.artworkUri
            } catch (e: Exception) {
                Log.d(TAG,"@ SharedViewModel setCurrentFolderByFolderPath  error = ${e.message}")
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
                Log.d(TAG,"SharedViewModel updatePlaylistOrder Error updating playlist order error = ${e.message}")
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(playlistId)
                loadPlaylists()
            } catch (e: Exception) {
                Log.d(TAG,"SharedViewModel deletePlaylist Error deleting playlist error = ${e.message}")
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

    fun loadAlbumSongsForAdding(albumId:Long){
        viewModelScope.launch {
            _listAlbumSong.value = albumRepository.getAlbumSongList(albumId)
        }
    }

    fun loadArtistSongsForAdding(artistId:Long){
        viewModelScope.launch {
            _listArtistSong.value = artistsRepository.getArtistSongList(artistId)
        }
    }

    fun loadFolderSongsForAdding(folderPath: String){
        viewModelScope.launch {
            _listFolderSong.value = folderRepository.getFolderSongList(folderPath)
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
                    Toast.makeText(App.instance, "Песня удалена из плейлиста", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(App.instance, "Ошибка удаления песни", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing song from playlist: ${e.message}", e)
                Toast.makeText(App.instance, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(App.instance, "Удалено песен: $removedCount", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(App.instance, "Не удалось удалить песни", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing songs from playlist: ${e.message}", e)
                Toast.makeText(App.instance, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Log.d(TAG, "--**-- ShareViewModel updatePlaylistSongsOrder Порядок песен сохранен")
                }
                callback(true)
            } catch (e: Exception) {
                Log.e(TAG, "❌--**-- ShareViewModel updatePlaylistSongsOrder Ошибка при сохранении порядка песен", e)
                callback(false)
            }
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
    fun saveResourceToCache(context: Context): String? {
        // 1. Формируем правильный Uri для ресурса R.drawable.muz_player3
        val resId = R.drawable.muz_player3
        val sourceUri ="android.resource://${context.packageName}/$resId".toUri()

        // 2. Создаем директорию album_art в кэше, если её нет
        val cacheDir = File(context.cacheDir, "album_art")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // 3. Формируем имя и итоговый файл по вашему условию
        val fileName = "embedded_${sourceUri.toString().hashCode()}.jpg"
        val destinationFile = File(cacheDir, fileName)

        // Если файл уже был сохранен ранее, просто возвращаем путь
        if (destinationFile.exists()) {
            Log.d(TAG, "!! SharedViewModel saveResourceToCache - файл уже был сохранен ранее")
            return destinationFile.absolutePath
        }

        // 4. Читаем ресурс и записываем его на диск
        return try {
            context.contentResolver.openInputStream(sourceUri).use { inputStream ->
                if (inputStream == null) return null

                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null // Возвращаем null в случае ошибки ввода-вывода
        }
    }

    fun updateAlbumArtUri(context: Context, album: Album) {
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveResourceToCache(context)
            val newArtUri = newArtUriString?.toUri()
            album.artworkUri = newArtUri
            // Обновляем альбом
            _albums.value = _albums.value?.map {a->
                if (a.albumId == album.albumId) a.copy(artworkUri =newArtUri) else a
            }
            // Обновляем альбом
            _filteredAlbums.value = _filteredAlbums.value?. map{fa->
                if (fa.albumId == album.albumId) fa.copy(artworkUri =newArtUri) else fa
            }

            // Обновляем текущий альбом если нужно
            _currentAlbum.value?.let { current ->
                if (current.albumId == album.albumId) {
                    _currentAlbum.value = current.copy(artworkUri = newArtUri)
                }
            }
            Log.d(TAG, " **??** SharedViewModel updateAlbumArtUri newArtUri $newArtUri")
            try {
                albumRepository.updateAlbumArtUriByAlbumId(album.albumId, newArtUriString)
                Log.d(TAG, "✅SharedViewModel updateAlbumArtUri Обновлён artUri для альбома с title = ${album.title}  id =  ${album.id} albumId = ${album.albumId}")
            } catch (e: Exception) {
                Log.e(TAG, "❌SharedViewModel updateAlbumArtUri Ошибка обновления artUri: ${e.message}")
            }
        }
    }

    fun updatePlaylistArtUri(context: Context,  playlist: Playlist){
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveResourceToCache(context)
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
            Log.d(TAG, " **??** SharedViewModel updatePlaylistArtUri newArtUri $newArtUri")
            try {
                playlistRepository.updatePlaylistArtUri(playlist.id, newArtUriString)
                Log.d(TAG, "✅SharedViewModel updatePlaylistArtUri Обновлён artUri для плейлиста = ${playlist.playlistName}  id = ${playlist.id} }")
            } catch (e: Exception) {
                Log.e(TAG, "❌SharedViewModel updatePlaylistArtUri Ошибка обновления для плейлиста = ${playlist.playlistName}  artUri: ${e.message}")
            }
        }
    }

    fun updateArtistArtUri(context: Context, artist: Artist){
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUriString = saveResourceToCache(context)
            val newArtUri = newArtUriString?.toUri()
            artist.artworkUri = newArtUri
            // Обновляем артиста
            _artists.value = _artists.value?.map {a->
                if (a.id == artist.id) a.copy(artworkUri = newArtUri) else a
            }
            // Обновляем артиста
            _filteredArtists.value = _filteredArtists.value?. map{fa->
                if (fa.id == artist.id) fa.copy(artworkUri =newArtUri) else fa
            }

            // Обновляем текущего артиста если нужно
            _currentArtist.value?.let { current ->
                if (current.id == artist.id) {
                    _currentArtist.value = current.copy(artworkUri = newArtUri)
                }
            }
            Log.d(TAG, " **??** SharedViewModel updateArtistArtUri newArtUri $newArtUri")
            try {
                artistsRepository.updateArtistArtUriById(artist.id, newArtUriString)
                Log.d(TAG, "✅SharedViewModel updateArtistArtUri Обновлён artUri для артиста с ${artist.name}  id =  ${artist.id} }")
            } catch (e: Exception) {
                Log.e(TAG, "❌SharedViewModel updateArtistArtUri Ошибка обновления artUri: ${e.message}")
            }
        }
    }

   fun updateFolderArtUri(context: Context, folder: Folder){
       viewModelScope.launch {
           //сохраняем картинку из ресурсов в кэш и получаем ссылку
           val newArtUriString = saveResourceToCache(context)
           val newArtUri = newArtUriString?.toUri()
           folder.artworkUri = newArtUri
           // Обновляем папку
           _folders.value = _folders.value?.map {f->
               if (f.id == folder.id) f.copy(artworkUri = newArtUri) else f
           }
           // Обновляем папку
           _filteredFolders.value = _filteredFolders.value?. map{ff->
               if (ff.id == folder.id) ff.copy(artworkUri =newArtUri) else ff
           }

           // Обновляем текущую папку если нужно
           _currentFolder.value?.let { current ->
               if (current.id == folder.id) {
                   _currentFolder.value = current.copy(artworkUri = newArtUri)
               }
           }
           Log.d(TAG, " **??** SharedViewModel updateFolderArtUri newArtUri $newArtUri")
           try {
              folderRepository.updateFolderArtUri(folder.id, newArtUriString)
               Log.d(TAG, "✅SharedViewModel updateFolderArtUri Обновлён artUri для папки  ${folder.name}  id = ${folder.id} }")
           } catch (e: Exception) {
               Log.e(TAG, "❌SharedViewModel updateFolderArtUri Ошибка обновления artUri для папки ${folder.name}: ${e.message}")
           }
       }
   }

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
    fun refreshCurrentPlaylist() {
        val playlistSongs=
            getCurrentPlaylist()?.playlistSongs ?: listOf()
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

    fun setPlaybackMode(): Boolean {
        // Меняем состояние на противоположное
        val newShuffleState = !playerHandler.isShuffleMode
        playerHandler.isShuffleMode = newShuffleState
        return newShuffleState
    }

}
