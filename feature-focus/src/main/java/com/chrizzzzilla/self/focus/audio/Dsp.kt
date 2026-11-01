package com.chrizzzzilla.self.focus.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** Gemeinsame DSP-Bausteine fuer alle Klang-Ebenen (ohne Allokationen im Audio-Pfad). */
internal class Dsp(val sampleRate: Int) {
    val sr = sampleRate.toFloat()
    val random = java.util.Random()
    fun rand(): Float = random.nextFloat()
    fun randInt(n: Int): Int = random.nextInt(n)

    private val tableSize = 4096
    private val table = FloatArray(tableSize + 1) { sin(2.0 * PI * it / tableSize).toFloat() }

    /** Sinus, Phase in Umdrehungen (0..1, darf ueberlaufen). */
    fun sin1(phase: Float): Float {
        var p = phase - phase.toInt()
        if (p < 0f) p += 1f
        val x = p * tableSize
        val i = x.toInt()
        val f = x - i
        return table[i] + (table[i + 1] - table[i]) * f
    }

    private var seed = 0x2545F491
    /** Weisses Rauschen -1..1 (xorshift). */
    fun noise(): Float {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return x * (1f / Int.MAX_VALUE)
    }

    fun decay(seconds: Float): Float = exp(-1f / (seconds * sr))
    fun midiHz(midi: Float): Float = (440.0 * 2.0.pow((midi - 69.0) / 12.0)).toFloat()
    /** Koeffizient fuer einen Ein-Pol-Tiefpass bei [hz]. */
    fun onePole(hz: Float): Float = (1f - exp(-2f * PI.toFloat() * hz / sr)).coerceIn(0.0001f, 1f)
}

/** Ein-Pol-Tiefpass. */
internal class OnePole {
    var z = 0f
    fun lp(x: Float, coef: Float): Float { z += coef * (x - z); return z }
}

/** State-Variable-Filter (Tief-/Band-/Hochpass), stabil auch bei Modulation. */
internal class Svf {
    private var ic1 = 0f
    private var ic2 = 0f
    private var g = 0f
    private var k = 1f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    var low = 0f; private set
    var band = 0f; private set
    var high = 0f; private set

    fun set(sr: Float, cutoff: Float, q: Float) {
        g = tan(PI * cutoff.coerceIn(20f, sr * 0.45f) / sr).toFloat()
        k = 1f / q.coerceAtLeast(0.3f)
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun process(x: Float) {
        val v3 = x - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2f * v1 - ic1
        ic2 = 2f * v2 - ic2
        low = v2
        band = v1
        high = x - k * v1 - v2
    }
}

/** Kleiner Stereo-Hall (Schroeder/Freeverb-Prinzip). */
internal class Reverb(sampleRate: Int, private val feedback: Float = 0.82f, private val damp: Float = 0.3f) {
    private val scale = sampleRate / 44100f
    private val combL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491).map { Comb((it * scale).toInt()) }
    private val combR = intArrayOf(1139, 1211, 1300, 1379, 1445, 1514).map { Comb((it * scale).toInt()) }
    private val apL = intArrayOf(556, 441, 341).map { AllPass((it * scale).toInt()) }
    private val apR = intArrayOf(579, 464, 364).map { AllPass((it * scale).toInt()) }

    var outL = 0f; private set
    var outR = 0f; private set

    fun process(input: Float) {
        var l = 0f
        var r = 0f
        for (c in combL) l += c.process(input)
        for (c in combR) r += c.process(input)
        for (a in apL) l = a.process(l)
        for (a in apR) r = a.process(r)
        outL = l * 0.16f
        outR = r * 0.16f
    }

    private inner class Comb(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(1))
        private var idx = 0
        private var store = 0f
        fun process(x: Float): Float {
            val y = buf[idx]
            store = y * (1f - damp) + store * damp
            buf[idx] = x + store * feedback
            idx++; if (idx == buf.size) idx = 0
            return y
        }
    }

    private class AllPass(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(1))
        private var idx = 0
        fun process(x: Float): Float {
            val b = buf[idx]
            val y = -x + b
            buf[idx] = x + b * 0.5f
            idx++; if (idx == buf.size) idx = 0
            return y
        }
    }
}

internal fun softClip(x: Float): Float {
    val c = x.coerceIn(-3f, 3f)
    return c * (27f + c * c) / (27f + 9f * c * c)
}
