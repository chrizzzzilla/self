package com.chrizzzzilla.self.focus.audio

/**
 * Die 10 Percussion-Muster. 16tel-Raster; je Instrument eine Zeile mit Staerken (0–9, '.' = Pause)
 * plus etwas Zufall, damit es lebendig bleibt. Tempo und Hell/Dunkel sind regelbar.
 */
internal class PercussionLayer(private val d: Dsp) {

    var outL = 0f; private set
    var outR = 0f; private set
    var send = 0f; private set
    /** Kick-Impuls fuer die Animation (0..1). */
    var pulse = 0f; private set

    var bpm = 80f
    var tone = 0.45f

    private var pattern: Percussion? = null
    private var gainNow = 0f

    fun setPattern(p: Percussion?) { pattern = p }

    private class Pat(
        val swing: Float,
        val kick: String = "", val snare: String = "", val hat: String = "", val open: String = "",
        val rim: String = "", val shaker: String = "", val congaLo: String = "", val congaHi: String = "",
        val tick: String = "", val tock: String = "", val brush: String = "", val tom: String = "",
        val random: Float = 0f, val halfTime: Boolean = false,
    )

    private val patterns = mapOf(
        Percussion.BOOM_BAP to Pat(
            swing = 0.17f,
            kick = "9.........7..3..", snare = "....8.......8...", hat = "6.4.6.4.6.4.6.45",
        ),
        Percussion.LAID_BACK to Pat(
            swing = 0.2f,
            kick = "8.........6.....", rim = "....5.......5...", shaker = "3232323232323232",
        ),
        Percussion.SHAKER to Pat(
            swing = 0.12f,
            shaker = "6243624362436243", tick = "........3.......",
        ),
        Percussion.BRUSHES to Pat(
            swing = 0.33f,
            kick = "5...............", brush = "....7.......7...", hat = "5..35..35..35..3",
        ),
        Percussion.HEARTBEAT to Pat(
            swing = 0f, kick = "9.6.............", halfTime = true,
        ),
        Percussion.HAND_DRUMS to Pat(
            swing = 0.15f,
            congaLo = "8.....6...7.....", congaHi = "...56......6..5.", shaker = "2.2.2.2.2.2.2.2.",
        ),
        Percussion.CLOCKWORK to Pat(
            swing = 0f, tick = "6...........6...", tock = "....5.......5...", hat = "..2...2...2...2.",
        ),
        Percussion.TRIP_HOP to Pat(
            swing = 0.1f, halfTime = true,
            kick = "9......6..8.....", snare = "........9.......", hat = "4...4...4...4.4.", open = "..............5.",
        ),
        Percussion.MINIMAL to Pat(
            swing = 0.05f, kick = "7...7...7...7...", hat = "..5...5...5...5.",
        ),
        Percussion.RAIN_DRUM to Pat(
            swing = 0f, tom = "", rim = "", random = 1f,
        ),
    )

    private var step = 0
    private var samplesToStep = 0.0

    // Stimmen
    private var kickEnv = 0f; private var kickT = 0f; private var kickPh = 0f; private var kickVel = 0f
    private var snEnv = 0f; private var snTone = 0f; private var snPh = 0f
    private var hatEnv = 0f; private var hatDecay = 0f
    private var rimEnv = 0f; private var rimPh = 0f
    private var shEnv = 0f
    private var cLoEnv = 0f; private var cLoPh = 0f; private var cLoT = 0f
    private var cHiEnv = 0f; private var cHiPh = 0f; private var cHiT = 0f
    private var tickEnv = 0f; private var tickPh = 0f; private var tickF = 2400f
    private var brushEnv = 0f; private var brushT = 0f
    private var tomEnv = 0f; private var tomPh = 0f; private var tomF = 140f; private var tomT = 0f
    private var tomPan = 0.5f

