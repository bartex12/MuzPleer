package com.example.muzpleer.ui.local.helper

import com.example.muzpleer.model.PlaybackSource

interface IPreferenceHelper {
    fun savePositionSong(position:Int)
    fun getPositionSong(): Int

    fun savePositionAlbum(position:Int)
    fun getPositionAlbum(): Int

    fun savePositionPlaylist(position:Int)
    fun getPositionPlaylist(): Int

    fun savePositionArtist(position:Int)
    fun getPositionArtist(): Int

    fun savePositionFolder(position:Int)
    fun getPositionFolder(): Int

    fun saveTabsLocalPosition(tabLocaPosition:Int)
    fun getTabsLocalPosition(): Int

    fun saveCurrentSongId(id:Long)
    /** id песен списка, который сейчас стоит в плеере (откуда пользователь выбрал песню), в порядке воспроизведения. */
    fun saveQueueIds(ids: List<Long>)
    fun getQueueIds(): List<Long>
    /** Экран, из которого выбрана играющая песня: по нему приложение при запуске открывает нужный список. */
    fun savePlaybackSource(source: PlaybackSource)
    fun getPlaybackSource(): PlaybackSource?
    fun getCurrentSongId(): Long

    fun getPositionMyTracks(): Int
    fun savePositionMyTracks(position: Int)

    fun getPositionMyKing(): Int
    fun savePositionMyKing(position: Int)

    fun getPositionFavoriteSong(): Int
    fun savePositionFavoriteSong(position: Int)

    fun saveFavorites(json:String)
    fun loadFavorites():String?

    fun getIndexOfCurrentSong(): Int
    fun saveIndexOfCurrentSong(index: Int)

}