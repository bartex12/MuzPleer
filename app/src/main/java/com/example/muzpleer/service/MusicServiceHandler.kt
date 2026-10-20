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
    //список песен, которые лежат в плеере (порядок совпадает с очередью плеера)
    internal var playlist: List<Song> = emptyList()
    private var positionUpdateJob: Job? = null

    /** Случайный порядок включает сам плеер: так "следующий" из уведомления и наушников тоже его учитывает. */
    var isShuffleMode: Boolean
        get() = player.shuffleModeEnabled
        set(value) { player.shuffleModeEnabled = value }

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
                    //смена трека самим плеером (конец трека, "следующий" из уведомления или наушников);
                    //при замене списка о треке сообщает setPlaylist
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
                    currentTrack()?.let { callback.onTrackChanged(it) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    callback.onError("Playback error: ${error.message}")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    //трек готов: сообщаем длительность, в том числе когда стоит пауза
                    if (playbackState == Player.STATE_READY) emitPosition()
                }
    }

    init {
        //после последнего трека начинаем сначала (как и раньше); следующий трек плеер включает сам
        player.repeatMode = Player.REPEAT_MODE_ALL
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

    /** Трек, который сейчас стоит в плеере (null, если плеер пуст). */
    fun currentTrack(): Song? =
        if (player.mediaItemCount == 0) null else playlist.getOrNull(player.currentMediaItemIndex)

    /** Загружает список песен в плеер и начинает играть с [startIndex]. */
    fun setPlaylist(tracks: List<Song>, startIndex: Int = 0) {
        if (startIndex !in tracks.indices) return
        // Проверяем, не тот ли это же плейлист и тот же трек
        if (playlist == tracks && player.currentMediaItemIndex == startIndex && player.isPlaying) {
            // Уже играет этот же трек, ничего не делаем
            Log.d(TAG, "@@@MusicServiceHandler setPlaylist: тот же трек уже играет, пропускаем")
            return
        }
        connectToService() //служба и уведомление нужны с первого запуска музыки
        if (playlist == tracks && player.mediaItemCount == tracks.size) {
            //тот же список - только переходим к нужному треку
            player.seekTo(startIndex, 0L)
        } else {
            playlist = tracks
            player.setMediaItems(tracks.map { buildMediaItem(it) }, startIndex, 0L)
        }
        player.prepare()
        player.play()
        callback.onTrackChanged(tracks[startIndex])
    }

    /** Переключает на песню с номером [index] в текущем списке. */
    fun playTrack(index: Int) {
        if (index !in playlist.indices) return
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
        callback.onTrackChanged(playlist[index])
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Трек с названием, исполнителем и обложкой: их показывает уведомление и экран блокировки. */
    private fun buildMediaItem(track: Song): MediaItem {
        val uri = track.getContentUri()
        val artwork = track.artUri?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("/")) Uri.fromFile(java.io.File(it)) else Uri.parse(it)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(artwork)
            .build()
        return MediaItem.Builder().setMediaId(track.id.toString()).setUri(uri).setMediaMetadata(metadata).build()
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

    /** "Вперёд" с учётом случайного порядка; после последнего трека - первый. */
    fun playNext() {
        if (player.mediaItemCount == 0) return
        player.seekToNextMediaItem()
        player.play()
    }

    /** "Назад" с учётом случайного порядка; перед первым треком - последний. */
    fun playPrevious() {
        if (player.mediaItemCount == 0) return
        player.seekToPreviousMediaItem()
        player.play()
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

