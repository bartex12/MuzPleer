package com.example.muzpleer.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import com.github.chrisbanes.photoview.PhotoView

/**
 * PhotoView, который по флагу [round] рисует картинку внутри круга (диаметр = меньшая сторона view).
 * Обрезка идёт через canvas.clipPath, а не через outline: на Android 10 outline-обрезка срабатывала
 * не всегда. Увеличение и перемещение картинки жестами работают как у обычного PhotoView.
 */
class RoundPhotoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PhotoView(context, attrs, defStyleAttr) {

    private val clipPath = Path()

    var round: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        if (!round) {
            super.onDraw(canvas)
            return
        }
        val radius = minOf(width, height) / 2f
        clipPath.reset()
        clipPath.addCircle(width / 2f, height / 2f, radius, Path.Direction.CW)
        val saved = canvas.save()
        canvas.clipPath(clipPath)
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
    }
}
