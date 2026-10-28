package com.example.muzpleer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
import java.io.DataInputStream
import java.io.EOFException

/**
 * Чтение текста песни из тега ID3v2 mp3-файла (кадр USLT; в ID3v2.2 он называется ULT).
 * MediaMetadataRetriever текст не отдаёт, поэтому тег разбираем сами и только по требованию.
 * Результат (в том числе "текста нет") запоминаем по id трека.
 */
object LyricsReader {
    private const val TAG = "LyricsReader"
    private const val MAX_TAG_SIZE = 32 * 1024 * 1024 // защита от повреждённых файлов

    private val cache = LruCache<Long, String>(30)
    private const val NO_LYRICS = "\u0000"

    /** Текст песни или null, если его в файле нет. Вызывать не из главного потока. */
    fun read(context: Context, songId: Long, uri: Uri): String? {
        cache.get(songId)?.let { return if (it == NO_LYRICS) null else it }
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { parseId3(DataInputStream(it.buffered())) }
        } catch (e: Exception) {
            Log.d(TAG, "Не удалось прочитать текст: ${e.message}")
            null
        }?.trim()?.takeIf { it.isNotEmpty() }
        cache.put(songId, text ?: NO_LYRICS)
        return text
    }

    /** Сбросить кэш трека (например, после смены файла). */
    fun forget(songId: Long) { cache.remove(songId) }

    private fun parseId3(input: DataInputStream): String? {
        val header = ByteArray(10)
        try { input.readFully(header) } catch (e: EOFException) { return null }
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val version = header[3].toInt() and 0xFF
        if (version < 2 || version > 4) return null
        val flags = header[5].toInt() and 0xFF
        val size = syncSafe(header, 6)
        if (size <= 0 || size > MAX_TAG_SIZE) return null

        var tag = ByteArray(size)
        input.readFully(tag)
        // Unsynchronisation на уровне тега (в v2.4 она задаётся в каждом кадре отдельно)
        if (version < 4 && flags and 0x80 != 0) tag = removeUnsync(tag)

        var pos = 0
        // Расширенный заголовок
        if (flags and 0x40 != 0 && version >= 3) {
            val extSize = if (version == 4) syncSafe(tag, 0) else readInt(tag, 0) + 4
            pos = extSize.coerceAtLeast(0)
        }

        val idLen = if (version == 2) 3 else 4
        val headLen = if (version == 2) 6 else 10
        while (pos + headLen <= tag.size) {
            if (tag[pos].toInt() == 0) break // начался padding
            val id = String(tag, pos, idLen, Charsets.ISO_8859_1)
            val frameSize = when (version) {
                2 -> ((tag[pos + 3].toInt() and 0xFF) shl 16) or ((tag[pos + 4].toInt() and 0xFF) shl 8) or (tag[pos + 5].toInt() and 0xFF)
                3 -> readInt(tag, pos + 4)
                else -> syncSafe(tag, pos + 4)
            }
            val frameFlags = if (version == 2) 0 else ((tag[pos + 8].toInt() and 0xFF) shl 8) or (tag[pos + 9].toInt() and 0xFF)
            val start = pos + headLen
            if (frameSize < 0 || start + frameSize > tag.size) break
            if (id == "USLT" || id == "ULT") {
                var body = tag.copyOfRange(start, start + frameSize)
                if (version == 4) {
                    if (frameFlags and 0x0001 != 0 && body.size > 4) body = body.copyOfRange(4, body.size) // data length indicator
                    if (frameFlags and 0x0002 != 0) body = removeUnsync(body)
                }
                // сжатые/зашифрованные кадры пропускаем
                val packed = if (version == 3) frameFlags and 0x00C0 != 0 else version == 4 && frameFlags and 0x000C != 0
                if (!packed) decodeUslt(body)?.let { return it }
            }
            pos = start + frameSize
        }
        return null
    }

    // USLT: кодировка(1) язык(3) описание(\0) текст
    private fun decodeUslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val enc = body[0].toInt() and 0xFF
        val charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val wide = enc == 1 || enc == 2
        var i = 4
        // пропускаем описание до терминатора
        while (i < body.size) {
            if (wide) {
                if (i + 1 < body.size && body[i].toInt() == 0 && body[i + 1].toInt() == 0) { i += 2; break }
                i += 2
            } else {
                if (body[i].toInt() == 0) { i += 1; break }
                i += 1
            }
        }
        if (i >= body.size) return null
        return String(body, i, body.size - i, charset).replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun syncSafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or ((b[off + 1].toInt() and 0x7F) shl 14) or
                ((b[off + 2].toInt() and 0x7F) shl 7) or (b[off + 3].toInt() and 0x7F)

    private fun readInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    // 0xFF 0x00 -> 0xFF
    private fun removeUnsync(src: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(src.size)
        var i = 0
        while (i < src.size) {
            out.write(src[i].toInt())
            if (src[i] == 0xFF.toByte() && i + 1 < src.size && src[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }
}
