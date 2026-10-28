package com.example.muzpleer.ui.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.lifecycle.Observer
import com.example.muzpleer.util.NowPlaying
import com.example.muzpleer.util.accentColor
import kotlin.random.Random

/**
 * Маленький «эквалайзер» из прыгающих столбиков: имитация, не реальный сигнал.
 * Анимируется, только пока играет музыка и view видна; на паузе столбики замирают.
 */
class EqualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Для предпросмотра темы: анимируется независимо от воспроизведения. */
    var demo = false
        set(value) {
            field = value
            updateAnimation()
        }

    private val bars = 4
    private val restPattern = floatArrayOf(0.35f, 0.7f, 0.5f, 0.85f)
    private val heights = restPattern.copyOf()
    private val from = restPattern.copyOf()
    private val to = restPattern.copyOf()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    private val playingObserver = Observer<Boolean> { updateAnimation() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        paint.color = context.accentColor()
        NowPlaying.isPlaying.observeForever(playingObserver)
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        NowPlaying.isPlaying.removeObserver(playingObserver)
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        val shouldRun = isAttachedToWindow && isShown &&
                (demo || NowPlaying.isPlaying.value == true)
        if (shouldRun) startAnimation() else stopAnimation()
    }

    private fun pickTargets() {
        for (i in 0 until bars) to[i] = 0.2f + Random.nextFloat() * 0.8f
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        for (i in 0 until bars) from[i] = heights[i]
        pickTargets()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                for (i in 0 until bars) heights[i] = from[i] + (to[i] - from[i]) * t
                invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(a: Animator) {}
                override fun onAnimationEnd(a: Animator) {}
                override fun onAnimationCancel(a: Animator) {}
                override fun onAnimationRepeat(a: Animator) {
                    for (i in 0 until bars) from[i] = to[i]
                    pickTargets()
                }
            })
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        //на паузе столбики стоят ровным «спокойным» рисунком
        for (i in 0 until bars) heights[i] = restPattern[i]
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val gap = w / (bars * 3f)
        val barWidth = (w - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val left = i * (barWidth + gap)
            rect.set(left, h * (1f - heights[i]), left + barWidth, h)
            canvas.drawRoundRect(rect, barWidth / 4f, barWidth / 4f, paint)
        }
    }
}
