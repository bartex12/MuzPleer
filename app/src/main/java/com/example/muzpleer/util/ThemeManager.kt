package com.example.muzpleer.util

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.example.muzpleer.R

/** Выбор цвета акцента: хранится номером в настройках, применяется накладкой темы ThemeOverlay.AccentN. */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_ACCENT = "accent_index"

    val accentColorRes = intArrayOf(
        R.color.accent_1, R.color.accent_2, R.color.accent_3, R.color.accent_4,
        R.color.accent_5, R.color.accent_6, R.color.accent_7, R.color.accent_8,
        R.color.accent_9, R.color.accent_10, R.color.accent_11, R.color.accent_12
    )

    val accentOverlays = intArrayOf(
        R.style.ThemeOverlay_Accent1, R.style.ThemeOverlay_Accent2, R.style.ThemeOverlay_Accent3,
        R.style.ThemeOverlay_Accent4, R.style.ThemeOverlay_Accent5, R.style.ThemeOverlay_Accent6,
        R.style.ThemeOverlay_Accent7, R.style.ThemeOverlay_Accent8, R.style.ThemeOverlay_Accent9,
        R.style.ThemeOverlay_Accent10, R.style.ThemeOverlay_Accent11, R.style.ThemeOverlay_Accent12
    )

    fun getAccentIndex(context: Context): Int {
        val index = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, 0)
        return index.coerceIn(0, accentOverlays.size - 1)
    }

    fun setAccentIndex(context: Context, index: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACCENT, index).apply()
    }

    /** Накладываем выбранный акцент на тему экрана; вызывать после setTheme и до super.onCreate. */
    fun apply(activity: Activity) {
        activity.theme.applyStyle(accentOverlays[getAccentIndex(activity)], true)
    }

    fun colorOf(context: Context, index: Int): Int =
        ContextCompat.getColor(context, accentColorRes[index])
}

/** Текущий цвет акцента из темы контекста. */
fun Context.accentColor(): Int {
    val value = TypedValue()
    return if (theme.resolveAttribute(R.attr.appAccent, value, true)) value.data
    else ContextCompat.getColor(this, R.color.colorSecondaryBlue)
}
