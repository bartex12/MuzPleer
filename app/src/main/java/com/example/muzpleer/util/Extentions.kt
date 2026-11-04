package com.example.muzpleer.util


import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

fun Long.formatDuration(): String {
    val seconds = this / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
    }
}


/** Суммарное время для подписей списков: 3:34 или 1:12:24 (минуты без ведущего нуля). */
fun Long.formatTotalDuration(): String {
    val seconds = coerceAtLeast(0L) / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val sec = seconds % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, sec)
    else String.format(Locale.getDefault(), "%d:%02d", minutes, sec)
}

fun Long.formatAsTime(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

fun Fragment.toast(string: String?) {
    Toast.makeText(context, string, Toast.LENGTH_SHORT).apply {
        setGravity(Gravity.CENTER,0,0)
        show()
    }
}

fun Activity.toast(string: String?) {
    Toast.makeText(this, string, Toast.LENGTH_SHORT).apply {
        setGravity(Gravity.CENTER,0,0)
        show()
    }
}

fun Fragment.snackBarLong(view: View, string: CharSequence) {
    Snackbar.make(view, string, Snackbar.LENGTH_LONG).show()
}
fun Fragment.snackBarShort(view: View, string: CharSequence) {
    Snackbar.make(view, string, Snackbar.LENGTH_SHORT).show()
}