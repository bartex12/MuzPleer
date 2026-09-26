package com.example.muzpleer.room.utils

import android.net.Uri
import androidx.core.net.toUri
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.model.Song
import com.example.muzpleer.room.entity.PlaylistFile
import com.example.muzpleer.room.entity.SongFile

fun fromSongFileListToSongList(songFiles:List<SongFile>):List<Song>{
    return songFiles.map{songFile->
        Song(
            id = songFile.mediaStoreId ,
            title =songFile.title.toString(),
            artist =songFile.artist.toString(),
            artistId = songFile.artistId,
            duration = songFile.duration ,
            mediaUri = songFile.path ,
            artUri = songFile.artUri ,
            isLocal = songFile.isLocal ,
            albumName = songFile.album ,
            albumId = songFile.albumId ,
            folderPath = songFile.folderPath,
            // Новые поля
            author = songFile.author,
            genre = songFile.genre,
            year = songFile.year
        )
    }
}

fun fromSongFileToSong(songFile:SongFile):Song{
    return Song(
            id = songFile.mediaStoreId ,
            title =songFile.title.toString(),
            artist =songFile.artist.toString(),
            artistId = songFile.artistId,
            duration = songFile.duration ,
            mediaUri = songFile.path ,
            artUri = songFile.artUri ,
            isLocal = songFile.isLocal ,
            albumName = songFile.album ,
            albumId = songFile.albumId ,
            folderPath = songFile.folderPath,
            // Новые поля
            author = songFile.author,
            genre = songFile.genre,
            year = songFile.year
        )
}

fun String.toArtistList(): List<String> {
    return if (this.isBlank()) {
        emptyList()
    } else {
        this.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "Unknown" }
    }
}

// Extension для преобразования пути в Uri
private fun String?.toUri(): Uri? {
    return this?.let { it.toUri() }
}

private fun generateArtistId(artistName: String): Long {
    return artistName.hashCode().toLong()
}

// Extension функции для конвертации
fun PlaylistFile.toPlaylist(songs: List<Song>): Playlist {
    return Playlist(
        id = this.id,
        playlistName = this.playlistName,
        playlistArtUri = this.playlistArtUri?.toUri(),
        playlistSongs = songs,
        songCount = this.songCount,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}