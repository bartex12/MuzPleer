package com.example.muzpleer.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.koin.android.ext.android.inject

/**
 * Служба воспроизведения. Владеет медиа-сессией общего плеера и показывает уведомление с названием,
 * исполнителем, обложкой и кнопками управления. Благодаря ей музыка не прерывается, когда приложение
 * закрыто, а управлять ею можно с шторки уведомлений, экрана блокировки и наушников.
 *
 * Сам плеер ([ExoPlayer]) создаётся в Koin и принадлежит процессу приложения, поэтому служба его не
 * освобождает - только закрывает свою сессию.
 */
class PlaybackService : MediaSessionService() {

    private val player: ExoPlayer by inject()
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        //нажатие на уведомление открывает приложение
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        mediaSession = MediaSession.Builder(this, player)
            .apply { openApp?.let { setSessionActivity(it) } }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Приложение смахнули из списка недавних: если музыка не играет, служба не нужна. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
