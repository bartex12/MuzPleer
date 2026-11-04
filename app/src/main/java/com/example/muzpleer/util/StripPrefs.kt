package com.example.muzpleer.util

import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.example.muzpleer.di.App

/** Ключ настройки «Панель с настройками» (значки сортировки и др. под тулбаром вкладок). */
const val PREF_SHOW_STRIP = "pref_show_strip"

/** Показывать ли панель со значками при открытии приложения и переходе по вкладкам; по умолчанию - нет. */
fun showStripByDefault(): Boolean =
    PreferenceManager.getDefaultSharedPreferences(App.instance).getBoolean(PREF_SHOW_STRIP, false)

/** Ключ настройки «Текст в панели с настройками» (подписи под значками панели). */
const val PREF_SHOW_STRIP_TEXT = "pref_show_strip_text"

/** Показывать ли подписи под значками панели; по умолчанию - нет. */
fun showStripTextByDefault(): Boolean =
    PreferenceManager.getDefaultSharedPreferences(App.instance).getBoolean(PREF_SHOW_STRIP_TEXT, false)

/** Показывает или прячет подписи (элементы с тегом stripLabel) на панели; без подписей значки по центру. */
fun View.applyStripLabels() {
    val show = showStripTextByDefault()
    val density = resources.displayMetrics.density
    fun walk(v: View) {
        if (v is ViewGroup) {
            val label = (0 until v.childCount).map { v.getChildAt(it) }.firstOrNull { it.tag == "stripLabel" }
            if (label != null) {
                label.visibility = if (show) View.VISIBLE else View.GONE
                for (i in 0 until v.childCount) {
                    val icon = v.getChildAt(i)
                    if (icon !== label) icon.setPadding(icon.paddingLeft,
                        if (show) (2 * density).toInt() else 0, icon.paddingRight,
                        if (show) (14 * density).toInt() else 0)
                }
            }
            for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
    }
    walk(this)
}
