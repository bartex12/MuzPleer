package com.example.muzpleer.model

/**
 * Откуда пользователь выбрал играющую песню. [tab] - номер вкладки главного экрана (порядок как в ViewPageAdapterLocal).
 */
enum class SourceType(val tab: Int) {
    SONGS(0),      //общий список
    FOLDER(1),     //папка
    FAVORITES(2),  //избранное
    PLAYLIST(3),   //плейлист
    ALBUM(4),      //альбом
    ARTIST(5)      //исполнитель
}

/**
 * Источник списка воспроизведения: тип и ключ экрана со списком песен.
 * [id] - playlistId / albumId / artistId, [key] - путь папки, [name] - название для заголовка экрана.
 */
data class PlaybackSource(
    val type: SourceType,
    val id: Long = -1L,
    val key: String = "",
    val name: String = ""
)
