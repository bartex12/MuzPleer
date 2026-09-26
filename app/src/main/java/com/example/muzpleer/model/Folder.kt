package com.example.muzpleer.model

import android.net.Uri

data class Folder(
    val id: Long,
    val path: String,
    val name: String,
    val songs: List<Song>,
    var artworkUri : Uri? = null
)