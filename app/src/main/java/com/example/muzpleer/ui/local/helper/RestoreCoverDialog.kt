package com.example.muzpleer.ui.local.helper

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import com.example.muzpleer.R

/**
 * Окно «Вернуться к изображению по умолчанию?» с картинкой, которая будет возвращена.
 * Слева «Да», справа «Отмена»; [onConfirm] вызывается только после «Да».
 */
fun showRestoreCoverDialog(context: Context, onConfirm: () -> Unit) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_restore_cover, null)
    val dialog = AlertDialog.Builder(context).setView(view).create()
    dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    view.findViewById<android.view.View>(R.id.btnRestoreYes).setOnClickListener {
        dialog.dismiss()
        onConfirm()
    }
    view.findViewById<android.view.View>(R.id.btnRestoreCancel).setOnClickListener { dialog.dismiss() }
    dialog.show()
}
