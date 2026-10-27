package com.example.muzpleer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.muzpleer.model.Song
import java.io.File

/** Отправить файл песни в другое приложение (то же, что пункт «Поделиться» в меню строки песни). */
fun shareSong(context: Context, song: Song) {
    try {
        val file = File(song.mediaUri)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, "Трек: ${song.title}")
        }
        val chooserIntent = Intent.createChooser(shareIntent, "Поделиться треком")

        // Временные права доступа к файлу для приложений из списка
        val resInfoList = context.packageManager
            .queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            context.grantUriPermission(
                resolveInfo.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        Log.d("ShareSong", "Ошибка при отправке песни: ${e.message}")
        Toast.makeText(context, "Не удалось поделиться треком", Toast.LENGTH_SHORT).show()
    }
}
