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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val MINUTE = 60f

/** Die 10 Minuten eines Durchgangs – von ganz still bis Sturm. */
private val LEVEL_NAMES = arrayOf(
    "STILLER SEE", "SEE AM ABEND", "RUHIGER STRAND", "STRAND IM WIND", "BRANDUNG",
    "IM BOOT", "DÜNUNG", "AUFKOMMENDER WIND", "STURM", "IM HERZEN DES STURMS",
)

/** Wellen-/Windstaerke je Minute */
private val WAVES = floatArrayOf(0f, 0.05f, 0.15f, 0.3f, 0.5f, 0.6f, 0.7f, 0.8f, 0.95f, 1f)

private val RED = Color(0xFFFF5A5A)
private val GREEN = Color(0xFF7FF0A0)

/**
 * Spiel „Ruhe“: ein Licht gleitet durch die Welt.
 *  Linker Daumen halten = steigen, loslassen = sanft sinken.
 *  Rechter Daumen halten = Schild (verbraucht Schildenergie) – damit kommt man durch Rotes.
 *  Rotes ausweichen, Gruenes sammeln (fuellt das Schild).
 * Ein Durchgang dauert 10 Minuten, jede Minute wird es schwerer und ist ein Checkpoint.
 * Getroffen → zurueck an den Anfang der Minute.
 */
