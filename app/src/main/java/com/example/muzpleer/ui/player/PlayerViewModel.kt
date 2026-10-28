package com.example.muzpleer.ui.player

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.muzpleer.model.Song
import com.example.muzpleer.service.MusicServiceHandler

/**
 * Всё, что относится к воспроизведению: управление ExoPlayer, состояние (играет/пауза),
 * позиция и длительность трека, ошибки плеера, режим shuffle и видимость мини-плеера.
 * Раньше это жило в SharedViewModel.
 *
 * О смене трека плеер сообщает через [onTrackChangedListener] - его выставляет MainActivity
 * и пробрасывает в SharedViewModel.onTrackChanged (там хранятся список песен и текущая песня).
 */
class PlayerViewModel(
    private val playerHandler: MusicServiceHandler
) : ViewModel(), MusicServiceHandler.PlayerCallback {

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData<Long>(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _duration = MutableLiveData<Long>(0L)
    val duration: LiveData<Long> = _duration

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _playerVisibility = MutableLiveData<Boolean>(true)
    val playerVisibility: LiveData<Boolean> = _playerVisibility

    /** Вызывается при смене трека плеером. Без повторной доставки, в отличие от LiveData. */
    var onTrackChangedListener: ((Song) -> Unit)? = null
        set(value) {
            field = value
            //приложение открыли заново, пока музыка играет в фоне: сразу сообщаем, какой трек сейчас в плеере
            if (value != null) playerHandler.currentTrack()?.let(value)
        }

    init {
        playerHandler.callback = this  //иначе не работает
        playerHandler.syncState()      //приложение могли открыть заново, пока музыка играет
        Log.d(TAG, "PlayerViewModel init: playerHandler = $playerHandler")
    }

    /** В плеере уже есть трек (музыка играла в фоне, пока приложение было закрыто). */
    fun hasCurrentTrack(): Boolean = playerHandler.currentTrack() != null

    fun setPlayerVisibility(visible: Boolean) {
        _playerVisibility.value = visible
    }

    fun setPlaylistForHandler(playlist: List<Song>, initialIndex: Int = 0) {
        playerHandler.setPlaylist(playlist, initialIndex)
    }

    /** Остановить музыку и очистить очередь (например, удалён плейлист, из которого играла песня). */
    fun stopAndClear() {
        playerHandler.stopAndClear()
    }

    /** Очередь воспроизведения и номер играющей песни в ней. */
    fun queue(): List<Song> = playerHandler.queue()
    fun currentQueueIndex(): Int = playerHandler.currentIndex()
    fun moveQueueTrack(from: Int, to: Int) = playerHandler.moveTrack(from, to)
    fun playQueueTrack(index: Int) = playerHandler.playTrack(index)
    /** Привести очередь к списку [tracks] (например, после правки плейлиста), не прерывая играющую песню. */
    fun syncQueue(tracks: List<Song>) = playerHandler.replaceQueue(tracks)

    fun togglePlayPause() {
        playerHandler.togglePlayPause()
    }

    fun playNext() {
        Log.d(TAG, "PlayerViewModel playNext ")
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

    /** Включён ли случайный порядок на самом деле (состояние плеера, а не значка). */
    fun isShuffleMode(): Boolean = playerHandler.isShuffleMode

    fun setPlaybackMode(): Boolean {
        // Меняем состояние на противоположное
        val newShuffleState = !playerHandler.isShuffleMode
        playerHandler.isShuffleMode = newShuffleState
        return newShuffleState
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onTrackChanged(track: Song) {
        onTrackChangedListener?.invoke(track)
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

    override fun onCleared() {
        super.onCleared()
        onTrackChangedListener = null
        //обработчик общий и живёт дальше (музыка продолжает играть): только отвязываем себя от него
        if (playerHandler.callback === this) playerHandler.callback = NoCallback
    }

    /** Заглушка на время, пока нет ни одного экрана. */
    private object NoCallback : MusicServiceHandler.PlayerCallback {
        override fun onTrackChanged(track: Song) {}
        override fun onPlaybackStateChanged(isPlaying: Boolean) {}
        override fun onPositionChanged(position: Long, duration: Long) {}
        override fun onError(message: String) {}
    }

    companion object {
        const val TAG = "33333"
    }
}
