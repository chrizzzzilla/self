package com.chrizzzzilla.self.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrizzzzilla.self.core.design.AmbientBackground
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import com.chrizzzzilla.self.focus.audio.Ambience
import com.chrizzzzilla.self.focus.audio.AmbientMix
import com.chrizzzzilla.self.focus.audio.NatureSound
import com.chrizzzzilla.self.focus.audio.Percussion
import com.chrizzzzilla.self.focus.scene.AmbientScene
import com.chrizzzzilla.self.focus.scene.Scene
import com.chrizzzzilla.self.focus.scene.Season
import com.chrizzzzilla.self.focus.scene.deviceHour
import kotlin.math.PI
import kotlin.math.sin

// ------------------------------------------------------------------------------------------
// Symbole + Farben der Klaenge
// ------------------------------------------------------------------------------------------

private class Look(val emoji: String, val color: Color)

private fun look(a: Ambience): Look = when (a) {
    Ambience.LOFI_KEYS -> Look("🎹", Color(0xFF8E7CF0))
    Ambience.WARM_PADS -> Look("🌅", Color(0xFFF07A4A))
    Ambience.NIGHT_CITY -> Look("🌃", Color(0xFF4A6FE0))
    Ambience.DEEP_SPACE -> Look("🌌", Color(0xFF6A4FC8))
    Ambience.BELLS -> Look("🔔", Color(0xFFE8C25A))
    Ambience.MORNING -> Look("☀️", Color(0xFFF5B84A))
    Ambience.DUSK -> Look("🌆", Color(0xFFC0607A))
    Ambience.CRYSTAL -> Look("💎", Color(0xFF5EC8E0))
    Ambience.PIANO -> Look("🎼", Color(0xFFA8A0C8))
    Ambience.FLOATING -> Look("🫧", Color(0xFF7ED0C0))
}

private fun look(n: NatureSound): Look = when (n) {
    NatureSound.RAIN -> Look("🌧️", Color(0xFF5E8CC8))
    NatureSound.WIND -> Look("🌬️", Color(0xFF9FB4C8))
    NatureSound.SEA -> Look("🌊", Color(0xFF3E9AC8))
    NatureSound.BIRDS -> Look("🐦", Color(0xFF8CC060))
    NatureSound.CRICKETS -> Look("🦗", Color(0xFF6AA84F))
    NatureSound.STREAM -> Look("💧", Color(0xFF4FB0D0))
    NatureSound.FIRE -> Look("🔥", Color(0xFFE86A3A))
    NatureSound.THUNDER -> Look("⛈️", Color(0xFF7A70A8))
}

private fun look(p: Percussion): Look = when (p) {
    Percussion.BOOM_BAP -> Look("🥁", Color(0xFFD08A5A))
    Percussion.LAID_BACK -> Look("🛋️", Color(0xFFB08870))
    Percussion.SHAKER -> Look("🎶", Color(0xFFD0B060))
    Percussion.BRUSHES -> Look("🎷", Color(0xFFC09060))
    Percussion.HEARTBEAT -> Look("💓", Color(0xFFE06A7A))
    Percussion.HAND_DRUMS -> Look("🪘", Color(0xFFC8784A))
    Percussion.CLOCKWORK -> Look("⏱️", Color(0xFFA0A8B8))
    Percussion.TRIP_HOP -> Look("🎧", Color(0xFF7A6AC0))
    Percussion.MINIMAL -> Look("◽", Color(0xFF9AA0A8))
    Percussion.RAIN_DRUM -> Look("🌦️", Color(0xFF6A98C0))
}

private val labelStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 3.sp, color = AmbientColors.Light)
private val smallStyle = TextStyle(fontFamily = Manrope, fontSize = 10.sp, letterSpacing = 1.sp, color = AmbientColors.Muted)

// ------------------------------------------------------------------------------------------
// Vollbild-Dialog
// ------------------------------------------------------------------------------------------

/**
 * „Ambiente“: Klang und Bild in einem Vollbild-Panel.
 * Links Ambiente / Naturgeraeusche / Percussion (mit senkrechten Lautstaerke-Reglern),
 * rechts die Bilder mit Jahreszeit, Tageszeit und Echtzeit.
 * [onFocusMode]: optional „Nur Ambiente“ (Buch ausblenden).
 */
