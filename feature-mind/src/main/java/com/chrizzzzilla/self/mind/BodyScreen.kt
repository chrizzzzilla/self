package com.chrizzzzilla.self.mind

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.ArtySun
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class Move { BREATHE, UP, CIRCLE, SWAY, UPDOWN, STEP, BOX, WATER }

private class Step(val title: String, val how: String, val seconds: Int, val move: Move)

/** Etwa sieben Minuten: ankommen, lockern, kraeftigen, Kreislauf, Atem, Wasser – dann ein Wort fuer den Tag. */
private val ROUTINE = listOf(
    Step("Ankommen", "Aufrecht stehen, Füße hüftbreit. Drei tiefe Atemzüge.", 30, Move.BREATHE),
    Step("Strecken", "Arme weit nach oben, ganz lang machen – dann locker lassen.", 30, Move.UP),
    Step("Schultern kreisen", "Langsam und groß nach hinten kreisen.", 40, Move.CIRCLE),
    Step("Nacken", "Kopf sanft zur Seite neigen, links und rechts. Nicht ziehen.", 40, Move.SWAY),
    Step("Seitbeuge", "Ein Arm über den Kopf, zur Seite neigen. Dann wechseln.", 40, Move.SWAY),
    Step("Hüfte kreisen", "Hände an die Hüfte, große, ruhige Kreise.", 40, Move.CIRCLE),
    Step("Kniebeugen", "Langsam runter, langsam hoch. Etwa zehn Mal.", 45, Move.UPDOWN),
    Step("Auf der Stelle gehen", "Knie hoch, Arme schwingen mit. Wach werden.", 45, Move.STEP),
    Step("Energieatmung", "4 ein · 4 halten · 4 aus · 4 halten.", 48, Move.BOX),
    Step("Ein Glas Wasser", "Trink jetzt ein Glas Wasser. Dein Körper hat die Nacht gefastet.", 20, Move.WATER),
)

private val WORDS = listOf("Ruhe", "Mut", "Freude", "Geduld", "Klarheit", "Kraft", "Leichtigkeit", "Dankbarkeit", "Fokus", "Güte")

private enum class Phase { IDLE, RUN, WORD, DONE }

