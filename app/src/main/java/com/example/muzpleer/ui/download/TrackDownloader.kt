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

/** Что именно качаем: адреса, которые пробуем по очереди, и (если удалось узнать) название трека. */
data class ResolvedTrack(val candidates: List<String>, val title: String?) {
    val audioUrl: String get() = candidates.first()
}

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

    /** Запрос "как из браузера": многие сайты отдают 403 запросам с UA библиотеки. */
    private fun browserRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", BROWSER_UA)
        .header("Referer", "https://suno.com/")
        .header("Accept", "*/*")
        .build()

    /** Превращает введённую ссылку в список возможных адресов аудиофайла. */
    suspend fun resolve(rawUrl: String): ResolvedTrack = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        val uri = try { URI(url) } catch (e: Exception) { null }
        val host = uri?.host?.lowercase()
        if (uri == null || host == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw DownloadException("Введите ссылку, начинающуюся с https://")
        }
        if (host == "suno.com" || host.endsWith(".suno.com")) {
            resolveSuno(url, uri.path ?: "")
        } else {
            ResolvedTrack(listOf(url), null)
        }
    }

    /**
     * Страница трека (suno.com/song/<id>) или короткая ссылка из приложения (suno.com/s/...):
     * находим id трека и название, адрес файла строим по id.
     */
    private fun resolveSuno(url: String, path: String): ResolvedTrack {
        var id = SUNO_SONG.find(path)?.groupValues?.get(1)
        var html = ""
        try {
            client.newCall(browserRequest(url)).execute().use { resp ->
                html = resp.body?.string().orEmpty()
                //короткая ссылка перенаправляет на страницу трека
                if (id == null) id = SUNO_SONG.find(resp.request.url.encodedPath)?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.d(TAG, "TrackDownloader resolveSuno error = ${e.message}")
        }
        //запасной вариант: id в адресе canonical / og:image
        if (id == null) id = SUNO_SONG_ANY.find(html)?.groupValues?.get(1)
        val songId = id ?: throw DownloadException("Не удалось найти трек по этой ссылке suno.com")

        val candidates = linkedSetOf<String>()
        //адрес аудио, если он прямо есть в коде страницы
        Regex("https://cdn[^\"'\\\\\\s<>]*?$songId\\.mp3[^\"'\\\\\\s<>]*").findAll(html)
            .forEach { candidates.add(it.value) }
        candidates.add("https://cdn1.suno.ai/$songId.mp3")
        candidates.add("https://cdn-o.suno.com/$songId.mp3")
        val title = OG_TITLE.find(html)?.groupValues?.get(1)
            ?.replace("&nbsp;", " ")?.replace("&amp;", "&")?.replace("&quot;", "\"")
            ?.replace("&#x27;", "'")?.trim()?.takeIf { it.isNotEmpty() }
        return ResolvedTrack(candidates.toList(), title)
    }

    /** Скачивает файл. [onProgress] получает проценты 0..100, либо -1, если размер неизвестен. */
    suspend fun download(track: ResolvedTrack, onProgress: (Int) -> Unit): DownloadedTrack =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            var response: okhttp3.Response? = null
            for (candidate in track.candidates) {
                val r = try { client.newCall(browserRequest(candidate)).execute() }
                catch (e: IOException) { failures.add("${hostOf(candidate)}: ${e.message}"); continue }
                val type = r.body?.contentType()?.toString().orEmpty()
                if (r.isSuccessful && !type.startsWith("text/")) { response = r; break }
                failures.add("${hostOf(candidate)}: " + if (r.isSuccessful) "веб-страница вместо аудио" else "код ${r.code}")
                r.close()
            }
            val resp = response ?: throw DownloadException(
                if (track.candidates.size > 1)
                    "Не удалось скачать файл трека (${failures.joinToString("; ")}). Возможно, сайт закрыл прямой доступ к файлу."
                else "Не удалось скачать файл (${failures.joinToString("; ")})"
            )
            resp.use {
                val body = resp.body ?: throw DownloadException("Пустой ответ сервера")
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

    private fun hostOf(url: String): String = try { URI(url).host ?: url } catch (e: Exception) { url }

    private fun fileNameFromUrl(url: String): String =
        url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { "track" }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100).ifBlank { "track" }

    companion object {
        const val TAG = "33333"
        const val FOLDER = "MuzPleer"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val SUNO_SONG = Regex("/song/([0-9a-fA-F-]{36})")
        private val SUNO_SONG_ANY = Regex("suno\\.com/song/([0-9a-fA-F-]{36})")
        private val OG_TITLE = Regex("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']*)[\"']")
    }
}
