package com.example.muzpleer.util

import com.example.muzpleer.model.Song

/** По какому полю отсортирован список песен; NONE - порядок вкладки по умолчанию. */
enum class SongSortField { NONE, TITLE, ALBUM, ARTIST, ALBUM_COUNT, COUNT, PATH, DURATION, DATE_ADDED }

/**
 * Сортировка списка песен. [datesAdded] - дата добавления файла по id песни (нужна для DATE_ADDED).
 * Для NONE список возвращается как есть. По убыванию порядок переворачивается целиком.
 */
fun sortSongsBy(
    songs: List<Song>,
    field: SongSortField,
    ascending: Boolean,
    datesAdded: Map<Long, Long> = emptyMap()
): List<Song> {
    val result = when (field) {
        SongSortField.NONE -> return songs
        SongSortField.TITLE -> sortByText(songs) { it.title }
        SongSortField.ALBUM -> sortByText(songs) { it.albumName.orEmpty() }
        SongSortField.ARTIST -> sortByText(songs) { it.artist }
        SongSortField.PATH -> sortByText(songs) { getNormalizedPath(it.mediaUri) }
        SongSortField.ALBUM_COUNT, SongSortField.COUNT -> return songs
        SongSortField.DURATION -> songs.sortedBy { it.duration }
        SongSortField.DATE_ADDED -> songs.sortedBy { datesAdded[it.id] ?: 0L }
    }
    return if (ascending) result else result.reversed()
}

/**
 * Сортировка альбомов, плейлистов, папок, исполнителей: по названию, числу треков (альбомов), месту в памяти или общей продолжительности.
 * Для NONE и неподходящих полей список возвращается как есть.
 */
fun <T> sortItemsBy(
    items: List<T>,
    field: SongSortField,
    ascending: Boolean,
    title: (T) -> String,
    count: (T) -> Int,
    duration: (T) -> Long,
    path: (T) -> String = { "" },
    albumCount: (T) -> Int = { 0 }
): List<T> {
    val result = when (field) {
        SongSortField.TITLE -> sortByText(items, title)
        SongSortField.COUNT -> items.sortedBy(count)
        SongSortField.ALBUM_COUNT -> items.sortedBy(albumCount)
        SongSortField.PATH -> sortByText(items, path)
        SongSortField.DURATION -> items.sortedBy(duration)
        else -> return items
    }
    return if (ascending) result else result.reversed()
}

//текст: сначала русские буквы, потом латинские, потом всё остальное (цифры и знаки); внутри групп по алфавиту
private fun <T> sortByText(songs: List<T>, text: (T) -> String): List<T> =
    songs.map { song ->
        val t = text(song).trim()
        val first = t.firstOrNull()
        val group = when {
            first == null -> 3
            first in 'а'..'я' || first in 'А'..'Я' || first == 'ё' || first == 'Ё' -> 0
            first in 'a'..'z' || first in 'A'..'Z' -> 1
            else -> 2
        }
        Triple(group, t.lowercase(), song)
    }.sortedWith(compareBy<Triple<Int, String, T>>({ it.first }, { it.second })).map { it.third }