/** Body: kurze Morgenroutine, die fit fuer den Tag macht – mit Lichtbewegung statt Figur. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyScreen(modifier: Modifier = Modifier, onOverlayChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { MindStore(context) }
    val morning by store.morning.collectAsStateWithLifecycle(initialValue = MorningState("", 0, "", ""))
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var step by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var word by remember { mutableStateOf("") }
    val elapsed = remember { mutableFloatStateOf(0f) }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(phase) { onOverlayChanged(phase != Phase.IDLE) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = start
        while (true) withFrameNanos { now ->
            val dt = (now - last) / 1e9f
            last = now
            time.floatValue = (now - start) / 1e9f
            if (phase == Phase.RUN && !paused) {
                elapsed.floatValue += dt
                if (elapsed.floatValue >= ROUTINE[step].seconds) {
                    elapsed.floatValue = 0f
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (step < ROUTINE.lastIndex) step++ else phase = Phase.WORD
                }
            }
        }
    }
    val total = ROUTINE.sumOf { it.seconds }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        when (phase) {
            Phase.IDLE -> {
                ArtySun(onClick = { step = 0; elapsed.floatValue = 0f; phase = Phase.RUN }, diameter = if (compact) 380.dp else 560.dp, modifier = Modifier.align(Alignment.BottomCenter))
                Column(
                    Modifier.align(Alignment.Center).padding(bottom = 80.dp).widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("BODY · MORGEN", style = mHead.copy(color = AmbientColors.Gold))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (morning.doneToday) "Heute schon gemacht – schön.\nDein Wort: ${morning.todayWord ?: "–"}" else "Gut ${total / 60} Minuten, die dich wach machen.",
                        textAlign = TextAlign.Center, style = mText.copy(fontSize = 20.sp, lineHeight = 30.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (morning.streak > 0) Text(
                        if (morning.streak == 1) "1 Morgen" else "${morning.streak} Morgen in Folge",
                        style = mText.copy(fontSize = 13.sp, color = AmbientColors.Muted),
                    )
                    Spacer(Modifier.height(18.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ROUTINE.forEach { s ->
                            Text(
                                s.title, style = mText.copy(fontSize = 11.sp, color = AmbientColors.Muted),
                                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    MindButton(if (morning.doneToday) "NOCH EINMAL" else "LOS GEHT'S") { step = 0; elapsed.floatValue = 0f; paused = false; phase = Phase.RUN }
                }
            }
            Phase.RUN -> {
                val s = ROUTINE[step]
                val frac = (elapsed.floatValue / s.seconds).coerceIn(0f, 1f)
                Column(Modifier.align(Alignment.Center).widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${step + 1} / ${ROUTINE.size}", style = mHead.copy(fontSize = 10.sp, color = AmbientColors.Muted))
                    Spacer(Modifier.height(8.dp))
                    Text(s.title.uppercase(), style = mHead.copy(fontSize = 16.sp, color = AmbientColors.Gold))
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.size(if (compact) 240.dp else 320.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawMove(s.move, elapsed.floatValue, time.floatValue)
                            val r = size.minDimension / 2f - 6f * density
                            drawArc(Color.White.copy(alpha = 0.1f), -90f, 360f, false, topLeft = center - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(3f * density))
                            drawArc(AmbientColors.Gold, -90f, 360f * frac, false, topLeft = center - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(3f * density, cap = StrokeCap.Round))
                        }
                        Text(
                            "${(s.seconds - elapsed.floatValue).toInt().coerceAtLeast(0)}",
                            style = mText.copy(fontSize = 13.sp, color = AmbientColors.Muted),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(s.how, textAlign = TextAlign.Center, style = mText.copy(fontSize = 19.sp, lineHeight = 28.sp))
                    Spacer(Modifier.height(10.dp))
                    ROUTINE.getOrNull(step + 1)?.let { Text("Danach: ${it.title}", style = mText.copy(fontSize = 12.sp, color = AmbientColors.Muted)) }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MindGhostButton("BEENDEN") { phase = Phase.IDLE }
                        MindGhostButton(if (paused) "WEITER" else "PAUSE") { paused = !paused }
                        MindButton("NÄCHSTE") {
                            elapsed.floatValue = 0f
                            if (step < ROUTINE.lastIndex) step++ else phase = Phase.WORD
                        }
                    }
                }
            }
            Phase.WORD -> Column(Modifier.align(Alignment.Center).widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DEIN WORT FÜR HEUTE", style = mHead.copy(color = AmbientColors.Gold))
                Spacer(Modifier.height(14.dp))
                Text("Was soll dich heute begleiten?\nAm Abend frage ich dich danach.", textAlign = TextAlign.Center, style = mText.copy(fontSize = 18.sp, lineHeight = 26.sp))
                Spacer(Modifier.height(22.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WORDS.forEach { w ->
                        val sel = w == word
                        Text(
                            w, style = mText.copy(fontSize = 15.sp, color = if (sel) AmbientColors.Light else AmbientColors.Light.copy(alpha = 0.8f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Color(0x446888EC) else Color(0x14FFFFFF))
                                .border(1.dp, if (sel) AmbientColors.Arty else Color(0x22FFFFFF), RoundedCornerShape(50))
                                .clickable { word = w }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
                MindButton(if (word.isEmpty()) "OHNE WORT WEITER" else "DAS NEHME ICH") {
                    scope.launch { store.morningDone(word) }
                    phase = Phase.DONE
                }
            }
            Phase.DONE -> Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(160.dp)) { drawSunrise(time.floatValue) }
                Spacer(Modifier.height(16.dp))
                Text("GUTEN MORGEN", style = mHead.copy(color = AmbientColors.Gold))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (word.isNotEmpty()) "Dein Wort für heute: $word" else "Du bist bereit für den Tag.",
                    textAlign = TextAlign.Center, style = mText.copy(fontSize = 20.sp),
                )
                Spacer(Modifier.height(24.dp))
                MindButton("FERTIG") { phase = Phase.IDLE; word = "" }
            }
        }
    }
}

private fun DrawScope.mGlow(c: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c, blendMode = BlendMode.Plus,
    )
}

/** Die Bewegung als Licht: ein Punkt, der vormacht, was der Koerper tut. */
private fun DrawScope.drawMove(move: Move, e: Float, t: Float) {
    val c = center
    val r = size.minDimension * 0.32f
    val gold = AmbientColors.Gold
    val blue = AmbientColors.ArtyText
    fun dot(p: Offset, col: Color = gold) {
        mGlow(p, r * 0.35f, col, 0.55f)
        drawCircle(Color(0xFFFFF4DC), radius = 5f * density, center = p)
    }
    when (move) {
        Move.BREATHE -> {
            val k = (sin(e / 10f * 2f * PI.toFloat() - PI.toFloat() / 2f) + 1f) / 2f
            mGlow(c, r * (0.6f + 0.6f * k), blue, 0.35f)
            drawCircle(blue.copy(alpha = 0.6f), radius = r * (0.3f + 0.5f * k), center = c, style = Stroke(1.5f * density))
        }
        Move.UP -> {
            val k = (sin(e / 6f * 2f * PI.toFloat()) + 1f) / 2f
            val top = c - Offset(0f, r * (0.3f + 0.7f * k))
            drawLine(gold.copy(alpha = 0.5f), c + Offset(0f, r * 0.8f), top, strokeWidth = 2f * density, cap = StrokeCap.Round)
            dot(top)
        }
        Move.CIRCLE -> {
            drawCircle(Color.White.copy(alpha = 0.12f), radius = r * 0.8f, center = c, style = Stroke(1.2f * density))
            val a = e / 4f * 2f * PI.toFloat()
            dot(c + Offset(cos(a), sin(a)) * (r * 0.8f))
        }
        Move.SWAY -> {
            val a = sin(e / 6f * 2f * PI.toFloat()) * 0.7f
            val pivot = c + Offset(0f, r * 0.8f)
            val tip = pivot + Offset(sin(a), -cos(a)) * (r * 1.4f)
            drawLine(gold.copy(alpha = 0.45f), pivot, tip, strokeWidth = 2f * density, cap = StrokeCap.Round)
            dot(tip)
        }
        Move.UPDOWN -> {
            val k = (cos(e / 4.5f * 2f * PI.toFloat()) + 1f) / 2f
            val p = c + Offset(0f, r * (0.7f - 1.4f * k))
            drawLine(Color.White.copy(alpha = 0.12f), c + Offset(0f, -r * 0.8f), c + Offset(0f, r * 0.8f), strokeWidth = 1.2f * density)
            dot(p)
        }
        Move.STEP -> {
            val ph = e * 2.2f * PI.toFloat()
            dot(c + Offset(-r * 0.35f, r * 0.3f - abs(sin(ph)) * r * 0.6f))
            dot(c + Offset(r * 0.35f, r * 0.3f - abs(cos(ph)) * r * 0.6f), blue)
        }
        Move.BOX -> {
            val side = r * 1.3f
            val tl = c - Offset(side / 2f, side / 2f)
            drawRect(Color.White.copy(alpha = 0.12f), topLeft = tl, size = Size(side, side), style = Stroke(1.2f * density))
            val q = (e % 16f) / 4f
            val i = q.toInt()
            val f = q - i
            val corners = listOf(tl + Offset(0f, side), tl, tl + Offset(side, 0f), tl + Offset(side, side))
            val a = corners[i % 4]
            val b = corners[(i + 1) % 4]
            dot(a + (b - a) * f, if (i % 2 == 0) blue else gold)
        }
        Move.WATER -> {
            val gw = r * 0.7f
            val gh = r * 1.1f
            val tl = c - Offset(gw / 2f, gh / 2f)
            drawRect(blue.copy(alpha = 0.6f), topLeft = tl, size = Size(gw, gh), style = Stroke(1.5f * density))
            val lv = (e / 20f).coerceIn(0f, 1f)
            drawRect(Color(0xFF9FD8FF).copy(alpha = 0.35f), topLeft = Offset(tl.x, tl.y + gh * (1f - lv)), size = Size(gw, gh * lv), blendMode = BlendMode.Plus)
        }
    }
}

private fun DrawScope.drawSunrise(t: Float) {
    val c = Offset(size.width / 2f, size.height * 0.7f)
    val r = size.minDimension * 0.25f
    mGlow(c, r * 3f, AmbientColors.Gold, 0.35f + 0.1f * sin(t * 1.2f))
    drawArc(Color(0xFFFFE6B0), 180f, 180f, true, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2))
    for (k in 0 until 7) {
        val a = PI.toFloat() + k / 6f * PI.toFloat()
        drawLine(AmbientColors.Gold.copy(alpha = 0.6f), c + Offset(cos(a), sin(a)) * (r * 1.3f), c + Offset(cos(a), sin(a)) * (r * 1.7f), strokeWidth = 2f * density, cap = StrokeCap.Round)
    }
    drawLine(AmbientColors.Light.copy(alpha = 0.4f), Offset(0f, c.y), Offset(size.width, c.y), strokeWidth = 1.2f * density)
}
