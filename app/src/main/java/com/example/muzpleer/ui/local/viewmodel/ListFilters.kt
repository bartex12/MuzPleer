package com.example.muzpleer.ui.local.viewmodel

import android.content.Context
import android.net.Uri
import com.example.muzpleer.model.Song
import com.example.muzpleer.util.copyCoverToAppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Общие функции для ViewModel, чтобы не копировать один и тот же код в каждой из них.
 */

/**
 * Фильтрация списка по поисковому запросу: пустой запрос возвращает весь список,
 * иначе остаются элементы, у которых хотя бы одно из [fields] содержит запрос (без учёта регистра).
 */
internal fun <T> List<T>.filterByQuery(query: String, vararg fields: (T) -> String): List<T> {
    if (query.isEmpty()) return toList()
    val searchQuery = query.lowercase(Locale.getDefault())
    return filter { item ->
        fields.any { field -> field(item).lowercase(Locale.getDefault()).contains(searchQuery) }
    }
}

/** Фильтрация списка песен по названию и исполнителю. */
internal fun List<Song>.filterSongsByQuery(query: String): List<Song> =
    filterByQuery(query, Song::title, Song::artist)

/**
 * Копирует выбранную картинку в память приложения (временный content-URI из picker позже
 * перестаёт читаться) и возвращает значение для записи в базу: путь к копии или,
 * если скопировать не удалось, сам URI, как раньше.
 */
internal suspend fun copyCoverOrKeepUri(context: Context, uri: Uri, prefix: String, id: Long): String {
    val appContext = context.applicationContext
    val savedPath = withContext(Dispatchers.IO) {
        copyCoverToAppStorage(appContext, uri, prefix, id)
    }
    return savedPath ?: uri.toString()
}
