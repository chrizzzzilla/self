package com.chrizzzzilla.self.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val ROUND_SECONDS = 60f
private const val LEAD_IN = 2.5f

/** Die 10 Stationen eines Tages – von ganz still bis Sturm. */
private val LEVEL_NAMES = arrayOf(
    "STILLER SEE", "SEE AM ABEND", "RUHIGER STRAND", "STRAND IM WIND", "BRANDUNG",
    "IM BOOT", "DÜNUNG", "AUFKOMMENDER WIND", "STURM", "IM HERZEN DES STURMS",
)

/** Wellen-/Windstaerke je Station */
private val WAVES = floatArrayOf(0f, 0.05f, 0.15f, 0.3f, 0.5f, 0.6f, 0.7f, 0.8f, 0.95f, 1f)

/**
 * Spiel „Ruhe“: einer leuchtenden Atemlinie folgen.
 *  Rechter Daumen halten = einatmen (das Licht steigt) · linker Daumen halten = ausatmen (es sinkt) ·
 *  nichts = Atem anhalten. Je naeher am Atemweg, desto heller wird die Welt.
 * Ab „Brandung“ schieben Wellen das Licht weg (Vorahnung und Schild helfen).
 */
@Composable
internal fun CalmGame(level: Int, abilities: CalmAbilities, onComplete: (passed: Boolean) -> Unit, onExit: () -> Unit) {
    val state = remember(level) { BreathState(level.coerceIn(1, 10), abilities) }
    val tick = remember { mutableFloatStateOf(0f) }
    val showResult = remember(level) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    BackHandler(onBack = onExit)

    LaunchedEffect(state) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                state.step(dt)
                tick.floatValue = state.t
                if (state.finished && state.finishT > 1.2f) showResult.value = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                val id = c.id.value
                                if (c.changedToDown()) {
                                    if (c.position.x >= size.width / 2f) state.right += id else state.left += id
                                } else if (c.changedToUp() || !c.pressed) {
                                    state.right -= id
                                    state.left -= id
                                }
                            }
                        }
                    }
                },
        ) {
            tick.floatValue
            drawBreath(state, measurer)
        }
        if (showResult.value) {
            ResultCard(
                passed = state.passed,
                percent = state.percent,
                goal = state.goalPercent,
                station = state.level,
                onContinue = { onComplete(state.passed) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(1.dp, AmbientColors.Light.copy(alpha = 0.16f), CircleShape)
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val c = AmbientColors.Light.copy(alpha = 0.8f)
                drawLine(c, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
            }
        }
    }
}

// ==========================================================================================
// Spiellogik
// ==========================================================================================

/** Abschnitt des Atemwegs: von y0 nach y1 zwischen t0 und t1. phase: 1 ein, 0 halten, -1 aus */
private class Seg(val t0: Float, val t1: Float, val y0: Float, val y1: Float, val phase: Int)

private class Push(val at: Float, val dir: Float) {
    var done = false
}

private class BreathState(val level: Int, val abilities: CalmAbilities) {
    val d = (level - 1) / 9f
    val waves = WAVES[level - 1]
    private val rnd = Random(level * 31 + System.nanoTime().toInt())
    var t = 0f
    var finished = false
    var finishT = 0f

    val left = mutableSetOf<Long>()
    val right = mutableSetOf<Long>()

    /** Lage des Lichts 0 (unten) … 1 (oben) */
    var y = 0.25f
    private var v = 0f
    /** wie nah man gerade am Atemweg ist (geglaettet) 0..1 */
    var close = 0.5f
    private var scoreAcc = 0f

    val tol = 0.17f - 0.06f * d + abilities.calmBreath * 0.2f
    private val segs = ArrayList<Seg>()
    private val pushes = ArrayList<Push>()
    var shieldsLeft = abilities.shields
    var shieldFlash = 0f
    private var pushVel = 0f
    var lightning = 0f
    private var nextFlash = 6f

    val trail = FloatArray(240) { 0.25f }
    var trailHead = 0
    private var trailAcc = 0f

