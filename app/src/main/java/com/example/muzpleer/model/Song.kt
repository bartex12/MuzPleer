package com.example.muzpleer.model

import android.content.ContentUris
import android.net.Uri
import android.os.Parcelable
import android.provider.MediaStore
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,  //id
    val title: String, //название
    val artist: String, //исполнитель
    val artistId:Long = artist.hashCode().toLong(), //id исполнителя
    val duration: Long,  //длительность трека
    val mediaUri: String,  // мп3 для треков из local
    var artUri: String? = null,  // путь к обложке для треков из local
    val isLocal: Boolean = true,  //из ресурсов или local
    val albumName: String? = null, //название альбома из local
    val albumId: Long = -1, // id альбома из local
    val folderPath: String = "",  //путь к папке
    // Новые поля
    val author: String? = null,
    val genre: String? = null,
    val year: Int? = null
): Parcelable{

    fun getContentUri(): Uri {
        return ContentUris
            .withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id )
    }

}

