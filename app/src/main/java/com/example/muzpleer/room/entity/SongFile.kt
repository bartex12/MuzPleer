package com.example.muzpleer.room.entity

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.muzpleer.model.Song
import java.io.File

@Entity(tableName = "media_files")
data class SongFile(
    @PrimaryKey
    val mediaStoreId: Long, // ID из MediaStore
    val title: String?,//название
    val artist: String?,//исполнитель
    val artistId: Long, //id исполнителя
    val duration: Long, //длительность трека
    val path: String,// мп3 для треков из local
    val artUri: String?,// путь к обложке для треков из local
    val isLocal: Boolean = true, //из ресурсов или local
    val album: String?, //название альбома из local
    val albumId: Long = -1,  //id альбома из local
    val folderPath: String = "", //путь к папке
    val lastModified: Long,
    val size: Long,
    val dateAdded: Long,
    // Новые поля
    val author: String? = null,      // Автор текста
    val genre: String? = null,       // Жанр
    val year: Int? = null            // Год выпуска
){
    // Метод конвертации в Domain model
    fun toSong(): Song {
        return Song(
            id = this.mediaStoreId,
            title = this.title ?: "Неизвестно",
            artist = this.artist ?: "Неизвестно",
            artistId = this.artistId,
            duration = this.duration,
            mediaUri =Uri.fromFile(File(this.path)).toString(),
            artUri = this.artUri,
            isLocal = this.isLocal,
            albumName = this.album,
            albumId = this.albumId,
            folderPath = this.folderPath,
            author = this.author,
            genre = this.genre,
            year = this.year
        )
    }
}