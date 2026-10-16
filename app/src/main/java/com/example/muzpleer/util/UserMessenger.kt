package com.example.muzpleer.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Показ коротких сообщений пользователю. ViewModel и репозитории работают через этот интерфейс,
 * а не через Toast и App.instance напрямую: они не зависят от Android UI, и сообщения
 * можно подменить в тестах или направить в другое место (Snackbar и т.п.).
 */
interface UserMessenger {
    fun show(text: String, long: Boolean = false)
}

/** Реализация на Toast. Сообщение всегда выводится в главном потоке. */
class ToastMessenger(private val context: Context) : UserMessenger {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun show(text: String, long: Boolean) {
        val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, text, duration).show()
        } else {
            mainHandler.post { Toast.makeText(context, text, duration).show() }
        }
    }
}
