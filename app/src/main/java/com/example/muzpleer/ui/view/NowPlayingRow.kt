package com.example.muzpleer.ui.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.muzpleer.util.accentColor

/**
 * Строка трека. Адаптеры помечают играющий трек через isSelected: заголовок окрашивается в цвет акцента,
 * справа показывается эквалайзер. Нужные view находятся по тегам nowPlayingTitle / nowPlayingEqualizer.
 */
class NowPlayingRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var normalTitleColors: ColorStateList? = null  //цвет заголовка в обычном состоянии

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        findViewWithTag<TextView>(TAG_TITLE)?.let { title ->
            //играющий трек: заголовок цвета акцента (вариант вместо жирного шрифта)
            if (normalTitleColors == null) normalTitleColors = title.textColors
            title.setTypeface(null, Typeface.NORMAL)
            if (selected) title.setTextColor(context.accentColor())
            else title.setTextColor(normalTitleColors)
        }
        findViewWithTag<View>(TAG_EQUALIZER)?.visibility = if (selected) View.VISIBLE else View.GONE
    }

    companion object {
        const val TAG_TITLE = "nowPlayingTitle"
        const val TAG_EQUALIZER = "nowPlayingEqualizer"
    }
}