    private val kickDecay = d.decay(0.24f)
    private val snDecay = d.decay(0.12f)
    private val snToneDecay = d.decay(0.045f)
    private val hatClosed = d.decay(0.028f)
    private val hatOpen = d.decay(0.22f)
    private val rimDecay = d.decay(0.02f)
    private val shDecay = d.decay(0.045f)
    private val congaDecay = d.decay(0.18f)
    private val tickDecay = d.decay(0.03f)
    private val brushDecay = d.decay(0.25f)
    private val tomDecay = d.decay(0.3f)

    private val snBp1 = OnePole(); private val snBp2 = OnePole()
    private val hatLp = OnePole(); private val shLp = OnePole(); private val shHp = OnePole()
    private val brushLp = OnePole(); private val brushHp = OnePole()
    private val toneL = Svf(); private val toneR = Svf()
    private var toneSet = -1f

    fun next() {
        val p = pattern
        val target = if (p != null) 1f else 0f
        gainNow += (target - gainNow) * (1f / (0.8f * d.sr))

        if (p != null) {
            if (samplesToStep <= 0.0) {
                val pat = patterns.getValue(p)
                val bpmEff = if (pat.halfTime) bpm * 0.5f else bpm
                val base = d.sr * 60.0 / bpmEff / 4.0
                samplesToStep += if (step % 2 == 0) base * (1 + pat.swing) else base * (1 - pat.swing)
                trigger(pat, step)
                step = (step + 1) % 16
            }
            samplesToStep -= 1.0
        }

        if (toneSet != tone) {
            toneSet = tone
            val cut = 1200f + tone * tone * 11000f
            toneL.set(d.sr, cut, 0.7f); toneR.set(d.sr, cut, 0.7f)
        }

        val dt = 1f / d.sr
        var l = 0f
        var r = 0f

        if (kickEnv > 0.0005f) {
            kickT += dt
            val f = 45f + 90f * fastExp(-kickT / 0.03f)
            kickPh += f * dt; if (kickPh > 1f) kickPh -= 1f
            val k = d.sin1(kickPh) * kickEnv * kickVel * 0.65f
            l += k; r += k
            kickEnv *= kickDecay
        }
        pulse = kickEnv * kickVel

        if (snEnv > 0.0005f || snTone > 0.0005f) {
            val n = d.noise()
            val band = snBp1.lp(n, 0.32f) - snBp2.lp(n, 0.06f)
            snPh += 185f * dt; if (snPh > 1f) snPh -= 1f
            val s = band * snEnv * 0.45f + d.sin1(snPh) * snTone * 0.16f
            l += s; r += s
            snEnv *= snDecay; snTone *= snToneDecay
        }
        if (hatEnv > 0.0005f) {
            val n = d.noise()
            val h = (n - hatLp.lp(n, 0.55f)) * hatEnv * 0.12f
            l += h * 0.8f; r += h
            hatEnv *= hatDecay
        }
        if (rimEnv > 0.0005f) {
            rimPh += 1700f * dt; if (rimPh > 1f) rimPh -= 1f
            val s = (d.sin1(rimPh) * 0.6f + d.noise() * 0.4f) * rimEnv * 0.2f
            l += s * 0.9f; r += s
            rimEnv *= rimDecay
        }
        if (shEnv > 0.0005f) {
            val n = d.noise()
            val hp = n - shHp.lp(n, 0.4f)
            val s = shLp.lp(hp, 0.6f) * shEnv * 0.16f
            l += s; r += s * 0.8f
            shEnv *= shDecay
        }
        if (cLoEnv > 0.0005f) {
            cLoT += dt
            cLoPh += (180f + 60f * fastExp(-cLoT / 0.02f)) * dt; if (cLoPh > 1f) cLoPh -= 1f
            val s = d.sin1(cLoPh) * cLoEnv * 0.4f
            l += s * 1.1f; r += s * 0.8f
            cLoEnv *= congaDecay
        }
        if (cHiEnv > 0.0005f) {
            cHiT += dt
            cHiPh += (300f + 90f * fastExp(-cHiT / 0.015f)) * dt; if (cHiPh > 1f) cHiPh -= 1f
            val s = d.sin1(cHiPh) * cHiEnv * 0.32f
            l += s * 0.8f; r += s * 1.1f
            cHiEnv *= congaDecay
        }
        if (tickEnv > 0.0005f) {
            tickPh += tickF * dt; if (tickPh > 1f) tickPh -= 1f
            val s = d.sin1(tickPh) * tickEnv * 0.18f
            l += s; r += s
            tickEnv *= tickDecay
        }
        if (brushEnv > 0.0005f) {
            brushT += dt
            val n = d.noise()
            val s = brushLp.lp(n - brushHp.lp(n, 0.1f), 0.35f) * brushEnv * (if (brushT < 0.04f) brushT / 0.04f else 1f) * 0.22f
            l += s; r += s * 0.9f
            brushEnv *= brushDecay
        }
        if (tomEnv > 0.0005f) {
            tomT += dt
            tomPh += tomF * (1f + 0.4f * fastExp(-tomT / 0.04f)) * dt; if (tomPh > 1f) tomPh -= 1f
            val s = d.sin1(tomPh) * tomEnv * 0.3f
            l += s * (1f - tomPan); r += s * tomPan
            tomEnv *= tomDecay
        }

        toneL.process(l); toneR.process(r)
        outL = toneL.low * gainNow
        outR = toneR.low * gainNow
        send = (outL + outR) * 0.15f
    }

