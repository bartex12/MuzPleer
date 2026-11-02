package com.example.muzpleer.util.landscape

import java.util.Random

/**
 * Точные копии random() и noise() из Processing: одно и то же зерно даёт те же числа,
 * что и в скетчах, поэтому картинка совпадает со скетчем.
 */
class ProcessingRandom(seed: Long) {
    private val rnd = Random(seed)

    fun random(high: Float): Float {
        if (high == 0f || high != high) return 0f
        var value: Float
        do { value = rnd.nextFloat() * high } while (value == high)
        return value
    }

    fun random(low: Float, high: Float): Float {
        if (low >= high) return low
        val diff = high - low
        var value: Float
        do { value = random(diff) + low } while (value == high)
        return value
    }
}

class ProcessingNoise(seed: Long) {
    private val perlin = FloatArray(PERLIN_SIZE + 1)
    private var octaves = 4
    private var falloff = 0.5f

    init {
        val r = Random(seed)
        for (i in 0..PERLIN_SIZE) perlin[i] = r.nextFloat()
    }

    fun detail(octaves: Int, falloff: Float) {
        this.octaves = octaves
        this.falloff = falloff
    }

    private fun fsc(i: Float): Float = 0.5f * (1.0f - COS[(i * PI_IDX).toInt() % TWO_PI_IDX])

    fun noise(x0: Float, y0: Float = 0f, z0: Float = 0f): Float {
        var x = if (x0 < 0) -x0 else x0
        var y = if (y0 < 0) -y0 else y0
        var z = if (z0 < 0) -z0 else z0
        var xi = x.toInt(); var yi = y.toInt(); var zi = z.toInt()
        var xf = x - xi; var yf = y - yi; var zf = z - zi
        var r = 0f
        var ampl = 0.5f
        for (i in 0 until octaves) {
            var of = xi + (yi shl YWRAPB) + (zi shl ZWRAPB)
            val rxf = fsc(xf)
            val ryf = fsc(yf)
            var n1 = perlin[of and PERLIN_SIZE]
            n1 += rxf * (perlin[(of + 1) and PERLIN_SIZE] - n1)
            var n2 = perlin[(of + YWRAP) and PERLIN_SIZE]
            n2 += rxf * (perlin[(of + YWRAP + 1) and PERLIN_SIZE] - n2)
            n1 += ryf * (n2 - n1)
            of += ZWRAP
            n2 = perlin[of and PERLIN_SIZE]
            n2 += rxf * (perlin[(of + 1) and PERLIN_SIZE] - n2)
            val n3a = perlin[(of + YWRAP) and PERLIN_SIZE]
            val n3 = n3a + rxf * (perlin[(of + YWRAP + 1) and PERLIN_SIZE] - n3a)
            n2 += ryf * (n3 - n2)
            n1 += fsc(zf) * (n2 - n1)
            r += n1 * ampl
            ampl *= falloff
            xi = xi shl 1; xf *= 2
            yi = yi shl 1; yf *= 2
            zi = zi shl 1; zf *= 2
            if (xf >= 1.0f) { xi++; xf-- }
            if (yf >= 1.0f) { yi++; yf-- }
            if (zf >= 1.0f) { zi++; zf-- }
        }
        return r
    }

    private companion object {
        const val YWRAPB = 4
        const val YWRAP = 1 shl YWRAPB
        const val ZWRAPB = 8
        const val ZWRAP = 1 shl ZWRAPB
        const val PERLIN_SIZE = 4095
        const val TWO_PI_IDX = 720        // SINCOS_LENGTH Processing при точности 0,5 градуса
        const val PI_IDX = 360
        val COS = FloatArray(TWO_PI_IDX).also {
            val degToRad = Math.PI.toFloat() / 180.0f
            for (i in it.indices) it[i] = Math.cos((i * degToRad * 0.5f).toDouble()).toFloat()
        }
    }
}

/** Цвета в стиле Processing: color() усекает до целого, lerpColor() считает по каналам. */
object PColor {
    fun color(r: Float, g: Float, b: Float): Int =
        (255 shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)

    fun gray(v: Float): Int = color(v, v, v)

    private fun ch(v: Float): Int = (if (v < 0f) 0f else if (v > 255f) 255f else v).toInt()

    fun lerp(c1: Int, c2: Int, amt0: Float): Int {
        val amt = if (amt0 < 0f) 0f else if (amt0 > 1f) 1f else amt0
        val r1 = (c1 shr 16) and 0xff; val g1 = (c1 shr 8) and 0xff; val b1 = c1 and 0xff
        val r2 = (c2 shr 16) and 0xff; val g2 = (c2 shr 8) and 0xff; val b2 = c2 and 0xff
        return (255 shl 24) or
            ((r1 + (r2 - r1) * amt).toInt() shl 16) or
            ((g1 + (g2 - g1) * amt).toInt() shl 8) or
            (b1 + (b2 - b1) * amt).toInt()
    }
}

/** Хеш SplitMix64 из скетчей: соседние зёрна превращаются в совсем разные числа. */
fun mixSeed(z0: Long): Long {
    var z = z0 + (-0x61c8864680b583ebL)          // 0x9E3779B97F4A7C15
    z = (z xor (z ushr 30)) * (-0x40a7b892e31b1a47L)   // 0xBF58476D1CE4E5B9
    z = (z xor (z ushr 27)) * (-0x6b2fb644ecceee15L)   // 0x94D049BB133111EB
    return z xor (z ushr 31)
}
