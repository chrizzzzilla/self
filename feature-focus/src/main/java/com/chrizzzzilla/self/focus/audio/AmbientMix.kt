package com.chrizzzzilla.self.focus.audio

import com.chrizzzzilla.self.focus.scene.Scene
import com.chrizzzzilla.self.focus.scene.Season
import org.json.JSONArray
import org.json.JSONObject

/** Die 10 Klangflaechen ("Ambiente"). Reihenfolge = Index, nicht umsortieren (Presets speichern den Namen). */
enum class Ambience(val label: String) {
    LOFI_KEYS("Lofi-Klavier"),
    WARM_PADS("Warme Flächen"),
    NIGHT_CITY("Nachtstadt"),
    DEEP_SPACE("Tiefer Raum"),
    BELLS("Glocken"),
    MORNING("Morgenlicht"),
    DUSK("Dämmerung"),
    PIANO("Klavier"),
}

/** Naturgeraeusche (mehrere gleichzeitig moeglich). */
enum class NatureSound(val label: String) {
    RAIN("Regen"),
    WIND("Wind"),
    SEA("Meeresrauschen"),
    BIRDS("Vogelgezwitscher"),
    CRICKETS("Grillenzirpen"),
    STREAM("Bach"),
    FIRE("Kaminfeuer"),
    THUNDER("Fernes Gewitter"),
}

/** Die 10 Percussion-Muster. */
enum class Percussion(val label: String) {
    BOOM_BAP("Lofi Boom-Bap"),
    LAID_BACK("Entspannt"),
    SHAKER("Shaker"),
    BRUSHES("Jazz-Besen"),
    HEARTBEAT("Herzschlag"),
    HAND_DRUMS("Handtrommeln"),
    TRIP_HOP("Trip-Hop"),
    MINIMAL("Minimal"),
    RAIN_DRUM("Regentrommel"),
}

/** Komplette Klang-Mischung – das, was in einem Preset gespeichert wird. */
data class AmbientMix(
    val ambience: Ambience? = Ambience.LOFI_KEYS,
    val ambienceVolume: Float = 0.6f,
    val nature: Set<NatureSound> = setOf(NatureSound.RAIN),
    val natureVolume: Float = 0.5f,
    val percussion: Percussion? = null,
    val percussionVolume: Float = 0.5f,
    /** 0 = langsam … 1 = schnell */
    val percussionSpeed: Float = 0.35f,
    /** 0 = dunkel … 1 = hell */
    val percussionTone: Float = 0.45f,
    /** 0 = dunkel … 1 = hell (Klangfarbe des Ambientes) */
    val ambienceTone: Float = 0.45f,
    /** Bild hinter dem Buch / im Fokusmodus */
    val scene: Scene = Scene.CITY,
    val season: Season = Season.SUMMER,
    /** Tageszeit 0..24 (stufenlos) */
    val hour: Float = 22f,
    /** true = Tageszeit folgt der echten Uhrzeit des Geraets */
    val realTime: Boolean = false,
) {
    val percussionBpm: Float get() = 60f + percussionSpeed.coerceIn(0f, 1f) * 60f

    fun toJson(): String = JSONObject().apply {
        put("ambience", ambience?.name ?: JSONObject.NULL)
        put("ambienceVolume", ambienceVolume.toDouble())
        put("ambienceTone", ambienceTone.toDouble())
        put("nature", JSONArray(nature.map { it.name }))
        put("natureVolume", natureVolume.toDouble())
        put("percussion", percussion?.name ?: JSONObject.NULL)
        put("percussionVolume", percussionVolume.toDouble())
        put("percussionSpeed", percussionSpeed.toDouble())
        put("percussionTone", percussionTone.toDouble())
        put("scene", scene.name)
        put("season", season.name)
        put("hour", hour.toDouble())
        put("realTime", realTime)
    }.toString()

    companion object {
        fun fromJson(json: String?): AmbientMix? = runCatching {
            val o = JSONObject(json ?: return null)
            val d = AmbientMix()
            val natureArr = o.optJSONArray("nature")
            AmbientMix(
                ambience = o.optString("ambience").let { n -> Ambience.entries.firstOrNull { it.name == n } },
                ambienceVolume = o.optDouble("ambienceVolume", d.ambienceVolume.toDouble()).toFloat(),
                ambienceTone = o.optDouble("ambienceTone", d.ambienceTone.toDouble()).toFloat(),
                nature = buildSet {
                    if (natureArr != null) for (i in 0 until natureArr.length()) {
                        NatureSound.entries.firstOrNull { it.name == natureArr.optString(i) }?.let(::add)
                    }
                },
                natureVolume = o.optDouble("natureVolume", d.natureVolume.toDouble()).toFloat(),
                percussion = o.optString("percussion").let { n -> Percussion.entries.firstOrNull { it.name == n } },
                percussionVolume = o.optDouble("percussionVolume", d.percussionVolume.toDouble()).toFloat(),
                percussionSpeed = o.optDouble("percussionSpeed", d.percussionSpeed.toDouble()).toFloat(),
                percussionTone = o.optDouble("percussionTone", d.percussionTone.toDouble()).toFloat(),
                scene = o.optString("scene").let { n -> Scene.entries.firstOrNull { it.name == n } } ?: d.scene,
                season = o.optString("season").let { n -> Season.entries.firstOrNull { it.name == n } } ?: d.season,
                hour = o.optDouble("hour", d.hour.toDouble()).toFloat(),
                realTime = o.optBoolean("realTime", d.realTime),
            )
        }.getOrNull()
    }
}
