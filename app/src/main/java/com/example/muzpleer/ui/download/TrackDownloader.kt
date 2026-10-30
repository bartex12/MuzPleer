package com.example.muzpleer.ui.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
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
    val coverUrl: String? = null,
    val isSuno: Boolean = false   //ссылка на suno.com: файл по ней приходит в защищённом виде
) {
    val audioUrl: String get() = candidates.first()
}

/** Скачанный файл: id в MediaStore (совпадает с id песни в базе приложения) и имя. */
data class DownloadedTrack(
    val mediaId: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val coverPath: String?,   //обложка, скачанная вместе с треком (если удалось)
    val alreadyExisted: Boolean = false   //такой файл уже лежал в Music/MuzPleer, новая копия не создавалась
)

/** Что пришло с сервера: начало файла, размер и тип - для проверки и сообщения об ошибке. */
private class StreamInfo(val contentType: String) {
    val head = ByteArray(16)
    var headLen = 0
    var size = 0L

    fun feed(buf: ByteArray, n: Int) {
        if (headLen < head.size) {
            val take = minOf(n, head.size - headLen)
            System.arraycopy(buf, 0, head, headLen, take)
            headLen += take
        }
        size += n
    }

    private fun at(i: Int) = if (i < headLen) head[i].toInt() and 0xFF else -1
    private fun text(from: Int, len: Int) = (from until from + len).map { at(it) }.let { l ->
        if (l.any { it < 0 }) "" else l.map { it.toChar() }.joinToString("")
    }

    /** Похоже ли начало на известный аудиоформат (MP4/M4A, MP3, Ogg, FLAC, WAV). */
    fun looksLikeAudio(): Boolean =
        text(4, 4) == "ftyp" || text(0, 3) == "ID3" || (at(0) == 0xFF && (at(1) and 0xE0) == 0xE0) ||
            text(0, 4) == "OggS" || text(0, 4) == "fLaC" || text(0, 4) == "RIFF"

    fun describe(): String {
        val hex = (0 until headLen).joinToString(" ") { "%02X".format(at(it)) }
        val ascii = (0 until headLen).map { at(it) }.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")
        return "размер $size байт, тип «${contentType.ifBlank { "не указан" }}», начало файла: $hex ($ascii)"
    }
}

/** Результат записи файла в медиатеку. */
private class SavedFile(val mediaId: Long, val durationMs: Long)

/** Понятная пользователю ошибка загрузки. */
open class DownloadException(message: String) : IOException(message)

