package com.example.muzpleer.service

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.muzpleer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MusicServiceHandler(
    private val context: Context,
    internal var callback: PlayerCallback
) {
    private var player: ExoPlayer? = null
    internal var playlist: List<Song> = emptyList()
    private var currentIndex = 0
    private var positionUpdateJob: Job? = null

    // 1. Новые переменные для управления Shuffle-режимом
    var isShuffleMode: Boolean = false
        set(value) {
            field = value
            if (value && playlist.isNotEmpty()) {
                generateShuffledIndices()
            }
        }
    private var shuffledIndices: List<Int> = emptyList()

    interface PlayerCallback {
        fun onTrackChanged(track: Song)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onPositionChanged(position: Long, duration: Long)
        fun onError(message: String)
    }

    init {  initializePlayer() }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(context).build().apply {

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    callback.onPlaybackStateChanged(isPlaying)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Убираем отсюда дублирующий вызов playNext(),
                    // так как STATE_ENDED ниже уже обрабатывает окончание трека.
//                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
//                        playNext()
//                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    callback.onError("Playback error: ${error.message}")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when(playbackState){
                        // Автопереключение при окончании трека
                        Player.STATE_ENDED ->{
                            playNext()
                        }
                    }
                }
            })
        }
        startPositionUpdates()
    }

    fun setPlaylist(tracks: List<Song>, startIndex: Int = 0) {
        // Проверяем, не тот ли это же плейлист и тот же трек
        if (playlist == tracks && currentIndex == startIndex && player?.isPlaying == true) {
            // Уже играет этот же трек, ничего не делаем
            Log.d(TAG, "@@@MusicServiceHandler setPlaylist: тот же трек уже играет, пропускаем")
            return
        }
        playlist = tracks
        currentIndex = startIndex
        // 2. Если включен Shuffle, генерируем новый порядок при установке плейлиста
        if (isShuffleMode) {
            generateShuffledIndices()
        }
        playTrack(startIndex)
    }

    // 3. Вспомогательный метод для генерации случайной последовательности
    private fun generateShuffledIndices() {
        if (playlist.isEmpty()) return
        // Создаем список индексов [0, 1, 2...] и перемешиваем его
        val indices = playlist.indices.toMutableList()
        indices.remove(currentIndex) // Удаляем текущий трек, чтобы перемешать остальные
        indices.shuffle()
        indices.add(0, currentIndex) // Ставим текущий трек на первое место, чтобы он не прервался
        shuffledIndices = indices
    }

    fun playTrack(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        val track = playlist[index]
        Log.d(TAG, "@@@MusicServiceHandler playTrack: index = $index  track = ${track.title}")
        player?.let { p ->
            val uri = track.getContentUri()
            val mediaItem =MediaItem.fromUri(uri)
            Log.d(TAG, "@@@MusicServiceHandler playTrack:mediaUri = ${track.mediaUri} uri = $uri artUri = ${track.artUri}")
            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
            callback.onTrackChanged(track)
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    // 4. Обновленная логика «Вперед» с учетом Shuffle
    fun playNext() {
        if (playlist.isEmpty()) return

        if (isShuffleMode) {
            val currentShufflePosition = shuffledIndices.indexOf(currentIndex)
            if (currentShufflePosition < shuffledIndices.size - 1) {
                // Играем следующий случайный трек
                playTrack(shuffledIndices[currentShufflePosition + 1])
            } else {
                // Плейлист закончился. Перемешиваем заново и включаем первый трек из нового списка
                generateShuffledIndices()
                playTrack(shuffledIndices[0])
            }
        } else {
            // Обычный прямой порядок
            if (currentIndex < playlist.size - 1) {
                playTrack(currentIndex + 1)
            } else {
                playTrack(0)
            }
        }
    }

    // 5. Обновленная логика «Назад» с учетом Shuffle
    fun playPrevious() {
        if (playlist.isEmpty()) return

        if (isShuffleMode) {
            val currentShufflePosition = shuffledIndices.indexOf(currentIndex)
            if (currentShufflePosition > 0) {
                // Возвращаемся по истории случайного порядка назад
                playTrack(shuffledIndices[currentShufflePosition - 1])
            } else {
                // Если мы в самом начале shuffle-списка, переходим в конец
                playTrack(shuffledIndices.last())
            }
        } else {
            // Обычный прямой порядок
            if (currentIndex > 0) {
                playTrack(currentIndex - 1)
            } else {
                playTrack(playlist.size - 1) // Опционально: переход к концу, если нажали назад на 1-м треке
            }
        }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                player?.let {
                    callback.onPositionChanged(it.currentPosition, it.duration)
                }
                delay(1000) // Обновляем позицию каждую секунду
            }
        }
    }

    fun release() {
        positionUpdateJob?.cancel()
        player?.release()
        player = null
    }

    companion object{
        const val TAG = "33333"
    }
}