    private fun lvl(row: String, s: Int): Float {
        if (row.isEmpty()) return 0f
        val c = row[s % row.length]
        return if (c in '1'..'9') (c - '0') / 9f else 0f
    }

    private fun humanize(v: Float) = (v * (0.85f + 0.3f * d.rand())).coerceIn(0f, 1.1f)

    private fun trigger(p: Pat, s: Int) {
        if (p.random > 0f) {
            // Regentrommel: zufaellige, weiche Schlaege
            if (d.rand() < 0.35f) { tomEnv = 0.3f + 0.5f * d.rand(); tomF = 90f + d.rand() * 160f; tomT = 0f; tomPan = d.rand() }
            if (d.rand() < 0.25f) rimEnv = 0.2f + 0.4f * d.rand()
            if (d.rand() < 0.15f) { cHiEnv = 0.3f * d.rand(); cHiT = 0f }
            return
        }
        lvl(p.kick, s).takeIf { it > 0f }?.let { kickEnv = 1f; kickT = 0f; kickPh = 0f; kickVel = humanize(it) }
        lvl(p.snare, s).takeIf { it > 0f }?.let { snEnv = humanize(it); snTone = snEnv }
        lvl(p.hat, s).takeIf { it > 0f && d.rand() < 0.95f }?.let { hatEnv = humanize(it); hatDecay = hatClosed }
        lvl(p.open, s).takeIf { it > 0f }?.let { hatEnv = humanize(it); hatDecay = hatOpen }
        lvl(p.rim, s).takeIf { it > 0f }?.let { rimEnv = humanize(it) }
        lvl(p.shaker, s).takeIf { it > 0f }?.let { shEnv = humanize(it) }
        lvl(p.congaLo, s).takeIf { it > 0f }?.let { cLoEnv = humanize(it); cLoT = 0f }
        lvl(p.congaHi, s).takeIf { it > 0f }?.let { cHiEnv = humanize(it); cHiT = 0f }
        lvl(p.tick, s).takeIf { it > 0f }?.let { tickEnv = humanize(it); tickF = 2600f }
        lvl(p.tock, s).takeIf { it > 0f }?.let { tickEnv = humanize(it); tickF = 1700f }
        lvl(p.brush, s).takeIf { it > 0f }?.let { brushEnv = humanize(it); brushT = 0f }
    }

    /** Schnelles e^x fuer kleine negative x (reicht fuer Huellkurven). */
    private fun fastExp(x: Float): Float {
        if (x < -10f) return 0f
        var y = 1f + x / 256f
        repeat(8) { y *= y }
        return y
    }
}
