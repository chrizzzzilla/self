package com.chrizzzzilla.self.game

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.ArtySun
import com.chrizzzzilla.self.core.design.Manrope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Die drei unteren Spiele (noch ohne Inhalt). Jedes hat 10 Minuten pro Tag. */
private enum class TrainingGame(@StringRes val label: Int, val color: Color) {
    CALM(R.string.game_calm, Color(0xFF6888EC)),
    AMBITION(R.string.game_ambition, Color(0xFFC8B48C)),
    GRATITUDE(R.string.game_gratitude, Color(0xFF68C4A8)),
}

private const val DAILY_MINUTES_SMALL = 10
private const val DAILY_MINUTES_WORLD = 30

/**
 * Spiel-Bereich – Bildschirmaufbau:
 *  oben das grosse Feld fuer das eigentliche Spiel (Welt/RPG, 30 Min. am Tag),
 *  darunter drei Felder, die sich beim Spielen wie Licht fuellen (je 10 Min. am Tag),
 *  ganz unten Arty als aufgehende Sonne.
 */
@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val sun = if (compact) 380.dp else 560.dp
        val usableHeight = maxHeight - sun / 2 - 24.dp
        val small = min(min(usableHeight * 0.30f, (maxWidth - 64.dp) / 3.4f), 210.dp).coerceAtLeast(84.dp)
        val big = min(min(usableHeight - small - 64.dp, maxWidth - 48.dp), 520.dp).coerceAtLeast(140.dp)

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WorldTile(size = big)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TrainingGame.entries.forEachIndexed { i, g ->
                    TrainingTile(game = g, size = small, level = 0, minutesToday = 0, index = i)
                }
            }
        }

        ArtySun(
            onClick = {},
            diameter = sun,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Grosses Feld oben: hier entsteht spaeter die Welt. Jetzt: ruhige Lichtringe als Platzhalter. */
@Composable
private fun WorldTile(size: Dp) {
    val time = rememberTime()
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE6181920), Color(0xF00F1014))))
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val base = this.size.minDimension * 0.3f
            // drei Ringe = die drei Werte, die spaeter von unten gespeist werden
            TrainingGame.entries.forEachIndexed { k, g ->
                val path = Path()
                val n = 160
                for (i in 0..n) {
                    val th = i / n.toFloat() * 2f * PI.toFloat()
                    val r = base * (0.8f + k * 0.16f) + sin(th * (3 + k) + t * (0.3f + k * 0.08f)) * base * 0.03f
                    val x = c.x + kotlin.math.cos(th) * r
                    val y = c.y + sin(th) * r * 0.92f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, g.color.copy(alpha = 0.32f), style = Stroke(width = 1.2f * density))
            }
            drawCircle(
                Brush.radialGradient(listOf(AmbientColors.Gold.copy(alpha = 0.18f), Color.Transparent), center = c, radius = base * 0.7f),
                radius = base * 0.7f, center = c,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.game_world).uppercase(),
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 6.sp, color = AmbientColors.Light),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.game_world_hint),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 12.sp, color = AmbientColors.Muted),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.game_today, 0, DAILY_MINUTES_WORLD),
                style = TextStyle(fontFamily = Manrope, fontSize = 10.sp, letterSpacing = 1.5.sp, color = AmbientColors.Muted.copy(alpha = 0.8f)),
            )
        }
    }
}

/**
 * Feld eines unteren Spiels: fuellt sich von unten mit Licht (welliger Rand).
 * Voll = Level-up (spaeter mit Bounce + Partikel). Jetzt: Antippen federt nur.
 */
@Composable
private fun TrainingTile(game: TrainingGame, size: Dp, level: Int, minutesToday: Int, index: Int) {
    val time = rememberTime()
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val fill = minutesToday / DAILY_MINUTES_SMALL.toFloat()
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .size(size)
            .clip(shape)
            .background(Color(0xE617181D))
            .border(1.dp, game.color.copy(alpha = 0.35f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch {
                    bounce.snapTo(0.92f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Lichtfuellung (mind. ein schmaler Schimmer am Boden, damit man das Prinzip sieht)
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val level01 = (0.06f + 0.94f * fill).coerceIn(0f, 1f)
            val top = this.size.height * (1f - level01)
            val path = Path().apply {
                moveTo(0f, this@Canvas.size.height)
                val n = 40
                for (i in 0..n) {
                    val x = this@Canvas.size.width * i / n
                    val y = top + sin(x / this@Canvas.size.width * 2f * PI.toFloat() * 1.5f + t * 1.4f + index) * 4f * density
                    lineTo(x, y)
                }
                lineTo(this@Canvas.size.width, this@Canvas.size.height)
                close()
            }
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(game.color.copy(alpha = 0.45f), game.color.copy(alpha = 0.12f)),
                    startY = top,
                    endY = this.size.height,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            Text(
                stringResource(game.label).uppercase(),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 2.5.sp, color = AmbientColors.Light),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.game_level, level),
                style = TextStyle(fontFamily = Manrope, fontSize = 10.sp, letterSpacing = 1.5.sp, color = game.color),
            )
            Text(
                stringResource(R.string.game_today, minutesToday, DAILY_MINUTES_SMALL),
                style = TextStyle(fontFamily = Manrope, fontSize = 9.sp, color = AmbientColors.Muted),
            )
        }
    }
}

@Composable
private fun rememberTime(): androidx.compose.runtime.MutableFloatState {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (it - start) / 1e9f }
    }
    return t
}
