package com.example.muzpleer.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.muzpleer.room.dao.AlbumDao
import com.example.muzpleer.room.dao.ArtistDao
import com.example.muzpleer.room.dao.FavoriteDao
import com.example.muzpleer.room.dao.FolderDao
import com.example.muzpleer.room.dao.PlaylistDao
import com.example.muzpleer.room.dao.SongDao
import com.example.muzpleer.room.entity.AlbumFile
import com.example.muzpleer.room.entity.ArtistFile
import com.example.muzpleer.room.entity.FavoriteSong
import com.example.muzpleer.room.entity.FolderFile
import com.example.muzpleer.room.entity.PlaylistFile
import com.example.muzpleer.room.entity.PlaylistSongCrossRef
import com.example.muzpleer.room.entity.SongFile

@Database(
    entities = [SongFile::class, AlbumFile::class, ArtistFile::class,
        FolderFile::class, FavoriteSong::class, PlaylistFile::class, PlaylistSongCrossRef::class],
    version = 9, // 1. Увеличили версию до 9
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun folderDao(): FolderDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val TAG = "33333"
        private const val DATABASE_NAME = "music_player.db"

        // Объект миграции внутри companion object
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorite_songs ADD COLUMN artUri TEXT")
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_8_9) // 2. Добавили миграцию
                .fallbackToDestructiveMigration() // Резервный вариант на случай сбоя
                .build()
        }
    }
}