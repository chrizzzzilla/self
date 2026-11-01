package com.chrizzzzilla.self.focus.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Regler der Fokus-Musik. */
data class LofiParams(
    /** 0 = ruhig (nur Klavier) … 1 = aktiv (mit Beat und Melodie). */
    val energy: Float = 0.5f,
    /** 0 = dunkel/dumpf … 1 = hell/klar. */
    val brightness: Float = 0.4f,
    /** Tempo in BPM. */
    val tempo: Float = 74f,
    val rain: Boolean = true,
    val volume: Float = 0.8f,
) {
    companion object {
        const val TEMPO_MIN = 60f
        const val TEMPO_MAX = 90f
    }
}

/**
 * Generative Lofi-Musik, komplett auf dem Geraet berechnet (keine Samples, kein Internet).
 *
 * Aufbau: Sequenzer (16tel mit Swing) → E-Piano-Akkorde (FM), Bass, Melodie, Boom-Bap-Drums
 * → Leiernder Band-Effekt, Tiefpass, Hall → Plattenknistern + Regen → sanfte Saettigung.
 * Akkordfolgen, Tonart, Melodie und Beat-Variationen werden laufend neu ausgewuerfelt.
 */
internal class LofiSynth(private val sampleRate: Int) {

    @Volatile
    var params = LofiParams()

    /** Fuer die Animation: Lautheit und Kick-Impuls des letzten Blocks (0..1). */
    @Volatile var level = 0f
        private set
    @Volatile var pulse = 0f
        private set

    private val sr = sampleRate.toFloat()
    private val random = java.util.Random()
    private fun rand() = random.nextFloat()

    // ---------- Sinus-Tabelle ----------
    private val tableSize = 4096
    private val sineTable = FloatArray(tableSize + 1) { sin(2.0 * PI * it / tableSize).toFloat() }
    private fun sinC(phaseCycles: Float): Float {
        var p = phaseCycles - phaseCycles.toInt()
        if (p < 0f) p += 1f
        val x = p * tableSize
        val i = x.toInt()
        val f = x - i
        return sineTable[i] + (sineTable[i + 1] - sineTable[i]) * f
    }

    // ---------- Rauschen ----------
    private var noiseSeed = 0x2545F491
    private fun noise(): Float {
        var x = noiseSeed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseSeed = x
        return x * (1f / Int.MAX_VALUE)
    }

    private fun midiToHz(midi: Int): Float = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
    private fun decayCoef(seconds: Float): Float = exp(-1f / (seconds * sr))

    // ---------- Harmonie ----------
    private class ChordType(val intervals: IntArray)

    private val maj9 = ChordType(intArrayOf(4, 7, 11, 14))
    private val maj7 = ChordType(intArrayOf(4, 7, 11, 12))
    private val min9 = ChordType(intArrayOf(3, 7, 10, 14))
    private val min7 = ChordType(intArrayOf(3, 7, 10, 12))
    private val dom9 = ChordType(intArrayOf(4, 10, 14, 19))
    private val min11 = ChordType(intArrayOf(3, 10, 14, 17))

    private class Chord(val degree: Int, val type: ChordType)

    private val progressions: List<List<Chord>> = listOf(
        listOf(Chord(2, min9), Chord(7, dom9), Chord(0, maj9), Chord(9, min7)),
        listOf(Chord(0, maj7), Chord(9, min9), Chord(2, min7), Chord(7, dom9)),
        listOf(Chord(5, maj9), Chord(4, min7), Chord(2, min9), Chord(0, maj9)),
        listOf(Chord(9, min9), Chord(5, maj7), Chord(0, maj9), Chord(7, dom9)),
        listOf(Chord(2, min9), Chord(7, dom9), Chord(4, min7), Chord(9, min11)),
        listOf(Chord(5, maj9), Chord(5, min9), Chord(4, min7), Chord(9, min7)),
        listOf(Chord(0, maj9), Chord(4, min7), Chord(5, maj9), Chord(7, dom9)),
    )
    private val keys = intArrayOf(0, 2, 3, 5, 7, 8, 10) // C, D, Eb, F, G, Ab, Bb
    private val pentatonic = intArrayOf(0, 2, 4, 7, 9)

    private var key = keys[random.nextInt(keys.size)]
    private var progression = progressions[random.nextInt(progressions.size)]
    private var currentChord = progression[0]

