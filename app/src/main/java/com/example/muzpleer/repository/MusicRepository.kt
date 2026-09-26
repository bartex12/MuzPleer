package com.example.muzpleer.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.muzpleer.di.App
import com.example.muzpleer.model.Song
import com.example.muzpleer.room.dao.SongDao
import com.example.muzpleer.room.entity.SongFile
import com.example.muzpleer.room.utils.fromSongFileListToSongList
import com.example.muzpleer.room.utils.fromSongFileToSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


class MusicRepository(
    private val songDao: SongDao,
    private val context: Context){

    companion object{
        const val TAG = "33333"
    }

   // private var songs = mutableListOf<Song>()

     suspend fun loadMusicFromMemory():List<Song> {
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanMusicApi29Plus(context)
        } else {
            scanMusicLegacy(context)
        })
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun scanMusicApi29Plus(context: Context):List<Song> {

        val dbIds = songDao.getAllIds().toMutableSet() //список id в базе
        Log.d(TAG, "#%# MusicRepository scanMusicApi29Plus dbIds size = ${dbIds.size}")
        val filesToAdd = mutableListOf<SongFile>()  //список для добавления
        val filesToUpdate = mutableListOf<SongFile>() //список для обновления
        val filesToDelete = mutableListOf<SongFile>()   //список для удаления

        val collection = MediaStore.Audio.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
        )

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(pathColumn)
                val lastModified = cursor.getLong(modifiedColumn) * 1000 // конвертируем в миллисекунды
                val title = cursor.getString(titleColumn) ?: "Неизвестно"
                val artist = cursor.getString(artistColumn) ?: "Неизвестно"
                val album = cursor.getString(albumColumn) ?: "Неизвестно"
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)
                val sizeFile = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(addedColumn) * 1000
                val folderPath = File(path).parent ?: ""

                //берём id просканированных в памяти телефона файлов
                songDao.getById(id)?.let { existingFile ->

                    // Файл с таким id существует в базе
                    dbIds.remove(id)  //удаляем запись с этой id из списка всех id

                    // Проверяем и дату изменения, путь, и тд
                    val isModified = existingFile.lastModified != lastModified
                    val isMoved = existingFile.path != path
                    val isTitleChanged = existingFile.title != title
                    val isArtistChanged = existingFile.artist != artist
                    val isAlbumChanged = existingFile.album != album
                    //чтобы обложка не пропадала после изменения другой информации,
                    // её нужно всё время обновлять, если она != null
                    val isCoverNotNull = existingFile.artUri != null

                   //обложка ==null?
                    if (isCoverNotNull){
                        //если обложка не равна null,то
                        //смотрим- был ли файл модифицирован по сравнению с файлом в базе
                        if (isModified || isMoved || isTitleChanged || isArtistChanged || isAlbumChanged) {
                            // При изменении файла проверяем, не появилась ли новая встроенная обложка
                            // Если появилась,сохраняем изображение в кэш и возвращаем URI в строковом виде
                            val embeddedArtUri = getEmbeddedArtworkAndSave(context, path)
                            Log.d(TAG, " #%# MusicRepository scanMusicApi29Plus при изменениях embeddedArtUri = $embeddedArtUri ")
                            val finalArtUri = embeddedArtUri ?: existingFile.artUri // Если нет встроенной, оставляем старую
                            Log.d(TAG, " #%# MusicRepository scanMusicApi29Plus при изменениях finalArtUri = $finalArtUri ")
                            //Добавляем в список для обновления
                            filesToUpdate.add(
                                SongFile(
                                    mediaStoreId = id,
                                    path = existingFile.path,
                                    lastModified = existingFile.lastModified,
                                    title = existingFile.title,
                                    artist = existingFile.artist,
                                    artistId = artist.hashCode().toLong(),
                                    album =  existingFile.album,
                                    albumId = albumId,
                                    duration = duration,
                                    isLocal = true,
                                    size = sizeFile,
                                    dateAdded = dateAdded,
                                    folderPath = folderPath,
                                    artUri = finalArtUri,
                                    // Новые поля - сохраняем существующие значения, если они есть
                                    author = existingFile.author ?: getAuthorFromMetadata(context, path),
                                    genre = existingFile.genre ?: getGenreFromMetadata(context, path),
                                    year = existingFile.year ?: getYearFromMetadata(context, path)
                                )
                            )
                            Log.d(TAG, "#%# File updated: $path (changes: modified=$isModified, moved=$isMoved, title=$isTitleChanged, artist=$isArtistChanged, album=$isAlbumChanged)")
                        }
                    }else{
                        //если обложка ==null, ничего не делаем
                        Log.d(TAG, " #%# MusicRepository scanMusicApi29Plus existingFile.artUri == null")
                    }
                } ?: run {
                    // Новый файл - пытаемся извлечь встроенную обложку, если есть, сохраняем в кэше
                    val embeddedArtUri = getEmbeddedArtworkAndSave(context, path)
                    Log.d(TAG, " #%# MusicRepository scanMusicApi29Plus Новый трек embeddedArtUri = $embeddedArtUri ")
                    filesToAdd.add(
                        SongFile(
                            mediaStoreId = id,
                            path = path,
                            lastModified = lastModified,
                            title = title,
                            artist = artist,
                            artistId = artist.hashCode().toLong(),
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            isLocal = true,
                            size = sizeFile,
                            dateAdded = dateAdded,
                            folderPath = folderPath,
                            artUri =  embeddedArtUri, // Сохраняем URI встроенной обложки
                            // Новые поля - извлекаем из метаданных
                            author = getAuthorFromMetadata(context, path),
                            genre = getGenreFromMetadata(context, path),
                            year = getYearFromMetadata(context, path)
                        )
                    )
                }
            }
        }

        // файлы, которые есть в базе, но нет в MediaStore
        //из списка id уже выкинуты все id кроме этих
        dbIds.forEach { id ->
            songDao.getById(id)?.let {
                filesToDelete.add(it)
                Log.d(TAG, "#%# MusicRepository scanMusicApi29Plus filesToDelete size= ${filesToDelete.size}")
            }
        }

        Log.d(TAG, "#%# MusicRepository scanMusicApi29Plus " +
                "filesToAdd.size = ${filesToAdd.size}  " +
                "filesToUpdate.size = ${filesToUpdate.size}  " +
                "filesToDelete.size = ${filesToDelete.size}")

        // Применяем изменения
        withContext(Dispatchers.IO) {
            // Вставляем новые файлы списком
            if (filesToAdd.isNotEmpty()) {
                songDao.insertAll(filesToAdd)
            }
            // Обновляем существующие файлы списком
            if (filesToUpdate.isNotEmpty()) {
                songDao.updateAll(filesToUpdate)
            }
            // Удаляем файлы
            filesToDelete.forEach { songDao.delete(it) }
        }

        val songList = fromSongFileListToSongList(songDao.getAllFiles())
        Log.d(TAG, "#%# MusicRepository scanMusicApi29Plus songList.size = ${songList.size}")

        //songs = songList.toMutableList()
        return songList
    }

    suspend fun getSongsFromDatabase(): List<Song> { //для проверки в MainActivity
        return fromSongFileListToSongList(songDao.getAllFiles())
    }

    private fun scanMusicLegacy(context: Context):List<Song>{
        return listOf()
    }

    suspend fun updateCoverPath(id : Long, coverPath:String) {
        Log.d(TAG, "5*** MusicRepository updateCoverPath in database id = $id coverPath = $coverPath")
        songDao.updateCoverPath(id, coverPath)
        Log.d(TAG, "7***MusicRepository updateCoverPath  coverPath из базы = ${songDao.getById(id)?.artUri}")
    }

    suspend fun updateSongInfo(
        songId: Long,
        title: String,
        artist: String,
        album: String?,
        author: String?,
        genre: String?,
        year: Int?
    ) {
        Log.d(TAG, "8*** MusicRepository updateSongInfo title = $title author = $author")
        songDao.updateSongInfo(songId, title, artist, album.toString(), author, genre, year)
        // Также обновляем lastModified чтобы изменения сохранились при следующем сканировании
        val updatedFile = songDao.getSongWithDetails(songId)
        updatedFile?.let {
            songDao.update(it.copy(lastModified = System.currentTimeMillis()))
        }
        Log.d(TAG, "9***MusicRepository updateSongInfo  author из базы = ${songDao.getById(songId)?.author}")
    }

    private fun getAuthorFromMetadata(context: Context, filePath: String): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            retriever.release()
            author
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract author metadata from $filePath error = ${e.message}")
            null
        }
    }

    private fun getGenreFromMetadata(context: Context, filePath: String): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            retriever.release()
            genre
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract genre metadata from $filePath error = ${e.message}")
            null
        }
    }

    private fun getYearFromMetadata(context: Context, filePath: String): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            retriever.release()
            yearStr?.toIntOrNull()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract year metadata from $filePath error = ${e.message}")
            null
        }
    }

    suspend fun getSongFileById(songId:Long):SongFile?{
        return songDao.getById(songId)
    }

    private fun getEmbeddedArtworkAndSave(context: Context, filePath: String): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.fromFile(File(filePath)))

            val embeddedPicture = retriever.embeddedPicture
            retriever.release()

            if (embeddedPicture != null) {
                // Сохраняем изображение в кэш и возвращаем URI
                val  uri = saveArtworkToCache(filePath, embeddedPicture)
                uri
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error extracting embedded artwork: ${e.message}")
            null
        }.toString()
    }

    private fun saveArtworkToCache(filePath: String, imageData: ByteArray): String? {
        return try {
            val fileName = "embedded_${filePath.hashCode()}.jpg"
            val cacheFile = File(App.instance.cacheDir, "album_art/$fileName")

            // Создаём директорию если не существует
            cacheFile.parentFile?.mkdirs()

            // Сохраняем изображение
            FileOutputStream(cacheFile).use { outputStream ->
                outputStream.write(imageData)
            }

            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving artwork to cache: ${e.message}")
            null
        }
    }

    suspend fun refreshAllArtworks(context: Context) {
        Log.d(TAG, "****** MusicRepository refreshAllArtworks ")
        val allSongs = songDao.getAllFiles()
        val songsToUpdate = mutableListOf<SongFile>()
        val songsToChangeCoverSave = mutableListOf<SongFile>()

        allSongs.forEach { songFile ->
            // Пропускаем, если уже есть artUri и он существует
            if (songFile.artUri != null && File(songFile.artUri).exists()) {
                //Log.d(TAG, "***** MusicRepository refreshAllArtworks пропускаем трек ${songFile.title}  File = ${File(songFile.artUri).absolutePath}")
                return@forEach
            }

            //если начинается с content://com.android.providers, переписываем в кэш приложения,
            // получаем новый адрес и помещаем в список songsToChangeCoverSave
            songFile.artUri?. let{artUriPro->
                if (artUriPro.startsWith("content://com.android.providers")){
                    songsToChangeCoverSave.add(songFile.copy(artUri = artUriPro))
                    //Log.d(TAG, "***** ***** MusicRepository refreshAllArtworks songFile.title = ${songFile.title}")
                }
            }

            // Пытаемся извлечь встроенную обложку, если успешно- в список songsToUpdate
            val embeddedArtUri = getEmbeddedArtworkAndSave(context, songFile.path)
            //Log.d(TAG, "***** ***** MusicRepository refreshAllArtworks embeddedArtUri = $embeddedArtUri")
            if (embeddedArtUri != null) {
                songsToUpdate.add(songFile.copy(artUri = embeddedArtUri))
            }
        }
        if (songsToUpdate.isNotEmpty()) {
            songDao.updateAll(songsToUpdate)
            //Log.d(TAG, "*****MusicRepository refreshAllArtworks Updated artworks for ${songsToUpdate.size} songs")
        }
    }

    suspend fun  updateSongArtUri(songId: Long, newArtUri: String?){
        songDao.updateCoverPath(songId, newArtUri)
   }

    suspend fun getById(songId: Long):SongFile?{
        return  songDao.getById(songId)
    }
}
