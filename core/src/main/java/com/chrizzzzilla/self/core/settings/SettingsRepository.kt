package com.chrizzzzilla.self.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Farbmodus fuer App-Oberflaeche UND Reader. */
enum class AppTheme { SYSTEM, LIGHT, DARK, SEPIA }

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    /** Schriftgroesse im Reader, 1.0 = 100 %. */
    val fontScale: Double = 1.0,
    /** Lesefilter vor dem Hintergrundbild: 0 = Bild voll sichtbar … 1 = Bild ganz verdeckt. */
    val readingFilter: Float = 0.8f,
) {
    companion object {
        const val FONT_SCALE_MIN = 0.75
        const val FONT_SCALE_MAX = 2.5
        const val FONT_SCALE_STEP = 0.125
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "einstellungen")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val fontScale = doublePreferencesKey("font_scale")
        val readingFilter = floatPreferencesKey("reading_filter")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.theme]
                ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
                ?: AppTheme.SYSTEM,
            fontScale = prefs[Keys.fontScale] ?: 1.0,
            readingFilter = prefs[Keys.readingFilter] ?: 0.8f,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(theme: AppTheme) {
        store.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setReadingFilter(value: Float) {
        store.edit { it[Keys.readingFilter] = value.coerceIn(0.3f, 1f) }
    }

    suspend fun setFontScale(scale: Double) {
        val clamped = scale.coerceIn(AppSettings.FONT_SCALE_MIN, AppSettings.FONT_SCALE_MAX)
        store.edit { it[Keys.fontScale] = clamped }
    }
}
