package com.chrizzzzilla.self.game

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

// ==========================================================================================
// Demut · „Der Garten“
// Pflanzen wachsen nur ueber Tage (gegossen + ohne Unkraut = morgen eine Stufe weiter).
// Zu viel Wasser schadet nicht dauerhaft, aber an dem Tag waechst nichts. Nichts geht ein.
// Dazu „Bemerken“: kleine Dinge, die man nur findet, wenn man langsam sucht.
// ==========================================================================================

internal enum class PlantKind(val label: String, val color: Color, val days: Int, val ideal: Float, val gives: String) {
    LICHTBLUETE("Lichtblüte", Color(0xFFF2D27A), 3, 0.5f, "Einsicht"),
    STERNMOOS("Sternmoos", Color(0xFFA8F0B0), 2, 0.4f, "Ruhe"),
    MONDKRAUT("Mondkraut", Color(0xFFB8C8FF), 4, 0.35f, "Heilung"),
    TAUGLOCKE("Tauglocke", Color(0xFF8FE0E8), 3, 0.7f, "Klarheit"),
    GLIMMBEERE("Glimmbeere", Color(0xFFFF8A9A), 5, 0.6f, "Wärme"),
    STILLWURZ("Stillwurz", Color(0xFFD8A8FF), 6, 0.3f, "Geduld"),
}

internal enum class SmallThing(val label: String, val line: String) {
    TAU("Ein Tautropfen", "Eine ganze Welt in einem Tropfen."),
    KIESEL("Ein glatter Kiesel", "Das Wasser hat lange an ihm gearbeitet."),
    FEDER("Eine Feder", "Leicht – und trotzdem trägt sie."),
    NETZ("Ein Netz voller Tau", "Geduld, Faden für Faden."),
    HERZBLATT("Ein Blatt in Herzform", "Manches findet man nur, wenn man nicht sucht."),
    MOOS("Ein Mooskissen", "Weich und still, seit Jahren."),
    SAMEN("Ein verlorenes Samenkorn", "Klein. Und alles ist schon darin."),
    STERN("Der erste Stern", "Er war die ganze Zeit da."),
    PFUETZE("Eine Pfütze mit Himmel", "Der Himmel liegt auch unten."),
    RINDE("Rinde mit Ringen", "Jedes Jahr ein Ring. Keiner zu viel."),
    MUSCHEL("Eine Muschel", "Weit weg vom Meer – und doch erinnert sie sich."),
    FUNKE("Ein Lichtfleck im Gras", "Nur ein Moment. Dann ist er weiter."),
}

internal const val WATER_BAND = 0.1f
internal const val GARDEN_SECONDS = 600f

/** Lage der Beete in der Szene (x, y der Erde, 0..1). */
internal val PLOT_POS = listOf(
    0.12f to 0.70f, 0.27f to 0.82f, 0.42f to 0.70f,
    0.57f to 0.82f, 0.72f to 0.70f, 0.87f to 0.82f,
)

internal class Plot(
    var kind: PlantKind? = null,
    var stage: Int = 0,
    var water: Float = 0f,
    var tooMuch: Boolean = false,
    var weed: Boolean = false,
) {
    /** heute richtig gegossen */
    val ok: Boolean
        get() {
            val k = kind ?: return false
            return !tooMuch && abs(water - k.ideal) <= WATER_BAND
        }
    val blooming: Boolean get() = kind?.let { stage >= it.days } ?: false
}

