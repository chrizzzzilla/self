package com.chrizzzzilla.self.focus.audio

import kotlin.math.abs

/**
 * Naturgeraeusche, beliebig kombinierbar. Jedes Geraeusch blendet beim An-/Abwaehlen weich ein und aus.
 */
internal class NatureLayer(private val d: Dsp) {

    var outL = 0f; private set
    var outR = 0f; private set
    var send = 0f; private set

    private val count = NatureSound.entries.size
    private val target = FloatArray(count)
    private val gain = FloatArray(count)

    fun setActive(active: Set<NatureSound>) {
        for (s in NatureSound.entries) target[s.ordinal] = if (s in active) 1f else 0f
    }

    // ---------- Regen ----------
    private val rainLp1 = OnePole(); private val rainLp2 = OnePole(); private val rainHp = OnePole()
    private var rainSwell = 0.5f
    private var dropEnv = 0f; private var dropPhase = 0f; private var dropFreq = 3000f
    private val dropDecay = d.decay(0.012f)

    // ---------- Wind ----------
    private val windSvfL = Svf(); private val windSvfR = Svf()
    private var windCut = 400f; private var windCutTarget = 400f
    private var windAmp = 0.5f; private var windAmpTarget = 0.5f
    private var windCounter = 0

    // ---------- Meer ----------
    private class Wave {
        var t = 0f; var length = 10f; var peak = 1f; var active = false
    }
    private val waves = arrayOf(Wave(), Wave())
    private var nextWave = 0f
    private val seaSvf = Svf(); private val seaHiss = OnePole()

    // ---------- Voegel ----------
    private class Chirp {
        var active = false; var delay = 0
        var phase = 0f; var f0 = 0f; var f1 = 0f; var t = 0f; var dur = 0.1f
        var amp = 0f; var pan = 0.5f; var trill = 0f; var trillPhase = 0f
    }
    private val chirps = Array(10) { Chirp() }
    private var birdTimer = 0f

    // ---------- Grillen ----------
    private class Cricket { var phase = 0f; var freq = 4500f; var rate = 2.2f; var clock = 0f; var pulses = 3; var pan = 0.5f; var amp = 0.5f }
    private val crickets = Array(4) { Cricket() }
    private var cricketsInit = false

    // ---------- Bach ----------
    private val streamBase = OnePole()
    private val bubbles = Array(5) { Svf() }
    private val bubbleFreq = FloatArray(5) { 600f + it * 300f }
    private var bubbleCounter = 0

    // ---------- Kaminfeuer ----------
    private val fireLp = OnePole()
    private var crackEnv = 0f; private var crackPhase = 0f; private var crackFreq = 2000f
    private val crackDecay = d.decay(0.008f)
    private var popEnv = 0f
    private val popDecay = d.decay(0.05f)
    private val popLp = OnePole()

    // ---------- Gewitter ----------
    private val thunderLp1 = OnePole(); private val thunderLp2 = OnePole()
    private var thunderEnv = 0f; private var thunderTarget = 0f; private var thunderNext = 8f; private var thunderT = 0f
    private var thunderLen = 4f

