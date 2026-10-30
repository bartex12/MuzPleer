package com.example.muzpleer.repository

import android.content.Context
import com.example.muzpleer.model.Song
import com.example.muzpleer.room.dao.LyricsDao
import com.example.muzpleer.room.entity.LyricsEntity
import com.example.muzpleer.util.LyricsReader

/** Текст песни: сначала свой (из базы), если его нет - из тега файла. */
class LyricsRepository(private val dao: LyricsDao, private val context: Context) {

    class Lyrics(val text: String?, val isCustom: Boolean)

    suspend fun get(song: Song): Lyrics {
        dao.get(song.id)?.let { return Lyrics(it.text, true) }
        return Lyrics(LyricsReader.read(context, song.id, song.getContentUri()), false)
    }

    /** Пустой текст = вернуться к тексту из файла. */
    suspend fun save(songId: Long, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) dao.delete(songId) else dao.save(LyricsEntity(songId, clean))
    }

    suspend fun reset(songId: Long) = dao.delete(songId)
}
