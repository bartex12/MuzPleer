package com.example.muzpleer.ui.local.helper

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