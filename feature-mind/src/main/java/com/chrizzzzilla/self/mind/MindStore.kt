package com.chrizzzzilla.self.mind

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.mindStore: DataStore<Preferences> by preferencesDataStore(name = "mind")

/** Ein Abend: Fragen und Antworten. */
internal class MindEntry(val date: String, val qa: List<Pair<String, String>>)

/** Morgen-Stand: letzter Tag, Serie, Wort fuer heute. */
internal class MorningState(val lastDone: String, val streak: Int, val wordDate: String, val word: String) {
    val doneToday: Boolean get() = lastDone == LocalDate.now().toString()
    val todayWord: String? get() = word.takeIf { wordDate == LocalDate.now().toString() && it.isNotBlank() }
}

/** Alles bleibt lokal auf dem Geraet. */
internal class MindStore(context: Context) {
    private val store = context.applicationContext.mindStore
    private val entriesKey = stringPreferencesKey("entries")
    private val lastKey = stringPreferencesKey("body_last")
    private val streakKey = intPreferencesKey("body_streak")
    private val wordDateKey = stringPreferencesKey("word_date")
    private val wordKey = stringPreferencesKey("word")

    val entries: Flow<List<MindEntry>> = store.data.map { p -> parse(p[entriesKey]) }

    val morning: Flow<MorningState> = store.data.map { p ->
        MorningState(p[lastKey] ?: "", p[streakKey] ?: 0, p[wordDateKey] ?: "", p[wordKey] ?: "")
    }

    suspend fun addEntry(e: MindEntry) {
        store.edit { p ->
            val list = parse(p[entriesKey]).filter { it.date != e.date } + e
            p[entriesKey] = JSONArray().apply {
                list.forEach { en ->
                    put(JSONObject().apply {
                        put("date", en.date)
                        put("qa", JSONArray().apply { en.qa.forEach { (q, a) -> put(JSONObject().put("q", q).put("a", a)) } })
                    })
                }
            }.toString()
        }
    }

    /** Morgenroutine geschafft + Wort fuer den Tag. */
    suspend fun morningDone(word: String) {
        store.edit { p ->
            val today = LocalDate.now()
            val last = p[lastKey] ?: ""
            val streak = p[streakKey] ?: 0
            p[streakKey] = when (last) {
                today.toString() -> streak
                today.minusDays(1).toString() -> streak + 1
                else -> 1
            }
            p[lastKey] = today.toString()
            if (word.isNotBlank()) {
                p[wordDateKey] = today.toString()
                p[wordKey] = word
            }
        }
    }

    private fun parse(s: String?): List<MindEntry> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val qa = o.optJSONArray("qa")
            MindEntry(
                o.optString("date"),
                (0 until (qa?.length() ?: 0)).map { k -> qa!!.getJSONObject(k).let { it.optString("q") to it.optString("a") } },
            )
        }
    }.getOrDefault(emptyList())
}