    // ---------- Sequenzer ----------
    private var step = 0
    private var bar = 0
    private var samplesToNextStep = 0.0
    private var drumsMutedThisHalf = false

    private fun stepLength(stepIndex: Int): Double {
        val base = sampleRate * 60.0 / params.tempo.coerceIn(LofiParams.TEMPO_MIN, LofiParams.TEMPO_MAX) / 4.0
        val swing = 0.17
        return if (stepIndex % 2 == 0) base * (1 + swing) else base * (1 - swing)
    }

    // ---------- Stimmen ----------
    private class KeyVoice {
        var active = false
        var delay = 0
        var freq = 0f
        var phaseC = 0f
        var phaseM = 0f
        var ratio = 1f
        var env = 0f
        var attackLeft = 0
        var attackInc = 0f
        var peak = 0f
        var decay = 0f
        var index = 0f
        var indexDecay = 0f
        var releasing = false
        var release = 1f
        var releaseCoef = 0f
        var pan = 0.5f
        var isMelody = false
    }

    private val keyVoices = Array(14) { KeyVoice() }

    private var bassFreq = 0f
    private var bassPhase = 0f
    private var bassEnv = 0f
    private var bassAttack = 0
    private val bassDecay = decayCoef(0.9f)

    private var kickEnv = 0f
    private var kickT = 0f
    private var kickPhase = 0f
    private var kickVel = 0f
    private var snareEnv = 0f
    private var snareTone = 0f
    private var snarePhase = 0f
    private var hatEnv = 0f
    private var snareLp1 = 0f
    private var snareLp2 = 0f
    private var hatLp = 0f
    private val kickDecay = decayCoef(0.22f)
    private val snareDecay = decayCoef(0.11f)
    private val snareToneDecay = decayCoef(0.04f)
    private val hatDecay = decayCoef(0.025f)

    // ---------- Band-Leiern ----------
    private var wowPhase = 0f
    private var flutterPhase = 0f

    // ---------- Vinyl + Regen ----------
    private var crackle = 0f
    private var hissLp = 0f
    private var rainLp1 = 0f
    private var rainLp2 = 0f
    private var rainGain = 0f
    private var rainSwell = 0f
    private var dropEnv = 0f
    private var dropPhase = 0f
    private var dropFreq = 3000f
    private val dropDecay = decayCoef(0.012f)

    // ---------- Master ----------
    private var fade = 0f
    @Volatile var fadeTarget = 1f
    val isSilent: Boolean get() = fade < 0.0005f && fadeTarget == 0f

    private var cutoffHz = -1f
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var lz1 = 0f; private var lz2 = 0f; private var rz1 = 0f; private var rz2 = 0f

    private val reverb = SimpleReverb(sampleRate)

    private fun updateFilter(cutoff: Float) {
        if (abs(cutoff - cutoffHz) < 1f) return
        cutoffHz = cutoff
        val q = 0.75f
        val k = tan(PI * cutoff / sr).toFloat()
        val norm = 1f / (1f + k / q + k * k)
        b0 = k * k * norm
        b1 = 2f * b0
        b2 = b0
        a1 = 2f * (k * k - 1f) * norm
        a2 = (1f - k / q + k * k) * norm
    }