    init {
        var yy = 0.25f
        segs += Seg(0f, LEAD_IN, yy, yy, 0)
        var tt = LEAD_IN
        while (tt < ROUND_SECONDS + 15f) {
            val top = (0.74f + (rnd.nextFloat() - 0.5f) * 0.24f * d).coerceIn(0.55f, 0.88f)
            val bottom = (0.24f + (rnd.nextFloat() - 0.5f) * 0.18f * d).coerceIn(0.12f, 0.36f)
            val inhale = (top - yy) / (0.12f + rnd.nextFloat() * 0.04f)
            segs += Seg(tt, tt + inhale, yy, top, 1); tt += inhale; yy = top
            val h1 = 0.8f + rnd.nextFloat() * (0.6f + d * 1.2f)
            segs += Seg(tt, tt + h1, yy, yy, 0); tt += h1
            val exhale = (yy - bottom) / (0.095f + rnd.nextFloat() * 0.035f)
            segs += Seg(tt, tt + exhale, yy, bottom, -1); tt += exhale; yy = bottom
            val h2 = 0.8f + rnd.nextFloat() * (0.5f + d * 1.0f)
            segs += Seg(tt, tt + h2, yy, yy, 0); tt += h2
        }
        if (waves >= 0.5f) {
            var p = 8f + rnd.nextFloat() * 4f
            while (p < ROUND_SECONDS - 4f) {
                pushes += Push(p, if (rnd.nextBoolean()) 1f else -1f)
                p += 9f - waves * 4f + rnd.nextFloat() * 4f
            }
        }
    }

    private fun seg(at: Float): Seg = segs.firstOrNull { at < it.t1 } ?: segs.last()

    fun target(at: Float): Float {
        val s = seg(at)
        if (s.t1 <= s.t0) return s.y1
        val f = ((at - s.t0) / (s.t1 - s.t0)).coerceIn(0f, 1f)
        val e = f * f * (3f - 2f * f)
        return s.y0 + (s.y1 - s.y0) * e
    }

    fun phase(at: Float): Int = seg(at).phase

    val percent: Int get() = (scoreAcc / (ROUND_SECONDS - LEAD_IN) * 100f).toInt().coerceIn(0, 100)
    val goalPercent: Int get() = (55f + d * 10f).toInt()
    val passed: Boolean get() = percent >= goalPercent

    /** naechste Welle (fuer die Vorahnung) */
    fun upcomingPush(): Push? = pushes.firstOrNull { !it.done && it.at > t }

    fun step(dt: Float) {
        t += dt
        lightning = (lightning - dt * 2.5f).coerceAtLeast(0f)
        shieldFlash = (shieldFlash - dt * 1.5f).coerceAtLeast(0f)
        if (level >= 9 && t > nextFlash) { lightning = 1f; nextFlash = t + 5f + rnd.nextFloat() * 7f }
        if (finished) { finishT += dt; return }

        val up = right.isNotEmpty()
        val down = left.isNotEmpty()
        val want = when {
            up && !down -> 0.14f
            down && !up -> -0.12f
            else -> 0f
        }
        v += (want - v) * min(1f, dt * 6f)
        for (p in pushes) {
            if (!p.done && t >= p.at) {
                p.done = true
                if (shieldsLeft > 0) { shieldsLeft--; shieldFlash = 1f } else pushVel += p.dir * 0.32f
            }
        }
        pushVel *= (1f - min(1f, dt * 2.2f))
        val sway = sin(t * 1.3f) * 0.012f * waves
        y = (y + (v + pushVel + sway) * dt).coerceIn(0.02f, 0.98f)

        val c = (1f - abs(y - target(t)) / tol).coerceIn(0f, 1f)
        close += (c - close) * min(1f, dt * 3f)
        if (t > LEAD_IN) scoreAcc += dt * c

        trailAcc += dt
        while (trailAcc >= 1f / 60f) {
            trailAcc -= 1f / 60f
            trailHead = (trailHead + 1) % trail.size
            trail[trailHead] = y
        }
        if (t >= ROUND_SECONDS) { finished = true; finishT = 0f }
    }
}

// ==========================================================================================
// Zeichnen (Licht-Stil)
// ==========================================================================================

private fun DrawScope.cGlow(c: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c, blendMode = BlendMode.Plus,
    )
}

