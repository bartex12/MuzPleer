package com.example.muzpleer.ui.local.helper

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import com.example.muzpleer.R
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.util.formatDuration
import com.google.android.material.button.MaterialButton

/**
 * Окно «Длительность плейлиста» (часы, минуты, секунды) для пункта «Добавить треки по времени».
 * Показывает текущую длительность плейлиста и принимает только большее время.
 * [onConfirm] получает заданную длительность в миллисекундах.
 */
fun showTimeLimitDialog(context: Context, playlist: Playlist, onConfirm: (Long) -> Unit) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_time_limit, null)
    val etHours = dialogView.findViewById<EditText>(R.id.etHours)
    val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)
    val etSeconds = dialogView.findViewById<EditText>(R.id.etSeconds)
    val currentMs = playlist.playlistSongs.sumOf { it.duration.coerceAtLeast(0L) }
    dialogView.findViewById<TextView>(R.id.tvTimeCurrent).text =
        "Сейчас в плейлисте: ${currentMs.formatDuration()}"
    val dialog = AlertDialog.Builder(context).setView(dialogView).create()
    dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

    dialogView.findViewById<MaterialButton>(R.id.btnTimeOk).setOnClickListener {
        val h = etHours.text.toString().toLongOrNull() ?: 0L
        val m = etMinutes.text.toString().toLongOrNull() ?: 0L
        val s = etSeconds.text.toString().toLongOrNull() ?: 0L
        val totalMs = (h * 3600 + m * 60 + s) * 1000
        if (totalMs <= currentMs) {
            Toast.makeText(
                context,
                "Задайте время больше текущего (${currentMs.formatDuration()})",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            dialog.dismiss()
            onConfirm(totalMs)
        }
    }
    dialogView.findViewById<MaterialButton>(R.id.btnTimeCancel).setOnClickListener { dialog.dismiss() }
    dialog.show()
}