    /** Fuellt [out] mit [frames] Stereo-Frames (interleaved L/R). */
    fun render(out: FloatArray, frames: Int) {
        val p = params
        val brightness = p.brightness.coerceIn(0f, 1f)
        updateFilter(900f + brightness * brightness * 6500f)
        val rainTarget = if (p.rain) 1f else 0f
        val fadeStep = 1f / (0.9f * sr)
        var sumSq = 0f
        var pulseMax = 0f

        for (i in 0 until frames) {
            // --- Sequenzer ---
            if (samplesToNextStep <= 0.0) {
                onStep(step, p)
                samplesToNextStep += stepLength(step)
                step = (step + 1) % 16
                if (step == 0) bar++
            }
            samplesToNextStep -= 1.0

            // --- Band-Leiern (alle tonalen Stimmen) ---
            wowPhase += 0.45f / sr
            flutterPhase += 5.7f / sr
            if (wowPhase > 1f) wowPhase -= 1f
            if (flutterPhase > 1f) flutterPhase -= 1f
            val wobble = 1f + 0.0022f * sinC(wowPhase) + 0.0007f * sinC(flutterPhase)

            // Sidechain-"Pumpen" durch die Kick
            val pump = 1f - 0.28f * kickEnv * kickVel

            // --- E-Piano + Melodie ---
            var keysL = 0f
            var keysR = 0f
            for (v in keyVoices) {
                if (!v.active) continue
                if (v.delay > 0) { v.delay--; continue }
                if (v.attackLeft > 0) {
                    v.env += v.attackInc
                    v.attackLeft--
                } else {
                    v.env *= v.decay
                }
                if (v.releasing) {
                    v.release *= v.releaseCoef
                    if (v.release < 0.0005f) { v.active = false; continue }
                }
                if (v.env < 0.0003f && v.attackLeft == 0) { v.active = false; continue }
                v.index *= v.indexDecay
                val f = v.freq * wobble
                v.phaseC += f / sr
                v.phaseM += f * v.ratio / sr
                if (v.phaseC > 1f) v.phaseC -= 1f
                if (v.phaseM > 1f) v.phaseM -= 1f
                val mod = v.index * sinC(v.phaseM) * 0.159f
                val s = sinC(v.phaseC + mod) * v.env * v.release
                keysL += s * (1f - v.pan)
                keysR += s * v.pan
            }
            keysL *= pump
            keysR *= pump

            // --- Bass ---
            var bass = 0f
            if (bassEnv > 0.0002f) {
                if (bassAttack > 0) bassAttack-- else bassEnv *= bassDecay
                bassPhase += bassFreq * wobble / sr
                if (bassPhase > 1f) bassPhase -= 1f
                val b = sinC(bassPhase) + 0.22f * sinC(bassPhase * 2f)
                val attackGain = if (bassAttack > 0) 1f - bassAttack / (0.012f * sr) else 1f
                bass = b * bassEnv * attackGain * 0.34f * pump
            }

            // --- Drums ---
            var drums = 0f
            if (kickEnv > 0.0005f) {
                kickT += 1f / sr
                val kf = 46f + 95f * exp(-kickT / 0.03f)
                kickPhase += kf / sr
                if (kickPhase > 1f) kickPhase -= 1f
                drums += sinC(kickPhase) * kickEnv * kickVel * 0.62f
                kickEnv *= kickDecay
            }
            pulseMax = maxOf(pulseMax, kickEnv * kickVel)
            if (snareEnv > 0.0005f || snareTone > 0.0005f) {
                val n = noise()
                snareLp1 += 0.32f * (n - snareLp1)
                snareLp2 += 0.06f * (n - snareLp2)
                val band = snareLp1 - snareLp2
                snarePhase += 185f / sr
                if (snarePhase > 1f) snarePhase -= 1f
                drums += band * snareEnv * 0.42f + sinC(snarePhase) * snareTone * 0.16f
                snareEnv *= snareDecay
                snareTone *= snareToneDecay
            }
            if (hatEnv > 0.0005f) {
                val n = noise()
                hatLp += 0.55f * (n - hatLp)
                drums += (n - hatLp) * hatEnv * 0.11f
                hatEnv *= hatDecay
            }

            // --- Mix vor Filter ---
            var l = keysL * 0.17f + bass + drums
            var r = keysR * 0.17f + bass + drums

            // Hall nur fuer Klavier/Melodie (Drums bleiben trocken und nah)
            val wet = reverb.process((keysL + keysR) * 0.5f * 0.17f)
            l += wet * 0.55f
            r += reverb.lastRight * 0.55f

            // Tiefpass ("dunkel ↔ hell"), Direct Form II transposed
            val lo = b0 * l + lz1
            lz1 = b1 * l - a1 * lo + lz2
            lz2 = b2 * l - a2 * lo
            val ro = b0 * r + rz1
            rz1 = b1 * r - a1 * ro + rz2
            rz2 = b2 * r - a2 * ro
            l = lo
            r = ro

            // --- Plattenknistern ---
            if (rand() < 0.00032f) crackle = (0.04f + 0.16f * rand() * rand()) * (if (rand() < 0.5f) -1f else 1f)
            val pop = crackle
            crackle *= 0.55f
            hissLp += 0.25f * (noise() - hissLp)
            val vinyl = pop + hissLp * 0.006f
            l += vinyl
            r += vinyl * 0.9f

            // --- Regen ---
            rainGain += (rainTarget - rainGain) * (1f / (1.5f * sr))
            if (rainGain > 0.001f) {
                val n = noise()
                rainLp1 += 0.35f * (n - rainLp1)
                rainLp2 += 0.35f * (rainLp1 - rainLp2)
                rainSwell += 0.00002f * (rand() - rainSwell)
                val rain = rainLp2 * (0.055f + 0.03f * rainSwell) * rainGain
                if (dropEnv < 0.001f && rand() < 0.0009f * rainGain) {
                    dropEnv = 0.03f + 0.05f * rand()
                    dropFreq = 1800f + 3500f * rand()
                }
                var drop = 0f
                if (dropEnv > 0.0005f) {
                    dropPhase += dropFreq / sr
                    if (dropPhase > 1f) dropPhase -= 1f
                    drop = sinC(dropPhase) * dropEnv
                    dropEnv *= dropDecay
                    dropFreq *= 0.9997f
                }
                l += rain + drop
                r += rain * 0.85f + noise() * 0.006f * rainGain + drop * 0.6f
            }

            // --- Master: Lautstaerke, Ein-/Ausblenden, sanfte Saettigung ---
            if (fade < fadeTarget) fade = minOf(fadeTarget, fade + fadeStep)
            else if (fade > fadeTarget) fade = maxOf(fadeTarget, fade - fadeStep)
            val gain = fade * p.volume.coerceIn(0f, 1f)
            l = softClip(l * gain * 1.1f)
            r = softClip(r * gain * 1.1f)

            out[2 * i] = l
            out[2 * i + 1] = r
            sumSq += (l * l + r * r) * 0.5f
        }

        level = sqrt(sumSq / frames).coerceIn(0f, 1f)
        pulse = pulseMax.coerceIn(0f, 1f)
    }

