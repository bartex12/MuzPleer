package com.example.muzpleer.ui.local.helper

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import com.example.muzpleer.R

/** Окно-подсказка «Как скачать картинку» для экранов замены обложки. */
fun showCoverHintDialog(context: Context) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_cover_hint, null)
    val dialog = AlertDialog.Builder(context).setView(view).create()
    dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    view.findViewById<android.view.View>(R.id.btnHintClose).setOnClickListener { dialog.dismiss() }
    dialog.show()
}
