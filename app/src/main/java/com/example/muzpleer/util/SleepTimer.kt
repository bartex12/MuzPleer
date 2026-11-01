package com.example.muzpleer.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.exoplayer.ExoPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Таймер остановки воспроизведения: по окончании времени ставит плеер на паузу.
 * Остаток считается по elapsedRealtime, время остановки (по часам) запоминается, чтобы пережить перезапуск приложения.
 */
object SleepTimer {
    private const val PREFS = "sleep_timer"
    private const val KEY_END = "end_wall"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var endElapsed = 0L

    /** Когда таймер остановит музыку, по часам (мс); null - таймер не запущен. */
    var endWall: Long? = null
        private set

    private val _remaining = MutableLiveData<Long?>(null)
    /** Остаток в мс, обновляется раз в секунду; null - таймер не запущен. */
    val remaining: LiveData<Long?> = _remaining

    private val tick = object : Runnable {
        override fun run() {
            val left = endElapsed - SystemClock.elapsedRealtime()
            if (left <= 0) {
                fire()
            } else {
                _remaining.value = left
                //следующий шаг - на границе секунды остатка, чтобы цифры менялись ровно раз в секунду
                val delay = (left % 1000).let { if (it == 0L) 1000L else it }
                handler.postDelayed(this, delay)
            }
        }
    }

    fun isActive(): Boolean = endWall != null

    /** Запустить (или перезапустить) таймер на [millis]. */
    fun start(context: Context, millis: Long) {
        appContext = context.applicationContext
        handler.removeCallbacks(tick)
        endElapsed = SystemClock.elapsedRealtime() + millis
        endWall = System.currentTimeMillis() + millis
        save()
        tick.run()
    }

    fun cancel(context: Context? = null) {
        context?.let { appContext = it.applicationContext }
        handler.removeCallbacks(tick)
        endWall = null
        save()
        _remaining.value = null
    }

    /** После запуска приложения: возвращаем таймер, если его время ещё не вышло. */
    fun restore(context: Context) {
        appContext = context.applicationContext
        if (endWall != null) return
        val end = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_END, 0L)
        val left = end - System.currentTimeMillis()
        if (end > 0 && left > 0) {
            handler.removeCallbacks(tick)
            endElapsed = SystemClock.elapsedRealtime() + left
            endWall = end
            tick.run()
        } else if (end > 0) {
            save() //время вышло, пока приложение было закрыто
        }
    }

    private fun fire() {
        handler.removeCallbacks(tick)
        endWall = null
        save()
        _remaining.value = null
        try {
            org.koin.core.context.GlobalContext.get().get<ExoPlayer>().pause()
        } catch (e: Exception) {
            android.util.Log.e("SleepTimer", "pause failed: ${e.message}")
        }
        appContext?.let { Toast.makeText(it, "Таймер остановки: воспроизведение остановлено", Toast.LENGTH_LONG).show() }
    }

    private fun save() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putLong(KEY_END, endWall ?: 0L)?.apply()
    }

    /** Остаток в виде ч:мм:сс (секунды округляются вверх). */
    fun formatRemaining(ms: Long): String {
        val total = (ms + 999) / 1000
        return String.format(Locale.getDefault(), "%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60)
    }

    /** Время остановки по часам: чч:мм:сс. */
    fun formatEnd(wallMs: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(wallMs))
}
