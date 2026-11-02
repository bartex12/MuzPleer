package com.example.muzpleer.util.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.muzpleer.util.landscape.PColor.color
import com.example.muzpleer.util.landscape.PColor.lerp
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Параметры пейзажа. Размер [width] x [height] - вся картинка (окно приложения);
 * [viewTop]..[viewBottom] - часть, которая видна между тулбаром и нижним плеером:
 * горизонт, доля воды и тростник считаются по ней.
 */
enum class LandscapeKind {
    CLOUDS, CLOUD_LAKE, FOREST, FOREST_LAKE;

    val hasWater get() = this == CLOUD_LAKE || this == FOREST_LAKE
    val hasClouds get() = this == CLOUDS || this == CLOUD_LAKE
}

/** Общий вид генераторов: готовая картинка (null - расчёт отменили) и номер выпавшей палитры. */
interface LandscapeGenerator {
    fun generate(): Bitmap?
    fun paletteUsed(): Int
}

fun createGenerator(p: LandscapeParams, cancelled: () -> Boolean = { false }): LandscapeGenerator =
    if (p.kind.hasClouds) CloudLakeGenerator(p, cancelled) else ForestGenerator(p, cancelled)

class LandscapeParams(
    val kind: LandscapeKind,
    val seed: Int,
    val width: Int,
    val height: Int,
    val viewTop: Float,
    val viewBottom: Float,
    val water: Float = 0.40f,     // доля видимой высоты под водой
    val cover: Float = 0.5f,      // облачность 0..1
    val palette: Int = -1,        // облака: 0 день, 1 закат, 2 ночь, 3 пасмурно; лес: 0 синий, 1 розовый, 2 серый; -1 - по зерну
    val density: Float = 1f,      // лес: густота, 0.5..2
    val treeHeight: Float = 1f,   // лес: высота деревьев, 0.6..1.6
    val moonSize: Float = 1f,     // лес: размер луны, 0.5..1.6
    val moonPos: Float = -1f      // лес: положение луны по горизонтали 0..1; отрицательное - по зерну
) {
    /** Те же параметры в другом размере (для предпросмотра): всё пропорционально. */
    fun scaled(s: Float) = LandscapeParams(
        kind, seed, (width * s).roundToInt().coerceAtLeast(1), (height * s).roundToInt().coerceAtLeast(1),
        viewTop * s, viewBottom * s, water, cover, palette, density, treeHeight, moonSize, moonPos
    )
}

/**
 * Небо с облаками: с озером (порт CloudLake.pde) или без него (порт Clouds.pde) на Android Canvas.
 */
