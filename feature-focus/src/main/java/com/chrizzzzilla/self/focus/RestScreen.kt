package com.chrizzzzilla.self.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import com.chrizzzzilla.self.focus.audio.NatureSound
import com.chrizzzzilla.self.focus.scene.AmbientScene
import kotlinx.coroutines.delay

private val rHead = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 6.sp, color = AmbientColors.Light)
private val rText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 15.sp, color = AmbientColors.Light)

/**
 * Rest: zum Einschlafen. Das gewaehlte Ambiente laeuft mit Schlaf-Timer und wird am Ende langsam leiser.
 * Optional ein Atemlicht (4 ein · 7 halten · 8 aus). Bildschirm darf aus – der Timer laeuft weiter.
 */
@Composable
fun RestScreen(modifier: Modifier = Modifier, onOverlayChanged: (Boolean) -> Unit = {}) {
    val mix by FocusPlayer.mix.collectAsState()
    val playing by FocusPlayer.isPlaying.collectAsState()
    val sleepEnd by FocusPlayer.sleepEnd.collectAsState()
    var minutes by rememberSaveable { mutableStateOf(30) }
    var breath by rememberSaveable { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(panel) { onOverlayChanged(panel) }
    LaunchedEffect(Unit) {
        while (true) { now.longValue = System.currentTimeMillis(); delay(500) }
    }
    val running = sleepEnd != null
    val left = ((sleepEnd ?: 0L) - now.longValue).coerceAtLeast(0L)
    val dim by animateFloatAsState(
        if (running) (0.45f + 0.4f * (1f - (left / (minutes * 60_000f)).coerceIn(0f, 1f))) else 0.35f,
        tween(1500), label = "dunkel",
    )

    Box(modifier.fillMaxSize()) {
        AmbientScene(
            mix = mix,
            animate = !panel,
            rain = NatureSound.RAIN in mix.nature && playing,
            pulse = { if (playing) FocusPlayer.pulse else 0f },
            level = { if (playing) FocusPlayer.level * 3f else 0f },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color(0xFF020306).copy(alpha = dim)))

        if (running && breath) BreathLight(Modifier.align(Alignment.Center))

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("REST", style = rHead.copy(color = AmbientColors.ArtyText))
            Spacer(Modifier.height(10.dp))
            if (running) {
                val m = left / 60_000L
                val s = (left / 1000L) % 60
                Text(
                    "%d:%02d".format(m, s),
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraLight, fontSize = 64.sp, color = AmbientColors.Light),
                )
                Text("Es wird von selbst leiser. Du kannst den Bildschirm ausschalten.", textAlign = TextAlign.Center, style = rText.copy(fontSize = 13.sp, color = AmbientColors.Muted))
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("+15 MIN", false) { FocusPlayer.extendSleepTimer(15) }
                    Pill(if (breath) "ATEMLICHT AN" else "ATEMLICHT AUS", breath) { breath = !breath }
                    Pill("STOP", false) { FocusPlayer.cancelSleepTimer() }
                }
            } else {
                Text("Ambiente mit Schlaf-Timer", style = rText)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { m -> Pill("$m MIN", minutes == m) { minutes = m } }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("AMBIENTE WÄHLEN", false) { panel = true }
                    Pill(if (breath) "ATEMLICHT AN" else "ATEMLICHT AUS", breath) { breath = !breath }
                }
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1E2A55))
                        .border(1.dp, AmbientColors.Arty, RoundedCornerShape(50))
                        .clickable { FocusPlayer.startSleepTimer(minutes) }
                        .padding(horizontal = 34.dp, vertical = 14.dp),
                ) {
                    Text("EINSCHLAFEN", style = rHead.copy(fontSize = 12.sp, letterSpacing = 4.sp))
                }
            }
        }
    }
    if (panel) AmbientPanelDialog(onDismiss = { panel = false })
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0x446888EC) else Color(0x33101116))
            .border(1.dp, if (selected) AmbientColors.Arty else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 2.sp, color = AmbientColors.Light))
    }
}

/** 4-7-8: vier Sekunden ein (waechst), sieben halten, acht aus (wird kleiner). */
@Composable
private fun BreathLight(modifier: Modifier) {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (it - start) / 1e9f }
    }
    val c = t.floatValue % 19f
    val (size, word) = when {
        c < 4f -> (c / 4f) to "einatmen"
        c < 11f -> 1f to "halten"
        else -> (1f - (c - 11f) / 8f) to "ausatmen"
    }
    val e = size * size * (3f - 2f * size)
    Box(modifier.size(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f * (0.3f + 0.6f * e)
            drawCircle(
                Brush.radialGradient(listOf(AmbientColors.Arty.copy(alpha = 0.35f), Color.Transparent), center = center, radius = r * 1.6f),
                radius = r * 1.6f, center = center, blendMode = BlendMode.Plus,
            )
            drawCircle(AmbientColors.ArtyText.copy(alpha = 0.55f), radius = r, center = center, style = Stroke(1.5f * density))
            drawCircle(Color(0xFFF4F0FF).copy(alpha = 0.8f), radius = 3f * density, center = center + Offset(0f, 0f))
        }
        Text(word, style = rText.copy(fontSize = 18.sp, letterSpacing = 3.sp, color = AmbientColors.Light.copy(alpha = 0.8f)))
    }
}
