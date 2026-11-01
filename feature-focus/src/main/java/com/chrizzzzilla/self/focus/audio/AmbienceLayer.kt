package com.chrizzzzilla.self.focus.audio

/**
 * Die 10 Klangflaechen. Alles wird live erzeugt und wiederholt sich nie:
 * Akkordfolgen, Tonart, Melodien und Abstaende werden laufend neu ausgewuerfelt.
 */
internal class AmbienceLayer(private val d: Dsp) {

    // ---------------- Ausgabe pro Sample ----------------
    var outL = 0f; private set
    var outR = 0f; private set
    /** Anteil fuer den Hall. */
    var send = 0f; private set

    var tone = 0.45f

    private var type: Ambience? = null
    private var pendingType: Ambience? = null
    private var switchFade = 1f

    fun setType(newType: Ambience?) {
        pendingType = newType
    }

    // ---------------- Harmonie ----------------
    private val keys = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private var key = keys[d.randInt(keys.size)]

    private class Chord(val degree: Int, val notes: IntArray)

    private val maj9 = intArrayOf(4, 7, 11, 14)
    private val maj7 = intArrayOf(4, 7, 11, 12)
    private val add9 = intArrayOf(4, 7, 14, 19)
    private val six9 = intArrayOf(4, 9, 14, 19)
    private val min9 = intArrayOf(3, 7, 10, 14)
    private val min7 = intArrayOf(3, 7, 10, 12)
    private val min11 = intArrayOf(3, 10, 14, 17)
    private val dom9 = intArrayOf(4, 10, 14, 19)

    private val lofiProgressions = listOf(
        listOf(Chord(2, min9), Chord(7, dom9), Chord(0, maj9), Chord(9, min7)),
        listOf(Chord(0, maj7), Chord(9, min9), Chord(2, min7), Chord(7, dom9)),
        listOf(Chord(5, maj9), Chord(4, min7), Chord(2, min9), Chord(0, maj9)),
        listOf(Chord(9, min9), Chord(5, maj7), Chord(0, maj9), Chord(7, dom9)),
        listOf(Chord(2, min9), Chord(7, dom9), Chord(4, min7), Chord(9, min11)),
    )
    private val brightProgressions = listOf(
        listOf(Chord(0, maj9), Chord(5, add9), Chord(9, min9), Chord(7, six9)),
        listOf(Chord(0, six9), Chord(7, add9), Chord(5, maj9), Chord(0, add9)),
        listOf(Chord(5, maj9), Chord(0, add9), Chord(2, min9), Chord(7, six9)),
    )
    private val darkProgressions = listOf(
        listOf(Chord(9, min9), Chord(5, maj9), Chord(2, min11), Chord(4, min7)),
        listOf(Chord(9, min11), Chord(7, add9), Chord(5, maj7), Chord(4, min9)),
        listOf(Chord(2, min9), Chord(9, min11), Chord(5, maj9), Chord(9, min9)),
    )
    private var progression = lofiProgressions[0]
    private var chordIndex = 0
    private val chord get() = progression[chordIndex % progression.size]
    private val pentatonic = intArrayOf(0, 2, 4, 7, 9)
    private val minorPenta = intArrayOf(0, 3, 5, 7, 10)

    // ---------------- Stimmen ----------------
    private class KeyVoice {
        var active = false; var delay = 0
        var freq = 0f; var phaseC = 0f; var phaseM = 0f; var ratio = 1f
        var env = 0f; var attackLeft = 0; var attackInc = 0f; var decay = 0f
        var index = 0f; var indexDecay = 0f
        var releasing = false; var release = 1f; var releaseCoef = 0f
        var pan = 0.5f; var melody = false
    }

    private class PadVoice {
        var active = false
        var freq = 0f
        val phase = FloatArray(2)
        var detune = 1.003f
        var env = 0f; var target = 0f; var coefUp = 0f; var coefDown = 0f
        var pan = 0.5f
        var pure = false // reine Sinus-Stimme (Kristall, Schwebetoene)
        var beat = 0f    // Schwebung in Hz (Schwebetoene)
        var trem = 0f; var tremRate = 0.2f
    }

    private val keyVoices = Array(16) { KeyVoice() }
    private val padVoices = Array(14) { PadVoice() }