@Composable
fun AmbientPanelDialog(onDismiss: () -> Unit, onFocusMode: (() -> Unit)? = null) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        AmbientPanel(onClose = onDismiss, onFocusMode = onFocusMode)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmbientPanel(onClose: () -> Unit, onFocusMode: (() -> Unit)? = null) {
    val playing by FocusPlayer.isPlaying.collectAsStateWithLifecycle()
    val mix by FocusPlayer.mix.collectAsStateWithLifecycle()
    val presets by FocusPlayer.presets.collectAsStateWithLifecycle()
    val active by FocusPlayer.activePreset.collectAsStateWithLifecycle()
    val update: (AmbientMix) -> Unit = { FocusPlayer.update(it) }

    Box(Modifier.fillMaxSize().background(AmbientColors.Ground)) {
        AmbientBackground(Modifier.fillMaxSize(), threads = false)
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            val wide = maxWidth > 760.dp
            Column(Modifier.fillMaxSize()) {
                // Kopf: Schliessen · Visualizer · Play
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(16.dp)) {
                            drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
                            drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
                        }
                    }
                    Visualizer(playing, mix, Modifier.weight(1f).height(56.dp).padding(horizontal = 20.dp))
                    FilledIconButton(
                        onClick = { FocusPlayer.toggle() },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1E2A55), contentColor = AmbientColors.Light),
                    ) {
                        Icon(
                            painterResource(if (playing) R.drawable.focus_ic_pause else R.drawable.focus_ic_play),
                            contentDescription = if (playing) "Pause" else "Abspielen",
                        )
                    }
                }
                // Presets
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PRESETS", style = labelStyle)
                    Spacer(Modifier.width(6.dp))
                    for (slot in 1..AmbientStore.PRESET_COUNT) {
                        val filled = presets.getOrNull(slot - 1) != null
                        Pill(text = "$slot", selected = active == slot, enabled = filled) { FocusPlayer.loadPreset(slot) }
                    }
                    Spacer(Modifier.width(6.dp))
                    Pill(text = "PRESET SPEICHERN", selected = false, enabled = true, wide = true) {
                        val slot = active ?: ((presets.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0) + 1)
                        FocusPlayer.savePreset(slot)
                    }
                    if (onFocusMode != null) {
                        Spacer(Modifier.weight(1f))
                        Pill(text = "NUR AMBIENTE", selected = false, enabled = true, wide = true) { onFocusMode() }
                    }
                }

                val left: @Composable (Modifier) -> Unit = { m ->
                    Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        SoundSection("AMBIENTE", mix.ambienceVolume, { update(mix.copy(ambienceVolume = it)) }) {
                            Ambience.entries.forEach { a ->
                                val l = look(a)
                                SoundTile(l.emoji, a.label, l.color, mix.ambience == a) {
                                    update(mix.copy(ambience = if (mix.ambience == a) null else a))
                                }
                            }
                        }
                        SoundSection("NATURGERÄUSCHE", mix.natureVolume, { update(mix.copy(natureVolume = it)) }) {
                            NatureSound.entries.forEach { n ->
                                val l = look(n)
                                val on = n in mix.nature
                                SoundTile(l.emoji, n.label, l.color, on) {
                                    update(mix.copy(nature = if (on) mix.nature - n else mix.nature + n))
                                }
                            }
                        }
                        SoundSection("PERCUSSION", mix.percussionVolume, { update(mix.copy(percussionVolume = it)) }) {
                            Percussion.entries.forEach { p ->
                                val l = look(p)
                                SoundTile(l.emoji, p.label, l.color, mix.percussion == p) {
                                    update(mix.copy(percussion = if (mix.percussion == p) null else p))
                                }
                            }
                        }
                        LineSlider("TEMPO · ${mix.percussionBpm.toInt()} BPM", "langsam", "schnell", mix.percussionSpeed) { update(mix.copy(percussionSpeed = it)) }
                        LineSlider("KLANG DER PERCUSSION", "dunkel", "hell", mix.percussionTone) { update(mix.copy(percussionTone = it)) }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                val right: @Composable (Modifier) -> Unit = { m ->
                    Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("BILD", style = labelStyle)
                        Scene.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { sc ->
                                    ScenePreview(sc, mix, selected = mix.scene == sc, modifier = Modifier.weight(1f)) {
                                        update(mix.copy(scene = sc))
                                    }
                                }
                            }
                        }
                        Text("JAHRESZEIT", style = labelStyle, modifier = Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Season.entries.forEach { s ->
                                Pill(text = s.label.uppercase(), selected = mix.season == s, enabled = true, wide = true) { update(mix.copy(season = s)) }
                            }
                        }
                        val shownHour = if (mix.realTime) deviceHour() else mix.hour
                        val hh = shownHour.toInt() % 24
                        val mm = ((shownHour - shownHour.toInt()) * 60).toInt()
                        LineSlider(
                            "TAGESZEIT · %02d:%02d".format(hh, mm), "0 Uhr", "24 Uhr", shownHour / 24f,
                            enabled = !mix.realTime,
                        ) { update(mix.copy(hour = it * 24f)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("ECHTZEIT", style = labelStyle)
                                Text("Die Sonne folgt der Uhrzeit, während du liest", style = smallStyle)
                            }
                            Switch(
                                checked = mix.realTime,
                                onCheckedChange = { update(mix.copy(realTime = it, hour = if (it) deviceHour() else mix.hour)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = AmbientColors.Arty),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        left(Modifier.weight(1f).fillMaxHeight())
                        right(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        left(Modifier)
                        right(Modifier)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Bausteine
// ------------------------------------------------------------------------------------------

@Composable
private fun Pill(text: String, selected: Boolean, enabled: Boolean, wide: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(36.dp)
            .then(if (wide) Modifier else Modifier.width(36.dp))
            .clip(shape)
            .background(if (selected) Color(0xFF1E2A55) else Color(0x33FFFFFF).copy(alpha = if (enabled) 0.08f else 0.03f))
            .border(1.dp, if (selected) AmbientColors.Arty else AmbientColors.Light.copy(alpha = if (enabled) 0.18f else 0.06f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (wide) 16.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = labelStyle.copy(fontSize = 10.sp, letterSpacing = 2.sp, color = if (enabled) AmbientColors.Light else AmbientColors.Muted.copy(alpha = 0.5f)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundSection(title: String, volume: Float, onVolume: (Float) -> Unit, items: @Composable () -> Unit) {
    Column {
        Text(title, style = labelStyle, modifier = Modifier.padding(bottom = 10.dp))
        Row {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { items() }
            Spacer(Modifier.width(14.dp))
            VerticalVolume(volume, onVolume, Modifier.width(34.dp).height(176.dp))
        }
    }
}

@Composable
private fun SoundTile(emoji: String, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .width(82.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(shape)
                .background(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = if (selected) 0.75f else 0.28f), color.copy(alpha = if (selected) 0.25f else 0.06f)),
                    ),
                )
                .border(if (selected) 1.5.dp else 1.dp, if (selected) color else AmbientColors.Light.copy(alpha = 0.10f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = TextStyle(fontFamily = Manrope, fontSize = 10.sp, color = if (selected) AmbientColors.Light else AmbientColors.Muted),
        )
    }
}

/** Senkrechter Lautstaerke-Regler: hoch = lauter. */
@Composable
private fun VerticalVolume(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val v = value.coerceIn(0f, 1f)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { pos -> onChange((1f - pos.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onChange((1f - change.position.y / size.height).coerceIn(0f, 1f)) }
            },
    ) {
        val trackW = size.width * 0.32f
        val left = (size.width - trackW) / 2f
        val r = androidx.compose.ui.geometry.CornerRadius(trackW / 2f)
        drawRoundRect(Color(0x22FFFFFF), topLeft = Offset(left, 0f), size = Size(trackW, size.height), cornerRadius = r)
        val fillTop = size.height * (1f - v)
        drawRoundRect(
            Brush.verticalGradient(listOf(AmbientColors.ArtyText, AmbientColors.Arty.copy(alpha = 0.6f)), startY = fillTop, endY = size.height),
            topLeft = Offset(left, fillTop), size = Size(trackW, size.height - fillTop), cornerRadius = r,
        )
        drawCircle(AmbientColors.Arty.copy(alpha = 0.35f), radius = size.width * 0.5f, center = Offset(size.width / 2f, fillTop), blendMode = BlendMode.Plus)
        drawCircle(AmbientColors.Light, radius = size.width * 0.22f, center = Offset(size.width / 2f, fillTop))
    }
}

@Composable
private fun LineSlider(
    title: String,
    low: String,
    high: String,
    value: Float,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(title, style = labelStyle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(low, style = smallStyle, modifier = Modifier.width(52.dp))
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = AmbientColors.Light, activeTrackColor = AmbientColors.Arty, inactiveTrackColor = Color(0x33FFFFFF)),
            )
            Text(high, style = smallStyle, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
        }
    }
}

@Composable
private fun ScenePreview(scene: Scene, mix: AmbientMix, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) AmbientColors.Arty else AmbientColors.Light.copy(alpha = 0.12f), shape)
                .clickable(onClick = onClick),
        ) {
            AmbientScene(
                mix = mix,
                rain = NatureSound.RAIN in mix.nature,
                pulse = { 0f },
                level = { 0f },
                sceneOverride = scene,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            scene.label.uppercase(),
            style = labelStyle.copy(fontSize = 10.sp, color = if (selected) AmbientColors.ArtyText else AmbientColors.Light),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Audio-Visualizer als eine Linie: still = gerade Linie; Ambiente = weiche Wellen;
 * Naturgeraeusche = feines Flirren; Percussion = kurzes Zittern (Saegezahn) im Takt.
 * Die Ausschlaege folgen den Lautstaerken.
 */
@Composable
private fun Visualizer(playing: Boolean, mix: AmbientMix, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    val a = remember { mutableFloatStateOf(0f) }
    val n = remember { mutableFloatStateOf(0f) }
    val p = remember { mutableFloatStateOf(0f) }
    val k = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = start
        while (true) withFrameNanos { now ->
            val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            time.floatValue = (now - start) / 1e9f
            val f = (dt * 6f).coerceAtMost(1f)
            a.floatValue += (FocusPlayer.levelAmbience - a.floatValue) * f
            n.floatValue += (FocusPlayer.levelNature - n.floatValue) * f
            p.floatValue += (FocusPlayer.levelPercussion - p.floatValue) * f
            k.floatValue += (FocusPlayer.pulse - k.floatValue) * (dt * 18f).coerceAtMost(1f)
        }
    }
    Canvas(modifier) {
        val t = time.floatValue
        val w = size.width
        val mid = size.height / 2f
        val amb = (a.floatValue * 2.2f).coerceIn(0f, 1f) * (if (mix.ambience != null) 1f else 0f)
        val nat = (n.floatValue * 2.5f).coerceIn(0f, 1f)
        val per = (p.floatValue * 2f).coerceIn(0f, 1f) * (0.35f + 0.65f * k.floatValue)
        val path = Path()
        val steps = 220
        for (i in 0..steps) {
            val x = w * i / steps
            val u = i / steps.toFloat()
            val taper = sin(u * PI.toFloat())
            var y = sin(u * 9f + t * 1.4f) * 0.55f + sin(u * 17f - t * 0.9f) * 0.3f
            y *= amb * size.height * 0.4f
            y += sin(u * 120f + t * 11f) * nat * size.height * 0.07f
            val saw = ((u * 38f + t * 7f) % 1f) * 2f - 1f
            y += saw * per * size.height * 0.28f
            val yy = mid + y * taper
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        val brush = Brush.horizontalGradient(listOf(AmbientColors.Arty.copy(alpha = 0.2f), AmbientColors.ArtyText, AmbientColors.Gold, AmbientColors.Arty.copy(alpha = 0.2f)))
        if (playing) {
            drawPath(path, brush, alpha = 0.25f, style = Stroke(width = 6f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        }
        drawPath(path, brush, alpha = if (playing) 1f else 0.4f, style = Stroke(width = 1.6f * density, cap = StrokeCap.Round))
    }
}

/** Fuer Aufrufer ohne eigenen Zustand: kleiner Wrapper fuer den Dialog. */
@Suppress("unused")
private fun dpOf(v: Int): Dp = v.dp
