package com.example.muzpleer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzpleer.ui.download.DownloadException
import com.example.muzpleer.ui.download.TrackDownloader
import kotlinx.coroutines.launch

/**
 * Принимает треки из других приложений («Поделиться» / «Открыть с помощью» в Telegram, файловом менеджере):
 * копирует их в Music/MuzPleer (без дублей) и закрывается. Новые треки появятся в списках при следующем обновлении.
 */
class ImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        //из набора */* оставляем только аудио (по типу файла или по расширению)
        val uris = collectUris(intent).filter { isAudio(it) }
        if (uris.isEmpty()) {
            Toast.makeText(this, "Нет файлов для добавления", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val status = findViewById<TextView>(R.id.tvImportStatus)
        val downloader = TrackDownloader(applicationContext)
        lifecycleScope.launch {
            var added = 0
            var existed = 0
            var failed = 0
            var lastId = -1L
            uris.forEachIndexed { i, uri ->
                status.text = "Добавляем в MuzPleer: ${i + 1} из ${uris.size}"
                try {
                    val track = downloader.importFile(uri, null) { }
                    if (track.alreadyExisted) existed++ else added++
                    lastId = track.mediaId
                } catch (e: DownloadException) {
                    failed++
                } catch (e: Exception) {
                    Log.d("33333", "ImportActivity error = $e")
                    failed++
                }
            }
            val parts = mutableListOf<String>()
            if (added > 0) parts.add("добавлено: $added")
            if (existed > 0) parts.add("уже были: $existed")
            if (failed > 0) parts.add("не удалось: $failed")
            Toast.makeText(this@ImportActivity, parts.joinToString(", ").replaceFirstChar { it.uppercase() }, Toast.LENGTH_LONG).show()
            openApp(lastId)
            finish()
        }
    }

    //открываем MuzPleer на папке, где лежит добавленный трек
    private fun openApp(songId: Long) {
        if (songId == -1L) return
        var folder: String? = null
        try {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL), songId)
            contentResolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)?.use {
                if (it.moveToFirst()) folder = it.getString(0)?.let { p -> java.io.File(p).parent }
            }
        } catch (e: Exception) { Log.d("33333", "ImportActivity openApp error = $e") }
        val path = folder ?: return
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FOLDER, path)
            putExtra(EXTRA_SONG_ID, songId)
        })
    }

    companion object {
        const val EXTRA_FOLDER = "import_folder"
        const val EXTRA_SONG_ID = "import_song_id"
    }

    private fun isAudio(uri: Uri): Boolean {
        val type = contentResolver.getType(uri).orEmpty()
        if (type.startsWith("audio/") || type == "application/ogg") return true
        if (type.isNotEmpty() && type != "application/octet-stream") return false
        val name = uri.lastPathSegment.orEmpty().lowercase()
        return listOf(".mp3", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wav").any { name.endsWith(it) } ||
            type == "application/octet-stream"
    }

    @Suppress("DEPRECATION")
    private fun collectUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        else -> emptyList()
    }
}