    private var bassFreq = 0f; private var bassPhase = 0f; private var bassEnv = 0f
    private val bassDecay = d.decay(1.1f)
    private var dronePhase = 0f; private var droneFreq = 55f; private var droneEnv = 0f; private var droneTarget = 0f
    private val humLp = OnePole(); private var humSwell = 0f
    private var crackle = 0f
    private val hissLp = OnePole()

    // ---------------- Zeit ----------------
    private var samplesToEvent = 0.0
    private var samplesToChord = 0.0
    private var lfoPhase = 0f
    private var lofiStep = 0
    private var bar = 0
    private var melodyDegree = 5

    private fun resetFor(t: Ambience?) {
        for (v in keyVoices) if (v.active) { v.releasing = true; v.releaseCoef = d.decay(0.6f) }
        for (v in padVoices) v.target = 0f
        droneTarget = 0f
        samplesToEvent = 0.0
        samplesToChord = 0.0
        chordIndex = 0
        lofiStep = 0
        bar = 0
        progression = when (t) {
            Ambience.LOFI_KEYS -> lofiProgressions[d.randInt(lofiProgressions.size)]
            Ambience.WARM_PADS, Ambience.MORNING, Ambience.BELLS -> brightProgressions[d.randInt(brightProgressions.size)]
            else -> darkProgressions[d.randInt(darkProgressions.size)]
        }
    }

    fun next() {
        // weiches Umschalten: alte Flaeche ausblenden, dann neue einblenden
        if (pendingType != type) {
            switchFade -= 1f / (1.2f * d.sr)
            if (switchFade <= 0f) {
                switchFade = 0f
                type = pendingType
                resetFor(type)
            }
        } else if (switchFade < 1f) {
            switchFade = (switchFade + 1f / (1.5f * d.sr)).coerceAtMost(1f)
        }

        val t = type
        if (t != null) schedule(t)

        lfoPhase += 1f / (23f * d.sr)
        if (lfoPhase > 1f) lfoPhase -= 1f
        val wobble = 1f + 0.0018f * d.sin1(lfoPhase * 10f) + 0.0005f * d.sin1(lfoPhase * 130f)

        var l = 0f
        var r = 0f

        // E-Piano / Glocken (FM)
        for (v in keyVoices) {
            if (!v.active) continue
            if (v.delay > 0) { v.delay--; continue }
            if (v.attackLeft > 0) { v.env += v.attackInc; v.attackLeft-- } else v.env *= v.decay
            if (v.releasing) {
                v.release *= v.releaseCoef
                if (v.release < 0.0005f) { v.active = false; continue }
            }
            if (v.env < 0.0003f && v.attackLeft == 0) { v.active = false; continue }
            v.index *= v.indexDecay
            val f = v.freq * wobble
            v.phaseC += f / d.sr; if (v.phaseC > 1f) v.phaseC -= 1f
            v.phaseM += f * v.ratio / d.sr; if (v.phaseM > 1f) v.phaseM -= 1f
            val s = d.sin1(v.phaseC + v.index * d.sin1(v.phaseM) * 0.159f) * v.env * v.release * 0.16f
            l += s * (1f - v.pan); r += s * v.pan
        }

        // Flaechen (additiv; "Klang" bestimmt die Obertoene)
        val bright = 0.3f + tone.coerceIn(0f, 1f) * 0.5f
        val b2 = bright; val b3 = b2 * bright; val b4 = b3 * bright; val b5 = b4 * bright
        for (v in padVoices) {
            if (!v.active) continue
            v.env += (v.target - v.env) * (if (v.target > v.env) v.coefUp else v.coefDown)
            if (v.target == 0f && v.env < 0.0002f) { v.active = false; continue }
            var s: Float
            if (v.pure) {
                v.phase[0] += v.freq * wobble / d.sr; if (v.phase[0] > 1f) v.phase[0] -= 1f
                v.phase[1] += (v.freq + v.beat) * wobble / d.sr; if (v.phase[1] > 1f) v.phase[1] -= 1f
                v.trem += v.tremRate / d.sr; if (v.trem > 1f) v.trem -= 1f
                s = (d.sin1(v.phase[0]) + d.sin1(v.phase[1])) * 0.5f * (0.8f + 0.2f * d.sin1(v.trem))
            } else {
                s = 0f
                for (k in 0 until 2) {
                    val f = v.freq * (if (k == 0) 1f else v.detune) * wobble
                    v.phase[k] += f / d.sr; if (v.phase[k] > 1f) v.phase[k] -= 1f
                    val p = v.phase[k]
                    s += d.sin1(p) + d.sin1(p * 2f) * 0.5f * b2 + d.sin1(p * 3f) * 0.33f * b3 +
                        d.sin1(p * 4f) * 0.25f * b4 + d.sin1(p * 5f) * 0.2f * b5
                }
                s *= 0.32f
            }
            s *= v.env * 0.11f
            l += s * (1f - v.pan); r += s * v.pan
        }

        // Bass (Lofi)
        if (bassEnv > 0.0003f) {
            bassEnv *= bassDecay
            bassPhase += bassFreq * wobble / d.sr; if (bassPhase > 1f) bassPhase -= 1f
            val b = (d.sin1(bassPhase) + 0.2f * d.sin1(bassPhase * 2f)) * bassEnv * 0.3f
            l += b; r += b
        }

        // Tiefer Grundton (Drone)
        droneEnv += (droneTarget - droneEnv) * (1f / (4f * d.sr))
        if (droneEnv > 0.0005f) {
            dronePhase += droneFreq / d.sr; if (dronePhase > 1f) dronePhase -= 1f
            val dr = (d.sin1(dronePhase) + 0.3f * d.sin1(dronePhase * 1.5f)) * droneEnv * 0.18f
            l += dr; r += dr
        }

        // Stadt-Brummen (Nachtstadt)
        if (t == Ambience.NIGHT_CITY) {
            humSwell += 0.00001f * (d.rand() - humSwell)
            val hum = humLp.lp(d.noise(), 0.006f) * (0.5f + humSwell) * 0.5f
            l += hum; r += hum * 0.9f
        }

        // Plattenknistern (Lofi)
        if (t == Ambience.LOFI_KEYS) {
            if (d.rand() < 0.0003f) crackle = (0.03f + 0.12f * d.rand() * d.rand()) * (if (d.rand() < 0.5f) -1f else 1f)
            val v = crackle + hissLp.lp(d.noise(), 0.25f) * 0.005f
            crackle *= 0.55f
            l += v; r += v * 0.9f
        }

        outL = l * switchFade
        outR = r * switchFade
        send = (l + r) * 0.5f * switchFade * when (t) {
            Ambience.BELLS, Ambience.PIANO -> 0.9f
            Ambience.LOFI_KEYS -> 0.45f
            else -> 0.6f
        }
    }

