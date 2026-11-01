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
import kotlin.random.Random

// ==========================================================================================
// Ehrgeiz · „Die Werkstatt“ – Steinzeit-Stufe
// Rohstoffe → Verarbeitet → Werkzeuge → Einrichtungen (→ spaeter Metall, dann Gebaeude fuer „Me“)
// ==========================================================================================

internal enum class Item(val label: String, val tier: Int, val color: Color) {
    // Rohstoffe
    AST("Ast", 1, Color(0xFF8A6A48)),
    STAMM("Stamm", 1, Color(0xFF6E4E32)),
    STEIN("Stein", 1, Color(0xFF9A9A98)),
    FEUERSTEIN("Feuerstein", 1, Color(0xFF4A4E5A)),
    FASERN("Fasern", 1, Color(0xFF8FB067)),
    LEHM("Lehm", 1, Color(0xFFB07A52)),
    SAND("Sand", 1, Color(0xFFE2C98E)),
    WASSER("Wasser", 1, Color(0xFF5EA8D8)),
    HARZ("Harz", 1, Color(0xFFE0A030)),
    // Verarbeitet
    STOCK("Stock", 2, Color(0xFFA07850)),
    SEIL("Seil", 2, Color(0xFFC8B078)),
    BEHAUENER_STEIN("Behauener Stein", 2, Color(0xFFB8B8B4)),
    HOLZSCHEIT("Holzscheit", 2, Color(0xFFB48858)),
    HOLZKOHLE("Holzkohle", 2, Color(0xFF34343A)),
    PECH("Pech", 2, Color(0xFF3A2A1A)),
    LEHMZIEGEL("Lehmziegel", 2, Color(0xFFC07850)),
    // Werkzeuge
    FAUSTKEIL("Faustkeil", 3, Color(0xFF6E7280)),
    STEINAXT("Steinaxt", 3, Color(0xFF8C8C90)),
    STEINHAMMER("Steinhammer", 3, Color(0xFF9C9C9A)),
    FEUERBOHRER("Feuerbohrer", 3, Color(0xFFB08A5A)),
    FACKEL("Fackel", 3, Color(0xFFF0A040)),
    // Einrichtungen (werden gebaut und stehen dann in der Werkstatt)
    LAGERFEUER("Lagerfeuer", 4, Color(0xFFF07A3A)),
    WERKBANK("Werkbank", 4, Color(0xFF9A7048)),
    ;

    val isStation: Boolean get() = tier == 4
    val isTool: Boolean get() = tier == 3
}

/** Art der Arbeit am Arbeitskreis (eine Hand). */
internal enum class Work(val hint: String) {
    TAP("mehrmals auf den Kreis tippen"),
    CIRCLE("Finger im Kreis ziehen"),
    RUB("Finger hin und her wischen"),
    HOLD("Finger auf dem Kreis halten"),
}

internal class Recipe(
    val inputs: Map<Item, Int>,
    val output: Item,
    val count: Int = 1,
    /** Werkzeug, das man besitzen muss (wird nicht verbraucht) */
    val tool: Item? = null,
    /** Einrichtung, die gebaut sein muss */
    val station: Item? = null,
    val work: Work,
    /** TAP: Anzahl · CIRCLE: Umdrehungen · RUB: Wechsel · HOLD: Sekunden */
    val amount: Float,
) {
    val id: String = output.name + ":" + inputs.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}${it.value}" }
}

internal val RECIPES: List<Recipe> = listOf(
    Recipe(mapOf(Item.AST to 1), Item.STOCK, work = Work.TAP, amount = 2f),
    Recipe(mapOf(Item.FASERN to 2), Item.SEIL, work = Work.CIRCLE, amount = 2f),
    Recipe(mapOf(Item.FEUERSTEIN to 1, Item.STEIN to 1), Item.FAUSTKEIL, work = Work.TAP, amount = 6f),
    Recipe(mapOf(Item.STEIN to 1), Item.BEHAUENER_STEIN, tool = Item.FAUSTKEIL, work = Work.TAP, amount = 4f),
    Recipe(mapOf(Item.STOCK to 1, Item.BEHAUENER_STEIN to 1, Item.SEIL to 1), Item.STEINAXT, work = Work.CIRCLE, amount = 1.5f),
    Recipe(mapOf(Item.STOCK to 1, Item.STEIN to 1, Item.SEIL to 1), Item.STEINHAMMER, work = Work.CIRCLE, amount = 1.5f),
    Recipe(mapOf(Item.STOCK to 1, Item.SEIL to 1), Item.FEUERBOHRER, work = Work.CIRCLE, amount = 1f),
    Recipe(mapOf(Item.STAMM to 1), Item.HOLZSCHEIT, count = 3, tool = Item.STEINAXT, work = Work.TAP, amount = 5f),
    Recipe(mapOf(Item.STEIN to 3, Item.HOLZSCHEIT to 2), Item.LAGERFEUER, tool = Item.FEUERBOHRER, work = Work.CIRCLE, amount = 3f),
    Recipe(mapOf(Item.HOLZSCHEIT to 1), Item.HOLZKOHLE, count = 2, station = Item.LAGERFEUER, work = Work.HOLD, amount = 3f),
    Recipe(mapOf(Item.HARZ to 1, Item.HOLZKOHLE to 1), Item.PECH, station = Item.LAGERFEUER, work = Work.CIRCLE, amount = 1.5f),
    Recipe(mapOf(Item.LEHM to 1, Item.SAND to 1, Item.WASSER to 1), Item.LEHMZIEGEL, count = 2, work = Work.RUB, amount = 6f),
    Recipe(mapOf(Item.STOCK to 1, Item.HARZ to 1), Item.FACKEL, station = Item.LAGERFEUER, work = Work.HOLD, amount = 1.5f),
    Recipe(mapOf(Item.HOLZSCHEIT to 4, Item.SEIL to 2, Item.PECH to 1), Item.WERKBANK, tool = Item.STEINHAMMER, work = Work.TAP, amount = 8f),
)

