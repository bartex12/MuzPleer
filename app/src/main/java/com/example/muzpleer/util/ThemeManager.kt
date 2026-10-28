package com.example.muzpleer.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.ImageView
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.example.muzpleer.R

/** Выбор цвета акцента: хранится номером в настройках, применяется накладкой темы AccentOverlayN. */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_ACCENT = "accent_index"

    val accentColorRes = intArrayOf(
        R.color.accent_1, R.color.accent_2, R.color.accent_3, R.color.accent_4,
        R.color.accent_5, R.color.accent_6, R.color.accent_7, R.color.accent_8,
        R.color.accent_9, R.color.accent_10, R.color.accent_11, R.color.accent_12
    )

    val accentOverlays = intArrayOf(
        R.style.AccentOverlay1, R.style.AccentOverlay2, R.style.AccentOverlay3,
        R.style.AccentOverlay4, R.style.AccentOverlay5, R.style.AccentOverlay6,
        R.style.AccentOverlay7, R.style.AccentOverlay8, R.style.AccentOverlay9,
        R.style.AccentOverlay10, R.style.AccentOverlay11, R.style.AccentOverlay12
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

    //снимок экрана перед пересозданием: показываем его поверх нового экрана и плавно убираем,
    //тогда мигание при смене темы не заметно
    private var snapshot: Bitmap? = null

    fun captureSnapshot(activity: Activity) {
        try {
            val decor = activity.window.decorView
            if (decor.width <= 0 || decor.height <= 0) return
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            snapshot = bitmap
        } catch (e: Exception) {
            snapshot = null
        }
    }

    /** Вызывать после setContentView нового экрана. */
    fun playCrossfade(activity: Activity) {
        val bitmap = snapshot ?: return
        snapshot = null
        val decor = activity.window.decorView as? ViewGroup
        if (decor == null) {
            bitmap.recycle()
            return
        }
        val overlay = ImageView(activity).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = true //пока виден снимок, нажатия не проходят
        }
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.animate()
            .alpha(0f)
            .setStartDelay(150)
            .setDuration(450)
            .withEndAction {
                decor.removeView(overlay)
                bitmap.recycle()
            }
            .start()
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