private fun cHash(i: Int): Float {
    val v = sin(i * 12.9898f + 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

private fun DrawScope.drawBreath(s: BreathState, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val t = s.t
    val bright = 0.35f + 0.65f * s.close
    drawWorld(s, bright)

    // ---- Atemweg ----
    val px = w * 0.3f
    val perSec = w * 0.085f
    fun sy(y: Float) = h * 0.84f - y * h * 0.66f
    val tolPx = s.tol * h * 0.66f
    val band = Path()
    val bandLow = ArrayList<Offset>()
    val line = Path()
    var x = 0f
    var first = true
    while (x <= w) {
        val at = t + (x - px) / perSec
        val yy = sy(s.target(at))
        if (first) { line.moveTo(x, yy); band.moveTo(x, yy - tolPx) } else { line.lineTo(x, yy); band.lineTo(x, yy - tolPx) }
        bandLow += Offset(x, yy + tolPx)
        first = false
        x += 6f * density
    }
    for (i in bandLow.indices.reversed()) band.lineTo(bandLow[i].x, bandLow[i].y)
    band.close()
    drawPath(band, AmbientColors.Gold.copy(alpha = 0.05f + 0.05f * s.close), blendMode = BlendMode.Plus)
    drawPath(line, AmbientColors.Gold.copy(alpha = 0.18f), style = Stroke(10f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    drawPath(line, AmbientColors.Gold.copy(alpha = 0.85f), style = Stroke(1.8f * density, cap = StrokeCap.Round))
    // Vergangenes abdunkeln
    drawRect(Brush.horizontalGradient(listOf(Color(0xCC05060A), Color.Transparent), startX = 0f, endX = px), size = Size(px, h))

    // ---- eigene Spur ----
    val trail = Path()
    val n = s.trail.size
    for (k in 0 until n) {
        val idx = (s.trailHead - k + n) % n
        val tx = px - k / 60f * perSec
        if (tx < 0f) break
        val ty = sy(s.trail[idx])
        if (k == 0) trail.moveTo(tx, ty) else trail.lineTo(tx, ty)
    }
    drawPath(trail, AmbientColors.Arty.copy(alpha = 0.3f), style = Stroke(8f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    drawPath(trail, AmbientColors.ArtyText, style = Stroke(2f * density, cap = StrokeCap.Round))

    // ---- das Licht ----
    val me = Offset(px, sy(s.y))
    cGlow(me, h * (0.08f + 0.1f * s.close), lerp(AmbientColors.Arty, AmbientColors.Gold, s.close), 0.6f)
    drawCircle(Color(0xFFF4F0FF), radius = 6f * density, center = me)
    if (s.shieldFlash > 0f) {
        drawCircle(
            AmbientColors.ArtyText.copy(alpha = s.shieldFlash), radius = h * (0.05f + (1f - s.shieldFlash) * 0.06f), center = me,
            style = Stroke(2f * density), blendMode = BlendMode.Plus,
        )
    }

    // ---- Hinweiswort ----
    val ph = s.phase(t + 0.4f)
    val word = when { t < LEAD_IN -> "bereit …"; ph > 0 -> "einatmen"; ph < 0 -> "ausatmen"; else -> "halten" }
    val layout = measurer.measure(word, TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = 3.sp, color = AmbientColors.Light.copy(alpha = 0.85f)))
    drawText(layout, topLeft = Offset(px + 28f * density, me.y - layout.size.height - 18f * density))

    // ---- Vorahnung einer Welle ----
    s.upcomingPush()?.let { p ->
        val fs = s.abilities.foresight
        if (fs > 0f && p.at - t < fs) {
            val a = 1f - (p.at - t) / fs
            val wy = if (p.dir > 0) me.y + h * 0.12f else me.y - h * 0.12f
            drawArc(
                AmbientColors.ArtyText.copy(alpha = 0.7f * a), startAngle = if (p.dir > 0) 200f else 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(px - h * 0.06f, wy - h * 0.06f), size = Size(h * 0.12f, h * 0.12f),
                style = Stroke(2f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus,
            )
        }
    }

    // ---- Daumenfelder ----
    thumbZone(Offset(w * 0.1f, h * 0.88f), s.left.isNotEmpty(), down = true, label = "AUSATMEN", measurer = measurer)
    thumbZone(Offset(w * 0.9f, h * 0.88f), s.right.isNotEmpty(), down = false, label = "EINATMEN", measurer = measurer)

    // ---- Kopf: Station + Fortschritt ----
    val name = measurer.measure(
        "${s.level} · ${LEVEL_NAMES[s.level - 1]}",
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 4.sp, color = AmbientColors.Light),
    )
    drawText(name, topLeft = Offset(32f * density, 34f * density))
    val x0 = 32f * density
    val x1 = w * 0.4f
    val yb = 34f * density + name.size.height + 12f * density
    val prog = (t / ROUND_SECONDS).coerceIn(0f, 1f)
    drawLine(Color.White.copy(alpha = 0.16f), Offset(x0, yb), Offset(x1, yb), strokeWidth = 1.2f * density, cap = StrokeCap.Round)
    drawLine(AmbientColors.Gold.copy(alpha = 0.8f), Offset(x0, yb), Offset(x0 + (x1 - x0) * prog, yb), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
    val ruhe = measurer.measure(
        "Ruhe ${s.percent} % · Ziel ${s.goalPercent} %",
        TextStyle(fontFamily = Manrope, fontSize = 11.sp, letterSpacing = 1.sp, color = AmbientColors.Muted),
    )
    drawText(ruhe, topLeft = Offset(x0, yb + 8f * density))
}

private fun DrawScope.thumbZone(c: Offset, pressed: Boolean, down: Boolean, label: String, measurer: TextMeasurer) {
    val r = size.minDimension * 0.07f
    val col = if (down) AmbientColors.Gold else AmbientColors.ArtyText
    cGlow(c, r * (if (pressed) 2.2f else 1.4f), col, if (pressed) 0.45f else 0.15f)
    drawCircle(col.copy(alpha = if (pressed) 0.9f else 0.35f), radius = r, center = c, style = Stroke(1.4f * density))
    val dir = if (down) 1f else -1f
    val a = Path().apply {
        moveTo(c.x - r * 0.35f, c.y - dir * r * 0.15f)
        lineTo(c.x, c.y + dir * r * 0.25f)
        lineTo(c.x + r * 0.35f, c.y - dir * r * 0.15f)
    }
    drawPath(a, col.copy(alpha = if (pressed) 1f else 0.6f), style = Stroke(2f * density, cap = StrokeCap.Round))
    val l = measurer.measure(label, TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 3.sp, color = col.copy(alpha = 0.8f)))
    drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - r - l.size.height - 8f * density))
}

/** Umgebung je Station: dunkel, Silhouetten mit Lichtkanten, Wasser mit Lichtstreifen; ab Station 6 schaukelt der Horizont. */
private fun DrawScope.drawWorld(s: BreathState, bright: Float) {
    val w = size.width
    val h = size.height
    val t = s.t
    val lv = s.level
    val evening = lv == 2 || lv == 5
    val storm = lv >= 8
    val sky = when {
        storm -> listOf(Color(0xFF07080C), Color(0xFF121722), Color(0xFF1A1F2A))
        evening -> listOf(Color(0xFF07060C), Color(0xFF1E1426), Color(0xFF3A1E24))
        lv in 3..4 -> listOf(Color(0xFF05080C), Color(0xFF0E1A24), Color(0xFF1C2A30))
        else -> listOf(Color(0xFF04050A), Color(0xFF0C1020), Color(0xFF161A2C))
    }
    drawRect(Color(0xFF05070A))
    drawRect(Brush.verticalGradient(sky, startY = 0f, endY = h * 0.6f), size = Size(w, h * 0.6f))
    val tilt = if (lv >= 6) sin(t * 0.55f) * (2f + s.waves * 5f) else 0f
    rotate(tilt, pivot = Offset(w / 2f, h * 0.58f)) {
        val horizon = h * 0.58f
        val lightC = when { evening -> Color(0xFFFFA070); storm -> Color(0xFFB8C8E0); else -> Color(0xFFE8EEFF) }
        val lx = w * 0.72f
        val ly = h * (if (evening) 0.48f else 0.2f)
        if (!storm) {
            cGlow(Offset(lx, ly), h * 0.5f, lightC, 0.12f * bright)
            cGlow(Offset(lx, ly), h * 0.1f, lightC, 0.5f * bright)
            drawCircle(lightC.copy(alpha = 0.9f), radius = h * 0.02f, center = Offset(lx, ly))
            for (k in 0 until 50) {
                val tw = 0.5f + 0.5f * sin(t * (0.4f + cHash(k)) + k)
                drawCircle(Color.White.copy(alpha = 0.4f * tw * bright), radius = 1f * density, center = Offset(cHash(k * 3 + 1) * w, cHash(k * 7 + 2) * horizon * 0.8f))
            }
        } else {
            for (k in 0 until 6) {
                val cx = ((cHash(k + 5) + t * 0.01f * (1 + k % 3)) % 1.2f) * w - w * 0.1f
                drawOval(Color(0xFF1C2230).copy(alpha = 0.8f), topLeft = Offset(cx - w * 0.2f, h * (0.08f + k * 0.06f)), size = Size(w * 0.4f, h * 0.1f))
            }
        }
        // ferne Ufer (See)
        if (lv <= 2) {
            val ridge = Path()
            val body = Path()
            val n = 70
            for (i in 0..n) {
                val x = w * i / n
                val y = horizon - (sin(x / w * 4.1f + 0.6f) * 0.6f + sin(x / w * 9.3f) * 0.4f) * h * 0.045f - h * 0.02f
                if (i == 0) { ridge.moveTo(x, y); body.moveTo(x, y) } else { ridge.lineTo(x, y); body.lineTo(x, y) }
            }
            body.lineTo(w, horizon); body.lineTo(0f, horizon); body.close()
            drawPath(body, Color(0xFF0C0C12))
            drawPath(ridge, lightC.copy(alpha = 0.3f * bright), style = Stroke(1.2f * density), blendMode = BlendMode.Plus)
        }
        // Wasser
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF0A1018), Color(0xFF05070A)), startY = horizon, endY = h * 1.2f),
            topLeft = Offset(-w * 0.2f, horizon), size = Size(w * 1.4f, h),
        )
        drawLine(lightC.copy(alpha = 0.35f * bright), Offset(-w * 0.2f, horizon), Offset(w * 1.2f, horizon), strokeWidth = 1f * density, blendMode = BlendMode.Plus)
        // Lichtweg auf dem Wasser
        for (k in 0 until 26) {
            val f = k / 26f
            val yy = horizon + h * 0.42f * f * f + h * 0.01f
            val spread = w * (0.03f + f * 0.12f)
            val jitter = sin(t * (1.2f + s.waves * 2f) + k * 1.7f) * spread * 0.6f
            val len = spread * (0.5f + 0.5f * cHash(k + 9))
            drawLine(
                lightC.copy(alpha = (0.35f - f * 0.2f) * bright), Offset(lx + jitter - len / 2f, yy), Offset(lx + jitter + len / 2f, yy),
                strokeWidth = (1f + f * 1.5f) * density, blendMode = BlendMode.Plus,
            )
        }
        // Wellenkaemme
        if (s.waves > 0f) {
            val rows = 5 + (s.waves * 6).toInt()
            for (r in 0 until rows) {
                val f = (r + 1f) / (rows + 1f)
                val baseY = horizon + h * 0.42f * f
                val amp = h * 0.008f * (0.4f + s.waves * 2.5f) * (0.5f + f)
                val wave = Path()
                var x = -w * 0.2f
                var started = false
                while (x <= w * 1.2f) {
                    val yy = baseY + sin(x / w * (6f + r) - t * (0.8f + s.waves * 1.6f) + r) * amp
                    if (!started) { wave.moveTo(x, yy); started = true } else wave.lineTo(x, yy)
                    x += 10f * density
                }
                drawPath(wave, lightC.copy(alpha = (0.06f + 0.12f * s.waves) * bright), style = Stroke(1f * density), blendMode = BlendMode.Plus)
            }
        }
        // Strand (3–5)
        if (lv in 3..5) {
            val beach = Path().apply {
                moveTo(-w * 0.2f, h * 0.9f)
                quadraticTo(w * 0.4f, h * 0.84f, w * 1.2f, h * 0.95f)
                lineTo(w * 1.2f, h * 1.2f); lineTo(-w * 0.2f, h * 1.2f); close()
            }
            drawPath(beach, Color(0xFF15120E))
            val foamY = h * 0.885f + sin(t * (0.6f + s.waves)) * h * 0.01f
            drawLine(Color.White.copy(alpha = 0.25f * bright), Offset(-w * 0.2f, foamY + h * 0.01f), Offset(w * 1.2f, foamY + h * 0.03f), strokeWidth = 1.4f * density, blendMode = BlendMode.Plus)
        }
        // Bootsrand (6–10)
        if (lv >= 6) {
            val bow = Path().apply {
                moveTo(-w * 0.1f, h * 1.05f)
                quadraticTo(w * 0.5f, h * 0.86f, w * 1.1f, h * 1.05f)
                lineTo(w * 1.1f, h * 1.3f); lineTo(-w * 0.1f, h * 1.3f); close()
            }
            drawPath(bow, Color(0xFF120E0B))
            val rim = Path().apply { moveTo(-w * 0.1f, h * 1.05f); quadraticTo(w * 0.5f, h * 0.86f, w * 1.1f, h * 1.05f) }
            drawPath(rim, Color(0xFFC8B48C).copy(alpha = 0.4f * bright), style = Stroke(1.6f * density), blendMode = BlendMode.Plus)
        }
    }
    // Regen
    if (storm) {
        val n = 40 + (s.waves * 80).toInt()
        for (k in 0 until n) {
            val q = (t * (1.4f + cHash(k) * 0.6f) + cHash(k + 3)) % 1f
            val x = cHash(k + 11) * w * 1.2f - w * 0.1f - q * w * 0.08f
            val y = q * h * 1.1f - h * 0.05f
            drawLine(Color(0xFFB8C8E0).copy(alpha = 0.18f), Offset(x, y), Offset(x - w * 0.008f, y + h * 0.04f), strokeWidth = 1f * density, blendMode = BlendMode.Plus)
        }
    }
    // Gischt / Staub
    for (k in 0 until 40) {
        val px = ((cHash(k + 100) + t * (0.004f + cHash(k + 200) * 0.01f) * (1f + s.waves * 3f)) % 1f) * w
        val py = (cHash(k + 150) * 0.95f + sin(t * 0.3f + k) * 0.02f) * h
        val a = (0.08f + cHash(k + 250) * 0.2f) * (0.6f + 0.4f * sin(t * 0.8f + k)) * bright
        drawCircle(Color(0xFFE8F0FF).copy(alpha = a), radius = (0.8f + cHash(k + 300) * 1.4f) * density, center = Offset(px, py), blendMode = BlendMode.Plus)
    }
    if (s.lightning > 0f) drawRect(Color(0xFFDDE6FF).copy(alpha = 0.25f * s.lightning), blendMode = BlendMode.Plus)
    drawRect(Color(0xFF020306).copy(alpha = (1f - bright) * 0.55f))
}