internal class GardenData(
    var date: String,
    var secondsToday: Float,
    val plots: List<Plot>,
    val seeds: MutableMap<PlantKind, Int>,
    val harvest: MutableMap<PlantKind, Int>,
    val found: MutableSet<SmallThing>,
    val unlocked: MutableSet<PlantKind>,
) {
    val score: Int get() = harvest.values.sum() + found.size

    fun toJson(): String = JSONObject().apply {
        put("date", date)
        put("seconds", secondsToday.toDouble())
        put("plots", JSONArray().apply {
            plots.forEach { p ->
                put(JSONObject().apply {
                    put("kind", p.kind?.name ?: "")
                    put("stage", p.stage)
                    put("water", p.water.toDouble())
                    put("tooMuch", p.tooMuch)
                    put("weed", p.weed)
                })
            }
        })
        put("seeds", JSONObject().apply { seeds.forEach { (k, v) -> put(k.name, v) } })
        put("harvest", JSONObject().apply { harvest.forEach { (k, v) -> put(k.name, v) } })
        put("found", JSONArray(found.map { it.name }))
        put("unlocked", JSONArray(unlocked.map { it.name }))
    }.toString()

    companion object {
        fun fresh() = GardenData(
            "", 0f, List(PLOT_POS.size) { Plot() }, mutableMapOf(), mutableMapOf(), mutableSetOf(), mutableSetOf(),
        )

        fun fromJson(s: String?): GardenData = runCatching {
            val o = JSONObject(s ?: return fresh())
            fun kind(n: String) = PlantKind.entries.firstOrNull { it.name == n }
            val plots = List(PLOT_POS.size) { i ->
                val j = o.optJSONArray("plots")?.optJSONObject(i)
                if (j == null) Plot() else Plot(
                    kind(j.optString("kind")), j.optInt("stage"), j.optDouble("water", 0.0).toFloat(),
                    j.optBoolean("tooMuch"), j.optBoolean("weed"),
                )
            }
            fun counts(key: String): MutableMap<PlantKind, Int> {
                val m = mutableMapOf<PlantKind, Int>()
                o.optJSONObject(key)?.let { j -> j.keys().forEach { k -> kind(k)?.let { m[it] = j.optInt(k) } } }
                return m
            }
            val found = mutableSetOf<SmallThing>()
            o.optJSONArray("found")?.let { a -> for (i in 0 until a.length()) SmallThing.entries.firstOrNull { it.name == a.optString(i) }?.let { found += it } }
            val unlocked = mutableSetOf<PlantKind>()
            o.optJSONArray("unlocked")?.let { a -> for (i in 0 until a.length()) kind(a.optString(i))?.let { unlocked += it } }
            GardenData(o.optString("date"), o.optDouble("seconds", 0.0).toFloat(), plots, counts("seeds"), counts("harvest"), found, unlocked)
        }.getOrDefault(fresh())
    }

    /** Neuer Tag: Gepflegtes waechst eine Stufe, Wasser zuruecksetzen, etwas Unkraut. Nichts geht ein. */
    fun rollDay() {
        val today = LocalDate.now().toString()
        if (date == today) return
        val first = date.isEmpty()
        date = today
        secondsToday = 0f
        if (first) {
            seeds[PlantKind.LICHTBLUETE] = 3
            unlocked += PlantKind.LICHTBLUETE
            return
        }
        val rnd = Random(today.hashCode())
        for (p in plots) {
            val k = p.kind
            if (k != null && p.ok && !p.weed && p.stage < k.days) p.stage++
            p.water = 0f
            p.tooMuch = false
            if (k != null && p.stage < k.days && !p.weed) p.weed = rnd.nextFloat() < 0.35f
        }
    }
}

private val Context.gardenStore: DataStore<Preferences> by preferencesDataStore(name = "garten")

internal class GardenStore(context: Context) {
    private val store = context.applicationContext.gardenStore
    private val key = stringPreferencesKey("state")

    suspend fun load(): GardenData = GardenData.fromJson(store.data.first()[key]).also { it.rollDay() }

    suspend fun save(data: GardenData) {
        store.edit { it[key] = data.toJson() }
    }

    /** heute verbrauchte Zeit, Punkte (Ernten + Funde) */
    suspend fun summary(): Pair<Float, Int> {
        val d = load()
        return d.secondsToday to d.score
    }
}