    fun next() {
        var l = 0f
        var r = 0f
        var rev = 0f
        val dt = 1f / d.sr

        for (i in 0 until count) {
            val g = gain[i]
            val tgt = target[i]
            if (g != tgt) gain[i] = if (g < tgt) (g + dt / 2f).coerceAtMost(tgt) else (g - dt / 2.5f).coerceAtLeast(tgt)
        }

        // Regen
        val gRain = gain[NatureSound.RAIN.ordinal]
        if (gRain > 0.001f) {
            val n = d.noise()
            val a = rainLp1.lp(n, 0.35f)
            val b = rainLp2.lp(a, 0.35f)
            val bright = n - rainHp.lp(n, 0.2f)
            rainSwell += 0.00002f * (d.rand() - rainSwell)
            val rain = (b * 0.32f + bright * 0.035f) * (0.75f + 0.5f * rainSwell)
            if (dropEnv < 0.001f && d.rand() < 0.0012f) {
                dropEnv = 0.04f + 0.06f * d.rand(); dropFreq = 1600f + 3600f * d.rand()
            }
            var drop = 0f
            if (dropEnv > 0.0005f) {
                dropPhase += dropFreq * dt; if (dropPhase > 1f) dropPhase -= 1f
                drop = d.sin1(dropPhase) * dropEnv
                dropEnv *= dropDecay; dropFreq *= 0.9997f
            }
            l += (rain + drop) * gRain
            r += (rain * 0.9f + d.noise() * 0.012f + drop * 0.5f) * gRain
        }

        // Wind
        val gWind = gain[NatureSound.WIND.ordinal]
        if (gWind > 0.001f) {
            if (--windCounter <= 0) {
                windCounter = 64
                if (d.rand() < 0.003f) { windCutTarget = 250f + d.rand() * 900f; windAmpTarget = 0.25f + d.rand() * 0.9f }
                windCut += (windCutTarget - windCut) * 0.004f
                windAmp += (windAmpTarget - windAmp) * 0.003f
                windSvfL.set(d.sr, windCut, 2.2f)
                windSvfR.set(d.sr, windCut * 1.13f, 2.2f)
            }
            windSvfL.process(d.noise()); windSvfR.process(d.noise())
            l += windSvfL.band * windAmp * 0.55f * gWind
            r += windSvfR.band * windAmp * 0.55f * gWind
        }

        // Meer
        val gSea = gain[NatureSound.SEA.ordinal]
        if (gSea > 0.001f) {
            nextWave -= dt
            if (nextWave <= 0f) {
                val w = waves.firstOrNull { !it.active } ?: waves[0]
                w.active = true; w.t = 0f; w.length = 7f + d.rand() * 6f; w.peak = 0.6f + d.rand() * 0.5f
                nextWave = 5f + d.rand() * 5f
            }
            var env = 0.12f
            var crash = 0f
            for (w in waves) {
                if (!w.active) continue
                w.t += dt
                val p = w.t / w.length
                if (p >= 1f) { w.active = false; continue }
                // Anschwellen, Brechen, langes Auslaufen
                val e = if (p < 0.35f) (p / 0.35f).let { it * it } else (1f - (p - 0.35f) / 0.65f).let { it * it }
                env += e * w.peak
                if (p in 0.3f..0.5f) crash += (1f - abs(p - 0.4f) / 0.1f) * w.peak
            }
            seaSvf.set(d.sr, 350f + env * 900f + crash * 1500f, 0.7f)
            val n = d.noise()
            seaSvf.process(n)
            val hiss = (n - seaHiss.lp(n, 0.3f)) * crash * 0.08f
            val sea = seaSvf.low * env * 0.42f + hiss
            l += sea * gSea
            r += (sea * 0.92f + d.noise() * 0.01f * env) * gSea
        }

        // Voegel
        val gBirds = gain[NatureSound.BIRDS.ordinal]
        if (gBirds > 0.001f) {
            birdTimer -= dt
            if (birdTimer <= 0f) {
                birdTimer = 0.6f + d.rand() * 3.5f
                startBirdPhrase()
            }
            for (c in chirps) {
                if (!c.active) continue
                if (c.delay > 0) { c.delay--; continue }
                c.t += dt
                val p = c.t / c.dur
                if (p >= 1f) { c.active = false; continue }
                val f = c.f0 + (c.f1 - c.f0) * p
                c.phase += f * dt; if (c.phase > 1f) c.phase -= 1f
                c.trillPhase += c.trill * dt; if (c.trillPhase > 1f) c.trillPhase -= 1f
                val am = if (c.trill > 0f) 0.5f + 0.5f * d.sin1(c.trillPhase) else 1f
                val env = d.sin1(p * 0.5f) // weiches Ein/Aus
                val s = d.sin1(c.phase) * env * am * c.amp * 0.12f * gBirds
                l += s * (1f - c.pan); r += s * c.pan
                rev += s * 0.8f
            }
        }

        // Grillen
        val gCr = gain[NatureSound.CRICKETS.ordinal]
        if (gCr > 0.001f) {
            if (!cricketsInit) {
                cricketsInit = true
                for (c in crickets) {
                    c.freq = 4000f + d.rand() * 1200f; c.rate = 1.6f + d.rand() * 1.6f
                    c.pulses = 2 + d.randInt(3); c.pan = d.rand(); c.amp = 0.4f + d.rand() * 0.6f; c.clock = d.rand()
                }
            }
            for (c in crickets) {
                c.clock += c.rate * dt; if (c.clock > 1f) c.clock -= 1f
                // in den ersten 30 % einer Periode: schnelle Pulse
                val pulseSpan = 0.3f
                var env = 0f
                if (c.clock < pulseSpan) {
                    val q = c.clock / pulseSpan * c.pulses
                    val frac = q - q.toInt()
                    env = if (frac < 0.6f) d.sin1(frac / 0.6f * 0.5f) else 0f
                }
                if (env > 0f) {
                    c.phase += c.freq * dt; if (c.phase > 1f) c.phase -= 1f
                    val s = d.sin1(c.phase) * env * c.amp * 0.035f * gCr
                    l += s * (1f - c.pan); r += s * c.pan
                    rev += s * 0.5f
                }
            }
        }

        // Bach
        val gSt = gain[NatureSound.STREAM.ordinal]
        if (gSt > 0.001f) {
            if (--bubbleCounter <= 0) {
                bubbleCounter = 48
                for (k in bubbles.indices) {
                    if (d.rand() < 0.02f) bubbleFreq[k] = 400f + d.rand() * 2200f
                    bubbles[k].set(d.sr, bubbleFreq[k], 9f)
                }
            }
            val n = d.noise()
            var b = 0f
            for ((k, f) in bubbles.withIndex()) { f.process(n); b += f.band * (if (k % 2 == 0) 1f else 0.7f) }
            val base = streamBase.lp(n, 0.12f)
            val st = (b * 0.05f + base * 0.18f)
            l += st * gSt; r += (st * 0.85f + b * 0.015f) * gSt
        }

        // Kaminfeuer
        val gF = gain[NatureSound.FIRE.ordinal]
        if (gF > 0.001f) {
            val rumble = fireLp.lp(d.noise(), 0.02f) * 0.5f
            if (crackEnv < 0.001f && d.rand() < 0.0025f) { crackEnv = 0.08f + 0.15f * d.rand(); crackFreq = 900f + d.rand() * 2600f }
            var crack = 0f
            if (crackEnv > 0.0005f) {
                crackPhase += crackFreq * dt; if (crackPhase > 1f) crackPhase -= 1f
                crack = (d.sin1(crackPhase) * 0.5f + d.noise() * 0.5f) * crackEnv
                crackEnv *= crackDecay
            }
            if (popEnv < 0.001f && d.rand() < 0.00015f) popEnv = 0.25f + d.rand() * 0.2f
            var pop = 0f
            if (popEnv > 0.0005f) { pop = popLp.lp(d.noise(), 0.3f) * popEnv; popEnv *= popDecay }
            l += (rumble + crack + pop) * gF
            r += (rumble * 0.9f + crack * 0.7f + pop * 0.8f) * gF
        }

        // Fernes Gewitter
        val gT = gain[NatureSound.THUNDER.ordinal]
        if (gT > 0.001f) {
            thunderNext -= dt
            if (thunderNext <= 0f) {
                thunderNext = 18f + d.rand() * 40f
                thunderT = 0f; thunderLen = 3f + d.rand() * 4f; thunderTarget = 0.6f + d.rand() * 0.5f
            }
            thunderT += dt
            val p = thunderT / thunderLen
            thunderEnv = if (p < 1f) {
                val e = if (p < 0.08f) p / 0.08f else (1f - (p - 0.08f) / 0.92f)
                e * e * thunderTarget * (0.7f + 0.3f * d.sin1(thunderT * 3.1f))
            } else 0f
            val n = d.noise()
            val th = thunderLp2.lp(thunderLp1.lp(n, 0.012f), 0.03f) * thunderEnv * 3.2f
            l += th * gT; r += th * 0.95f * gT
        }

        outL = l
        outR = r
        send = rev
    }