    private fun softClip(x: Float): Float {
        val c = x.coerceIn(-3f, 3f)
        return c * (27f + c * c) / (27f + 9f * c * c)
    }

    // ---------- Ereignisse pro 16tel ----------
    private fun onStep(s: Int, p: LofiParams) {
        val energy = p.energy.coerceIn(0f, 1f)

        if (s == 0) onNewBar(energy)

        // Akkord
        if (s == 0) playChord(velocity = 0.55f + 0.15f * rand(), strum = true)
        if (s == 10 && energy > 0.35f && rand() < 0.25f + energy * 0.35f) {
            playChord(velocity = 0.28f + 0.1f * rand(), strum = false)
        }

        // Bass
        val root = (key + currentChord.degree) % 12
        if (s == 0) playBass(36 + root, 0.9f)
        if (s == 10 && energy > 0.3f && rand() < 0.55f) {
            playBass(36 + root + if (rand() < 0.35f) 7 else 0, 0.6f)
        }
        if (s == 14 && energy > 0.55f && rand() < 0.2f) playBass(36 + root + 12, 0.4f)

        // Drums
        val drumsOn = energy >= 0.3f && !(drumsMutedThisHalf && s >= 8)
        if (drumsOn) {
            if (s == 0) kick(1f)
            if (s == 10 && rand() < 0.75f) kick(0.8f)
            if (s == 3 && rand() < 0.25f * energy) kick(0.55f)
            if (s == 4 || s == 12) snare(0.8f + 0.2f * rand())
            if (s % 2 == 0 && rand() < 0.92f) hat(if (s % 4 == 0) 0.8f else 0.5f + 0.2f * rand())
            if (s % 2 == 1 && rand() < energy * 0.3f) hat(0.3f)
        } else if (energy >= 0.15f) {
            // Nur ganz leise Becken, damit es "atmet"
            if (s % 4 == 2 && rand() < 0.8f) hat(0.35f)
        }

        // Melodie (pentatonisch, lose)
        val melodyChance = 0.03f + 0.2f * energy
        if (s % 2 == 0 && rand() < melodyChance) playMelodyNote(energy)
    }

    private fun onNewBar(energy: Float) {
        val inPhrase = bar % 4
        if (inPhrase == 0 && bar > 0) {
            if (bar % 32 == 0 && rand() < 0.6f) {
                // Tonart wechseln (Quinte rauf oder runter oder ganz neu)
                key = if (rand() < 0.7f) (key + if (rand() < 0.5f) 7 else 5) % 12 else keys[random.nextInt(keys.size)]
            }
            if (bar % 8 == 0 && rand() < 0.55f) {
                progression = progressions[random.nextInt(progressions.size)]
            }
        }
        currentChord = progression[inPhrase]
        // Ab und zu in der zweiten Takthaelfte die Drums weglassen (Luft holen)
        drumsMutedThisHalf = bar % 8 == 7 && rand() < 0.5f && energy < 0.85f
    }

