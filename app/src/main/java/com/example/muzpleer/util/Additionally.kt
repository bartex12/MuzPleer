package com.example.muzpleer.util

import android.net.Uri
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzpleer.model.Album
import com.example.muzpleer.model.Artist
import com.example.muzpleer.model.Folder
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.model.Song
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG = "33333"

//вариант для всех провайдеров
fun isContentProviderUri(uriString: String): Boolean {
    return uriString.startsWith("content://com.android.providers")
}

//вариант для picker
fun isContentProviderUriPicker(uriString: String): Boolean {
    return uriString.startsWith("content://media/picker_get_content")
}

fun getTracksCountString(count: Int): String {
    return when {
        count % 100 in 11..14 -> "$count треков"
        count % 10 == 1 -> "$count трек"
        count % 10 in 2..4 -> "$count трека"
        else -> "$count треков"
    }
}

fun getAlbumsCountString(count: Int): String {
    return when {
        count % 100 in 11..14 -> "$count альбомов "
        count % 10 == 1 -> "$count альбом"
        count % 10 in 2..4 -> "$count альбома"
        else -> "$count альбомов"
    }
}

fun getSortedDataFolder( folders:List<Folder>):List<Folder>{
    return folders.sortedWith(compareBy(
        { folder -> when {
            folder.name.matches(Regex("^[а-яА-ЯёЁ].*")) -> 0
            folder.name.matches(Regex("^[a-zA-Z].*")) -> 1
            else -> 2}
        },
        { folder -> folder.name.lowercase() }
    )
    )
}
fun getSortedDataArtist(artists:List<Artist>):List<Artist>{
    return artists.sortedWith(compareBy(
        { artist -> when {
            artist.name.matches(Regex("^[а-яА-ЯёЁ].*")) -> 0
            artist.name.matches(Regex("^[a-zA-Z].*")) -> 1
            else -> 2}
        },
        { artist -> artist.name.lowercase() }
    )
    )
}

fun getSortedDataAlbum(tracks:List<Album>):List<Album>{
    return tracks.sortedWith(compareBy(
        { album -> when {
            album.title.matches(Regex("^[а-яА-ЯёЁ].*")) -> 0
            album.title.matches(Regex("^[a-zA-Z].*")) -> 1
            else -> 2}
        },
        { album -> album.title.lowercase() }
    )
    )
}

fun getSortedDataPlaylists(tracks:List<Playlist>):List<Playlist>{
    return tracks.sortedWith(compareBy(
        { playlist -> when {
            playlist.playlistName.matches(Regex("^[а-яА-ЯёЁ].*")) -> 0
            playlist.playlistName.matches(Regex("^[a-zA-Z].*")) -> 1
            else -> 2}
        },
        { playlist -> playlist.playlistName.lowercase() }
    ))
}

//регулярки компилируются один раз (раньше создавались заново на каждом сравнении при сортировке)
private val RUSSIAN_TITLE_REGEX = Regex("^[а-яА-ЯёЁ].*")
private val LATIN_TITLE_REGEX = Regex("^[a-zA-Z].*")

fun getSortedDataSong(tracks:List<Song>):List<Song>{
    //ключи сортировки считаем один раз на песню, а не на каждое сравнение
    return tracks.map { track ->
        val group = when {
            track.title.matches(RUSSIAN_TITLE_REGEX) -> 0
            track.title.matches(LATIN_TITLE_REGEX) -> 1
            else -> 2
        }
        Triple(group, track.title.lowercase(), track)
    }.sortedWith(compareBy<Triple<Int, String, Song>>({ it.first }, { it.second })).map { it.third }
}

// Добавьте в SongsAdapter или в отдельный файл Utils.kt
fun formatFileSize(size: Long): String {
    return when {
        size >= 1024 * 1024 -> "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
        size >= 1024 -> "${String.format("%.1f", size / 1024.0)} KB"
        else -> "$size B"
    }
}

fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Неизвестно"
    }
}

fun formatDuration(duration: Long): String {
    val seconds = duration / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

//функция для нормализации URI
fun getNormalizedPath(uriString: String): String {
    return if (uriString.startsWith("file://")) {
        Uri.decode(uriString.substring(7))
    } else {
        uriString
    }
}

/** Индекс песни в списке: по id или по нормализованному пути (в разных списках путь записан по-разному). */
fun List<com.example.muzpleer.model.Song>.indexOfSong(song: com.example.muzpleer.model.Song?): Int {
    if (song == null) return -1
    val path = getNormalizedPath(song.mediaUri)
    return indexOfFirst { it.id == song.id || getNormalizedPath(it.mediaUri) == path }
}


/**
 * Прокручивает список так, чтобы строка [index] оказалась в поле зрения (чуть ниже верхнего края).
 * Если строки нет (index < 0) - сообщает, что играющий трек в этом списке не найден.
 */
fun RecyclerView.scrollToPlayingRow(index: Int) {
    if (index < 0) {
        Toast.makeText(context, "Играющий трек в этом списке не найден", Toast.LENGTH_SHORT).show()
        return
    }
    (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, height / 4)
}
