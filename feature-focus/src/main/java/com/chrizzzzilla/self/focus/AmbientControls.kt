package com.chrizzzzilla.self.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrizzzzilla.self.focus.audio.Ambience
import com.chrizzzzilla.self.focus.audio.AmbientMix
import com.chrizzzzilla.self.focus.audio.NatureSound
import com.chrizzzzilla.self.focus.audio.Percussion

/**
 * Klang-Mixer: Play/Pause, Presets 1–5, Ambiente (eins), Naturgeraeusche (mehrere),
 * Percussion (eins) – je mit Lautstaerke, dazu Tempo und Hell/Dunkel.
 * Gedacht fuer ein BottomSheet im Reader.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AmbientControls(modifier: Modifier = Modifier) {
    val playing by FocusPlayer.isPlaying.collectAsStateWithLifecycle()
    val mix by FocusPlayer.mix.collectAsStateWithLifecycle()
    val presets by FocusPlayer.presets.collectAsStateWithLifecycle()
    val active by FocusPlayer.activePreset.collectAsStateWithLifecycle()
    val update: (AmbientMix) -> Unit = { FocusPlayer.update(it) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Kopf: Titel + Play/Pause
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ambient_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledIconButton(onClick = { FocusPlayer.toggle() }, modifier = Modifier.size(52.dp)) {
                Icon(
                    painterResource(if (playing) R.drawable.focus_ic_pause else R.drawable.focus_ic_play),
                    contentDescription = stringResource(if (playing) R.string.focus_pause else R.string.focus_play),
                )
            }
        }

        // Presets
        SectionTitle(stringResource(R.string.ambient_presets))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (slot in 1..AmbientStore.PRESET_COUNT) {
                val filled = presets.getOrNull(slot - 1) != null
                FilterChip(
                    selected = active == slot,
                    enabled = filled || active == slot,
                    onClick = { FocusPlayer.loadPreset(slot) },
                    label = { Text("$slot") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { FocusPlayer.savePreset(active ?: firstFreeSlot(presets)) }) {
                Text(
                    stringResource(
                        R.string.ambient_save_in,
                        active ?: firstFreeSlot(presets),
                    ),
                )
            }
        }
        Text(
            stringResource(R.string.ambient_presets_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (slot in 1..AmbientStore.PRESET_COUNT) {
                    if (slot == active) continue
                    OutlinedButton(onClick = { FocusPlayer.savePreset(slot) }) {
                        Text(stringResource(R.string.ambient_save_as, slot))
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // Ambiente
        SectionTitle(stringResource(R.string.ambient_ambience))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = mix.ambience == null, onClick = { update(mix.copy(ambience = null)) }, label = { Text(stringResource(R.string.ambient_off)) })
            Ambience.entries.forEach { a ->
                FilterChip(selected = mix.ambience == a, onClick = { update(mix.copy(ambience = a)) }, label = { Text(a.label) })
            }
        }
        LabeledSlider(stringResource(R.string.ambient_volume), mix.ambienceVolume) { update(mix.copy(ambienceVolume = it)) }
        LabeledSlider(
            stringResource(R.string.ambient_tone),
            mix.ambienceTone,
            low = stringResource(R.string.ambient_dark),
            high = stringResource(R.string.ambient_bright),
        ) { update(mix.copy(ambienceTone = it)) }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // Natur
        SectionTitle(stringResource(R.string.ambient_nature))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NatureSound.entries.forEach { n ->
                val on = n in mix.nature
                FilterChip(
                    selected = on,
                    onClick = { update(mix.copy(nature = if (on) mix.nature - n else mix.nature + n)) },
                    label = { Text(n.label) },
                )
            }
        }
        LabeledSlider(stringResource(R.string.ambient_volume), mix.natureVolume) { update(mix.copy(natureVolume = it)) }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // Percussion
        SectionTitle(stringResource(R.string.ambient_percussion))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = mix.percussion == null, onClick = { update(mix.copy(percussion = null)) }, label = { Text(stringResource(R.string.ambient_off)) })
            Percussion.entries.forEach { p ->
                FilterChip(selected = mix.percussion == p, onClick = { update(mix.copy(percussion = p)) }, label = { Text(p.label) })
            }
        }
        LabeledSlider(stringResource(R.string.ambient_volume), mix.percussionVolume) { update(mix.copy(percussionVolume = it)) }
        LabeledSlider(
            stringResource(R.string.ambient_speed, mix.percussionBpm.toInt()),
            mix.percussionSpeed,
            low = stringResource(R.string.ambient_slow),
            high = stringResource(R.string.ambient_fast),
        ) { update(mix.copy(percussionSpeed = it)) }
        LabeledSlider(
            stringResource(R.string.ambient_tone),
            mix.percussionTone,
            low = stringResource(R.string.ambient_dark),
            high = stringResource(R.string.ambient_bright),
        ) { update(mix.copy(percussionTone = it)) }
        Spacer(Modifier.height(8.dp))
    }
}

private fun firstFreeSlot(presets: List<AmbientMix?>): Int =
    (presets.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0) + 1

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    low: String? = null,
    high: String? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (low != null) {
                Text(low, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
            }
            Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, modifier = Modifier.weight(1f))
            if (high != null) {
                Text(
                    high,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(52.dp)
                        .padding(start = 8.dp),
                )
            }
        }
    }
}
