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

    init {
        playerHandler.callback = this  //иначе не работает
        Log.d(TAG, "PlayerViewModel init: playerHandler = $playerHandler")
    }

    fun setPlayerVisibility(visible: Boolean) {
        _playerVisibility.value = visible
    }

    fun setPlaylistForHandler(playlist: List<Song>, initialIndex: Int = 0) {
        playerHandler.setPlaylist(playlist, initialIndex)
    }

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
        playerHandler.release()
    }

    companion object {
        const val TAG = "33333"
    }
}
