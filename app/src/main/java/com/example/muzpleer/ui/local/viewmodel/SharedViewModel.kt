package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.example.muzpleer.R
import com.example.muzpleer.model.PlaybackSource
import com.example.muzpleer.model.Song
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.repository.MusicRepository
import com.example.muzpleer.room.entity.SongFile
import com.example.muzpleer.room.utils.fromSongFileToSong
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.util.getNormalizedPath
import com.example.muzpleer.util.getSortedDataSong
import com.example.muzpleer.util.UserMessenger
import com.example.muzpleer.util.saveDefaultCoverToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SharedViewModel(
    var helper : IPreferenceHelper,
    private val repository: MusicRepository,
    private val messenger: UserMessenger,
    private val appContext: Context
) : ViewModel() {

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    //false, пока первое сканирование не завершено. Пустой список до этого момента означает
    //"ещё грузится", а не "треков нет"
    private val _libraryLoaded = MutableLiveData<Boolean>(false)
    val libraryLoaded: LiveData<Boolean> = _libraryLoaded

    private val _filteredSongs = MutableLiveData<List<Song>>()
    val filteredSongs: LiveData<List<Song>> = _filteredSongs

    private val _playlist = MutableLiveData<List<Song>>()
    val playlist: LiveData<List<Song>> = _playlist

    private val _songAndPlaylist = MutableLiveData<SongAndPlaylist>()
    val songAndPlaylist: LiveData<SongAndPlaylist> = _songAndPlaylist

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong



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
            initParamsSong(repository.loadMusicFromMemory())
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
        _libraryLoaded.value = true //раньше списков: наблюдатели списка должны уже знать, что загрузка закончилась
        _songs.value = songs
        _filteredSongs.value  = _songs.value
        //трек мог быть назван плеером раньше, чем загрузился список: теперь можно найти его индекс
        _currentSong.value?.let { updateIndexOfCurrentSong(it) }
        Log.d(TAG, "SharedViewModel initParamsSong songs.size = ${songs.size}")
    }

    fun setSongAndPlaylist(songAndPlaylist: SongAndPlaylist){
        _songAndPlaylist.value = songAndPlaylist
    }

    fun setPlaylist(songsList : List<Song>){
        _playlist.value = songsList
    }

    //отсортированный список всех песен. Сортировка дорогая, а нужен он при каждой смене трека,
    //поэтому результат запоминается и пересчитывается только когда список песен заменили
    //(любое изменение песни создаёт новый список, так что сравнение по ссылке достаточно)
    private var sortedSongsSource: List<Song>? = null
    private var sortedSongsCache: List<Song> = emptyList()

    private fun getSortedSongs(): List<Song> {
        val source = getSongs()
        if (source !== sortedSongsSource) {
            sortedSongsCache = getSortedDataSong(source)
            sortedSongsSource = source
        }
        return sortedSongsCache
    }

    /** Вызывается из MainActivity, когда PlayerViewModel сообщает о смене трека. */
    fun onTrackChanged(track: Song) {
        //так не меняется автоматом- остаётся старый artUri из плейлиста
        _currentSong.value = track
        updateIndexOfCurrentSong(track)
    }

    private fun updateIndexOfCurrentSong(track: Song) {
        //считаем индекс выбранной песни в отсортированном списке песен,
        // чтобы при возврате на песни можно было перейти к этой песне по индексу
        val trackMediaUri = getNormalizedPath(track.mediaUri)
        val indexOfSong = getSortedSongs()
            .indexOfFirst { getNormalizedPath(it.mediaUri) == trackMediaUri }
        Log.d(TAG, " !@# !@# SharedViewModel onTrackChanged track title = ${track.title} " +
                "track mediaUri = ${track.mediaUri} trackMediaUri = $trackMediaUri")
        _indexOfCurrentSong.value = indexOfSong
        Log.d(TAG, " !@# !@# SharedViewModel onTrackChanged indexOfSong = $indexOfSong " +
                "  currentTrack  title = ${getCurrentSong()?.title}")
    }

    //функция для нормализации URI


    fun setCurrentSong(song: Song) {
        Log.d(TAG, "SharedViewModel setCurrentSong song = $song")
        _currentSong.value = song
    }

    fun getCurrentSong() : Song?{
        //Log.d(TAG, "SharedViewModel getCurrentSong ")
         return currentSong.value
    }

    companion object{
        const val TAG= "33333"
    }

    internal fun filterSongs(query: String) {
        //данные ещё не загружены: фильтровать нечего, пустой результат принимался за "список пуст"
        if (songs.value == null) return
        _filteredSongs.value = (songs.value ?: listOf()).filterSongsByQuery(query)
    }

    internal fun filterListSelectedSongs(query: String) {
        _filteredListSelectedSong.value = (listSelectedSong.value ?: listOf()).filterSongsByQuery(query)
    }


    //до загрузки библиотеки значения ещё нет (плеер может сообщить о треке раньше, при повторном открытии приложения)
    fun getSongs():List<Song> {
       return songs.value ?: emptyList()
    }

    fun getPlaylist():List<Song> {
        return playlist.value ?: emptyList()
    }

    fun getPositionSong(): Int{  return helper.getPositionSong() }
    fun savePositionSong(position: Int){helper.savePositionSong(position)}

    fun getTabsLocalPosition():Int = helper.getTabsLocalPosition()
    fun saveTabsLocalPosition(currentItem: Int) = helper.saveTabsLocalPosition(currentItem)

    fun setSelectedPosition(position: Int) { _selectedSongPosition.value = position }

    fun setSelectedAlbumPosition(position: Int) { _selectedAlbumPosition.value = position }
    fun setSelectedArtistPosition(position: Int) { _selectedArtistPosition.value = position }
    fun setSelectedFolderPosition(position: Int) { _selectedFolderPosition.value = position }
    fun setSelectedPlaylistPosition(position: Int) { _selectedPlaylistPosition.value = position }

    /**
     * Восстановление последней песни при запуске: играть продолжает тот список, из которого она была выбрана
     * ([queueIds] из настроек), а не общий список всех песен. Если сохранённого списка нет или в нём уже нет
     * этой песни - общий список.
     */
    fun restoreLastSong(songId: Long, queueIds: List<Long>) {
        val all = getSongs()
        val song = all.find { it.id == songId } ?: return
        val byId = all.associateBy { it.id }
        val queue = queueIds.mapNotNull { byId[it] }
        val playlist = if (queue.any { it.id == songId }) queue else getSortedSongs()
        _currentSong.value = song
        setSongAndPlaylist(SongAndPlaylist(song = song, playlist = playlist))
    }

    /** Запоминает, из какого списка (экрана) выбрана песня: при следующем запуске приложение откроет этот список. */
    fun setPlaybackSource(source: PlaybackSource) = helper.savePlaybackSource(source)

    /** Песни по сохранённым id в том же порядке (те, что пропали из библиотеки, пропускаются). */
    fun songsFromIds(ids: List<Long>): List<Song> {
        val byId = getSongs().associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** После обновления библиотеки берём свежий объект текущей песни; воспроизведение и список не трогаем. */
    fun reloadCurrentSong(songId: Long) {
        songs.value?.find { it.id == songId }?.let { _currentSong.value = it }
    }

    fun setCurrentSongById(songId: Long) {
        songs.value?.find { it.id == songId }?.let { song ->
            _currentSong.value = song //передаём сохранённую песню из преференсис

            setSongAndPlaylist( SongAndPlaylist( //передаём плейлист и текущую песню
                song = song,  //текущая песня
                playlist = getSortedSongs() //текущий плейлист
            ))
        }
    }

    fun getIndexOfCurrentSong():Int{
        return indexOfCurrentSong.value
    }


    //обложка выбранной песни на момент выбора. Хранится отдельно, потому что объект Song
    //общий для списка и плеера, и другие места подменяют в нём artUri на лету
    private var selectedOriginalArtUri: String? = null

    fun setSelectedSong(song: Song) {
        selectedOriginalArtUri = song.artUri
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
    /**
     * [onDone] вызывается, когда обложка записана в базу (или сохранять было нечего). Экран замены
     * уходит назад только после этого: список песен папки/альбома/исполнителя при возврате заново
     * читает песни из базы и иначе мог прочитать их до записи, оставив песню без обложки.
     */
    fun updateCoverImageAndSave(uri: Uri, onDone: () -> Unit = {}) {
        Log.d(TAG, "2*** SharedViewModel updateCoverImageAndSave uri = $uri")
        if (_selectedSong.value == null) { onDone(); return }
        // Проверяем, не тот же ли это URI. Сравниваем с обложкой на момент выбора песни, а не с
        // song.artUri: MainActivity подменяет artUri у играющей песни при выборе картинки, и тогда
        // сравнение ошибочно считало обложку неизменённой и она не сохранялась в базу
        val originalUri = selectedOriginalArtUri ?: getDefaultCoverUri().toString()
        if (originalUri == uri.toString()) {
            Log.d(TAG, "  --##-- SharedViewModel updateCoverImageAndSave Same URI, skipping update")
            onDone()
            return // Не обновляем, если URI не изменился
        }
        _coverImageUri.value = uri  //обновляем на экране
        saveCoverToDatabase(uri, onDone)  //обновляем в базе
    }

    fun getDefaultCoverUri(): Uri {
        return "android.resource://${appContext.packageName}/${R.drawable.muz_player3}".toUri()
    }

    fun restoreDefaultCover() {
        _selectedSong.value?.let { song ->
            // Восстанавливаем обложку по умолчанию
            val defaultUri=getDefaultCoverUri()
            _coverImageUri.value=defaultUri
            song.artUri=defaultUri.toString()
        }
    }

    fun saveCoverToDatabase(uri: Uri, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                _selectedSong.value?.let { selectedSong ->
                    Log.d(TAG, "110*** ✅SharedViewModel saveCoverToDatabase uri = $uri")

                    //копия кладётся в постоянное хранилище приложения (filesDir), а не в кэш:
                    //систему можно очистить кэш, и выбранная пользователем обложка пропала бы.
                    //Если копия не удалась, остаётся исходный uri, а не null
                    val finalArt = copyCoverOrKeepUri(appContext, uri, "song", selectedSong.id)
                    Log.d(TAG, "111*** ✅SharedViewModel saveCoverToDatabase finalArt = $finalArt  uri = $uri")

                    //записываем путь к файлу обложки в базу
                    repository.updateCoverPath(selectedSong.id, finalArt)
                    _coverImageUri.value = finalArt.toUri() //повторно? перезаписываем _coverImageUri

                    //обновляем песню во всех списках: общем, отфильтрованном, выбранной и текущей
                    patchSong(selectedSong.id) { it.copy(artUri = finalArt) }
                }
            } finally {
                onDone()
            }
        }
    }


    /**
     * Единое место, где песня обновляется во всех списках сразу: общем, отфильтрованном,
     * выбранной и текущей. Раньше этот блок из четырёх обновлений копировался в трёх функциях.
     */
    private fun patchSong(songId: Long, transform: (Song) -> Song) {
        fun patch(list: List<Song>?) = list?.map { if (it.id == songId) transform(it) else it }
        _songs.value = patch(_songs.value)
        _filteredSongs.value = patch(_filteredSongs.value)
        _selectedSong.value?.let { if (it.id == songId) _selectedSong.value = transform(it) }
        _currentSong.value?.let { if (it.id == songId) _currentSong.value = transform(it) }
    }

    /** Обложка, скачанная вместе с треком: пишем в базу и обновляем песню во всех списках. */
    fun setSongCover(songId: Long, coverPath: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateCoverPath(songId, coverPath)
                patchSong(songId) { it.copy(artUri = coverPath) }
            } catch (e: Exception) {
                Log.d(TAG, "SharedViewModel setSongCover error = ${e.message}")
            }
            onDone()
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

                // Обновляем во всех списках: общем, отфильтрованном, выбранной и текущей песне
                patchSong(songId) {
                    it.copy(
                        title = title,
                        artist = artist,
                        albumName = album,
                        author = author,
                        genre = genre,
                        year = year
                    )
                }
                // Уведомляем об успешном обновлении
                //_message.value = "Информация обновлена"
                messenger.show("Информация обновлена")
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

    fun loadAllSongsForAdding() {
        viewModelScope.launch {
            val allSongs =  repository.getSongsFromDatabase()
            _libraryLoaded.value = true
            _songs.value = allSongs
            _filteredSongs.value = allSongs
        }
    }

    //принудительное обновление обложек


    fun updateSongArtUri(context: Context, track:Song) {
        viewModelScope.launch {
            //сохраняем картинку из ресурсов в кэш и получаем ссылку
            val newArtUri = saveDefaultCoverToCache(context)
            track.artUri = newArtUri
            patchSong(track.id) { it.copy(artUri = newArtUri) }

            Log.d(TAG, " **??** SharedViewModel updateSongArtUri newArtUri $newArtUri")
            try {
                repository.updateSongArtUri(track.id, newArtUri)
                Log.d(TAG, "✅SharedViewModel updateSongArtUri Обновлён artUri для песни ${track.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌SharedViewModel updateSongArtUri Ошибка обновления artUri: ${e.message}")
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
