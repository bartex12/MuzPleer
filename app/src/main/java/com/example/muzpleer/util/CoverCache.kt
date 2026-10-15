package com.example.muzpleer.util

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.example.muzpleer.R
import java.io.File
import java.io.FileOutputStream

/**
 * Сохраняет картинку-заглушку (R.drawable.muz_player3) в кэш и возвращает путь к файлу.
 * Вынесено из SharedViewModel, чтобы им могли пользоваться и другие ViewModel.
 */
fun saveDefaultCoverToCache(context: Context): String? {
    // 1. Формируем правильный Uri для ресурса R.drawable.muz_player3
    val resId = R.drawable.muz_player3
    val sourceUri = "android.resource://${context.packageName}/$resId".toUri()

    // 2. Создаем директорию album_art в кэше, если её нет
    val cacheDir = File(context.cacheDir, "album_art")
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }

    // 3. Формируем имя и итоговый файл
    val fileName = "embedded_${sourceUri.toString().hashCode()}.jpg"
    val destinationFile = File(cacheDir, fileName)

    // Если файл уже был сохранен ранее, просто возвращаем путь
    if (destinationFile.exists()) {
        Log.d("33333", "!! saveDefaultCoverToCache - файл уже был сохранен ранее")
        return destinationFile.absolutePath
    }

    // 4. Читаем ресурс и записываем его на диск
    return try {
        context.contentResolver.openInputStream(sourceUri).use { inputStream ->
            if (inputStream == null) return null

            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null // Возвращаем null в случае ошибки ввода-вывода
    }
}
