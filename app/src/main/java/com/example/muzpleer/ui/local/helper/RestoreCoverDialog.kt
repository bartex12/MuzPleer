package com.example.muzpleer.ui.local.helper

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.example.muzpleer.R
import java.io.File

/**
 * Окно «Вернуться к изображению по умолчанию?»: слева текущая картинка [current], справа та,
 * что будет возвращена. Слева «Да», справа «Отмена»; [onConfirm] вызывается только после «Да».
 */
fun showRestoreCoverDialog(context: Context, current: Uri?, onConfirm: () -> Unit) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_restore_cover, null)
    val ivCurrent = view.findViewById<ImageView>(R.id.ivRestoreCurrent)
    if (current != null) {
        //путь без схемы - файл в кэше приложения, остальное (content, android.resource, file) Glide читает сам
        val model: Any = if (current.scheme == null) File(current.toString()) else current
        Glide.with(context).load(model)
            .placeholder(R.drawable.muz_player3).error(R.drawable.muz_player3)
            .into(ivCurrent)
    }
    val dialog = AlertDialog.Builder(context).setView(view).create()
    dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    view.findViewById<android.view.View>(R.id.btnRestoreYes).setOnClickListener {
        dialog.dismiss()
        onConfirm()
    }
    view.findViewById<android.view.View>(R.id.btnRestoreCancel).setOnClickListener { dialog.dismiss() }
    dialog.show()
}
