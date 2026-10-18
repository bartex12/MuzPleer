package com.example.muzpleer.ui.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import kotlin.coroutines.resume

/** Что именно качаем: адрес аудиофайла и (если удалось узнать) название трека. */
data class ResolvedTrack(val audioUrl: String, val title: String?)

/** Скачанный файл: id в MediaStore (совпадает с id песни в базе приложения) и имя. */
data class DownloadedTrack(val mediaId: Long, val title: String)

/** Понятная пользователю ошибка загрузки. */
class DownloadException(message: String) : IOException(message)

/**
 * Загрузка mp3 по ссылке в папку Music/MuzPleer.
 * Принимает прямую ссылку на аудиофайл или ссылку на страницу трека suno.com/song/...
 */
class TrackDownloader(private val context: Context) {

    private val client = OkHttpClient()

    /** Превращает введённую ссылку в адрес аудиофайла. */
    suspend fun resolve(rawUrl: String): ResolvedTrack = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        val uri = try { URI(url) } catch (e: Exception) { null }
        val host = uri?.host?.lowercase()
        if (uri == null || host == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw DownloadException("Введите ссылку, начинающуюся с https://")
        }
        val sunoId = SUNO_SONG.find(uri.path ?: "")?.groupValues?.get(1)
        if (host.endsWith("suno.com") && sunoId != null) {
            //страница трека: адрес файла строится по id, название берём со страницы
            ResolvedTrack("https://cdn1.suno.ai/$sunoId.mp3", fetchPageTitle(url))
        } else {
            ResolvedTrack(url, null)
        }
    }

    private fun fetchPageTitle(pageUrl: String): String? = try {
        client.newCall(Request.Builder().url(pageUrl).build()).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            OG_TITLE.find(html)?.groupValues?.get(1)
                ?.replace("&nbsp;", " ")?.replace("&amp;", "&")?.replace("&quot;", "\"")
                ?.replace("&#x27;", "'")?.trim()?.takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        Log.d(TAG, "TrackDownloader fetchPageTitle error = ${e.message}")
        null
    }

    /** Скачивает файл. [onProgress] получает проценты 0..100, либо -1, если размер неизвестен. */
    suspend fun download(track: ResolvedTrack, onProgress: (Int) -> Unit): DownloadedTrack =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(track.audioUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw DownloadException(
                        if (resp.code == 403 || resp.code == 404) "Файл по этой ссылке не найден или недоступен (код ${resp.code})"
                        else "Сервер ответил ошибкой ${resp.code}"
                    )
                }
                val body = resp.body ?: throw DownloadException("Пустой ответ сервера")
                val type = body.contentType()?.toString().orEmpty()
                if (type.startsWith("text/")) {
                    throw DownloadException("По ссылке открывается веб-страница, а не аудиофайл")
                }
                val title = track.title ?: fileNameFromUrl(track.audioUrl)
                val displayName = safeFileName(title) + ".mp3"
                val total = body.contentLength()
                val job = currentCoroutineContext()

                saveToMusicFolder(displayName, title) { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -2
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            job.ensureActive() //отмена загрузки
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                        }
                    }
                    if (done == 0L) throw DownloadException("Получен пустой файл")
                }.let { DownloadedTrack(it, title) }
            }
        }

    /** Создаёт файл в Music/MuzPleer, пишет в него через [writer] и возвращает id в MediaStore. */
    private suspend fun saveToMusicFolder(
        displayName: String, title: String, writer: (java.io.OutputStream) -> Unit
    ): Long {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw DownloadException("Не удалось создать файл")
            try {
                resolver.openOutputStream(uri)?.use { writer(it) }
                    ?: throw DownloadException("Не удалось открыть файл для записи")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                return ContentUris.parseId(uri)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null) //не оставляем недокачанный файл
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
            if (!dir.exists() && !dir.mkdirs()) throw DownloadException("Не удалось создать папку ${dir.path}")
            val file = uniqueFile(dir, displayName)
            try {
                file.outputStream().use { writer(it) }
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
            return scanLegacy(file)
        }
    }

    private suspend fun scanLegacy(file: File): Long = suspendCancellableCoroutine<Long> { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("audio/mpeg")) { _, uri ->
            cont.resume(uri?.let { ContentUris.parseId(it) } ?: -1L)
        }
    }.also { if (it < 0) throw DownloadException("Файл сохранён, но не попал в медиатеку") }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        while (f.exists()) { f = File(dir, name.removeSuffix(".mp3") + " ($i).mp3"); i++ }
        return f
    }

    private fun fileNameFromUrl(url: String): String =
        url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { "track" }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100).ifBlank { "track" }

    companion object {
        const val TAG = "33333"
        const val FOLDER = "MuzPleer"
        private val SUNO_SONG = Regex("/song/([0-9a-fA-F-]{36})")
        private val OG_TITLE = Regex("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']*)[\"']")
    }
}
