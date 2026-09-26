package com.example.muzpleer.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.example.muzpleer.model.Song
import com.example.muzpleer.service.MusicServiceHandler
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.ui.local.helper.PreferenceHelperImpl
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<IPreferenceHelper> { PreferenceHelperImpl(get()) }
    single { provideExoPlayer(get()) }
    factory {
        MusicServiceHandler(
            context = androidContext(),
            callback = get() // Получаем реализацию колбэка из контейнера
        )
    }

    viewModel { SharedViewModel(get(),get(),get(),get(),get(),get(),get(),get())}

    // Регистрируем заглушку для колбэка (реальная реализация будет в ViewModel)
    factory<MusicServiceHandler.PlayerCallback> {
        object : MusicServiceHandler.PlayerCallback {
            override fun onTrackChanged(track: Song) {}
            override fun onPlaybackStateChanged(isPlaying: Boolean) {}
            override fun onPositionChanged(position: Long, duration: Long) {}
            override fun onError(message: String) {}
        }
    }
}

private fun provideExoPlayer(context: Context): ExoPlayer {
    return ExoPlayer.Builder(context).build()
}