    private fun freeVoice(): KeyVoice =
        keyVoices.firstOrNull { !it.active } ?: keyVoices.minBy { it.env * it.release }

    private fun playChord(velocity: Float, strum: Boolean) {
        // vorherige Akkordtoene ausklingen lassen
        for (v in keyVoices) if (v.active && !v.isMelody) {
            v.releasing = true
            v.releaseCoef = decayCoef(0.35f)
        }
        val root = 48 + (key + currentChord.degree) % 12
        var delay = 0
        for (interval in currentChord.type.intervals) {
            var note = root + interval
            if (note > 74) note -= 12
            startKey(
                freeVoice(), note, velocity * (0.85f + 0.3f * rand()),
                delay = delay, decaySec = 2.2f, ratio = 1f, index = 1.1f, melody = false,
            )
            if (strum) delay += (0.006f * sr + 0.018f * sr * rand()).toInt()
        }
    }

    private var melodyDegree = 5

    private fun playMelodyNote(energy: Float) {
        melodyDegree = (melodyDegree + random.nextInt(5) - 2).coerceIn(0, 9)
        val octave = melodyDegree / 5
        val note = 72 + key % 12 - (if (key % 12 > 5) 12 else 0) + octave * 12 + pentatonic[melodyDegree % 5]
        startKey(
            freeVoice(), note, 0.22f + 0.12f * rand() + 0.08f * energy,
            delay = (0.01f * sr * rand()).toInt(), decaySec = 1.4f, ratio = 2f, index = 0.7f, melody = true,
        )
    }

    private fun startKey(
        v: KeyVoice, midi: Int, velocity: Float, delay: Int,
        decaySec: Float, ratio: Float, index: Float, melody: Boolean,
    ) {
        v.active = true
        v.delay = delay
        v.freq = midiToHz(midi)
        v.phaseC = 0f
        v.phaseM = 0f
        v.ratio = ratio
        v.env = 0f
        v.peak = velocity
        v.attackLeft = (0.005f * sr).toInt().coerceAtLeast(1)
        v.attackInc = velocity / v.attackLeft
        v.decay = decayCoef(decaySec)
        v.index = index * (0.6f + velocity)
        v.indexDecay = decayCoef(0.3f)
        v.releasing = false
        v.release = 1f
        v.pan = 0.3f + 0.4f * rand()
        v.isMelody = melody
    }

    private fun playBass(midi: Int, velocity: Float) {
        bassFreq = midiToHz(midi)
        bassEnv = velocity
        bassAttack = (0.012f * sr).toInt()
    }

    private fun kick(velocity: Float) {
        kickEnv = 1f
        kickT = 0f
        kickPhase = 0f
        kickVel = velocity
    }

    private fun snare(velocity: Float) {
        snareEnv = velocity
        snareTone = velocity
    }

    private fun hat(velocity: Float) {
        hatEnv = velocity
    }
}

/** Kleiner Stereo-Hall (Schroeder/Freeverb-Prinzip). */
private class SimpleReverb(sampleRate: Int) {
    private val scale = sampleRate / 44100f
    private val combL = intArrayOf(1116, 1188, 1277, 1356).map { Comb((it * scale).toInt()) }
    private val combR = intArrayOf(1139, 1211, 1300, 1379).map { Comb((it * scale).toInt()) }
    private val apL = intArrayOf(556, 441).map { AllPass((it * scale).toInt()) }
    private val apR = intArrayOf(579, 464).map { AllPass((it * scale).toInt()) }

    var lastRight = 0f
        private set

    fun process(input: Float): Float {
        var l = 0f
        var r = 0f
        for (c in combL) l += c.process(input)
        for (c in combR) r += c.process(input)
        for (a in apL) l = a.process(l)
        for (a in apR) r = a.process(r)
        lastRight = r * 0.25f
        return l * 0.25f
    }

    private class Comb(size: Int) {
        private val buf = FloatArray(size.coerceAtLeast(1))
        private var idx = 0
        private var store = 0f
        fun process(x: Float): Float {
            val y = buf[idx]
            store = y * 0.7f + store * 0.3f
            buf[idx] = x + store * 0.8f
            idx = (idx + 1) % buf.size
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
            idx = (idx + 1) % buf.size
            return y
        }
    }
}