    // ---------------- Ablauf je Ambiente ----------------
    private fun secs(s: Float) = (s * d.sr).toDouble()

    private fun schedule(t: Ambience) {
        samplesToEvent -= 1.0
        samplesToChord -= 1.0
        when (t) {
            Ambience.LOFI_KEYS -> {
                if (samplesToEvent <= 0.0) {
                    // 16tel bei 72 BPM mit Swing
                    val base = d.sr * 60.0 / 72.0 / 4.0
                    samplesToEvent += if (lofiStep % 2 == 0) base * 1.17 else base * 0.83
                    lofiStep(lofiStep)
                    lofiStep = (lofiStep + 1) % 16
                    if (lofiStep == 0) bar++
                }
            }
            Ambience.WARM_PADS -> padChords(change = 11f, octave = 48, darkness = 0f, sub = true)
            Ambience.NIGHT_CITY -> {
                padChords(change = 14f, octave = 48, darkness = 0.4f, sub = false)
                droneTarget = 0.6f; droneFreq = d.midiHz((36 + (key + chord.degree) % 12).toFloat())
            }
            Ambience.DEEP_SPACE -> {
                if (samplesToChord <= 0.0) {
                    samplesToChord = secs(35f + d.rand() * 25f)
                    if (d.rand() < 0.5f) key = (key + 5) % 12
                    releasePads()
                    val root = 36 + key
                    padOn(root.toFloat(), 0.9f, attack = 8f, release = 10f)
                    padOn((root + 7).toFloat(), 0.6f, attack = 10f, release = 10f)
                    padOn((root + 12).toFloat(), 0.5f, attack = 12f, release = 10f)
                    padOn((root + 19).toFloat(), 0.25f, attack = 14f, release = 10f)
                    droneTarget = 0.7f; droneFreq = d.midiHz((24 + key).toFloat())
                }
            }
            Ambience.BELLS -> {
                padChords(change = 16f, octave = 48, darkness = 0f, sub = false, level = 0.35f)
                if (samplesToEvent <= 0.0) {
                    samplesToEvent = secs(0.7f + d.rand() * 2.6f)
                    val count = if (d.rand() < 0.3f) 2 else 1
                    repeat(count) {
                        val note = 79 + key % 12 - 12 + pentatonic[d.randInt(5)] + 12 * d.randInt(2)
                        keyOn(note.toFloat(), 0.35f + 0.25f * d.rand(), delay = it * (0.12f * d.sr).toInt(), decaySec = 4.5f, ratio = 3.5f, index = 1.6f, melody = true)
                    }
                }
            }
            Ambience.MORNING -> {
                padChords(change = 10f, octave = 52, darkness = 0f, sub = true, level = 0.7f)
                if (samplesToEvent <= 0.0) {
                    samplesToEvent = secs(0.42f)
                    if (d.rand() < 0.8f) {
                        val notes = chord.notes
                        val n = 60 + (key + chord.degree) % 12 + notes[d.randInt(notes.size)]
                        keyOn(n.toFloat(), 0.22f + 0.1f * d.rand(), 0, decaySec = 1.6f, ratio = 2f, index = 0.6f, melody = true)
                    }
                }
            }
            Ambience.DUSK -> {
                padChords(change = 13f, octave = 48, darkness = 0.3f, sub = true)
                if (samplesToEvent <= 0.0) {
                    samplesToEvent = secs(1.8f + d.rand() * 3.5f)
                    melodyDegree = (melodyDegree + d.randInt(5) - 2).coerceIn(0, 9)
                    val n = 67 + key % 12 - 12 + minorPenta[melodyDegree % 5] + 12 * (melodyDegree / 5)
                    keyOn(n.toFloat(), 0.3f, 0, decaySec = 3f, ratio = 1f, index = 0.8f, melody = true)
                }
            }
            Ambience.PIANO -> {
                if (samplesToEvent <= 0.0) {
                    samplesToEvent = secs(1.4f + d.rand() * 3.2f)
                    if (samplesToChord <= 0.0) { samplesToChord = secs(12f); chordIndex++ }
                    val root = 48 + (key + chord.degree) % 12
                    val notes = chord.notes
                    val count = 1 + d.randInt(3)
                    repeat(count) {
                        val n = root + notes[d.randInt(notes.size)] + if (d.rand() < 0.4f) 12 else 0
                        keyOn(n.toFloat(), 0.3f + 0.25f * d.rand(), delay = it * (0.09f * d.sr * (1 + d.rand())).toInt(), decaySec = 3.2f, ratio = 1f, index = 0.55f, melody = true)
                    }
                    if (d.rand() < 0.35f) keyOn((36 + (key + chord.degree) % 12).toFloat(), 0.35f, 0, 3.5f, 1f, 0.4f, true)
                }
            }
        }
    }