@Composable
internal fun CalmGame(
    startMinute: Int,
    secondsLeft: Int,
    abilities: CalmAbilities,
    onMinuteDone: (minute: Int) -> Unit,
    onTime: (seconds: Int) -> Unit,
    onExit: () -> Unit,
) {
    val state = remember { FlightState(startMinute.coerceIn(0, 9), secondsLeft.toFloat(), abilities.shieldMax, abilities.magnet) }
    val tick = remember { mutableFloatStateOf(0f) }
    val ended = remember { mutableStateOf<String?>(null) }
    val measurer = rememberTextMeasurer()
    val minuteDone by rememberUpdatedState(onMinuteDone)
    val timeCb by rememberUpdatedState(onTime)
    LaunchedEffect(abilities.shieldMax) { state.shieldMax = abilities.shieldMax; state.magnet = abilities.magnet }

    fun leave() {
        timeCb(state.takeSeconds())
        onExit()
    }
    BackHandler { leave() }

    LaunchedEffect(state) {
        var last = 0L
        var report = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                state.step(dt)
                tick.floatValue = state.t
                state.completed?.let { m -> state.completed = null; minuteDone(m) }
                report += dt
                if (report > 5f) { report = 0f; timeCb(state.takeSeconds()) }
                if (state.end != null && ended.value == null) {
                    timeCb(state.takeSeconds())
                    ended.value = state.end
                }
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
            drawFlight(state, measurer)
        }
        ended.value?.let { text ->
            EndCard(text, state, Modifier.align(Alignment.Center)) { leave() }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(1.dp, AmbientColors.Light.copy(alpha = 0.16f), CircleShape)
                .clickable { leave() },
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

/** Ein Ding in der Welt. x in Bildbreiten (0 = links), y 0 (unten) … 1 (oben). */
private class Obj(var x: Float, var y: Float, val r: Float, val red: Boolean, val vx: Float = 0f, val wobble: Float = 0f, val phase: Float = 0f, val vy: Float = 0f) {
    var gone = 0f
    val baseY = y
}

private class FlightState(startMinute: Int, var secondsLeft: Float, var shieldMax: Float, var magnet: Float) {
    var minute = startMinute
    private val startedAt = startMinute
    var tm = 0f
    var t = 0f
    val level: Int get() = minute + 1
    val waves: Float get() = WAVES[minute.coerceIn(0, 9)]
    var lightning = 0f
    private var nextFlash = 5f
    private var rnd = Random(System.nanoTime())

    val left = mutableSetOf<Long>()
    val right = mutableSetOf<Long>()

    var y = 0.5f
    private var vy = 0f
    var energy = shieldMax
    var shielding = false
    val objs = ArrayList<Obj>()
    var aspect = 1.6f

    var dying = 0f
    var banner = 0f
    var greens = 0
    var hits = 0
    var completed: Int? = null
    var end: String? = null
    private var played = 0f

    private var nextRed = 1.5f
    private var nextGreen = 2.5f
    private var nextGate = 8f
    private var nextRain = 0f
    var gust = 0f

    val trail = FloatArray(200) { 0.5f }
    var trailHead = 0
    private var trailAcc = 0f
    private var prevY = 0.5f

    val minutesDone: Int get() = minute - startedAt

    /** gespielte, noch nicht gemeldete Sekunden */
    fun takeSeconds(): Int {
        val s = played.toInt()
        played -= s
        return s
    }

    private fun resetMinute() {
        tm = 0f
        objs.clear()
        y = 0.5f; vy = 0f; prevY = 0.5f
        trail.fill(0.5f)
        energy = shieldMax
        nextRed = 2f; nextGreen = 2.5f; nextGate = 7f; nextRain = 3f
        rnd = Random(System.nanoTime())
    }

    private val speed: Float get() = 0.2f + 0.025f * minute

    fun step(dt: Float) {
        t += dt
        lightning = (lightning - dt * 2.5f).coerceAtLeast(0f)
        banner = (banner - dt).coerceAtLeast(0f)
        if (minute >= 8 && t > nextFlash) { lightning = 1f; nextFlash = t + 5f + rnd.nextFloat() * 7f }
        if (end != null) return
        played += dt
        secondsLeft -= dt
        if (secondsLeft <= 0f) { end = "Für heute genug – dein Licht ruht bis morgen."; return }

        if (dying > 0f) {
            dying -= dt
            if (dying <= 0f) { resetMinute(); banner = 2.2f }
            return
        }

        // ---- Licht bewegen: steigen solange links gehalten, sonst sanft sinken ----
        val lift = left.isNotEmpty()
        val acc = if (lift) 1.5f else -1.0f
        vy += acc * dt
        vy *= (1f - min(1f, dt * 2.2f))
        vy = vy.coerceIn(-0.55f, 0.6f)
        // Wind ab Minute 7
        gust = if (minute >= 6) sin(t * 0.45f) * 0.5f + sin(t * 1.3f) * 0.3f else 0f
        y += (vy + gust * 0.12f * waves) * dt
        if (y < 0.05f) { y = 0.05f; vy = 0f }
        if (y > 0.95f) { y = 0.95f; vy = 0f }

        // ---- Schild ----
        shielding = right.isNotEmpty() && energy > 0f
        if (shielding) energy = (energy - dt).coerceAtLeast(0f) else energy = (energy + dt * 0.08f).coerceAtMost(shieldMax)

        // ---- Welt bewegen + erzeugen ----
        tm += dt
        val m = minute
        val calmEnd = tm > MINUTE - 3f
        for (o in objs) {
            o.x -= (speed + o.vx) * dt
            if (o.wobble > 0f) o.y = (o.baseY + sin(t * 1.4f + o.phase) * o.wobble).coerceIn(0.03f, 0.97f)
            if (o.vy != 0f) o.y += o.vy * dt
            if (!o.red && magnet > 0f) {
                val dx = (o.x - 0.25f) * aspect
                val dy = o.y - y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < magnet + 0.12f && dist > 0.001f) { o.x -= dx / dist * dt * 0.4f / aspect; o.y -= dy / dist * dt * 0.4f }
            }
            if (o.gone > 0f) o.gone += dt
        }
        objs.removeAll { it.x < -0.1f || it.gone > 0.5f || it.y < -0.1f }
        if (!calmEnd) {
            nextRed -= dt
            if (nextRed <= 0f) {
                nextRed = (1.5f - 0.09f * m) * (0.7f + rnd.nextFloat() * 0.6f)
                val wob = if (m >= 2 && rnd.nextFloat() < 0.4f + m * 0.04f) 0.06f + rnd.nextFloat() * 0.12f else 0f
                objs += Obj(1.1f, 0.08f + rnd.nextFloat() * 0.84f, 0.035f + rnd.nextFloat() * 0.02f, true, wobble = wob, phase = rnd.nextFloat() * 6f)
            }
            nextGreen -= dt
            if (nextGreen <= 0f) {
                nextGreen = 2f + rnd.nextFloat() * 1.5f
                objs += Obj(1.1f, 0.1f + rnd.nextFloat() * 0.8f, 0.025f, false, phase = rnd.nextFloat() * 6f)
            }
            if (m >= 4) {
                nextGate -= dt
                if (nextGate <= 0f) {
                    nextGate = 6.5f - 0.35f * m + rnd.nextFloat() * 2f
                    val gap = 0.34f - 0.015f * m
                    val c = gap / 2f + 0.06f + rnd.nextFloat() * (0.88f - gap)
                    var yy = 0.04f
                    while (yy < 0.98f) {
                        if (abs(yy - c) > gap / 2f) objs += Obj(1.15f, yy, 0.03f, true)
                        yy += 0.075f
                    }
                    objs += Obj(1.15f, c, 0.025f, false)
                }
            }
            if (m >= 8) {
                nextRain -= dt
                if (nextRain <= 0f) {
                    nextRain = 0.5f + rnd.nextFloat() * 0.6f
                    objs += Obj(0.4f + rnd.nextFloat() * 0.7f, 1.05f, 0.022f, true, vx = 0.05f, vy = -0.25f - rnd.nextFloat() * 0.1f)
                }
            }
        }

        // ---- Treffer ----
        val pr = 0.028f
        for (o in objs) {
            if (o.gone > 0f) continue
            val dx = (o.x - 0.25f) * aspect
            val dy = o.y - y
            if (dx * dx + dy * dy < (o.r + pr) * (o.r + pr)) {
                if (!o.red) {
                    o.gone = 0.01f
                    greens++
                    energy = (energy + 0.6f).coerceAtMost(shieldMax)
                } else if (shielding) {
                    o.gone = 0.01f
                } else {
                    hits++
                    dying = 1.6f
                    return
                }
            }
        }

        // ---- Spur ----
        trailAcc += dt
        val n = (trailAcc * 60f).toInt()
        for (k in 0 until n) {
            trailHead = (trailHead + 1) % trail.size
            trail[trailHead] = prevY + (y - prevY) * (k + 1) / n
        }
        trailAcc -= n / 60f
        if (n > 0) prevY = y

        // ---- Minute geschafft = Checkpoint ----
        if (tm >= MINUTE) {
            completed = minute + 1
            if (minute >= 9) { end = "Durchgang geschafft – zehn Minuten Ruhe."; return }
            minute++
            tm = 0f
            banner = 2.6f
        }
    }
}

// ==========================================================================================
// Zeichnen (Licht-Stil)
// ==========================================================================================

private fun DrawScope.cGlow(c: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), color.copy(alpha = (alpha * 0.35f).coerceIn(0f, 1f)), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c, blendMode = BlendMode.Plus,
    )
}

