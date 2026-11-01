package com.chrizzzzilla.self.game

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

// ==========================================================================================
// Demut · „Die Pflanze am Fenster“
// Eine Pflanze, die man pflegt: giessen, duengen, stutzen, anbinden, drehen, besprühen,
// abstauben, zureden, umtopfen, Vorhang. Sie waechst nur ueber Nacht und geht nie ein.
// Je nach Situation (und etwas Zufall) schenkt sie Dinge, die man fuer „Me“ braucht.
// ==========================================================================================

internal enum class Gift(val label: String, val meaning: String, val color: Color) {
    TAU("Tautropfen", "Geduld", Color(0xFF9FD8FF)),
    LICHTBLATT("Lichtblatt", "Einsicht", Color(0xFFB8F07A)),
    STECKLING("Steckling", "Neubeginn", Color(0xFF7FD89A)),
    BLUETE("Blüte", "Freude", Color(0xFFFF9ABE)),
    SAMEN("Samen", "Hoffnung", Color(0xFFE0C080)),
    SAFT("Pflanzensaft", "Heilung", Color(0xFF8AF0C8)),
    LICHTFADEN("Lichtfaden", "Ausrichtung", Color(0xFFFFE6A0)),
    GLANZ("Blattglanz", "Klarheit", Color(0xFFE8F4FF)),
    DUFT("Duft der Ruhe", "Ruhe", Color(0xFFD8B8FF)),
    MONDTROPFEN("Mondtropfen", "Stille", Color(0xFFC8D4FF)),
    WURZEL("Wurzelfaser", "Halt", Color(0xFFC89A6A)),
    HUMUS("Humus", "Demut", Color(0xFFB08A60)),
}

internal enum class Care(val label: String) {
    WATER("GIESSEN"), FEED("DÜNGEN"), PRUNE("STUTZEN"), TIE("ANBINDEN"), TURN("DREHEN"),
    MIST("SPRÜHEN"), DUST("ABSTAUBEN"), TALK("ZUREDEN"), REPOT("UMTOPFEN"), CURTAIN("VORHANG"),
}

internal const val PLANT_SECONDS = 600f
internal const val MAX_STAGE = 12

internal class PlantData {
    var date = ""
    var seconds = 0f
    var stage = 1
    var partial = 0f
    var pot = 1
    var water = 0.2f
    var nutrients = 0.5f
    var mood = 0.3f
    var lean = 0.2f
    var humidity = 0.5f
    var dust = 0f
    var ties = 0
    var wild = 0
    var wilted = 1
    var flowers = 0
    var faded = 0
    var curtain = false
    var sun = false
    var soggy = false
    var burnt = false
    var talkedFull = false
    val gifts = mutableMapOf<Gift, Int>()

    val neededTies: Int get() = if (stage >= 4) (stage - 2) / 2 else 0
    val potTooSmall: Boolean get() = stage > pot * 2 + 1
    val giftCount: Int get() = gifts.values.sum()

    fun sunNow(hour: Float) = sun && hour in 8f..18f
    fun lightOk(hour: Float) = if (sunNow(hour)) curtain else !curtain

    /** Was braucht die Pflanze gerade? */
    fun needs(hour: Float): Set<Care> = buildSet {
        if (water < 0.3f) add(Care.WATER)
        if (nutrients < 0.25f) add(Care.FEED)
        if (wild > 0 || wilted > 0 || faded > 0) add(Care.PRUNE)
        if (ties < neededTies) add(Care.TIE)
        if (lean > 0.4f) add(Care.TURN)
        if (humidity < 0.3f) add(Care.MIST)
        if (dust > 0.4f) add(Care.DUST)
        if (mood < 0.5f) add(Care.TALK)
        if (potTooSmall) add(Care.REPOT)
        if (!lightOk(hour)) add(Care.CURTAIN)
    }

    fun toJson(): String = JSONObject().apply {
        put("date", date); put("seconds", seconds.toDouble()); put("stage", stage); put("partial", partial.toDouble())
        put("pot", pot); put("water", water.toDouble()); put("nutrients", nutrients.toDouble()); put("mood", mood.toDouble())
        put("lean", lean.toDouble()); put("humidity", humidity.toDouble()); put("dust", dust.toDouble()); put("ties", ties)
        put("wild", wild); put("wilted", wilted); put("flowers", flowers); put("faded", faded); put("curtain", curtain)
        put("sun", sun); put("soggy", soggy); put("burnt", burnt); put("talkedFull", talkedFull)
        put("gifts", JSONObject().apply { gifts.forEach { (k, v) -> put(k.name, v) } })
    }.toString()

