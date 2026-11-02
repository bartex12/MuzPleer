package com.example.muzpleer.util.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.muzpleer.util.landscape.PColor.color
import com.example.muzpleer.util.landscape.PColor.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Лес: деревья на нескольких планах на фоне луны (порт Trees_and_Stones_Phone.pde)
 * и тот же лес на дальнем берегу озера с отражением (порт Lake.pde).
 */
class ForestGenerator(
    private val p: LandscapeParams,
    private val cancelled: () -> Boolean = { false }
) : LandscapeGenerator {
    private val lake = p.kind == LandscapeKind.FOREST_LAKE
    private val W = p.width
    private val H = p.height
    private val u = min(W, H) / 900f
    private val fq = 1.25f * p.density
    private var tall = 1f
    private val hk get() = p.treeHeight

    private val seedMix = mixSeed(p.seed.toLong())
    private val rnd = ProcessingRandom(seedMix)
    private val nz = ProcessingNoise(seedMix)
    private val level = FloatArray(W + 1)
    private var colorCase = 0

    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var ox = 0f; private var oz = 0f
    private var shore = 0f
    private var moonX = 0f; private var moonY = 0f; private var moonR = 0f
    private var layerIndex = 0

    override fun paletteUsed(): Int = colorCase

    override fun generate(): Bitmap? {
        val c = pickColor()
        val cb = color(red(c) * 0.7f, green(c) * 0.7f, blue(c) * 0.7f)
        val cf = color(red(c) * 0.15f, green(c) * 0.15f, blue(c) * 0.15f)
        bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        canvas.drawColor(cb)
        val viewH = p.viewBottom - p.viewTop
        layerIndex = 0

        if (lake) {
            shore = p.viewTop + viewH * (1f - p.water)
            // деревья подстраиваются под высоту области над водой (в исходнике она была 900)
            tall = ((0.85f * (shore - p.viewTop) / u / 900f)).coerceIn(0.75f, 1.6f)
            ox = rnd.random(100f, 1000f)
            oz = rnd.random(100f, 1000f)
            // луна: над лесом и так, чтобы её отражение попало в воду
            moonR = (if (H > W) 210f else 150f) * u * p.moonSize
            val rx = rnd.random(0.35f, 0.7f)
            moonX = W * (if (p.moonPos >= 0f) p.moonPos else rx)
            moonY = shore - moonR - rnd.random(0.12f, 0.3f) * (shore - p.viewTop)
            moonY = max(moonY, p.viewTop + moonR * 0.3f)
            paint.color = c
            canvas.drawOval(RectF(moonX - moonR, moonY - moonR, moonX + moonR, moonY + moonR), paint)
            layer(0.275f, shore - 50 * u, 0.10f, 0.15f, 8, cb, cf)    // дальний план
            layer(0.55f, shore - 35 * u, 0.40f, 0.50f, 10, cb, cf)    // средний
            layer(1.1f, shore - 15 * u, 0.80f, 1.00f, 12, cb, cf)     // ближний берег
            if (!water(cb, cf, c)) return null
            reeds(cf, 1)
        } else {
            val vb = p.viewBottom
            paint.color = c
            if (H > W) {   // вертикальный экран: четыре плана, деревья выше и стройнее
                tall = max(1f, 1.65f * (viewH / W) / (2712f / 1220f))
                val mx = W * (if (p.moonPos >= 0f) p.moonPos else 0.64f); val my = p.viewTop + viewH * 0.34f; val mr = 240 * u * p.moonSize
                canvas.drawOval(RectF(mx - mr, my - mr, mx + mr, my + mr), paint)
                layer(0.20f, vb - 110 * u, 0.05f, 0.10f, 8, cb, cf)
                layer(0.35f, vb - 80 * u, 0.25f, 0.32f, 9, cb, cf)
                layer(0.60f, vb - 50 * u, 0.48f, 0.58f, 10, cb, cf)
                layer(1.10f, vb - 20 * u, 0.80f, 1.00f, 12, cb, cf)
            } else {
                tall = 1f
                val mx = W * (if (p.moonPos >= 0f) p.moonPos else 0.7f); val my = p.viewTop + viewH * 0.45f; val mr = 225 * u * p.moonSize
                canvas.drawOval(RectF(mx - mr, my - mr, mx + mr, my + mr), paint)
                layer(0.275f, vb - 50 * u, 0.10f, 0.15f, 8, cb, cf)
                layer(0.55f, vb - 40 * u, 0.40f, 0.50f, 10, cb, cf)
                layer(1.1f, vb - 20 * u, 0.80f, 1.00f, 12, cb, cf)
            }
        }
        return if (cancelled()) null else bitmap
    }

    private fun red(c: Int) = ((c shr 16) and 0xff).toFloat()
    private fun green(c: Int) = ((c shr 8) and 0xff).toFloat()
    private fun blue(c: Int) = (c and 0xff).toFloat()

    private fun irandom(mn: Int, mx: Int) = rnd.random(mn.toFloat(), mx + 1.0f).toInt()

    /** Цвет луны и неба: синий, розовый или серый (как getColor() в скетчах). */
    private fun pickColor(): Int {
        val pick = rnd.random(3f).toInt()   // irandom(2): число 0..2
        colorCase = if (p.palette in 0..2) p.palette else pick
        return when (colorCase) {
            0 -> color(irandom(85, 105).toFloat(), irandom(135, 155).toFloat(), irandom(165, 185).toFloat())
            1 -> color(irandom(165, 185).toFloat(), irandom(115, 135).toFloat(), irandom(125, 145).toFloat())
            else -> color(irandom(140, 150).toFloat(), irandom(140, 160).toFloat(), irandom(140, 160).toFloat())
        }
    }

    private fun smooth01(v0: Float): Float {
        val v = if (v0 < 0f) 0f else if (v0 > 1f) 1f else v0
        return v * v * (3 - 2 * v)
    }

    /** Случайное 0..1 по номеру клетки: неровность земли не зависит от размера картинки. */
    private fun hash01(i: Int, salt: Int): Float {
        val h = mixSeed(seedMix xor (i.toLong() shl 20) xor (salt.toLong() shl 48))
        return ((h ushr 40) and 0xFFFFFF).toInt() / 16777216f
    }

    //////////////////////////////////////////////////////////////// слой леса
    // base - линия земли этого плана, ts/tg - насколько цвет камней и земли ушёл от неба к тёмному
    private fun layer(sz: Float, base: Float, ts: Float, tg: Float, d: Int, cb: Int, cf: Int) {
        layerIndex++
        level(sz, base)
        paint.color = lerp(cb, cf, ts)
        // кандидаты на камень - по одному на клетку размером u пикселей (число случайных не зависит от размера)
        val cells = (W / u).toInt()
        for (k in 0 until cells) if (rnd.random(1f) < 0.01f / sz) stone(k * u, sz)
        paint.color = lerp(cb, cf, tg)
        ground(sz)
        var x = rnd.random(80 * sz * u, 240 * sz * u) / fq
        while (x < W) {
            tree(x, rnd.random(sz * 0.75f, sz * 1.25f), 0.3f, 0.1f, d)
            x += rnd.random(80 * sz * u, 240 * sz * u) / fq
        }
        x = rnd.random(120 * sz * u, 360 * sz * u) / fq
        while (x < W) {
            bush(x, rnd.random(sz * 0.75f, sz * 1.25f), 0.3f, 0.1f, d * 3 / 4)
            x += rnd.random(120 * sz * u, 360 * sz * u) / fq
        }
    }

    // уровень земли: ломаная со случайными отрезками (отрезки считаем в единицах u, чтобы число случайных не менялось с размером)
    private fun level(sz: Float, base: Float) {
        val d = sz * 150 * u
        val v = 25 * sz * u
        var p = rnd.random(d / 4, d)
        var v1 = rnd.random(v)
        var v2 = rnd.random(v)
        var segStart = 0f
        for (x in 0..W) {
            while (x >= segStart + p) {
                segStart += p
                p = rnd.random(d / 4, d)
                v1 = v2
                v2 = rnd.random(v)
            }
            level[x] = base - (v1 + (v2 - v1) * (x - segStart) / p)
        }
    }

    private fun levelAt(x: Float): Float = level[(x + 2 * W).toInt() % W]

    private fun ground(sz: Float) {
        val path = Path()
        path.moveTo(0f, H.toFloat())
        for (x in 0..W) {
            val jitter = (hash01((x / u).toInt(), layerIndex) * 4f - 2f)
            path.lineTo(x.toFloat(), level[x] - sz * u * jitter)
        }
        path.lineTo(W.toFloat(), H.toFloat())
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun stone(x: Float, sz: Float) {
        val a = (PI * 0.7).toFloat()
        var ad = -a
        var d = sz * 19 * u
        var y = levelAt(x)
        d *= rnd.random(0.5f, 1.5f)
        if (rnd.random(1f) < 0.1f) d *= 3
        var r = rnd.random(d * 0.8f, d * 1.2f)
        y += rnd.random(d * 0.1f, d * 0.25f)
        val path = Path()
        var first = true
        var go = true
        while (go) {
            if (ad > a) { ad = a; go = false }
            r += rnd.random(-d * 0.1f, d * 0.1f)
            val px = x + r * sin(ad); val py = y - 0.5f * r * cos(ad)
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            ad += rnd.random(0.1f, 0.3f)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun bush(x: Float, sz0: Float, rb: Float, rj: Float, d: Int) {
        val sz = sz0 * rnd.random(0.3f, 0.5f)
        canvas.save()
        canvas.translate(x, levelAt(x) + rnd.random(100f, 200f) * sz * u * tall * hk)
        canvas.rotate(Math.toDegrees(rnd.random(-0.1f, 0.1f).toDouble()).toFloat())
        branch(sz, rb, rj, d, true)
        canvas.restore()
    }

    private fun tree(x: Float, sz: Float, rb: Float, rj: Float, d: Int) {
        canvas.save()
        canvas.translate(x, levelAt(x) + 6 * sz * u)
        canvas.rotate(Math.toDegrees(rnd.random(-0.1f, 0.1f).toDouble()).toFloat())
        branch(sz, rb, rj, d, true)
        canvas.restore()
    }

    private fun branch(sz: Float, b: Float, j: Float, d: Int, s: Boolean) {
        val w = 12 * sz * u
        val len = 135 * sz * u * tall * hk
        val sc = if (s) 0.8f else 0.9f
        if (d > 0) {
            val v = rnd.random(1f) < 0.6f
            fromTo(0f, 0f, 0f, -len, w, w * sc, v)
            canvas.translate(0f, -len)
            canvas.rotate(Math.toDegrees(rnd.random(-0.1f, 0.1f).toDouble()).toFloat())
            if (v) {
                val k = sc * 0.833f
                canvas.scale(k, k)
                canvas.save(); canvas.rotate(Math.toDegrees(rnd.random(b - j, b + j).toDouble()).toFloat()); branch(sz, b, j, d - 1, false); canvas.restore()
                canvas.save(); canvas.rotate(Math.toDegrees(rnd.random(-b - j, -b + j).toDouble()).toFloat()); branch(sz, b, j, d - 1, false); canvas.restore()
            } else {
                canvas.scale(sc, sc)
                branch(sz, b, j, d, false)
            }
        }
    }

    private fun fromTo(x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float, v: Boolean) {
        var dx = x2 - x1; var dy = y2 - y1
        val dd = dx * dx + dy * dy
        if (dd < 0.001f) return
        val l = sqrt(dd)
        dx /= l; dy /= l
        val path = Path()
        var ax = dx * w1 / 2; var ay = dy * w1 / 2
        path.moveTo(x1 - ay, y1 + ax)
        path.lineTo(x1 - ax, y1 - ay)
        path.lineTo(x1 + ay, y1 - ax)
        ax = dx * w2 / 2; ay = dy * w2 / 2
        if (v) {
            path.lineTo(x2 + ay + ax / 2, y2 - ax + ay / 2)
            path.lineTo(x2 - ay + ax / 2, y2 + ax + ay / 2)
        } else {
            path.lineTo(x2 + ay, y2 - ax)
            path.lineTo(x2 + ax, y2 + ay)
            path.lineTo(x2 - ay, y2 + ax)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    //////////////////////////////////////////////////////////////// вода (как в Lake.pde)
    private fun water(cb: Int, cf: Int, cm: Int): Boolean {
        val pix = IntArray(W * H)
        bitmap.getPixels(pix, 0, W, 0, 0, W, H)
        val src = pix.clone()                              // картинка над водой - из неё берём отражение
        val deep = lerp(cb, cf, 0.75f)                     // цвет толщи воды
        val glow = lerp(cm, color(255f, 255f, 255f), 0.35f)   // блики чуть светлее луны
        val y0 = ceil(shore).toInt()
        val wh = p.viewBottom - shore
        val ym = 2 * shore - moonY                         // где в воде отражается центр луны
        val L = 45 * u
        val kz = 1.12f * wh / L
        val kx = W / (0.94f * 7 * L)
        nz.detail(3, 0.5f)

        for (y in y0 until H) {
            if (cancelled()) return false
            val pp = (y - shore) / wh
            val wz = 1 / (pp + 0.06f)
            val nzz = oz + wz * kz
            val amp = (1.0f + 20 * pp * pp) * u
            val mod = smooth01(pp / 0.12f)
            val fres = 0.20f + 0.40f * pp
            val hw = moonR * (0.45f + 1.1f * pp)
            val along = 0.35f + 0.65f * exp(-abs(y - ym) / (wh * 0.35f))

            for (x in 0 until W) {
                val nx = ox + (x - W * 0.5f) / W * wz * kx
                val n1 = nz.noise(nx, nzz)
                val n2 = nz.noise(nx + 37.3f, nzz)
                val n3 = nz.noise(nx, nzz + 0.25f)
                val slope = ((n3 - n1) * 5).coerceIn(-1f, 1f)

                val sx = (x + (n1 - 0.45f) * 2.5f * amp * mod).roundToInt().coerceIn(0, W - 1)
                val sy = (2 * shore - y + (n2 - 0.45f) * 1.2f * amp * mod).roundToInt().coerceIn(0, y0 - 1)
                var col = src[sy * W + sx]

                col = lerp(col, deep, fres)
                col = if (slope > 0) lerp(col, cb, slope * 0.30f * mod)
                else lerp(col, deep, -slope * 0.35f * mod)

                val dxm = (x - moonX) / hw
                if (abs(dxm) < 1.6f) {                      // лунная дорожка
                    val g = smooth01((slope - 0.15f) / 0.30f) * exp(-dxm * dxm * 2) * along
                    col = lerp(col, glow, g * 0.9f)
                }
                pix[y * W + x] = col
            }
        }
        val edge = max(1, (1.5f * u).roundToInt())
        for (x in 0 until W) {
            val a = smooth01((nz.noise(ox + x * 0.02f / u, 7.7f) - 0.40f) / 0.15f) * 0.5f
            var y = y0
            while (y < y0 + edge && y < H) { pix[y * W + x] = lerp(pix[y * W + x], cb, a); y++ }
        }
        bitmap.setPixels(pix, 0, W, 0, 0, W, H)
        return true
    }

    //////////////////////////////////////////////////////////////// тростник на переднем плане
    private fun reeds(col: Int, minClusters: Int) {
        paint.color = col
        val base = p.viewBottom + 10 * u
        val clusters = rnd.random(minClusters.toFloat(), 3.99f).toInt()
        for (k in 0 until clusters) {
            val cx = (if (rnd.random(1f) < 0.5f) rnd.random(-0.05f, 0.3f) else rnd.random(0.7f, 1.05f)) * W
            val n = rnd.random(10f, 22f).toInt()
            for (i in 0 until n) {
                val x = cx + rnd.random(-110f, 110f) * u
                val h = rnd.random(120f, 360f) * u * (1 - 0.5f * abs(x - cx) / (110 * u))
                reed(x, base, h, rnd.random(3f, 6f) * u, rnd.random(-0.35f, 0.35f))
                if (rnd.random(1f) < 0.2f) cattail(x, base, h * rnd.random(0.8f, 1.05f), rnd.random(-0.15f, 0.15f))
            }
        }
    }

    private fun reed(x: Float, y: Float, h: Float, w: Float, bend: Float) {
        val n = 14
        val path = Path()
        for (i in 0..n) {
            val t = i.toFloat() / n
            val px = x + bend * h * t * t - w * (1 - t) / 2; val py = y - h * t
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in n downTo 0) {
            val t = i.toFloat() / n
            path.lineTo(x + bend * h * t * t + w * (1 - t) / 2, y - h * t)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun cattail(x: Float, y: Float, h: Float, bend: Float) {
        reed(x, y, h, 3 * u, bend)
        val tx = x + bend * h * 0.8f * 0.8f
        val ty = y - h * 0.8f
        canvas.save()
        canvas.translate(tx, ty)
        canvas.rotate(Math.toDegrees(atan(bend * 1.6f).toDouble()).toFloat())
        canvas.drawRoundRect(RectF(-5 * u, -30 * u, 5 * u, 30 * u), 5 * u, 5 * u, paint)
        canvas.restore()
    }
}
