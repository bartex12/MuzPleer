package com.example.muzpleer.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Окно для выбора фрагмента картинки: по центру квадратная рамка, картинку можно двигать
 * одним пальцем и масштабировать двумя. Картинка всегда закрывает рамку целиком.
 */
class CropImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val frame = RectF()
    private val tmp = RectF()
    private val values = FloatArray(9)

    private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = dp(2f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = dp(1f)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private var minScale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var activePointer = -1

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (bitmap == null) return true
                val cur = currentScale()
                val target = (cur * d.scaleFactor).coerceIn(minScale, minScale * MAX_ZOOM)
                val f = target / cur
                matrix.postScale(f, f, d.focusX, d.focusY)
                keepCovering()
                invalidate()
                return true
            }
        })

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** Размер рамки задаётся здесь, а не в разметке: 90% меньшей стороны окна. */
    private fun layoutFrame() {
        val side = min(width, height) * 0.9f
        frame.set((width - side) / 2f, (height - side) / 2f, (width + side) / 2f, (height + side) / 2f)
    }

    fun setBitmap(b: Bitmap) {
        bitmap = b
        resetMatrix()
        invalidate()
    }

    fun hasBitmap() = bitmap != null

    private fun resetMatrix() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        layoutFrame()
        minScale = max(frame.width() / b.width, frame.height() / b.height)
        matrix.reset()
        matrix.postScale(minScale, minScale)
        matrix.postTranslate(
            frame.centerX() - b.width * minScale / 2f,
            frame.centerY() - b.height * minScale / 2f
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetMatrix()
    }

    private fun currentScale(): Float {
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /** Сдвигает картинку так, чтобы она не оставляла пустых мест внутри рамки. */
    private fun keepCovering() {
        val b = bitmap ?: return
        tmp.set(0f, 0f, b.width.toFloat(), b.height.toFloat())
        matrix.mapRect(tmp)
        var dx = 0f
        var dy = 0f
        if (tmp.left > frame.left) dx = frame.left - tmp.left
        else if (tmp.right < frame.right) dx = frame.right - tmp.right
        if (tmp.top > frame.top) dy = frame.top - tmp.top
        else if (tmp.bottom < frame.bottom) dy = frame.bottom - tmp.bottom
        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bitmap == null) return false
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = e.getPointerId(0)
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                //при смене числа пальцев берём опорной точкой оставшийся палец, чтобы не было скачка
                val up = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
                val idx = (0 until e.pointerCount).firstOrNull { it != up } ?: 0
                activePointer = e.getPointerId(idx)
                lastX = e.getX(idx); lastY = e.getY(idx)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val idx = e.findPointerIndex(activePointer)
                    if (idx >= 0) {
                        matrix.postTranslate(e.getX(idx) - lastX, e.getY(idx) - lastY)
                        keepCovering()
                        invalidate()
                    }
                }
                val idx = e.findPointerIndex(activePointer)
                if (idx >= 0) { lastX = e.getX(idx); lastY = e.getY(idx) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activePointer = -1
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.drawBitmap(b, matrix, bitmapPaint)
        //затемняем всё, что вне рамки
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top, dimPaint)
        canvas.drawRect(0f, frame.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dimPaint)
        canvas.drawRect(frame.right, frame.top, width.toFloat(), frame.bottom, dimPaint)
        //сетка 3x3
        for (i in 1..2) {
            val x = frame.left + frame.width() * i / 3f
            val y = frame.top + frame.height() * i / 3f
            canvas.drawLine(x, frame.top, x, frame.bottom, gridPaint)
            canvas.drawLine(frame.left, y, frame.right, y, gridPaint)
        }
        canvas.drawRect(frame, borderPaint)
    }

    /** Вырезает выбранную рамкой область в квадратную картинку заданного размера. */
    fun cropBitmap(outSize: Int): Bitmap? {
        val b = bitmap ?: return null
        if (frame.width() <= 0f) return null
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val m = Matrix(matrix)
        m.postTranslate(-frame.left, -frame.top)
        val k = outSize / frame.width()
        m.postScale(k, k)
        Canvas(out).drawBitmap(b, m, bitmapPaint)
        return out
    }

    private companion object {
        const val MAX_ZOOM = 8f
    }
}
