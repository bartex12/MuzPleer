package com.example.muzpleer.di

import com.example.muzpleer.repository.AlbumRepository
import com.example.muzpleer.repository.ArtistRepository
import com.example.muzpleer.repository.FavoriteRepository
import com.example.muzpleer.repository.FolderRepository
import com.example.muzpleer.repository.MusicRepository
import com.example.muzpleer.repository.PlaylistRepository
import com.example.muzpleer.room.AppDatabase
import com.example.muzpleer.room.dao.AlbumDao
import com.example.muzpleer.room.dao.ArtistDao
import com.example.muzpleer.room.dao.FavoriteDao
import com.example.muzpleer.room.dao.FolderDao
import com.example.muzpleer.room.dao.PlaylistDao
import com.example.muzpleer.room.dao.SongDao
import org.koin.dsl.module

val databaseModule = module {
    // Синглтон базы данных
    single<AppDatabase> {
        AppDatabase.create(get())
    }

    // DAO
    single<SongDao> { get<AppDatabase>().songDao() }
    single<FolderDao> { get<AppDatabase>().folderDao() }
    single<AlbumDao> { get<AppDatabase>().albumDao() }
    single<ArtistDao> { get<AppDatabase>().artistDao() }
    single<FavoriteDao> { get<AppDatabase>().favoriteDao() }
    single<PlaylistDao> { get<AppDatabase>().playlistDao() }

    // Репозитории
    single { MusicRepository(get(), get()) }
    single<AlbumRepository> { AlbumRepository(get(), get()) }
    single<ArtistRepository> { ArtistRepository(get(), get(), get()) }
    single<FolderRepository> { FolderRepository(get(), get()) }
    single<FavoriteRepository> { FavoriteRepository(get(), get()) }
    single<PlaylistRepository> { PlaylistRepository(get(), get()) }

}
