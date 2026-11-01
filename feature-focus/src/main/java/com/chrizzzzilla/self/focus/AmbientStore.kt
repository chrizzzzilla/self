package com.chrizzzzilla.self.focus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chrizzzzilla.self.focus.audio.AmbientMix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ambientStore: DataStore<Preferences> by preferencesDataStore(name = "ambiente")

/** Speichert die aktuelle Mischung und die Presets 1–5. */
internal class AmbientStore(context: Context) {

    private val store = context.applicationContext.ambientStore
    private val currentKey = stringPreferencesKey("current")
    private fun presetKey(slot: Int) = stringPreferencesKey("preset_$slot")

    suspend fun loadCurrent(): AmbientMix? = AmbientMix.fromJson(store.data.first()[currentKey])

    suspend fun saveCurrent(mix: AmbientMix) {
        store.edit { it[currentKey] = mix.toJson() }
    }

    /** Presets 1–5 (null = leer). */
    val presets: Flow<List<AmbientMix?>> = store.data.map { prefs ->
        (1..PRESET_COUNT).map { AmbientMix.fromJson(prefs[presetKey(it)]) }
    }

    suspend fun savePreset(slot: Int, mix: AmbientMix) {
        store.edit { it[presetKey(slot)] = mix.toJson() }
    }

    companion object {
        const val PRESET_COUNT = 5
    }
}
