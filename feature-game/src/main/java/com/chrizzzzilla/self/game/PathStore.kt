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

/** Stand eines Spiels: heutige Stationen + Level-Fortschritt. */
internal data class CalmProgress(
    val stationsToday: Int,
    val level: Int,
    /** 0..1 Fortschritt zum naechsten Level */
    val toNext: Float,
)

/**
 * Fortschritt der Path-Spiele.
 * Ruhe: 10 Stationen pro Tag (je 1 Min.). Station n bringt n Punkte (ein voller Tag = 55).
 * Level 1→2 braucht 55 Punkte, danach je ~25 % mehr – oben langsamer, aber nie zaeh.
 */
internal class PathStore(context: Context) {
    private val store = context.applicationContext.pathStore
    private val dateKey = stringPreferencesKey("date")
    private val calmKey = intPreferencesKey("calm_rounds")
    private val calmXpKey = intPreferencesKey("calm_xp")

    private fun today() = LocalDate.now().toString()

    val calm: Flow<CalmProgress> = store.data.map { p ->
        val stations = if (p[dateKey] == today()) p[calmKey] ?: 0 else 0
        val xp = p[calmXpKey] ?: 0
        val (level, toNext) = levelFor(xp)
        CalmProgress(stations, level, toNext)
    }

    /** Station geschafft. Gibt (altes Level, neues Level) zurueck. */
    suspend fun completeCalmStation(station: Int): Pair<Int, Int> {
        val before = levelFor(store.data.first()[calmXpKey] ?: 0).first
        var after = before
        store.edit { p ->
            val t = today()
            val current = if (p[dateKey] == t) p[calmKey] ?: 0 else 0
            p[dateKey] = t
            p[calmKey] = (current + 1).coerceAtMost(STATIONS_PER_DAY)
            val xp = (p[calmXpKey] ?: 0) + station.coerceIn(1, STATIONS_PER_DAY)
            p[calmXpKey] = xp
            after = levelFor(xp).first
        }
        return before to after
    }

    companion object {
        const val STATIONS_PER_DAY = 10

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

/** Was man auf einem Level an innerer Ruhe dazugewonnen hat (die Helfer sind man selbst). */
internal class CalmAbilities(level: Int) {
    /** Sekunden, die Wellen/Verlockungen vorher angekuendigt werden (0 = keine Vorahnung) */
    val foresight: Float = if (level >= 2) 1.5f + (level - 2) * 0.25f else 0f
    /** Fehler, die pro Station abgefangen werden */
    val shields: Int = if (level >= 3) 1 + (level - 3) / 2 else 0
    /** zusaetzliche Toleranz im Atemtakt (Sekunden) */
    val calmBreath: Float = if (level >= 4) 0.15f + (level - 4) * 0.03f else 0f

    companion object {
        fun unlocked(level: Int): String = when {
            level == 2 -> "Vorahnung – du spürst, wenn eine Welle oder Verlockung kommt."
            level == 3 -> "Schild – ein Fehler pro Station wird von deiner Ruhe abgefangen."
            level == 4 -> "Ruhiger Atem – der Takt verzeiht dir etwas mehr."
            level % 2 == 1 -> "Ein weiteres Schild – deine Ruhe trägt dich weiter."
            else -> "Deine Vorahnung reicht weiter."
        }
    }
}