    companion object {
        fun fromJson(s: String?): PlantData {
            val p = PlantData()
            val o = runCatching { JSONObject(s ?: return p) }.getOrNull() ?: return p
            fun f(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
            p.date = o.optString("date"); p.seconds = f("seconds", 0f); p.stage = o.optInt("stage", 1); p.partial = f("partial", 0f)
            p.pot = o.optInt("pot", 1); p.water = f("water", 0.2f); p.nutrients = f("nutrients", 0.5f); p.mood = f("mood", 0.3f)
            p.lean = f("lean", 0.2f); p.humidity = f("humidity", 0.5f); p.dust = f("dust", 0f); p.ties = o.optInt("ties")
            p.wild = o.optInt("wild"); p.wilted = o.optInt("wilted"); p.flowers = o.optInt("flowers"); p.faded = o.optInt("faded")
            p.curtain = o.optBoolean("curtain"); p.sun = o.optBoolean("sun"); p.soggy = o.optBoolean("soggy")
            p.burnt = o.optBoolean("burnt"); p.talkedFull = o.optBoolean("talkedFull")
            o.optJSONObject("gifts")?.let { j -> j.keys().forEach { k -> Gift.entries.firstOrNull { it.name == k }?.let { p.gifts[it] = j.optInt(k) } } }
            return p
        }
    }

    /** Neuer Tag: aus dem Zustand von gestern waechst sie (oder nicht), dann vergeht etwas Zeit. Nichts geht ein. */
    fun rollDay() {
        val today = LocalDate.now()
        if (date == today.toString()) return
        if (date.isEmpty()) { date = today.toString(); return }
        val last = runCatching { LocalDate.parse(date) }.getOrNull()
        val days = (if (last == null) 1L else ChronoUnit.DAYS.between(last, today)).toInt().coerceIn(1, 4)
        val rnd = Random(today.hashCode())
        val conds = listOf(
            water in 0.3f..0.9f && !soggy, nutrients >= 0.25f && !burnt, mood >= 0.4f, lean < 0.6f, !potTooSmall,
            ties >= neededTies, dust < 0.6f, !sun || curtain, humidity >= 0.25f, wilted + wild <= 3,
        )
        val good = conds.count { it }
        if (good == conds.size) partial += 1f else if (good >= 7) partial += 0.5f
        if (partial >= 1f && stage < MAX_STAGE) { stage++; partial -= 1f }
        faded = (faded + flowers).coerceAtMost(4)
        flowers = if (good >= 9 && mood >= 0.6f && stage >= 5) 1 + rnd.nextInt(2) else 0
        repeat(days) {
            water = (water - 0.4f).coerceAtLeast(0.05f)
            nutrients = (nutrients - 0.18f).coerceAtLeast(0f)
            mood = (mood - 0.2f).coerceAtLeast(0.1f)
            lean = (lean + 0.22f).coerceAtMost(1f)
            humidity = (humidity - 0.25f).coerceAtLeast(0.05f)
            dust = (dust + 0.25f).coerceAtMost(1f)
        }
        if (stage >= 3) wild = (wild + rnd.nextInt(0, 3)).coerceAtMost(4)
        wilted = (wilted + rnd.nextInt(0, 2)).coerceAtMost(3)
        sun = rnd.nextFloat() < 0.35f
        soggy = false; burnt = false; talkedFull = false
        date = today.toString()
        seconds = 0f
    }
}

private val Context.plantStore: DataStore<Preferences> by preferencesDataStore(name = "pflanze")

internal class PlantStore(context: Context) {
    private val store = context.applicationContext.plantStore
    private val key = stringPreferencesKey("state")

    suspend fun load(): PlantData = PlantData.fromJson(store.data.first()[key]).also { it.rollDay() }

    suspend fun save(data: PlantData) {
        store.edit { it[key] = data.toJson() }
    }

    /** heute verbrauchte Zeit, Stufe, Geschenke */
    suspend fun summary(): Triple<Float, Int, Int> {
        val d = load()
        return Triple(d.seconds, d.stage, d.giftCount)
    }
}
