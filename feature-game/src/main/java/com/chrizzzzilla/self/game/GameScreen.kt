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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Die drei unteren Spiele (noch ohne Inhalt). Jedes hat 10 Minuten pro Tag. */
private enum class TrainingGame(@StringRes val label: Int, val color: Color) {
    CALM(R.string.game_calm, Color(0xFF6888EC)),
    AMBITION(R.string.game_ambition, Color(0xFFC8B48C)),
    HUMILITY(R.string.game_humility, Color(0xFF68C4A8)),
}


/**
 * Path – Bildschirmaufbau:
 *  oben das grosse Feld „Me“ (das eigentliche Spiel),
 *  darunter Ruhe / Ehrgeiz / Demut, die sich beim Spielen wie Licht fuellen,
 *  alles mittig vor Arty (aufgehende Sonne am unteren Rand).
 * Sind die 10 Minuten eines Spiels fuer heute voll, wird sein Rand rot und es laesst sich nicht oeffnen.
 */
@Composable
fun GameScreen(modifier: Modifier = Modifier, onOverlayChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { PathStore(context) }
    val calm by store.calm.collectAsStateWithLifecycle(initialValue = CalmProgress(0, 0, 1, 0f, 0))
    var playing by remember { mutableStateOf(false) }
    var workshop by remember { mutableStateOf(false) }
    val workshopStore = remember { WorkshopStore(context) }
    var workshopInfo by remember { mutableStateOf(0f to 0) }
    LaunchedEffect(workshop) { if (!workshop) workshopInfo = workshopStore.summary() }
    var garden by remember { mutableStateOf(false) }
    val plantStore = remember { PlantStore(context) }
    var plantInfo by remember { mutableStateOf(Triple(0f, 1, 0)) }
    LaunchedEffect(garden) { if (!garden) plantInfo = plantStore.summary() }
    var levelUp by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(playing, workshop, garden) { onOverlayChanged(playing || workshop || garden) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val sun = if (compact) 380.dp else 560.dp

        ArtySun(
            onClick = {},
            diameter = sun,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val gap = 22.dp
        val small = min(min(maxHeight * 0.26f, (maxWidth - 80.dp - gap * 2) / 3f), 260.dp).coerceAtLeast(96.dp)
        val big = min(min(maxHeight - small - gap - 96.dp, small * 3 + gap * 2), 420.dp).coerceAtLeast(150.dp)

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MeTile(size = big)
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                TrainingGame.entries.forEachIndexed { i, g ->
                    if (g == TrainingGame.CALM) {
                        TrainingTile(
                            game = g, size = small, level = calm.level, fill = calm.toNext,
                            done = calm.secondsToday >= PathStore.CALM_DAILY_SECONDS,
                            index = i,
                            onOpen = { playing = true },
                        )
                    } else if (g == TrainingGame.AMBITION) {
                        val (secs, found) = workshopInfo
                        TrainingTile(
                            game = g, size = small, level = 1 + found / 3, fill = (found % 3) / 3f,
                            done = secs >= DAILY_SECONDS, index = i, onOpen = { workshop = true },
                        )
                    } else {
                        val (pSecs, pStage, pGifts) = plantInfo
                        TrainingTile(
                            game = g, size = small, level = pStage, fill = (pGifts % 5) / 5f,
                            done = pSecs >= PLANT_SECONDS, index = i, onOpen = { garden = true },
                        )
                    }
                }
            }
        }

        // Aufstieg: kurz zeigen, was an innerer Ruhe dazugekommen ist
        if (!playing) levelUp?.let { lv ->
            LevelUpBanner(lv, CalmAbilities.unlocked(lv), Modifier.align(Alignment.Center)) { levelUp = null }
        }
    }

    if (playing) {
        CalmGame(
            startMinute = calm.checkpoint,
            secondsLeft = PathStore.CALM_DAILY_SECONDS - calm.secondsToday,
            abilities = CalmAbilities(calm.level),
            onMinuteDone = { minute ->
                scope.launch {
                    val (before, after) = store.completeCalmMinute(minute)
                    if (after > before) levelUp = after
                }
            },
            onTime = { sec -> scope.launch { store.addCalmSeconds(sec) } },
            onExit = { playing = false },
        )
        levelUp?.let { lv ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LevelUpBanner(lv, CalmAbilities.unlocked(lv), Modifier) { levelUp = null }
            }
        }
    }

    if (workshop) WorkshopGame(onExit = { workshop = false })
    if (garden) PlantGame(onExit = { garden = false })
}

@Composable
private fun LevelUpBanner(level: Int, text: String, modifier: Modifier, onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(level) {
        a.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        kotlinx.coroutines.delay(4200)
        a.animateTo(0f, androidx.compose.animation.core.tween(700))
        onDone()
    }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); scaleX = 0.8f + 0.2f * a.value; scaleY = 0.8f + 0.2f * a.value }
            .clip(shape)
            .background(Color(0xF0141519))
            .border(1.dp, AmbientColors.Arty, shape)
            .padding(horizontal = 36.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "RUHE · LEVEL $level",
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 6.sp, color = AmbientColors.ArtyText),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 16.sp, color = AmbientColors.Light),
        )
    }
}

/** Grosses Feld „Me“: hier entsteht spaeter das eigentliche Spiel. Drei ruhige Lichtringe = die drei Werte. */
@Composable
private fun MeTile(size: Dp) {
    val time = rememberTime()
    val shape = RoundedCornerShape(32.dp)
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
            TrainingGame.entries.forEachIndexed { k, g ->
                val path = Path()
                val n = 160
                for (i in 0..n) {
                    val th = i / n.toFloat() * 2f * PI.toFloat()
                    val r = base * (0.8f + k * 0.16f) + sin(th * (3 + k) + t * (0.3f + k * 0.08f)) * base * 0.03f
                    val x = c.x + cos(th) * r
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
        Text(
            stringResource(R.string.game_me).uppercase(),
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 8.sp, color = AmbientColors.Light),
        )
    }
}

/**
 * Feld eines unteren Spiels: fuellt sich von unten mit Licht (welliger Rand).
 * Tageslimit erreicht → roter Rand, nicht mehr antippbar.
 */
@Composable
private fun TrainingTile(
    game: TrainingGame,
    size: Dp,
    level: Int,
    fill: Float,
    done: Boolean,
    index: Int,
    onOpen: () -> Unit,
) {
    val time = rememberTime()
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .size(size)
            .clip(shape)
            .background(Color(0xE617181D))
            .border(
                if (done) 2.dp else 1.dp,
                if (done) Color(0xFFE05A5A) else game.color.copy(alpha = 0.35f),
                shape,
            )
            .clickable(
                enabled = !done,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                scope.launch {
                    bounce.snapTo(0.93f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    onOpen()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Lichtfuellung (mind. ein schmaler Schimmer am Boden)
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val level01 = (0.06f + 0.94f * fill.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            val top = this.size.height * (1f - level01)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(0f, h)
                val n = 40
                for (i in 0..n) {
                    val x = w * i / n
                    val y = top + sin(x / w * 2f * PI.toFloat() * 1.5f + t * 1.4f + index) * 4f * density
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(game.color.copy(alpha = 0.45f), game.color.copy(alpha = 0.12f)),
                    startY = top,
                    endY = h,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(game.label).uppercase(),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 3.sp, color = AmbientColors.Light),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.game_level, level).uppercase(),
                style = TextStyle(fontFamily = Manrope, fontSize = 11.sp, letterSpacing = 2.sp, color = game.color),
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
