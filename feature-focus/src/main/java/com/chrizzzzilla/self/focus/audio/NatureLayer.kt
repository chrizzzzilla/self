package com.chrizzzzilla.self.focus.audio

import java.util.concurrent.Executors

/**
 * Naturgeraeusche aus echten Aufnahmen (CC0, siehe assets/nature/QUELLEN.txt), beliebig kombinierbar.
 * Jede Aufnahme laeuft als nahtlose Schleife (2 s Ueberblendung am Ende). Aufnahmen werden erst
 * geladen, wenn man sie einschaltet, und nach einer Weile Stille wieder freigegeben (Speicher).
 * Fernes Gewitter: einzelne Donner in zufaelligen Abstaenden.
 */
internal class NatureLayer(private val d: Dsp) {

    var outL = 0f; private set
    var outR = 0f; private set
    val send = 0f

    /** Laedt eine Aufnahme (Dateiname ohne Endung) – wird von FocusPlayer mit Context gesetzt. */
    @Volatile var loader: ((String) -> ShortArray?)? = null

    private val count = NatureSound.entries.size
    private val target = FloatArray(count)
    private val gain = FloatArray(count)
    private val silentFor = FloatArray(count)

    private class Loop(val data: ShortArray) {
        val frames = data.size / 2
        var pos = 0
    }

    @Volatile private var loops = arrayOfNulls<Loop>(count)
    @Volatile private var thunders: List<ShortArray>? = null
    private val loading = BooleanArray(count)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "self-nature-loader").apply { isDaemon = true } }

    private fun fileFor(s: NatureSound) = when (s) {
        NatureSound.RAIN -> "rain"
        NatureSound.WIND -> "wind"
        NatureSound.SEA -> "sea"
        NatureSound.BIRDS -> "birds"
        NatureSound.CRICKETS -> "crickets"
        NatureSound.STREAM -> "stream"
        NatureSound.FIRE -> "fire"
        NatureSound.THUNDER -> null
    }

    fun setActive(active: Set<NatureSound>) {
        for (s in NatureSound.entries) {
            val on = s in active
            target[s.ordinal] = if (on) 1f else 0f
            if (on) ensureLoaded(s)
        }
    }

    private fun ensureLoaded(s: NatureSound) {
        val i = s.ordinal
        val load = loader ?: return
        if (s == NatureSound.THUNDER) {
            if (thunders != null || loading[i]) return
            loading[i] = true
            worker.execute {
                thunders = listOfNotNull(load("thunder1"), load("thunder2"), load("thunder3"))
                loading[i] = false
            }
            return
        }
        if (loops[i] != null || loading[i]) return
        val name = fileFor(s) ?: return
        loading[i] = true
        worker.execute {
            val data = load(name)
            if (data != null && data.size > 4 * XFADE) {
                val copy = loops.copyOf()
                copy[i] = Loop(data)
                loops = copy
            }
            loading[i] = false
        }
    }

    // Donner
    private var thunderData: ShortArray? = null
    private var thunderPos = 0
    private var thunderNext = 6f
    private var thunderPan = 0.5f
    private var thunderLevel = 1f

    fun next() {
        val dt = 1f / d.sr
        var l = 0f
        var r = 0f
        val ls = loops
        for (i in 0 until count) {
            val tgt = target[i]
            var g = gain[i]
            val loop = ls[i]
            // erst einblenden, wenn die Aufnahme geladen ist
            val goal = if (loop == null && i != NatureSound.THUNDER.ordinal) 0f else tgt
            if (g != goal) {
                g = if (g < goal) (g + dt / 2.5f).coerceAtMost(goal) else (g - dt / 2f).coerceAtLeast(goal)
                gain[i] = g
            }
            if (loop == null || g <= 0.0001f) continue
            val p = loop.pos
            val start = loop.frames - XFADE
            var sl = loop.data[2 * p] * SCALE
            var sr = loop.data[2 * p + 1] * SCALE
            if (p >= start) {
                // Ueberblendung Ende → Anfang
                val a = (p - start).toFloat() / XFADE
                val q = p - start
                sl = sl * (1f - a) + loop.data[2 * q] * SCALE * a
                sr = sr * (1f - a) + loop.data[2 * q + 1] * SCALE * a
            }
            loop.pos = if (p + 1 >= loop.frames) XFADE else p + 1
            l += sl * g * LEVEL
            r += sr * g * LEVEL
        }

        // Fernes Gewitter
        val gT = gain[NatureSound.THUNDER.ordinal]
        val bank = thunders
        if (gT > 0.001f && bank != null && bank.isNotEmpty()) {
            val td = thunderData
            if (td == null) {
                thunderNext -= dt
                if (thunderNext <= 0f) {
                    thunderData = bank[d.randInt(bank.size)]
                    thunderPos = 0
                    thunderPan = 0.25f + d.rand() * 0.5f
                    thunderLevel = 0.55f + d.rand() * 0.45f
                    thunderNext = 18f + d.rand() * 40f
                }
            } else {
                val v = (td[2 * thunderPos] + td[2 * thunderPos + 1]) * 0.5f * SCALE * thunderLevel * gT * LEVEL
                l += v * (1.2f - thunderPan)
                r += v * (0.2f + thunderPan)
                thunderPos++
                if (thunderPos * 2 >= td.size) thunderData = null
            }
        }

        outL = l
        outR = r
    }

    /** Ab und zu aufrufen (nicht im Audio-Takt): lange stille Aufnahmen wieder freigeben. */
    fun housekeeping(seconds: Float) {
        var changed = false
        val copy = loops.copyOf()
        for (i in 0 until count) {
            if (target[i] == 0f && gain[i] == 0f && copy[i] != null) {
                silentFor[i] += seconds
                if (silentFor[i] > 30f) { copy[i] = null; changed = true; silentFor[i] = 0f }
            } else silentFor[i] = 0f
        }
        if (changed) loops = copy
        if (target[NatureSound.THUNDER.ordinal] == 0f && gain[NatureSound.THUNDER.ordinal] == 0f) thunders = null
    }

    private companion object {
        const val XFADE = 44_100 * 2 // 2 s
        const val SCALE = 1f / 32768f
        /** Grundpegel der (auf −26 LUFS normalisierten) Aufnahmen im Mix */
        const val LEVEL = 2.2f
    }
}