    private fun lofiStep(s: Int) {
        if (s == 0) {
            val phrase = bar % 4
            if (phrase == 0 && bar > 0 && bar % 8 == 0 && d.rand() < 0.55f) {
                progression = lofiProgressions[d.randInt(lofiProgressions.size)]
            }
            if (bar % 32 == 0 && bar > 0 && d.rand() < 0.5f) key = (key + 7) % 12
            chordIndex = phrase
            playKeysChord(0.55f + 0.15f * d.rand(), strum = true)
            bassFreq = d.midiHz((36 + (key + chord.degree) % 12).toFloat()); bassEnv = 0.85f
        }
        if (s == 10 && d.rand() < 0.4f) playKeysChord(0.3f, strum = false)
        if (s == 10 && d.rand() < 0.5f) { bassFreq = d.midiHz((36 + (key + chord.degree) % 12).toFloat()); bassEnv = 0.55f }
        if (s % 2 == 0 && d.rand() < 0.1f) {
            melodyDegree = (melodyDegree + d.randInt(5) - 2).coerceIn(0, 9)
            val n = 72 + key % 12 - (if (key % 12 > 5) 12 else 0) + 12 * (melodyDegree / 5) + pentatonic[melodyDegree % 5]
            keyOn(n.toFloat(), 0.25f + 0.1f * d.rand(), (0.01f * d.sr * d.rand()).toInt(), 1.4f, 2f, 0.7f, true)
        }
    }

