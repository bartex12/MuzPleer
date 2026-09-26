package com.example.muzpleer.di

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class App : Application() {
    companion object{
        lateinit var instance:App
    }

    override fun onCreate() {
        super.onCreate()

       instance = this

        startKoin {
            androidContext(this@App)
            modules(listOf(appModule, databaseModule)) // Добавляем databaseModule
        }
    }
}
