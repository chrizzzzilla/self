package com.chrizzzzilla.self.game

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

private val Context.pathStore: DataStore<Preferences> by preferencesDataStore(name = "path")

/** Stand von „Ruhe“. */
internal data class CalmProgress(
    /** im laufenden Durchgang geschaffte Minuten (= Checkpoint) 0..9 */
    val checkpoint: Int,
    /** heute gespielte Sekunden (Tageslimit 10 Minuten) */
    val secondsToday: Int,
    val level: Int,
    /** 0..1 Fortschritt zum naechsten Level */
    val toNext: Float,
    /** komplett geschaffte Durchgaenge */
    val runs: Int,
)

/**
 * Fortschritt der Path-Spiele.
 * Ruhe: ein Durchgang = 10 Minuten am Stueck, jede Minute schwieriger und ein Checkpoint.
 * Der Checkpoint bleibt auch ueber Nacht. Minute n bringt n Punkte (ein ganzer Durchgang = 55).
 * Level 1→2 braucht 55 Punkte, danach je ~25 % mehr.
 */
internal class PathStore(context: Context) {
    private val store = context.applicationContext.pathStore
    private val dateKey = stringPreferencesKey("date")
    private val calmXpKey = intPreferencesKey("calm_xp")
    private val checkpointKey = intPreferencesKey("calm_checkpoint")
    private val secondsKey = intPreferencesKey("calm_seconds")
    private val runsKey = intPreferencesKey("calm_runs")

    private fun today() = LocalDate.now().toString()

    val calm: Flow<CalmProgress> = store.data.map { p ->
        val isToday = p[dateKey] == today()
        val (level, toNext) = levelFor(p[calmXpKey] ?: 0)
        CalmProgress(
            checkpoint = p[checkpointKey] ?: 0,
            secondsToday = if (isToday) p[secondsKey] ?: 0 else 0,
            level = level,
            toNext = toNext,
            runs = p[runsKey] ?: 0,
        )
    }

    suspend fun addCalmSeconds(sec: Int) {
        if (sec <= 0) return
        store.edit { p ->
            val t = today()
            val base = if (p[dateKey] == t) p[secondsKey] ?: 0 else 0
            p[dateKey] = t
            p[secondsKey] = (base + sec).coerceAtMost(CALM_DAILY_SECONDS)
        }
    }

    /** Minute [minute] (1..10) geschafft. Gibt (altes Level, neues Level) zurueck. */
    suspend fun completeCalmMinute(minute: Int): Pair<Int, Int> {
        val before = levelFor(store.data.first()[calmXpKey] ?: 0).first
        var after = before
        store.edit { p ->
            val xp = (p[calmXpKey] ?: 0) + minute.coerceIn(1, MINUTES)
            p[calmXpKey] = xp
            after = levelFor(xp).first
            if (minute >= MINUTES) {
                p[checkpointKey] = 0
                p[runsKey] = (p[runsKey] ?: 0) + 1
            } else p[checkpointKey] = minute
        }
        return before to after
    }

    companion object {
        const val MINUTES = 10
        const val CALM_DAILY_SECONDS = 600

        /** Punkte, die man von Level [level] zum naechsten braucht. */
        fun needed(level: Int): Int = (55.0 * 1.25.pow(level - 1)).roundToInt()

        fun levelFor(xp: Int): Pair<Int, Float> {
            var level = 1
            var rest = xp
            while (rest >= needed(level)) {
                rest -= needed(level)
                level++
            }
            return level to rest / needed(level).toFloat()
        }
    }
}

/** Was man auf einem Level an innerer Ruhe dazugewonnen hat. */
internal class CalmAbilities(level: Int) {
    /** Schildenergie (Sekunden, die das Schild haelt) */
    val shieldMax: Float = 2.5f + (level - 1) * 0.6f
    /** Gruene Lichter werden ab Level 3 angezogen */
    val magnet: Float = if (level >= 3) 0.06f + (level - 3) * 0.015f else 0f

    companion object {
        fun unlocked(level: Int): String = when {
            level == 3 -> "Grünes Licht findet zu dir – und dein Schild hält länger."
            else -> "Deine Schildenergie wächst – das Schild hält länger."
        }
    }
}
