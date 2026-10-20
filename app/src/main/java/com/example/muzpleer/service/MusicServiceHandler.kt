package com.example.muzpleer.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.example.muzpleer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Управляет воспроизведением. Плеер ([player]) общий на всё приложение и принадлежит не экрану, а процессу:
 * его же использует [PlaybackService] (уведомление, управление с экрана блокировки и наушников),
 * поэтому музыка не прерывается, когда приложение закрыто.
 */
class MusicServiceHandler(
    private val context: Context,
    private val player: ExoPlayer,
    internal var callback: PlayerCallback
) {
    //подключение к службе: пока оно есть, система не останавливает службу, а служба показывает уведомление
    private var controllerFuture: ListenableFuture<MediaController>? = null
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

    private val playerListener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    callback.onPlaybackStateChanged(isPlaying)
                    //позицию опрашиваем только пока играет музыка
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
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
                        // трек готов: сообщаем длительность, в том числе когда стоит пауза
                        Player.STATE_READY -> emitPosition()
                    }
                }
    }

    init {
        player.addListener(playerListener)
        //начальное состояние; дальше позиция опрашивается только во время воспроизведения
        emitPosition()
    }

    /**
     * Сообщает экрану текущее состояние плеера. Нужно, когда приложение открыли заново, а музыка
     * в это время уже играла (плеер жил в службе).
     */
    fun syncState() {
        callback.onPlaybackStateChanged(player.isPlaying)
        emitPosition()
    }

    fun setPlaylist(tracks: List<Song>, startIndex: Int = 0) {
        // Проверяем, не тот ли это же плейлист и тот же трек
        if (playlist == tracks && currentIndex == startIndex && player.isPlaying == true) {
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
        val uri = track.getContentUri()
        val mediaItem = buildMediaItem(track, uri)
        Log.d(TAG, "@@@MusicServiceHandler playTrack:mediaUri = ${track.mediaUri} uri = $uri artUri = ${track.artUri}")
        connectToService() //служба и уведомление нужны с первого запуска музыки
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        callback.onTrackChanged(track)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Трек с названием, исполнителем и обложкой: их показывает уведомление и экран блокировки. */
    private fun buildMediaItem(track: Song, uri: Uri): MediaItem {
        val artwork = track.artUri?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("/")) Uri.fromFile(java.io.File(it)) else Uri.parse(it)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(artwork)
            .build()
        return MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build()
    }

    /**
     * Подключается к [PlaybackService]: система создаёт службу и держит её, а при начале воспроизведения
     * служба сама переходит в режим с уведомлением. Подключение делаем один раз.
     */
    private fun connectToService() {
        if (controllerFuture != null) return
        try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, token).buildAsync()
        } catch (e: Exception) {
            Log.d(TAG, "MusicServiceHandler connectToService error = ${e.message}")
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
        player.seekTo(position)
        emitPosition() //на паузе опроса нет, поэтому новую позицию сообщаем сразу
    }

    fun getCurrentPosition(): Long = player.currentPosition

    private fun emitPosition() {
        callback.onPositionChanged(player.currentPosition, player.duration)
    }

    private fun startPositionUpdates() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                emitPosition()
                delay(1000) // Обновляем позицию каждую секунду, пока играет музыка
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        emitPosition() //последнее значение на момент паузы
    }

    /** Плеер здесь не освобождается: он общий и живёт, пока работает приложение или служба. */
    fun release() {
        positionUpdateJob?.cancel()
        player.removeListener(playerListener)
    }

    companion object{
        const val TAG = "33333"
    }
}