/** Файл прочитан, но это не аудио. */
class NotAudioException(message: String) : DownloadException(message)

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
        return ResolvedTrack(candidates.toList(), title, artist, cover, isSuno = true)
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
                val info = StreamInfo(body.contentType()?.toString().orEmpty())

                val artist = track.artist ?: "Suno"
                findExisting(displayName, total)?.let { return@withContext it.copy(alreadyExisted = true) }
                val saved = try { saveToMusicFolder(displayName, title, artist, mime, info) { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -2
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            info.feed(buf, n)
                            done += n
                            job.ensureActive() //отмена загрузки
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                        }
                    }
                    if (done == 0L) throw DownloadException("Получен пустой файл")
                } } catch (e: NotAudioException) {
                    if (track.isSuno) throw DownloadException(SUNO_PROTECTED) else throw e
                }
                val cover = track.coverUrl?.let { downloadCover(it, saved.mediaId) }
                DownloadedTrack(saved.mediaId, title, artist, saved.durationMs, cover)
            }
        }

    /**
     * Ищет в Music/MuzPleer такой же файл: тот же размер и имя (либо имя с добавкой " (1)", которую система
     * дописывает сама при совпадении). Так один и тот же трек не копируется повторно и не занимает память.
     * Размер неизвестен (<= 0) - не проверяем.
     */
    @Suppress("DEPRECATION")
    private fun findExisting(displayName: String, size: Long): DownloadedTrack? {
        if (size <= 0) return null
        val base = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "")
        val nameRe = Regex("^" + Regex.escape(base) + "( \\(\\d+\\))?\\." + Regex.escape(ext) + "$", RegexOption.IGNORE_CASE)
        val (selection, arg) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            "${MediaStore.Audio.Media.SIZE}=? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to
                "${Environment.DIRECTORY_MUSIC}/$FOLDER/%"
        else
            "${MediaStore.Audio.Media.SIZE}=? AND ${MediaStore.Audio.Media.DATA} LIKE ?" to "%/${Environment.DIRECTORY_MUSIC}/$FOLDER/%"
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
                ),
                selection, arrayOf(size.toString(), arg), null
            )?.use { c ->
                var found: DownloadedTrack? = null
                while (found == null && c.moveToNext()) {
                    if (nameRe.matches(c.getString(1).orEmpty())) {
                        found = DownloadedTrack(c.getLong(0), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getLong(4), null)
                    }
                }
                found
            }
        } catch (e: Exception) {
            Log.d(TAG, "TrackDownloader findExisting error = ${e.message}")
            null
        }
    }

    /**
     * Ищет во всей медиатеке (не только в MuzPleer) файл того же размера с тем же именем или названием:
     * тогда это тот же трек, и копия в MuzPleer только создала бы дубль.
     */
    private fun findInLibrary(fileName: String, title: String, size: Long, durationMs: Long): DownloadedTrack? {
        Log.d(TAG, "TrackDownloader findInLibrary file=$fileName title=$title size=$size dur=$durationMs")
        if (size <= 0) return null
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val nFile = norm(fileName.substringBeforeLast('.'))
        val nTitle = norm(title)
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
                ),
                "${MediaStore.Audio.Media.SIZE}=?", arrayOf(size.toString()), null
            )?.use { c ->
                var found: DownloadedTrack? = null
                while (found == null && c.moveToNext()) {
                    val dn = norm(c.getString(1).orEmpty().substringBeforeLast('.'))
                    val tt = norm(c.getString(2).orEmpty())
                    val dur = c.getLong(4)
                    Log.d(TAG, "TrackDownloader findInLibrary candidate=${c.getString(1)} title=${c.getString(2)} dur=$dur")
                    fun like(a: String, b: String) = a.isNotEmpty() && b.isNotEmpty() && (a.startsWith(b) || b.startsWith(a))
                    val sameName = like(dn, nFile) || like(dn, nTitle) || like(tt, nTitle) || like(tt, nFile)
                    val sameDuration = durationMs > 0 && dur > 0 && kotlin.math.abs(dur - durationMs) <= 1000
                    //одинаковый размер и (то же имя или та же длительность) - это тот же файл
                    if (sameName || sameDuration) {
                        found = DownloadedTrack(c.getLong(0), c.getString(2).orEmpty(), c.getString(3).orEmpty(), dur, null)
                    }
                }
                found
            }
        } catch (e: Exception) {
            Log.d(TAG, "TrackDownloader findInLibrary error = ${e.message}")
            null
        }
    }

    /** Название, исполнитель и встроенная обложка из тегов выбранного файла. */
    private class FileTags(val title: String?, val artist: String?, val picture: ByteArray?)

    private fun readTags(uri: Uri): FileTags {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            FileTags(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() },
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() },
                r.embeddedPicture
            )
        } catch (e: Exception) {
            Log.d(TAG, "TrackDownloader readTags error = ${e.message}")
            FileTags(null, null, null)
        } finally {
            try { r.release() } catch (e: Exception) { /* ignore */ }
        }
    }

    /**
     * Данные со страницы трека suno.com (название, автор, обложка) для файла, выбранного пользователем.
     * Ссылка необязательна: при любой ошибке или чужом сайте возвращает null.
     */
    suspend fun resolveMeta(rawUrl: String): ResolvedTrack? {
        if (rawUrl.isBlank()) return null
        return try { resolve(rawUrl).takeIf { it.isSuno } } catch (e: Exception) { null }
    }

    /**
     * Копирует выбранный пользователем аудиофайл в Music/MuzPleer. Название и исполнитель берутся из тегов файла,
     * недостающее - из [meta] (страница Suno) или из имени файла.
     */
    suspend fun importFile(uri: Uri, meta: ResolvedTrack?, onProgress: (Int) -> Unit): DownloadedTrack =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var name = "track"
            var total = -1L
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            c.getString(0)?.let { name = it }
                            if (!c.isNull(1)) total = c.getLong(1)
                        }
                    }
            } catch (e: Exception) {
                Log.d(TAG, "TrackDownloader importFile query error = ${e.message}")
            }
            val tags = readTags(uri)
            val baseName = name.substringBeforeLast('.', name)
            //безымянный трек из Telegram приходит под внутренним именем вида 2_5267202794934283522
            val telegramName = Regex("^\\d+_\\d+(_\\d+)?$").matches(baseName)
            val title = tags.title ?: meta?.title ?: if (telegramName) {
                "Трек из Telegram, " + java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            } else baseName.ifBlank { "track" }
            val artist = tags.artist ?: meta?.artist ?: UNKNOWN_ARTIST
            val ext = importExtension(name, resolver.getType(uri))
            val mime = mimeForExtension(ext)
            val displayName = safeFileName(title) + "." + ext
            val job = currentCoroutineContext()
            val info = StreamInfo(resolver.getType(uri).orEmpty())

            //такой файл уже есть в библиотеке (в MuzPleer или в любой другой папке): копию не создаём
            (findInLibrary(name, title, total, probeDuration(uri, null)) ?: findExisting(displayName, total))
                ?.let { return@withContext it.copy(alreadyExisted = true) }
            val saved = try {
                saveToMusicFolder(displayName, title, artist, mime, info) { out ->
                    val input = resolver.openInputStream(uri) ?: throw DownloadException("Не удалось открыть выбранный файл")
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -2
                    input.use {
                        while (true) {
                            val n = it.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            info.feed(buf, n)
                            done += n
                            job.ensureActive()
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                        }
                    }
                    if (done == 0L) throw DownloadException("Выбранный файл пустой")
                }
            } catch (e: NotAudioException) {
                throw DownloadException("Выбранный файл не распознан как аудио (${info.describe()}).")
            }
            val cover = tags.picture?.takeIf { it.size >= 100 }?.let { saveCover(it, saved.mediaId) }
                ?: meta?.coverUrl?.let { downloadCover(it, saved.mediaId) }
            DownloadedTrack(saved.mediaId, title, artist, saved.durationMs, cover)
        }

    private fun saveCover(bytes: ByteArray, mediaId: Long): String? = try {
        val f = File(context.filesDir, "downloaded_covers/$mediaId.jpg")
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        f.absolutePath
    } catch (e: Exception) {
        Log.d(TAG, "TrackDownloader saveCover error = ${e.message}")
        null
    }

    private fun importExtension(name: String, mimeType: String?): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName in AUDIO_EXTENSIONS) return fromName
        return when {
            mimeType?.contains("mp4") == true || mimeType?.contains("aac") == true -> "m4a"
            mimeType?.contains("flac") == true -> "flac"
            mimeType?.contains("ogg") == true -> "ogg"
            mimeType?.contains("wav") == true -> "wav"
            else -> "mp3"
        }
    }

    private fun mimeForExtension(ext: String): String = when (ext) {
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/x-wav"
        else -> "audio/mpeg"
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

    /**
     * Если система не смогла прочитать длительность, смотрим на начало файла: известный аудиоформат -
     * не отклоняем (длительность определит медиатека), иначе сообщаем, что именно пришло.
     */
    private fun checkIsAudio(duration: Long, info: StreamInfo) {
        if (duration > 0) return
        Log.d(TAG, "TrackDownloader checkIsAudio: ${info.describe()}")
        if (!info.looksLikeAudio()) {
            throw NotAudioException("Сервер вернул не аудиофайл (${info.describe()}). Возможно, файл зашифрован или закрыт для скачивания.")
        }
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
        displayName: String, title: String, artist: String, mime: String, info: StreamInfo,
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
                checkIsAudio(duration, info)
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
            try { checkIsAudio(duration, info) } catch (e: DownloadException) { file.delete(); throw e }
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
        private const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
        private const val SUNO_PROTECTED =
            "Suno отдаёт этот трек в защищённом (зашифрованном) виде, напрямую его скачать нельзя. " +
                "Скачайте трек кнопкой Download в Suno и нажмите «Выбрать файл с устройства»."
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