private fun cHash(i: Int): Float {
    val v = sin(i * 12.9898f + 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

private fun DrawScope.drawFlight(s: FlightState, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    s.aspect = w / h
    val t = s.t
    drawWorld(s, 0.85f)
    fun sx(x: Float) = x * w
    fun sy(y: Float) = h * (0.92f - y * 0.84f)
    val unit = h * 0.84f

    // Wind sichtbar machen
    if (s.minute >= 6) {
        for (k in 0 until 18) {
            val q = (t * 0.6f + cHash(k + 40)) % 1f
            val x = w * (1.1f - q * 1.3f)
            val y = sy(cHash(k + 50)) - s.gust * unit * 0.05f * q
            drawLine(Color(0xFFE8F0FF).copy(alpha = 0.12f * sin(q * 3.14f)), Offset(x, y), Offset(x + w * 0.06f, y + s.gust * unit * 0.02f), strokeWidth = 1f * density, blendMode = BlendMode.Plus)
        }
    }

    // Dinge
    for (o in s.objs) {
        val c = Offset(sx(o.x), sy(o.y))
        val r = o.r * unit
        val fade = if (o.gone > 0f) (1f - o.gone / 0.5f).coerceIn(0f, 1f) else 1f
        val grow = if (o.gone > 0f) 1f + o.gone * 3f else 1f
        if (o.red) {
            val p = 0.85f + 0.15f * sin(t * 4f + o.phase)
            cGlow(c, r * 2.6f * p * grow, RED, 0.45f * fade)
            drawCircle(Color(0xFFFF8A80).copy(alpha = 0.9f * fade), radius = r * 0.55f, center = c)
            drawCircle(RED.copy(alpha = 0.6f * fade), radius = r, center = c, style = Stroke(1.3f * density), blendMode = BlendMode.Plus)
        } else {
            val p = 0.8f + 0.2f * sin(t * 3f + o.phase)
            cGlow(c, r * 3f * p * grow, GREEN, 0.5f * fade)
            drawCircle(Color(0xFFE0FFE8).copy(alpha = fade), radius = r * 0.35f, center = c)
            for (k in 0 until 4) {
                val a = t * 1.5f + k * 1.57f + o.phase
                drawCircle(GREEN.copy(alpha = 0.7f * fade), radius = 1.4f * density, center = c + Offset(kotlin.math.cos(a), sin(a)) * (r * 1.3f), blendMode = BlendMode.Plus)
            }
        }
    }

    // eigene Spur
    val px = sx(0.25f)
    val perSec = w * (0.2f + 0.025f * s.minute)
    val trail = Path()
    val n = s.trail.size
    for (k in 0 until n) {
        val idx = (s.trailHead - k + n) % n
        val tx = px - k / 60f * perSec
        if (tx < 0f) break
        val ty = sy(s.trail[idx])
        if (k == 0) trail.moveTo(tx, ty) else trail.lineTo(tx, ty)
    }
    val life = if (s.dying > 0f) (s.dying / 1.6f).coerceIn(0f, 1f) else 1f
    drawPath(trail, AmbientColors.Arty.copy(alpha = 0.3f * life), style = Stroke(8f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    drawPath(trail, AmbientColors.ArtyText.copy(alpha = life), style = Stroke(2f * density, cap = StrokeCap.Round))

    // das Licht
    val me = Offset(px, sy(s.y))
    cGlow(me, unit * 0.12f * (0.3f + 0.7f * life), lerp(AmbientColors.Gold, AmbientColors.Light, 0.3f), 0.55f * life)
    drawCircle(Color(0xFFF4F0FF).copy(alpha = life), radius = 6f * density, center = me)
    if (s.shielding) {
        val pr = unit * 0.06f * (1f + 0.05f * sin(t * 12f))
        cGlow(me, pr * 1.6f, AmbientColors.Arty, 0.35f)
        drawCircle(AmbientColors.ArtyText.copy(alpha = 0.9f), radius = pr, center = me, style = Stroke(2f * density), blendMode = BlendMode.Plus)
    }
    if (s.dying > 0f) {
        val e = 1f - life
        drawCircle(RED.copy(alpha = (1f - e) * 0.6f), radius = unit * (0.04f + e * 0.25f), center = me, style = Stroke(2f * density), blendMode = BlendMode.Plus)
        drawRect(Color(0xFF020306).copy(alpha = 0.5f * e))
        val txt = measurer.measure("Zurück zum Checkpoint …", TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 22.sp, letterSpacing = 2.sp, color = AmbientColors.Light.copy(alpha = e)))
        drawText(txt, topLeft = Offset(w / 2f - txt.size.width / 2f, h * 0.42f))
    }

    // Daumenfelder
    flightZone(Offset(w * 0.1f, h * 0.86f), s.left.isNotEmpty(), AmbientColors.Gold, "STEIGEN", up = true, measurer = measurer)
    flightZone(Offset(w * 0.9f, h * 0.86f), s.shielding, AmbientColors.ArtyText, "SCHILD", up = false, measurer = measurer)
    // Schildenergie (Bogen um das rechte Feld)
    val rc = Offset(w * 0.9f, h * 0.86f)
    val rr = size.minDimension * 0.095f
    drawArc(Color.White.copy(alpha = 0.12f), -90f, 360f, false, topLeft = rc - Offset(rr, rr), size = Size(rr * 2f, rr * 2f), style = Stroke(3f * density))
    drawArc(
        AmbientColors.Arty, -90f, 360f * (s.energy / s.shieldMax).coerceIn(0f, 1f), false,
        topLeft = rc - Offset(rr, rr), size = Size(rr * 2f, rr * 2f), style = Stroke(3f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus,
    )

    // Kopf: Minute + Fortschritt (10 Abschnitte)
    val name = measurer.measure(
        "${s.level} · ${LEVEL_NAMES[s.minute.coerceIn(0, 9)]}",
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 4.sp, color = AmbientColors.Light),
    )
    drawText(name, topLeft = Offset(32f * density, 34f * density))
    val x0 = 32f * density
    val segW = (w * 0.4f - x0) / 10f
    val yb = 34f * density + name.size.height + 12f * density
    for (k in 0 until 10) {
        val a = Offset(x0 + k * segW + 2f * density, yb)
        val b = Offset(x0 + (k + 1) * segW - 2f * density, yb)
        drawLine(Color.White.copy(alpha = 0.14f), a, b, strokeWidth = 2f * density, cap = StrokeCap.Round)
        val f = when { k < s.minute -> 1f; k == s.minute -> (s.tm / MINUTE).coerceIn(0f, 1f); else -> 0f }
        if (f > 0f) drawLine(AmbientColors.Gold.copy(alpha = 0.85f), a, Offset(a.x + (b.x - a.x) * f, yb), strokeWidth = 2f * density, cap = StrokeCap.Round)
    }
    val info = measurer.measure(
        "${s.greens} grüne Lichter · noch ${(s.secondsLeft / 60f).toInt() + 1} Min. heute",
        TextStyle(fontFamily = Manrope, fontSize = 11.sp, letterSpacing = 1.sp, color = AmbientColors.Muted),
    )
    drawText(info, topLeft = Offset(x0, yb + 10f * density))

    // Checkpoint-Banner
    if (s.banner > 0f) {
        val a = (s.banner.coerceAtMost(1f)) * (if (s.banner > 2f) (2.6f - s.banner) / 0.6f else 1f)
        val l1 = measurer.measure("CHECKPOINT", TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 6.sp, color = AmbientColors.Gold.copy(alpha = a.coerceIn(0f, 1f))))
        val l2 = measurer.measure(LEVEL_NAMES[s.minute.coerceIn(0, 9)], TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = 4.sp, color = AmbientColors.Light.copy(alpha = a.coerceIn(0f, 1f))))
        drawText(l1, topLeft = Offset(w / 2f - l1.size.width / 2f, h * 0.36f))
        drawText(l2, topLeft = Offset(w / 2f - l2.size.width / 2f, h * 0.36f + l1.size.height + 8f * density))
    }
}

private fun DrawScope.flightZone(c: Offset, pressed: Boolean, col: Color, label: String, up: Boolean, measurer: TextMeasurer) {
    val r = size.minDimension * 0.07f
    cGlow(c, r * (if (pressed) 2.2f else 1.4f), col, if (pressed) 0.45f else 0.15f)
    drawCircle(col.copy(alpha = if (pressed) 0.9f else 0.35f), radius = r, center = c, style = Stroke(1.4f * density))
    if (up) {
        val a = Path().apply {
            moveTo(c.x - r * 0.35f, c.y + r * 0.15f); lineTo(c.x, c.y - r * 0.25f); lineTo(c.x + r * 0.35f, c.y + r * 0.15f)
        }
        drawPath(a, col.copy(alpha = if (pressed) 1f else 0.6f), style = Stroke(2f * density, cap = StrokeCap.Round))
    } else {
        drawCircle(col.copy(alpha = if (pressed) 1f else 0.6f), radius = r * 0.38f, center = c, style = Stroke(2f * density))
    }
    val l = measurer.measure(label, TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 3.sp, color = col.copy(alpha = 0.8f)))
    drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - r * 1.45f - l.size.height - 6f * density))
}