    private fun playKeysChord(velocity: Float, strum: Boolean) {
        for (v in keyVoices) if (v.active && !v.melody) { v.releasing = true; v.releaseCoef = d.decay(0.35f) }
        val root = 48 + (key + chord.degree) % 12
        var delay = 0
        for (iv in chord.notes) {
            var n = root + iv
            if (n > 74) n -= 12
            keyOn(n.toFloat(), velocity * (0.85f + 0.3f * d.rand()), delay, 2.2f, 1f, 1.1f, melody = false)
            if (strum) delay += (0.006f * d.sr + 0.018f * d.sr * d.rand()).toInt()
        }
    }

    /** Flaechen-Akkorde, die alle [change] Sekunden weich wechseln. */
    private fun padChords(change: Float, octave: Int, darkness: Float, sub: Boolean, level: Float = 1f) {
        if (samplesToChord > 0.0) return
        samplesToChord = secs(change * (0.85f + 0.3f * d.rand()))
        chordIndex++
        if (chordIndex % progression.size == 0 && d.rand() < 0.4f) {
            val pool = if (darkness > 0.2f) darkProgressions else brightProgressions
            progression = pool[d.randInt(pool.size)]
        }
        releasePads()
        val root = octave + (key + chord.degree) % 12
        for (iv in chord.notes) {
            var n = root + iv
            if (n > octave + 22) n -= 12
            padOn(n.toFloat(), level * (0.7f + 0.3f * d.rand()) * (1f - darkness * 0.3f), attack = change * 0.35f, release = change * 0.45f)
        }
        if (sub) padOn((root - 12).toFloat(), level * 0.55f, attack = change * 0.3f, release = change * 0.5f)
    }

    private fun releasePads() { for (v in padVoices) v.target = 0f }

    private fun releaseOldestPure() {
        val alive = padVoices.filter { it.active && it.target > 0f }
        if (alive.size >= 5) alive.minBy { it.env }.target = 0f
    }

    private fun freePad(): PadVoice = padVoices.firstOrNull { !it.active } ?: padVoices.minBy { it.env }

    private fun padOn(midi: Float, level: Float, attack: Float, release: Float) {
        val v = freePad()
        v.active = true
        v.pure = false
        v.freq = d.midiHz(midi)
        v.detune = 1.002f + d.rand() * 0.004f
        v.env = 0f
        v.target = level
        v.coefUp = 1f / (attack.coerceAtLeast(0.1f) * d.sr)
        v.coefDown = 1f / (release.coerceAtLeast(0.1f) * d.sr)
        v.pan = 0.25f + 0.5f * d.rand()
    }

    private fun pureOn(midi: Float, level: Float, beat: Float, attack: Float, release: Float) {
        val v = freePad()
        v.active = true
        v.pure = true
        v.freq = d.midiHz(midi)
        v.beat = beat
        v.env = 0f
        v.target = level
        v.coefUp = 1f / (attack * d.sr)
        v.coefDown = 1f / (release * d.sr)
        v.pan = 0.2f + 0.6f * d.rand()
        v.tremRate = 0.1f + d.rand() * 0.3f
    }

    private fun keyOn(
        midi: Float, velocity: Float, delay: Int, decaySec: Float, ratio: Float, index: Float, melody: Boolean,
    ) {
        val v = keyVoices.firstOrNull { !it.active } ?: keyVoices.minBy { it.env * it.release }
        v.active = true
        v.delay = delay
        v.freq = d.midiHz(midi)
        v.phaseC = 0f; v.phaseM = 0f
        v.ratio = ratio
        v.env = 0f
        v.attackLeft = (0.005f * d.sr).toInt().coerceAtLeast(1)
        v.attackInc = velocity / v.attackLeft
        v.decay = d.decay(decaySec)
        v.index = index * (0.6f + velocity)
        v.indexDecay = d.decay(0.3f)
        v.releasing = false
        v.release = 1f
        v.pan = 0.3f + 0.4f * d.rand()
        v.melody = melody
    }
}
