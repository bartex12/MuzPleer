package com.example.muzpleer.model

import android.os.Parcelable
import com.example.muzpleer.model.Song
import kotlinx.parcelize.Parcelize


@Parcelize
data class SongAndPlaylist(
    val song:Song,
    val playlist:List<Song>
): Parcelable{}