/** Umgebung je Station: dunkel, Silhouetten mit Lichtkanten, Wasser mit Lichtstreifen; ab Station 6 schaukelt der Horizont. */
private fun DrawScope.drawWorld(s: FlightState, bright: Float) {
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


/** Ende: Tageszeit aufgebraucht oder Durchgang geschafft. */
@Composable
private fun EndCard(text: String, s: FlightState, modifier: Modifier, onBack: () -> Unit) {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
    }
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier
            .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); scaleX = 0.85f + 0.15f * a.value; scaleY = 0.85f + 0.15f * a.value }
            .clip(shape)
            .background(Color(0xF0101116))
            .border(1.dp, AmbientColors.ArtyText.copy(alpha = 0.8f), shape)
            .padding(horizontal = 44.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RUHE", style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 6.sp, color = AmbientColors.ArtyText))
        Spacer(Modifier.size(14.dp))
        Text(text, textAlign = TextAlign.Center, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 20.sp, color = AmbientColors.Light))
        Spacer(Modifier.size(12.dp))
        Text(
            "${s.minutesDone} Minuten geschafft · ${s.greens} grüne Lichter",
            style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = AmbientColors.Muted),
        )
        Spacer(Modifier.size(22.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1E2A55))
                .border(1.dp, AmbientColors.Arty, RoundedCornerShape(50))
                .clickable(onClick = onBack)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) {
            Text("ZURÜCK", style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 4.sp, color = AmbientColors.Light))
        }
    }
}
