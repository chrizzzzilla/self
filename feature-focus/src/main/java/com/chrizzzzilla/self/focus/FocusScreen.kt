package com.chrizzzzilla.self.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrizzzzilla.self.focus.audio.LofiParams
import com.chrizzzzilla.self.focus.scene.WindowScene
import kotlin.math.roundToInt

/**
 * Fokus-Bereich: animierte Fensterszene + generative Lofi-Musik.
 * Tippen auf das Bild blendet die Regler ein/aus. Solange der Bereich offen ist,
 * bleibt der Bildschirm an.
 */
@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    val playing by FocusPlayer.isPlaying.collectAsStateWithLifecycle()
    val params by FocusPlayer.params.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(modifier.fillMaxSize()) {
        WindowScene(
            rain = params.rain,
            pulse = { if (playing) FocusPlayer.pulse else 0f },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { controlsVisible = !controlsVisible },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            FocusControls(
                playing = playing,
                params = params,
                onToggle = { FocusPlayer.toggle() },
                onChange = { FocusPlayer.update(it) },
            )
        }
    }
}

@Composable
private fun FocusControls(
    playing: Boolean,
    params: LofiParams,
    onToggle: () -> Unit,
    onChange: (LofiParams) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 3.dp,
        modifier = Modifier.widthIn(max = 560.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painterResource(if (playing) R.drawable.focus_ic_pause else R.drawable.focus_ic_play),
                        contentDescription = stringResource(if (playing) R.string.focus_pause else R.string.focus_play),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.focus_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.focus_rain), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = params.rain,
                    onCheckedChange = { onChange(params.copy(rain = it)) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            LabeledSlider(
                label = stringResource(R.string.focus_energy),
                low = stringResource(R.string.focus_energy_low),
                high = stringResource(R.string.focus_energy_high),
                value = params.energy,
                onValueChange = { onChange(params.copy(energy = it)) },
            )
            LabeledSlider(
                label = stringResource(R.string.focus_brightness),
                low = stringResource(R.string.focus_brightness_low),
                high = stringResource(R.string.focus_brightness_high),
                value = params.brightness,
                onValueChange = { onChange(params.copy(brightness = it)) },
            )
            LabeledSlider(
                label = stringResource(R.string.focus_tempo),
                low = stringResource(R.string.focus_tempo_value, LofiParams.TEMPO_MIN.roundToInt()),
                high = stringResource(R.string.focus_tempo_value, LofiParams.TEMPO_MAX.roundToInt()),
                value = (params.tempo - LofiParams.TEMPO_MIN) / (LofiParams.TEMPO_MAX - LofiParams.TEMPO_MIN),
                onValueChange = {
                    onChange(params.copy(tempo = LofiParams.TEMPO_MIN + it * (LofiParams.TEMPO_MAX - LofiParams.TEMPO_MIN)))
                },
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    low: String,
    high: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                low,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp),
            )
            Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange, modifier = Modifier.weight(1f))
            Text(
                high,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(56.dp)
                    .padding(start = 8.dp),
            )
        }
    }
}
