package com.example.muzpleer.ui.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Строка трека. Адаптеры помечают играющий трек через isSelected: заголовок становится жирным,
 * справа показывается эквалайзер. Нужные view находятся по тегам nowPlayingTitle / nowPlayingEqualizer.
 */
class NowPlayingRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        findViewWithTag<TextView>(TAG_TITLE)?.setTypeface(
            null, if (selected) Typeface.BOLD else Typeface.NORMAL
        )
        findViewWithTag<View>(TAG_EQUALIZER)?.visibility = if (selected) View.VISIBLE else View.GONE
    }

    companion object {
        const val TAG_TITLE = "nowPlayingTitle"
        const val TAG_EQUALIZER = "nowPlayingEqualizer"
    }
}
