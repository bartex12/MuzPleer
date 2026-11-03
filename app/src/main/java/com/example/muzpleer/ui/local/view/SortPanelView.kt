package com.example.muzpleer.ui.local.view

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.example.muzpleer.R
import com.example.muzpleer.util.SongSortField

/**
 * Панель «Сортировать по» над вкладками: «Без сортировки», пять пунктов и «По возрастанию».
 * Высота по содержимому; прячется нажатием на заголовок или свайпом вниз по нему.
 * Состояние сортировки хранит ViewModel вкладки, панель только показывает его и сообщает о выборе.
 */
class SortPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val options: List<Pair<RadioButton, SongSortField>>
    private val ascending: CheckBox
    private var currentField: () -> SongSortField = { SongSortField.NONE }
    private var currentAscending: () -> Boolean = { true }
    private var onChange: (SongSortField, Boolean) -> Unit = { _, _ -> }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_sort_panel, this, true)
        options = listOf(
            findViewById<RadioButton>(R.id.sortNone) to SongSortField.NONE,
            findViewById<RadioButton>(R.id.sortTitle) to SongSortField.TITLE,
            findViewById<RadioButton>(R.id.sortAlbum) to SongSortField.ALBUM,
            findViewById<RadioButton>(R.id.sortArtist) to SongSortField.ARTIST,
            findViewById<RadioButton>(R.id.sortAlbumCount) to SongSortField.ALBUM_COUNT,
            findViewById<RadioButton>(R.id.sortCount) to SongSortField.COUNT,
            findViewById<RadioButton>(R.id.sortPath) to SongSortField.PATH,
            findViewById<RadioButton>(R.id.sortDuration) to SongSortField.DURATION,
            findViewById<RadioButton>(R.id.sortDate) to SongSortField.DATE_ADDED
        )
        ascending = findViewById(R.id.sortAscending)
        //по умолчанию нужны пункты для списка песен; вкладки с другими критериями задают свой набор через setAvailable
        setAvailable(setOf(SongSortField.NONE, SongSortField.TITLE, SongSortField.ALBUM,
            SongSortField.ARTIST, SongSortField.DURATION, SongSortField.DATE_ADDED))
        for ((button, field) in options) {
            button.setOnClickListener {
                onChange(field, ascending.isChecked)
                refresh()
            }
        }
        ascending.setOnCheckedChangeListener { _, checked ->
            if (checked != currentAscending()) onChange(currentField(), checked)
        }
        //заголовок: нажатие и свайп вниз сворачивают панель
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { hide(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy > 600) { hide(); return true }
                return false
            }
        })
        findViewById<TextView>(R.id.sortHeader).setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    /** Какие пункты показывать (остальные скрыты). */
    fun setAvailable(fields: Set<SongSortField>) {
        for ((button, field) in options) button.visibility = if (field in fields) View.VISIBLE else View.GONE
    }

    /** Подключает панель к состоянию сортировки вкладки. */
    fun bind(field: () -> SongSortField, ascending: () -> Boolean, onChange: (SongSortField, Boolean) -> Unit) {
        currentField = field
        currentAscending = ascending
        this.onChange = onChange
        refresh()
    }

    /** Отмечает пункты по текущему состоянию сортировки. */
    fun refresh() {
        for ((button, field) in options) button.isChecked = currentField() == field
        ascending.isChecked = currentAscending()
    }

    fun toggle() { if (visibility == View.VISIBLE) hide() else show() }

    fun show() {
        refresh()
        visibility = View.VISIBLE
        post {
            translationY = height.toFloat()
            animate().translationY(0f).setDuration(200).start()
        }
    }

    fun hide(animate: Boolean = true) {
        if (visibility != View.VISIBLE) return
        if (!animate) { visibility = View.GONE; return }
        animate().translationY(height.toFloat()).setDuration(200).withEndAction {
            visibility = View.GONE
            translationY = 0f
        }.start()
    }
}
