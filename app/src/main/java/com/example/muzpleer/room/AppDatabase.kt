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
import com.example.muzpleer.room.dao.LyricsDao
import com.example.muzpleer.room.entity.LyricsEntity
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
        FolderFile::class, FavoriteSong::class, PlaylistFile::class, PlaylistSongCrossRef::class, LyricsEntity::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun folderDao(): FolderDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        const val TAG = "33333"
        private const val DATABASE_NAME = "music_player.db"

        // Объект миграции внутри companion object
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorite_songs ADD COLUMN artUri TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `lyrics` (`songId` INTEGER NOT NULL, `text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
            }
        }

        //Откат 10→9 (поставили сборку с более старой базой): убираем только таблицу текстов, плейлисты и избранное не трогаем
        val MIGRATION_10_9 = object : Migration(10, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `lyrics`")
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_9)
                //стирать базу можно только при совсем старых версиях; откаты и обновления сохраняют плейлисты и избранное
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7)
                .build()
        }
    }
}