class CloudLakeGenerator(
    private val p: LandscapeParams,
    private val cancelled: () -> Boolean = { false }
) : LandscapeGenerator {
    private val lake = p.kind == LandscapeKind.CLOUD_LAKE
    private val W = p.width
    private val H = p.height
    private val u = min(W, H) / 900f

    private val rnd = ProcessingRandom(mixSeed(p.seed.toLong()))
    private val nz = ProcessingNoise(mixSeed(p.seed.toLong()))
    private val seedMix = mixSeed(p.seed.toLong())

    private var pal = 0
    private var skyTop = 0; private var skyHor = 0
    private var cLight = 0; private var cShade = 0
    private var cSun = 0; private var cDark = 0
    private var sunX = 0f; private var sunY = 0f; private var sunR = 0f; private var glow = 0f
    private var horizon = 0f
    private var cover = 0f
    private var stretch = 0f
    private var ox = 0f; private var oz = 0f

    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pix = IntArray(W * H)

    /** Возвращает готовую картинку или null, если расчёт отменили. */
    override fun generate(): Bitmap? {
        val viewH = p.viewBottom - p.viewTop
        horizon = p.viewTop + viewH * (if (lake) 1f - p.water else 0.84f)
        pickPalette()
        // облачность всегда тянет один random(), чтобы сцена не менялась от положения ползунка
        if (pal != 3) rnd.random(0.25f, 0.8f) else if (lake) rnd.random(0.8f, 0.95f) else rnd.random(0.85f, 1.0f)
        cover = if (pal == 3) max(p.cover, 0.75f) else p.cover
        stretch = rnd.random(1.0f, 2.5f)
        ox = rnd.random(100f, 1000f)
        oz = rnd.random(100f, 1000f)

        bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        if (!sky()) return null
        bitmap.setPixels(pix, 0, W, 0, 0, W, H)

        if (lake) {
            // дальний берег: холмы в дымке, ближе - тёмная полоса с лесом
            hills(horizon - 8 * u, 55 * u, 0.0025f / u, lerp(skyHor, cDark, 0.35f))
            val far = rnd.random(1f)
            if (far < 0.6f) treeLine(lerp(skyHor, cDark, 0.7f))
            hills(horizon + 2 * u, 14 * u, 0.006f / u, lerp(skyHor, cDark, 0.75f))
            if (!water()) return null
            reeds(cDark)
        } else {
            hills(horizon - 25 * u, 70 * u, 0.0025f / u, lerp(skyHor, cDark, 0.55f))   // дальние холмы
            hills(horizon + 15 * u, 45 * u, 0.0045f / u, cDark)                        // ближние
        }
        return bitmap
    }

    //////////////////////////////////////////////////////////////// палитры
    private fun pickPalette() {
        val pick = rnd.random(4f).toInt()
        pal = if (p.palette in 0..3) p.palette else pick
        // светило ставим так, чтобы его отражение попало в видимую воду
        val lo = max(0.15f, (2 * horizon - p.viewBottom) / horizon + 0.12f)
        when (pal) {
            0 -> {   // день
                skyTop = jc(55f, 105f, 175f); skyHor = jc(165f, 195f, 225f)
                cLight = color(255f, 255f, 255f); cShade = jc(135f, 150f, 180f)
                cSun = color(255f, 250f, 230f); cDark = jc(30f, 45f, 60f)
                sunR = 34 * u; glow = 0.55f
                sunX = rnd.random(0.15f, 0.85f) * W; sunY = (if (lake) rnd.random(lo, lo + 0.3f) else rnd.random(0.12f, 0.45f)) * horizon
            }
            1 -> {   // закат: солнце у горизонта, тёплый свет снизу
                skyTop = jc(60f, 60f, 120f); skyHor = jc(245f, 150f, 95f)
                cLight = jc(255f, 195f, 150f); cShade = jc(100f, 70f, 100f)
                cSun = color(255f, 225f, 170f); cDark = jc(35f, 25f, 40f)
                sunR = 55 * u; glow = 0.8f
                sunX = rnd.random(0.15f, 0.85f) * W; sunY = (if (lake) rnd.random(0.80f, 0.92f) else rnd.random(0.80f, 0.95f)) * horizon
            }
            2 -> {   // ночь: луна и звёзды
                skyTop = jc(8f, 12f, 30f); skyHor = jc(35f, 50f, 80f)
                cLight = jc(165f, 175f, 200f); cShade = jc(28f, 33f, 52f)
                cSun = color(235f, 235f, 225f); cDark = jc(5f, 8f, 15f)
                sunR = 60 * u; glow = 0.35f
                sunX = rnd.random(0.2f, 0.8f) * W; sunY = (if (lake) rnd.random(lo, lo + 0.3f) else rnd.random(0.12f, 0.45f)) * horizon
            }
            else -> {   // пасмурно: солнце только угадывается за облаками
                skyTop = jc(105f, 115f, 130f); skyHor = jc(175f, 180f, 185f)
                cLight = jc(225f, 225f, 228f); cShade = jc(105f, 110f, 120f)
                cSun = color(250f, 248f, 240f); cDark = jc(40f, 45f, 50f)
                sunR = 0f; glow = 0.3f
                sunX = rnd.random(0.2f, 0.8f) * W; sunY = (if (lake) rnd.random(lo, lo + 0.2f) else rnd.random(0.15f, 0.4f)) * horizon
            }
        }
    }

    private fun jc(r: Float, g: Float, b: Float): Int =
        color(r + rnd.random(-12f, 12f), g + rnd.random(-12f, 12f), b + rnd.random(-12f, 12f))

    private fun smooth01(v0: Float): Float {
        val v = if (v0 < 0f) 0f else if (v0 > 1f) 1f else v0
        return v * v * (3 - 2 * v)
    }

    /** Случайное число 0..1 по координатам пикселя: звёзды не зависят от размера картинки и порядка расчёта. */
    private fun hash01(x: Int, y: Int, salt: Int): Float {
        val h = mixSeed(seedMix xor (y.toLong() shl 24) xor x.toLong() xor (salt.toLong() shl 48))
        return ((h ushr 40) and 0xFFFFFF).toInt() / 16777216f
    }

    //////////////////////////////////////////////////////////////// небо и облака
    private fun sky(): Boolean {
        val th = 0.60f + (0.38f - 0.60f) * cover
        val white = color(255f, 255f, 255f)
        val yEnd = if (lake) min(H, ceil(horizon).toInt() + 2) else min(H, ceil(horizon + 80 * u).toInt())
        for (y in 0 until yEnd) {
            if (cancelled()) return false
            val pp = (horizon - y) / horizon
            val skyc = lerp(skyTop, skyHor, (1 - pp).coerceIn(0f, 1f).pow(1.6f))
            val wz = 1 / (max(pp, 0f) + 0.03f)
            val haze = smooth01(pp / 0.3f)
            val vis = smooth01(pp / 0.06f)
            for (x in 0 until W) {
                var c = skyc
                val dx = x - sunX; val dy = y - sunY
                val d = sqrt(dx * dx + dy * dy)
                if (pal == 2 && hash01(x, y, 1) < 0.0007f * pp)
                    c = lerp(c, white, 0.3f + hash01(x, y, 2) * 0.7f)          // звёзды
                c = lerp(c, cSun, glow * exp(-d / (40 * u + sunR * 5)))        // свечение вокруг светила
                if (sunR > 0) c = lerp(c, cSun, (sunR - d + 0.5f).coerceIn(0f, 1f))   // диск
                if (pp > 0) {
                    val nx = ox + (x - W * 0.5f) / W * wz * 2.2f / stretch
                    val nzz = oz + wz * 2.2f
                    nz.detail(5, 0.5f)
                    val n = nz.noise(nx, nzz)
                    val a = smooth01((n - th) / 0.10f)
                    if (a > 0) {
                        val vx = sunX - x; val vy = sunY - y
                        val vl = sqrt(vx * vx + vy * vy) + 1
                        nz.detail(3, 0.5f)
                        val m1 = nz.noise(nx, nzz)
                        val m2 = nz.noise(nx + vx / vl * 0.12f, nzz - vy / vl * 0.12f)
                        val thick = ((n - th) / 0.22f).coerceIn(0f, 1f)
                        val shade = (0.25f + thick * 0.55f - (m1 - m2) * 4).coerceIn(0f, 1f)
                        var cc = lerp(cLight, cShade, shade)
                        cc = lerp(cc, cSun, (1 - thick) * glow * exp(-d / (60 * u + sunR * 8)))
                        cc = lerp(skyHor, cc, haze)
                        c = lerp(c, cc, a * vis)
                    }
                }
                pix[y * W + x] = c
            }
        }
        return true
    }

    //////////////////////////////////////////////////////////////// дальний берег
    private fun hills(base: Float, amp: Float, freq: Float, col: Int) {
        nz.detail(4, 0.5f)
        val h0 = rnd.random(1000f)
        paint.color = col
        val path = Path()
        path.moveTo(0f, H.toFloat())
        var x = 0
        while (x <= W) { path.lineTo(x.toFloat(), base - nz.noise(h0 + x * freq) * amp * 2); x += 2 }
        path.lineTo(W.toFloat(), H.toFloat())
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun treeLine(col: Int) {       // далёкий лес: маленькие деревья из скетча Trees and Stones
        paint.color = col
        var x = rnd.random(0f, 20f) * u
        val gap = if (rnd.random(1f) < 0.5f) rnd.random(0.2f, 0.6f) * W else -1f   // иногда - просвет в лесу
        val gw = rnd.random(0.15f, 0.35f) * W
        while (x < W) {
            if (gap < 0 || x < gap || x > gap + gw) {
                val sz = rnd.random(0.08f, 0.14f)
                canvas.save()
                canvas.translate(x, horizon + 2 * u)
                canvas.rotate(Math.toDegrees(rnd.random(-0.08f, 0.08f).toDouble()).toFloat())
                branch(sz, 0.3f, 0.1f, 7, true)
                canvas.restore()
            }
            x += rnd.random(8f, 26f) * u
        }
    }

    private fun branch(sz: Float, b: Float, j: Float, d: Int, s: Boolean) {
        val w = 12 * sz * u
        val len = 135 * sz * u
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

    //////////////////////////////////////////////////////////////// вода
    private fun water(): Boolean {
        bitmap.getPixels(pix, 0, W, 0, 0, W, H)
        val src = pix.clone()                          // картинка над водой - из неё берём отражение
        val deep = lerp(skyHor, cDark, 0.55f)          // цвет толщи воды
        val light = lerp(skyTop, skyHor, 0.6f)         // небо, которое отражают грани волн
        val glint = lerp(cSun, color(255f, 255f, 255f), 0.3f)
        val y0 = ceil(horizon).toInt()
        val wh = p.viewBottom - horizon                // высота водной глади в видимой части
        val ym = 2 * horizon - sunY                    // где в воде отражается центр светила
        val L = 45 * u                                 // длина волны ряби у нижнего края
        val kz = 1.12f * wh / L                        // частота шума поперёк волн
        val kx = W / (0.94f * 7 * L)                   // вдоль волн ряби в 7 раз длиннее
        val hw0 = max(sunR, 30 * u)
        nz.detail(3, 0.5f)

        for (y in y0 until H) {
            if (cancelled()) return false
            val pp = (y - horizon) / wh                // 0 - у дальнего берега, 1 - у зрителя
            val wz = 1 / (pp + 0.06f)
            val nzz = oz + 500 + wz * kz
            val amp = (1.0f + RIPPLE * pp * pp) * u
            val mod = smooth01(pp / 0.12f)
            val fres = 0.10f + 0.40f * pp
            val hw = hw0 * (0.5f + 1.2f * pp)
            val along = 0.35f + 0.65f * exp(-abs(y - ym) / (wh * 0.35f))

            for (x in 0 until W) {
                val nx = ox + 500 + (x - W * 0.5f) / W * wz * kx
                val n1 = nz.noise(nx, nzz)
                val n2 = nz.noise(nx + 37.3f, nzz)
                val n3 = nz.noise(nx, nzz + 0.25f)
                val slope = ((n3 - n1) * 5).coerceIn(-1f, 1f)

                val sx = (x + (n1 - 0.45f) * 2.5f * amp * mod).roundToInt().coerceIn(0, W - 1)
                val sy = (2 * horizon - y + (n2 - 0.45f) * 1.2f * amp * mod).roundToInt().coerceIn(0, y0 - 1)
                var col = src[sy * W + sx]

                col = lerp(col, deep, fres)
                col = if (slope > 0) lerp(col, light, slope * 0.25f * mod)
                else lerp(col, deep, -slope * 0.30f * mod)

                if (sunR > 0) {                        // дорожка бликов от солнца или луны
                    val dxm = (x - sunX) / hw
                    if (abs(dxm) < 1.6f) {
                        val g = smooth01((slope - 0.15f) / 0.30f) * exp(-dxm * dxm * 2) * along
                        col = lerp(col, glint, g * 0.85f)
                    }
                }
                pix[y * W + x] = col
            }
        }
        // тонкая светлая кромка вдоль линии воды
        val edge = max(1, (1.5f * u).roundToInt())
        for (x in 0 until W) {
            val a = smooth01((nz.noise(ox + x * 0.02f / u, 7.7f) - 0.40f) / 0.15f) * 0.5f
            var y = y0
            while (y < y0 + edge && y < H) { pix[y * W + x] = lerp(pix[y * W + x], skyHor, a); y++ }
        }
        bitmap.setPixels(pix, 0, W, 0, 0, W, H)
        return true
    }

    //////////////////////////////////////////////////////////////// тростник на переднем плане
    private fun reeds(col: Int) {
        paint.color = col
        val base = p.viewBottom + 10 * u
        val clusters = rnd.random(0f, 3.99f).toInt()          // иногда тростника нет совсем
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

    /** Какая палитра выпала (для подписи на экране). */
    override fun paletteUsed(): Int = pal

    private companion object {
        const val RIPPLE = 14f      // сила ряби: 0 - гладкое зеркало, 30 - ветрено
    }
}