/** Sammelstellen in der Landschaft. x/y = Lage im Bild (0..1). */
internal class Node(val item: Item, val x: Float, val y: Float, val perDay: Int, val needs: Item? = null, val rare: Boolean = false)

internal val NODES = listOf(
    Node(Item.AST, 0.12f, 0.52f, 8),
    Node(Item.STAMM, 0.24f, 0.66f, 3, needs = Item.STEINAXT),
    Node(Item.HARZ, 0.07f, 0.38f, 0, needs = Item.FAUSTKEIL, rare = true),
    Node(Item.FASERN, 0.36f, 0.72f, 8),
    Node(Item.STEIN, 0.48f, 0.58f, 8),
    Node(Item.FEUERSTEIN, 0.56f, 0.68f, 0, rare = true),
    Node(Item.LEHM, 0.70f, 0.74f, 4),
    Node(Item.SAND, 0.80f, 0.62f, 5),
    Node(Item.WASSER, 0.90f, 0.76f, 99),
)

internal const val DAILY_SECONDS = 600f

/** Gespeicherter Stand der Werkstatt. */
internal class WorkshopData(
    var date: String,
    var secondsToday: Float,
    val inventory: MutableMap<Item, Int>,
    val discovered: MutableSet<String>,
    val built: MutableSet<Item>,
    val nodes: MutableMap<Item, Int>,
) {
    fun has(item: Item) = (inventory[item] ?: 0) > 0 || item in built

    fun toJson(): String = JSONObject().apply {
        put("date", date)
        put("seconds", secondsToday.toDouble())
        put("inv", JSONObject().apply { inventory.forEach { (k, v) -> if (v > 0) put(k.name, v) } })
        put("disc", JSONArray(discovered.toList()))
        put("built", JSONArray(built.map { it.name }))
        put("nodes", JSONObject().apply { nodes.forEach { (k, v) -> put(k.name, v) } })
    }.toString()

    companion object {
        fun fresh(): WorkshopData = WorkshopData("", 0f, mutableMapOf(), mutableSetOf(), mutableSetOf(), mutableMapOf())

        fun fromJson(s: String?): WorkshopData = runCatching {
            val o = JSONObject(s ?: return fresh())
            fun item(n: String) = Item.entries.firstOrNull { it.name == n }
            val inv = mutableMapOf<Item, Int>()
            o.optJSONObject("inv")?.let { j -> j.keys().forEach { k -> item(k)?.let { inv[it] = j.optInt(k) } } }
            val disc = mutableSetOf<String>()
            o.optJSONArray("disc")?.let { a -> for (i in 0 until a.length()) disc += a.optString(i) }
            val built = mutableSetOf<Item>()
            o.optJSONArray("built")?.let { a -> for (i in 0 until a.length()) item(a.optString(i))?.let { built += it } }
            val nodes = mutableMapOf<Item, Int>()
            o.optJSONObject("nodes")?.let { j -> j.keys().forEach { k -> item(k)?.let { nodes[it] = j.optInt(k) } } }
            WorkshopData(o.optString("date"), o.optDouble("seconds", 0.0).toFloat(), inv, disc, built, nodes)
        }.getOrDefault(fresh())
    }

    /** Neuer Tag: Zeit zuruecksetzen, Sammelstellen auffuellen (seltene zufaellig 0–2). */
    fun rollDay() {
        val today = LocalDate.now().toString()
        if (date == today) return
        date = today
        secondsToday = 0f
        val rnd = Random(today.hashCode())
        for (n in NODES) {
            nodes[n.item] = if (n.rare) rnd.nextInt(0, 3) else n.perDay
        }
        // Damit der Einstieg moeglich ist: ohne Faustkeil gibt es mindestens einen Feuerstein
        if (!has(Item.FAUSTKEIL)) nodes[Item.FEUERSTEIN] = maxOf(1, nodes[Item.FEUERSTEIN] ?: 0)
        // Harz wird fuer Pech/Werkbank gebraucht – solange die fehlt, mindestens eins
        if (Item.WERKBANK !in built) nodes[Item.HARZ] = maxOf(1, nodes[Item.HARZ] ?: 0)
    }
}

private val Context.workshopStore: DataStore<Preferences> by preferencesDataStore(name = "werkstatt")

internal class WorkshopStore(context: Context) {
    private val store = context.applicationContext.workshopStore
    private val key = stringPreferencesKey("state")

    suspend fun load(): WorkshopData = WorkshopData.fromJson(store.data.first()[key]).also { it.rollDay() }

    suspend fun save(data: WorkshopData) {
        store.edit { it[key] = data.toJson() }
    }

    /** Fuer die Path-Kachel: heute verbrauchte Zeit und entdeckte Rezepte. */
    suspend fun summary(): Pair<Float, Int> {
        val d = load()
        return d.secondsToday to d.discovered.size
    }
}