/** Abschluss einer Runde: Ruhe in Prozent, Ziel, geschafft oder nicht – dann weiter (naechste Station oder nochmal). */
@Composable
private fun ResultCard(passed: Boolean, percent: Int, goal: Int, station: Int, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
    }
    val shape = RoundedCornerShape(30.dp)
    val accent = if (passed) AmbientColors.ArtyText else AmbientColors.Gold
    Column(
        modifier
            .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); scaleX = 0.85f + 0.15f * a.value; scaleY = 0.85f + 0.15f * a.value }
            .clip(shape)
            .background(Color(0xF0101116))
            .border(1.dp, accent.copy(alpha = 0.8f), shape)
            .padding(horizontal = 44.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (passed) "GESCHAFFT" else "NOCH NICHT GANZ",
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 6.sp, color = accent),
        )
        Spacer(Modifier.size(14.dp))
        Text("$percent %", style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 54.sp, color = AmbientColors.Light))
        Text("Ruhe · Ziel $goal %", style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, letterSpacing = 1.sp, color = AmbientColors.Muted))
        Spacer(Modifier.size(12.dp))
        Text(
            if (passed) "+$station Punkte · weiter zur nächsten Station" else "Atme noch einmal mit – gleiche Station.",
            style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = AmbientColors.Light),
        )
        Spacer(Modifier.size(22.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1E2A55))
                .border(1.dp, AmbientColors.Arty, RoundedCornerShape(50))
                .clickable(onClick = onContinue)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) {
            Text(
                if (passed) "WEITER" else "NOCHMAL",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 4.sp, color = AmbientColors.Light),
            )
        }
    }
}
