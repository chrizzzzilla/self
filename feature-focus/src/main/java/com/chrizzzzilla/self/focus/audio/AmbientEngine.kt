package com.chrizzzzilla.self.focus.audio

import kotlin.math.sqrt

/** Mischpult: Ambiente + Natur + Percussion → Hall → Lautstaerke → sanfte Saettigung. */
internal class AmbientEngine(sampleRate: Int) {

    private val d = Dsp(sampleRate)
    private val ambience = AmbienceLayer(d)
    private val nature = NatureLayer(d)
    private val percussion = PercussionLayer(d)
    private val reverb = Reverb(sampleRate)

    @Volatile private var mix = AmbientMix()
    @Volatile var fadeTarget = 1f
    private var fade = 0f

    @Volatile var level = 0f; private set
    @Volatile var pulse = 0f; private set

    // weich nachgefuehrte Lautstaerken (kein Knacken beim Ziehen am Regler)
    private var volA = 0f
    private var volN = 0f
    private var volP = 0f

    val isSilent: Boolean get() = fade < 0.0005f && fadeTarget == 0f

    fun setMix(m: AmbientMix) {
        mix = m
        ambience.setType(m.ambience)
        ambience.tone = m.ambienceTone
        nature.setActive(m.nature)
        percussion.setPattern(m.percussion)
        percussion.bpm = m.percussionBpm
        percussion.tone = m.percussionTone
    }

    fun render(out: FloatArray, frames: Int) {
        val m = mix
        val fadeStep = 1f / (1.0f * d.sr)
        val smooth = 1f / (0.15f * d.sr)
        var sumSq = 0f
        var pulseMax = 0f
        for (i in 0 until frames) {
            volA += (m.ambienceVolume - volA) * smooth
            volN += (m.natureVolume - volN) * smooth
            volP += (m.percussionVolume - volP) * smooth

            ambience.next()
            nature.next()
            percussion.next()
            pulseMax = maxOf(pulseMax, percussion.pulse)

            val a2 = volA * volA * 1.4f
            val n2 = volN * volN * 1.4f
            val p2 = volP * volP * 1.4f

            reverb.process(ambience.send * a2 + nature.send * n2 + percussion.send * p2)

            var l = ambience.outL * a2 + nature.outL * n2 + percussion.outL * p2 + reverb.outL
            var r = ambience.outR * a2 + nature.outR * n2 + percussion.outR * p2 + reverb.outR

            if (fade < fadeTarget) fade = minOf(fadeTarget, fade + fadeStep)
            else if (fade > fadeTarget) fade = maxOf(fadeTarget, fade - fadeStep)
            l = softClip(l * fade)
            r = softClip(r * fade)
            out[2 * i] = l
            out[2 * i + 1] = r
            sumSq += (l * l + r * r) * 0.5f
        }
        level = sqrt(sumSq / frames).coerceIn(0f, 1f)
        pulse = pulseMax.coerceIn(0f, 1f) * (volP * 1.5f).coerceAtMost(1f)
    }
}
