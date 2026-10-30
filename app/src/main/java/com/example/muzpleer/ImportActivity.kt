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
        val uris = collectUris(intent)
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
            uris.forEachIndexed { i, uri ->
                status.text = "Добавляем в MuzPleer: ${i + 1} из ${uris.size}"
                try {
                    val track = downloader.importFile(uri, null) { }
                    if (track.alreadyExisted) existed++ else added++
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
            finish()
        }
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
