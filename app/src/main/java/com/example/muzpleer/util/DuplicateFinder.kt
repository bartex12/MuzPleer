package com.example.muzpleer.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Поиск «призраков» (записи в MediaStore, файла которых уже нет) и дублей (один и тот же трек
 * скачан несколько раз: «Билет.mp3», «Билет (1).mp3»). Дубль - тот же размер и то же название.
 */
object DuplicateFinder {
    private const val TAG = "33333"

    data class Item(
        val id: Long,
        val path: String,
        val title: String,
        val artist: String,
        val size: Long,
        val duration: Long,
        val dateAdded: Long,
        val exists: Boolean
    ) {
        val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), id)
        val fileName: String get() = File(path).name
    }

    private val suffixRe = Regex("\\s*\\(\\d+\\)\\s*$")

    private fun hasCopySuffix(item: Item) = suffixRe.containsMatchIn(File(item.path).nameWithoutExtension)

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun key(item: Item): String {
        val t = norm(item.title.replace(suffixRe, "")).ifEmpty { norm(File(item.path).nameWithoutExtension.replace(suffixRe, "")) }
        return "${item.size}|$t"
    }

    /** Все аудиозаписи MediaStore с признаком, есть ли файл на самом деле. */
    fun loadItems(context: Context): List<Item> {
        val result = mutableListOf<Item>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val path = c.getString(1) ?: continue
                    val item = Item(
                        id, path, c.getString(2).orEmpty(), c.getString(3).orEmpty(),
                        c.getLong(4), c.getLong(5), c.getLong(6) * 1000, true
                    )
                    result.add(item.copy(exists = fileExists(context, item)))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "DuplicateFinder loadItems error = ${e.message}")
        }
        return result
    }

    //файл есть, если он виден по пути или открывается через MediaStore
    private fun fileExists(context: Context, item: Item): Boolean {
        if (File(item.path).exists()) return true
        return try {
            context.contentResolver.openFileDescriptor(item.uri, "r")?.use { true } ?: false
        } catch (e: Exception) { false }
    }

    /**
     * Группы дублей (по две записи и больше); первый в группе - тот, которого оставляем:
     * сначала тот, что уже в плейлистах или избранном, затем без «(1)» в имени, затем самый старый.
     */
    fun groups(items: List<Item>, referencedIds: Set<Long> = emptySet()): List<List<Item>> =
        items.filter { it.exists && it.size > 0 }
            .groupBy { key(it) }
            .values
            .filter { it.size >= 2 }
            .map { g ->
                g.sortedWith(
                    compareBy<Item>({ it.id !in referencedIds }, { hasCopySuffix(it) }, { it.dateAdded }, { it.id })
                )
            }

    /** Записи, которые не надо показывать в приложении: призраки и лишние копии, не входящие в плейлисты и избранное. */
    fun hiddenIds(context: Context, referencedIds: Set<Long>): Set<Long> {
        val items = loadItems(context)
        val hidden = mutableSetOf<Long>()
        items.filter { !it.exists }.forEach { hidden.add(it.id) }
        groups(items, referencedIds).forEach { g ->
            g.drop(1).filter { it.id !in referencedIds }.forEach { hidden.add(it.id) }
        }
        Log.d(TAG, "DuplicateFinder hiddenIds = ${hidden.size} из ${items.size}")
        return hidden
    }
}