    private fun startBirdPhrase() {
        val kind = d.randInt(4)
        val notes = 2 + d.randInt(5)
        val pan = 0.1f + d.rand() * 0.8f
        val base = 2400f + d.rand() * 2600f
        var delay = 0
        val gap = (0.06f + d.rand() * 0.12f) * d.sr
        repeat(notes) { i ->
            val c = chirps.firstOrNull { !it.active } ?: return
            c.active = true; c.delay = delay; c.t = 0f; c.phase = 0f; c.pan = pan
            c.amp = 0.5f + d.rand() * 0.5f
            c.trill = 0f
            when (kind) {
                0 -> { c.f0 = base; c.f1 = base * 1.5f; c.dur = 0.07f + d.rand() * 0.05f }          // aufwaerts
                1 -> { c.f0 = base * 1.4f; c.f1 = base * 0.9f; c.dur = 0.09f + d.rand() * 0.06f }   // abwaerts
                2 -> { c.f0 = base; c.f1 = base * 1.05f; c.dur = 0.25f; c.trill = 28f + d.rand() * 20f } // Triller
                else -> { val up = i % 2 == 0; c.f0 = if (up) base else base * 1.3f; c.f1 = if (up) base * 1.3f else base; c.dur = 0.06f }
            }
            delay += (c.dur * d.sr + gap).toInt()
        }
    }
}
