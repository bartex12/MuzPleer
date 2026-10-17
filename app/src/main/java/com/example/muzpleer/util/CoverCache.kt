package com.example.muzpleer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.example.muzpleer.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

/**
 * Копирует выбранную пользователем картинку (content:// из галереи/picker) в постоянное
 * хранилище приложения и возвращает путь к файлу, либо null при ошибке.
 *
 * Зачем: content-URI из picker выдаётся с временным разрешением и через какое-то время
 * перестаёт читаться, поэтому сохранять в базу сам URI нельзя. Файл кладём во filesDir
 * (не в cacheDir, который система может очистить). Имя уникально для каждой замены.
 * Вызывать не на главном потоке.
 */
fun copyCoverToAppStorage(context: Context, sourceUri: android.net.Uri, prefix: String, id: Long): String? {
    return try {
        val dir = File(context.filesDir, "level_covers")
        if (!dir.exists()) dir.mkdirs()
        val destinationFile = File(dir, "${prefix}_${id}_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(sourceUri) ?: return null
        input.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        Log.e("33333", "❌ copyCoverToAppStorage Ошибка копирования обложки: ${e.message}")
        null
    }
}

/** Копирует картинку обложки песни в кэш приложения, возвращает путь к файлу или null при ошибке. */
fun copyAlbumArtToCache(context: Context, sourceUri: Uri): String? {
    try {
        // 1. Создаем директорию album_art в кэше, если её нет
        val cacheDir = File(context.cacheDir, "album_art")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        // 2. Генерируем уникальное имя файла на основе хэш-кода пути URI
        val fileName = "embedded_${sourceUri.toString().hashCode()}.jpg"
        val destinationFile = File(cacheDir, fileName)

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            // Убеждаемся что файл создан
            if (!destinationFile.exists() && !destinationFile.createNewFile()) {
                throw IOException("Failed to create cover file")
            }

            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
        }

        // 4. Возвращаем абсолютный путь для сохранения в БД
        val absolutePath = destinationFile.absolutePath
        Log.d("33333", "copyAlbumArtToCache Файл успешно сохранен в кэш: $absolutePath")
        return absolutePath

    } catch (e: Exception) {
        Log.e("33333", " ❌copyAlbumArtToCache Ошибка при копировании обложки в кэш", e)
        return null
    }
}

/** true, если [path] - путь к существующему локальному файлу (а не пустая строка, content-URI или потерянный файл). */
fun isExistingLocalFile(path: String?): Boolean =
    !path.isNullOrBlank() && path.startsWith("/") && File(path).exists()
