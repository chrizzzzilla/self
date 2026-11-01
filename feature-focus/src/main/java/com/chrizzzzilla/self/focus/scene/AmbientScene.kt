package com.chrizzzzilla.self.focus.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.chrizzzzilla.self.focus.audio.AmbientMix
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Aktuelle Uhrzeit des Geraets als 0..24. */
fun deviceHour(): Float {
    val now = LocalTime.now()
    return now.hour + now.minute / 60f + now.second / 3600f
}

/**
 * Das gewaehlte Bild als Hintergrund (beim Lesen, im Fokusmodus, als Vorschau).
 * Tageszeit: fest eingestellt oder – mit „Echtzeit“ – die echte Uhrzeit, sodass beim Lesen
 * die Sonne langsam auf- und untergeht.
 */
@Composable
fun AmbientScene(
    mix: AmbientMix,
    rain: Boolean,
    pulse: () -> Float,
    level: () -> Float,
    modifier: Modifier = Modifier,
    sceneOverride: Scene? = null,
    animate: Boolean = true,
) {
    val clock = remember { mutableFloatStateOf(deviceHour()) }
    if (mix.realTime) {
        LaunchedEffect(Unit) {
            while (true) {
                clock.floatValue = deviceHour()
                delay(5_000)
            }
        }
    }
    val hour: () -> Float = if (mix.realTime) ({ clock.floatValue }) else ({ mix.hour })
    when (val scene = sceneOverride ?: mix.scene) {
        Scene.CITY -> CityScene(
            rain = rain, pulse = pulse, level = level, modifier = modifier,
            hour = hour, season = mix.season, animate = animate,
        )
        else -> LandscapeScene(scene = scene, hour = hour, season = mix.season, rain = rain, modifier = modifier, animate = animate)
    }
}
