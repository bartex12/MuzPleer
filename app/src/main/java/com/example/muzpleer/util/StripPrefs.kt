package com.example.muzpleer.util

import androidx.preference.PreferenceManager
import com.example.muzpleer.di.App

/** Ключ настройки «Панель с настройками» (значки сортировки и др. под тулбаром вкладок). */
const val PREF_SHOW_STRIP = "pref_show_strip"

/** Показывать ли панель со значками при открытии приложения и переходе по вкладкам; по умолчанию - нет. */
fun showStripByDefault(): Boolean =
    PreferenceManager.getDefaultSharedPreferences(App.instance).getBoolean(PREF_SHOW_STRIP, false)
