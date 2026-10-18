package com.example.muzpleer.ui.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
data class ResolvedTrack(
    val candidates: List<String>,
    val title: String?,
    val artist: String? = null,
    val coverUrl: String? = null
) {
    val audioUrl: String get() = candidates.first()
}

/** Скачанный файл: id в MediaStore (совпадает с id песни в базе приложения) и имя. */
data class DownloadedTrack(
    val mediaId: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val coverPath: String?   //обложка, скачанная вместе с треком (если удалось)
)

/** Результат записи файла в медиатеку. */
private class SavedFile(val mediaId: Long, val durationMs: Long)

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
        Regex("https://cdn[^\"'\\\\\\s<>]*?$songId\\.(?:m4a|mp3)[^\"'\\\\\\s<>]*").findAll(html)
            .forEach { candidates.add(it.value) }
        //адрес, который реально запрашивает веб-плеер Suno (виден во вкладке "Сеть" браузера)
        candidates.add("https://d2lwuy8qc234o3.cloudfront.net/1/clip/$songId.m4a")
        //запасные варианты на случай, если Suno сменит хранилище
        candidates.add("https://cdn1.suno.ai/$songId.m4a")
        candidates.add("https://cdn1.suno.ai/$songId.mp3")
        candidates.add("https://cdn-o.suno.com/$songId.m4a")
        val title = OG_TITLE.find(html)?.groupValues?.get(1)
            ?.replace("&nbsp;", " ")?.replace("&amp;", "&")?.replace("&quot;", "\"")
            ?.replace("&#x27;", "'")?.trim()?.takeIf { it.isNotEmpty() }
        //автор и обложка со страницы (если нашлись)
        val artist = (SUNO_DISPLAY_NAME.find(html)?.groupValues?.get(1)
            ?: SUNO_HANDLE.find(html)?.groupValues?.get(1))?.trim()?.takeIf { it.isNotEmpty() }
        val cover = OG_IMAGE.find(html)?.groupValues?.get(1)?.replace("&amp;", "&")?.takeIf { it.startsWith("http") }
        return ResolvedTrack(candidates.toList(), title, artist, cover)
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
                val ext = audioExtension(resp.request.url.encodedPath, resp.body?.contentType()?.toString())
                val mime = if (ext == "m4a") "audio/mp4" else "audio/mpeg"
                val displayName = safeFileName(title) + "." + ext
                val total = body.contentLength()
                val job = currentCoroutineContext()

                val artist = track.artist ?: "Suno"
                val saved = saveToMusicFolder(displayName, title, artist, mime) { out ->
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
                }
                val cover = track.coverUrl?.let { downloadCover(it, saved.mediaId) }
                DownloadedTrack(saved.mediaId, title, artist, saved.durationMs, cover)
            }
        }

    /** Картинка трека: сохраняется в память приложения, ошибки не критичны (вернёт null). */
    private fun downloadCover(url: String, mediaId: Long): String? = try {
        client.newCall(browserRequest(url)).execute().use { r ->
            val bytes = r.body?.bytes()
            if (!r.isSuccessful || bytes == null || bytes.size < 100) null
            else {
                val f = File(context.filesDir, "downloaded_covers/$mediaId.jpg")
                f.parentFile?.mkdirs()
                f.writeBytes(bytes)
                f.absolutePath
            }
        }
    } catch (e: Exception) {
        Log.d(TAG, "TrackDownloader downloadCover error = ${e.message}")
        null
    }

    /** Длительность файла в мс по данным самого файла; 0, если он не читается как аудио. */
    private fun probeDuration(uri: android.net.Uri?, path: String?): Long {
        val r = MediaMetadataRetriever()
        return try {
            if (uri != null) r.setDataSource(context, uri) else r.setDataSource(path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.d(TAG, "TrackDownloader probeDuration error = ${e.message}")
            0L
        } finally {
            try { r.release() } catch (e: Exception) { /* ignore */ }
        }
    }

    /** Записывает название и исполнителя в медиатеку, чтобы сканер приложения взял их оттуда. */
    private fun writeTags(mediaId: Long, title: String, artist: String) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), mediaId
            )
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
            }, null, null)
        } catch (e: Exception) {
            Log.d(TAG, "TrackDownloader writeTags error = ${e.message}")
        }
    }

    /** Ждём, пока медиатека сама определит длительность (она читает файл не мгновенно). */
    private suspend fun waitMediaStoreDuration(mediaId: Long) {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), mediaId
        )
        repeat(10) {
            val d = try {
                context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DURATION), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
            } catch (e: Exception) { 0L }
            if (d > 0) return
            delay(300)
        }
    }

    /** Создаёт файл в Music/MuzPleer, пишет в него через [writer], проверяет, что это аудио, и возвращает id в MediaStore. */
    private suspend fun saveToMusicFolder(
        displayName: String, title: String, artist: String, mime: String,
        writer: (java.io.OutputStream) -> Unit
    ): SavedFile {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw DownloadException("Не удалось создать файл")
            try {
                resolver.openOutputStream(uri)?.use { writer(it) }
                    ?: throw DownloadException("Не удалось открыть файл для записи")
                //пока файл "в ожидании", проверяем, что его можно прочитать как аудио
                val duration = probeDuration(uri, null)
                if (duration <= 0) throw DownloadException(
                    "Файл скачан, но не распознан как аудио (формат не поддерживается или файл повреждён)"
                )
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                val id = ContentUris.parseId(uri)
                writeTags(id, title, artist)
                waitMediaStoreDuration(id)
                return SavedFile(id, duration)
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
            val duration = probeDuration(null, file.path)
            if (duration <= 0) {
                file.delete()
                throw DownloadException("Файл скачан, но не распознан как аудио (формат не поддерживается или файл повреждён)")
            }
            val id = scanLegacy(file, mime)
            writeTags(id, title, artist)
            waitMediaStoreDuration(id)
            return SavedFile(id, duration)
        }
    }

    private suspend fun scanLegacy(file: File, mime: String): Long = suspendCancellableCoroutine<Long> { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime)) { _, uri ->
            cont.resume(uri?.let { ContentUris.parseId(it) } ?: -1L)
        }
    }.also { if (it < 0) throw DownloadException("Файл сохранён, но не попал в медиатеку") }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "mp3")
        while (f.exists()) { f = File(dir, "$base ($i).$ext"); i++ }
        return f
    }

    /** m4a, если файл такой (по адресу или типу ответа), иначе mp3. */
    private fun audioExtension(path: String, contentType: String?): String = when {
        path.endsWith(".m4a", true) || contentType?.contains("mp4") == true || contentType?.contains("aac") == true -> "m4a"
        else -> "mp3"
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
        private val SUNO_DISPLAY_NAME = Regex("""\\*"display_name\\*":\\*"([^"\\\\]+)"""")
        private val SUNO_HANDLE = Regex("""\\*"handle\\*":\\*"([^"\\\\]+)"""")
        private val OG_IMAGE = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']*)[\"']")
        private val SUNO_SONG_ANY = Regex("suno\\.com/song/([0-9a-fA-F-]{36})")
        private val OG_TITLE = Regex("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']*)[\"']")
    }
}
