package com.example.muzpleer.ui.local.helper

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class PreferenceHelperImpl(private  val app: Application): IPreferenceHelper {
    companion object{
        const val TAG = "33333"
        const val FIRST_POSITION_SONG = "FIRST_POSITION_SONG"
        const val FIRST_POSITION_MY_TRACKS = "FIRST_POSITION_MY_TRACKS"
        const val FIRST_POSITION_MY_KING = "FIRST_POSITION_MY_KING"
        const val FIRST_POSITION_ALBUM = "FIRST_POSITION_ALBUM"
        const val FIRST_POSITION_ARTIST ="FIRST_POSITION_ARTIST"
        const val FIRST_POSITION_FOLDER ="FIRST_POSITION_FOLDER"
        const val FIRST_POSITION_PLAYLIST = "FIRST_POSITION_PLAYLIST"
        const val PAGER_LOCAL_POSITION ="PAGER_LOCAL_POSITION"
        const val CURRENT_SONG_KEY = "CURRENT_SONG_KEY"
        const val FIRST_POSITION_FAVORITE_SONG = "FIRST_POSITION_FAVORITE_SONG"
        const val INDEX_OF_CURRENT_SONG = "INDEX_OF_CURRENT_SONG"
        const val TOOLBAR_TITLE = "TOOLBAR_TITLE"
    }

    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(app)
    }

    private fun putValue(pair:Pair<String, Any>) =
        prefs.edit{

        val key=pair.first
        val value=pair.second

        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
            else -> error("Only primitives!!!")
        }
    }


    override fun savePositionSong(position: Int) {
        putValue(FIRST_POSITION_SONG to position )
        Log.d(TAG,"PreferenceHelper savePositionSong position = $position" )
    }

    override fun getPositionSong(): Int {
        val position = prefs .getInt(FIRST_POSITION_SONG, 0)
        Log.d(TAG, "PreferenceHelper getPositionSong position = $position")
        return position
    }

    override fun savePositionAlbum(position: Int) {
        putValue(FIRST_POSITION_ALBUM to position )
        Log.d(TAG,"PreferenceHelper savePositionAlbum position = $position" )
    }

    override fun getPositionAlbum(): Int {
        val position = prefs .getInt(FIRST_POSITION_ALBUM, 0)
        Log.d(TAG, "PreferenceHelper getPositionAlbum position = $position")
        return position
    }

    override fun savePositionPlaylist(position: Int) {
        putValue(FIRST_POSITION_PLAYLIST to position )
        Log.d(TAG,"PreferenceHelper savePositionPlaylist position = $position" )
    }

    override fun getPositionPlaylist(): Int {
        val position = prefs .getInt(FIRST_POSITION_PLAYLIST, 0)
        Log.d(TAG, "PreferenceHelper getPositionPlaylist position = $position")
        return position
    }

    override fun savePositionArtist(position: Int) {
        putValue(FIRST_POSITION_ARTIST to position )
        Log.d(TAG,"PreferenceHelper savePositionArtist position = $position" )
    }

    override fun getPositionArtist(): Int {
        val position = prefs .getInt(FIRST_POSITION_ARTIST, 0)
        Log.d(TAG, "PreferenceHelper getPositionArtist position = $position")
        return position
    }

    override fun savePositionFolder(position: Int) {
        putValue(FIRST_POSITION_FOLDER to position )
        Log.d(TAG,"PreferenceHelper savePositionFolder position = $position" )
    }

    override fun getPositionFolder(): Int {
        val position = prefs .getInt(FIRST_POSITION_FOLDER, 0)
        Log.d(TAG, "PreferenceHelper getPositionFolder position = $position")
        return position
    }

    override fun saveTabsLocalPosition(position:Int) {
        putValue(PAGER_LOCAL_POSITION to position)
    }

    override fun getTabsLocalPosition(): Int {
        return  prefs.getInt(PAGER_LOCAL_POSITION, 0)
    }

    override fun saveCurrentSongId(id: Long) {
        putValue(CURRENT_SONG_KEY to id)
    }

    override fun getCurrentSongId(): Long {
        return  prefs.getLong(CURRENT_SONG_KEY, -1L)
    }

    override fun getPositionMyTracks(): Int {
        val position = prefs .getInt(FIRST_POSITION_MY_TRACKS, 0)
        Log.d(TAG, "PreferenceHelper getPositionMyTracks position = $position")
        return position
    }

    override fun savePositionMyTracks(position: Int) {
        putValue(FIRST_POSITION_MY_TRACKS to position )
        Log.d(TAG,"PreferenceHelper savePositionMyTracks position = $position" )
    }

    override fun getPositionMyKing(): Int {
        val position = prefs .getInt(FIRST_POSITION_MY_KING, 0)
        Log.d(TAG, "PreferenceHelper getPositionMyKing position = $position")
        return position
    }

    override fun savePositionMyKing(position: Int) {
        putValue(FIRST_POSITION_MY_KING to position )
        Log.d(TAG,"PreferenceHelper savePositionMyKing position = $position" )
    }

    override fun getPositionFavoriteSong(): Int {
        val position = prefs .getInt(FIRST_POSITION_FAVORITE_SONG, 0)
        Log.d(TAG, "PreferenceHelper getPositionFavoriteSong position = $position")
        return position
    }

    override fun savePositionFavoriteSong(position: Int) {
        putValue(FIRST_POSITION_FAVORITE_SONG to position )
        Log.d(TAG,"PreferenceHelper savePositionFavoriteSong position = $position" )
    }

    override fun saveFavorites(json:String) {
        app.getSharedPreferences("PlayerPrefs", Context.MODE_PRIVATE)
            .edit {
                putString("favorites", json)
            }
    }

    override fun loadFavorites():String? {
        val json = app.getSharedPreferences("PlayerPrefs", Context.MODE_PRIVATE)
            .getString("favorites", null)
        return json
    }

    override fun getIndexOfCurrentSong(): Int {
        val index = prefs .getInt(INDEX_OF_CURRENT_SONG, 0)
        Log.d(TAG, "PreferenceHelper getIndexOfCurrentSong index = $index")
        return index
    }

    override fun saveIndexOfCurrentSong(index: Int) {
        putValue(INDEX_OF_CURRENT_SONG to index )
        Log.d(TAG,"PreferenceHelper saveIndexOfCurrentSong index = $index" )
    }


    fun getSoundLevel(): Int {
        return prefs.getString("sound_level", "80")?.toInt()?: 80
    }

}