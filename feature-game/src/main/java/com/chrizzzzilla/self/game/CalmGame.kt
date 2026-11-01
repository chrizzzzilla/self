package com.chrizzzzilla.self.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Dauer einer Runde in Sekunden (4 Runden = 10 Minuten am Tag). */
private const val ROUND_SECONDS = 60f

/** Die 10 Stationen eines Tages – von ganz still bis Sturm. */
private val LEVEL_NAMES = arrayOf(
    "STILLER SEE", "SEE AM ABEND", "RUHIGER STRAND", "STRAND IM WIND", "BRANDUNG",
    "IM BOOT", "DÜNUNG", "AUFKOMMENDER WIND", "STURM", "IM HERZEN DES STURMS",
)

/**
 * Spiel „Ruhe“. Ein Licht lebt von deinem ruhigen Atem:
 *  Finger auflegen, solange der Ring waechst (einatmen) – loslassen, solange er kleiner wird (ausatmen).
 * Die Umgebung versucht dich je nach Level mehr oder weniger zu stoeren; Antippen von Verlockungen
 * oder hastiges Tippen beim Ausatmen kostet Licht. Geht das Licht aus, gibt es kein „Game Over“:
 * es verglimmt, ein ruhiger Text erscheint, dann kommt es zurueck.
 */
@Composable
internal fun CalmGame(level: Int, abilities: CalmAbilities, onComplete: (passed: Boolean) -> Unit, onExit: () -> Unit) {
    val state = remember(level) { CalmState(level.coerceIn(1, 10), abilities) }
    val tick = remember { mutableFloatStateOf(0f) }
    val showResult = remember(level) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    BackHandler(onBack = onExit)

    LaunchedEffect(level) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                state.step(dt)
                tick.floatValue = state.t
                if (state.finished && state.finishT > 1.6f) showResult.value = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(level) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                val nx = c.position.x / size.width
                                val ny = c.position.y / size.height
                                val id = c.id.value
                                when {
                                    c.changedToDown() -> {
                                        if (nx >= 0.5f && state.breathPointer == null) {
                                            state.breathPointer = id
                                            state.press(nx, ny)
                                        } else if (nx < 0.5f && state.leftPointer == null) {
                                            state.leftPointer = id
                                            state.leftDown(nx, ny)
                                        }
                                    }
                                    c.changedToUp() -> {
                                        if (id == state.breathPointer) { state.breathPointer = null; state.release() }
                                        if (id == state.leftPointer) { state.leftPointer = null; state.leftUp() }
                                    }
                                    c.pressed && id == state.leftPointer -> state.leftMove(nx, ny)
                                }
                            }
                        }
                    }
                },
        ) {
            tick.floatValue // Neuzeichnen pro Frame
            drawCalm(state, measurer)
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
        // Verlassen (ohne Fortschritt)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val c = AmbientColors.Light.copy(alpha = 0.7f)
                drawLine(c, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
            }
        }
    }
}

// ==========================================================================================
// Spiellogik
// ==========================================================================================

private val THOUGHTS = arrayOf(
    "E-Mails", "Was wäre wenn …", "Muss noch …", "Termine", "Morgen", "Später", "Sorgen", "To-do", "Hab ich …?", "Nachrichten",
)

private class Thought(val text: String, var x: Float, var y: Float) {
    var grabbed = false
    var alpha = 0f
    var gone = false
    var fade = 1f
    var slip = 0f
}

/** 8 Sekunden Verlauf bei 20 Werten pro Sekunde */
private const val HIST = 160

private class Lure(var x: Float, var y: Float, var life: Float, val max: Float) {
    var tapped = false
}

private class CalmState(val level: Int, val abilities: CalmAbilities) {
    /** Zwei Haende: rechts atmen, links Gedanken (Station 1–5) bzw. Balance (6–10) */
    var breathPointer: Long? = null
    var leftPointer: Long? = null
    /** Gedanken-Modus ist abgeschaltet – alle Stationen nutzen die Balance. */
    val thoughtsMode: Boolean get() = false
    val thoughts = ArrayList<Thought>()
    private var nextThought = 4f
    private var grabbed: Thought? = null
    private var lastLX = 0f
    private var lastLY = 0f
    private var lastLT = 0f
    private var leftStartX = 0f
    var leftX = 0f
    /** Neigung der Welt in Grad (Boot) und Gegensteuern der linken Hand */
    var tilt = 0f
    /** Lage des Balance-Punkts −1 … 1 (0 = Mitte) */
    var bal = 0f
    private var drift = 0f
    private var driftVel = 0f
    /** was der Daumen bisher verschoben hat (bleibt beim Loslassen erhalten) */
    private var thumbOffset = 0f
    private var thumbBase = 0f
    private var thumbDownX = 0f
    var levelGood = 0f // 0..1 wie gerade man liegt (fuer die Wasserwaage)
    /** verbleibende Schilde in dieser Station */
    var shieldsLeft = abilities.shields
    var shieldFlash = 0f
    /** Vorahnung: angekuendigte Welle / Verlockung */
    var waveWarning = 0f
    var lureWarnX = -1f
    var lureWarnY = -1f
    var lureWarning = 0f
    var t = 0f
    var energy = 0.45f
    var holding = false
    var pressedAt = -10f
    var mistakeFlash = 0f
    var out = false
    var outT = 0f
    var finished = false
    var finishT = 0f
    var bloom = 0f
    var shown = 0.45f // sanft nachgefuehrte Energie fuer die Anzeige
    private var scoreAcc = 0f
    /** Ruhe der Runde in Prozent (Licht 70 % + Balance 30 %) */
    val percent: Int get() = (scoreAcc / ROUND_SECONDS * 100f).toInt().coerceIn(0, 100)
    val goalPercent: Int get() = (55f + d * 10f).toInt()
    val passed: Boolean get() = percent >= goalPercent
    /** dein Atem als Kurve 0..1 (steigt beim Halten, sinkt beim Loslassen) */
    var userBreath = 0f
    /** Verlauf der letzten Sekunden: Ideal + du (fuer die Atem-Linie) */
    val histIdeal = FloatArray(HIST)
    val histUser = FloatArray(HIST)
    var histPos = 0
    private var histAcc = 0f

    // Atemtakt
    /** Schwierigkeit 0 (Station 1) … 1 (Station 10) */
    val d: Float = (level - 1) / 9f
    private val period = 10f - d * 1.2f
    private val irregular = if (level <= 2) 0f else d * 0.22f
    var breathPhase = 0f // 0..1, < 0.5 = einatmen
    private val tolerance = 0.95f - d * 0.5f + abilities.calmBreath
    private val penalty = 0.05f + d * 0.08f

    // Stoerungen
    var push = 0f        // seitliche Verschiebung des Lichts (Welle/Wind)
    var pushVel = 0f
    private var nextWave = if (level >= 4) 6f else 999f
    var waveT = -10f
    var lightning = 0f
    private var nextLightning = 14f
    val lures = ArrayList<Lure>()
    private var nextLure = 999f // Verlockungen abgeschaltet (waren missverstaendlich)
    private val rnd = Random(level * 101)

    val inhale: Boolean get() = breathPhase < 0.5f
    /** 0..1..0 ueber einen Atemzug (eingehuellt), fuer die Ringgroesse */
    val breath: Float get() {
        val x = if (breathPhase < 0.5f) breathPhase * 2f else 2f - breathPhase * 2f
        return x * x * (3f - 2f * x)
    }

    private fun nearTransition(): Boolean {
        val secondsToSwitch = min(
            abs(breathPhase - 0.5f) * period,
            min(breathPhase, 1f - breathPhase) * period,
        )
        return secondsToSwitch < tolerance
    }

    fun press(nx: Float, ny: Float) {
        if (finished) return
        holding = true
        pressedAt = t
        // Verlockung angetippt?
        if (hitLure(nx, ny)) return
        // Hastig tippen, obwohl ausgeatmet werden soll
        if (!out && !inhale && !nearTransition()) mistake(penalty)
    }

    fun release() { holding = false }

    private fun hitLure(nx: Float, ny: Float): Boolean {
        val hit = lures.firstOrNull { abs(it.x - nx) < 0.08f && abs(it.y - ny) < 0.1f } ?: return false
        hit.life = 0f
        hit.tapped = true
        mistake(penalty * 1.5f)
        return true
    }

    fun leftDown(nx: Float, ny: Float) {
        if (finished) return
        if (hitLure(nx, ny)) return
        lastLX = nx; lastLY = ny; lastLT = t
        leftStartX = nx; leftX = nx
        thumbDownX = nx
        thumbBase = thumbOffset
        if (thoughtsMode) {
            grabbed = thoughts.filter { !it.gone }.minByOrNull { (it.x - nx) * (it.x - nx) + (it.y - ny) * (it.y - ny) }
                ?.takeIf { abs(it.x - nx) < 0.12f && abs(it.y - ny) < 0.1f }
            grabbed?.grabbed = true
        }
    }

    fun leftMove(nx: Float, ny: Float) {
        val dt = (t - lastLT).coerceAtLeast(0.008f)
        val speed = kotlin.math.sqrt((nx - lastLX) * (nx - lastLX) + (ny - lastLY) * (ny - lastLY)) / dt
        if (thoughtsMode) {
            val g = grabbed
            if (g != null) {
                if (speed > 1.1f) {
                    // hastig → der Gedanke rutscht weg
                    g.grabbed = false
                    g.slip = 1f
                    grabbed = null
                } else {
                    g.x += nx - lastLX
                    g.y += ny - lastLY
                }
            }
        } else {
            leftX = nx
            // 20 % der Bildschirmbreite ziehen = ganzer Ausschlag
            thumbOffset = (thumbBase + (nx - thumbDownX) * 5f).coerceIn(-2f, 2f)
            // hektisch hin und her bringt etwas Unruhe
            if (speed > 2.2f) driftVel += (if (rnd.nextBoolean()) 1f else -1f) * 0.3f
        }
        lastLX = nx; lastLY = ny; lastLT = t
    }

    fun leftUp() {
        grabbed?.grabbed = false
        grabbed = null
    }

    private fun mistake(amount: Float) {
        // Schild: die eigene Ruhe faengt den Fehler ab
        if (shieldsLeft > 0) {
            shieldsLeft--
            shieldFlash = 1f
            return
        }
        energy -= amount
        mistakeFlash = 1f
    }

    fun step(dt: Float) {
        t += dt
        // Atemtakt (ab Level 2 leicht unregelmaessig)
        val speed = 1f + irregular * (sin(t * 0.37f) * 0.7f + sin(t * 0.91f + 1.3f) * 0.3f)
        breathPhase = (breathPhase + dt / period * speed) % 1f

        userBreath += ((if (holding) 1f else 0f) - userBreath) * min(1f, dt * 1.1f)
        histAcc += dt
        if (histAcc >= 1f / 20f) {
            histAcc = 0f
            histIdeal[histPos] = breath
            histUser[histPos] = userBreath
            histPos = (histPos + 1) % HIST
        }
        mistakeFlash = (mistakeFlash - dt * 1.5f).coerceAtLeast(0f)
        shieldFlash = (shieldFlash - dt * 1.2f).coerceAtLeast(0f)
        // Vorahnung: kurz vor einer Welle bzw. Verlockung leise ankuendigen
        val fs = abilities.foresight
        waveWarning = if (fs > 0f && nextWave - t in 0f..fs) 1f - (nextWave - t) / fs else 0f
        if (fs > 0f && nextLure - t in 0f..fs) {
            if (lureWarnX < 0f) { lureWarnX = 0.15f + rnd.nextFloat() * 0.7f; lureWarnY = 0.2f + rnd.nextFloat() * 0.45f }
            lureWarning = 1f - (nextLure - t) / fs
        } else lureWarning = 0f
        lightning = (lightning - dt * 2.5f).coerceAtLeast(0f)

        if (finished) {
            finishT += dt
            bloom = (bloom + dt * 0.5f).coerceAtMost(1f)
            shown += (1f - shown) * dt
            return
        }

        if (out) {
            outT += dt
            shown += (0f - shown) * min(1f, dt * 2f)
            if (outT > 8f) {
                out = false
                energy = 0.35f
            }
        } else {
            val ok = holding == inhale || nearTransition()
            energy += dt * (if (ok) 0.05f else -0.08f * (1f + level * 0.15f))
            energy = energy.coerceIn(0f, 1f)
            if (energy <= 0f) { out = true; outT = 0f }
            shown += (energy - shown) * min(1f, dt * 3f)
        }

        // Gedanken treiben auf das Licht zu (Station 1–5)
        if (thoughtsMode && !out) {
            if (t > nextThought) {
                nextThought = t + 11f - level * 1.2f + rnd.nextFloat() * 4f
                val side = rnd.nextInt(3)
                val (sx, sy) = when (side) {
                    0 -> -0.08f to 0.2f + rnd.nextFloat() * 0.5f
                    1 -> 1.08f to 0.2f + rnd.nextFloat() * 0.5f
                    else -> 0.2f + rnd.nextFloat() * 0.6f to -0.06f
                }
                thoughts += Thought(THOUGHTS[rnd.nextInt(THOUGHTS.size)], sx, sy)
            }
        }
        val thoughtDrift = 0.035f + level * 0.006f
        val th = thoughts.iterator()
        while (th.hasNext()) {
            val g = th.next()
            g.slip = (g.slip - dt * 2f).coerceAtLeast(0f)
            if (g.gone) {
                g.fade -= dt * 1.5f
                if (g.fade <= 0f) th.remove()
                continue
            }
            g.alpha = (g.alpha + dt * 1.2f).coerceAtMost(1f)
            if (!g.grabbed) {
                val dx = 0.5f + push - g.x
                val dy = 0.42f - g.y
                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                g.x += dx / len * thoughtDrift * dt
                g.y += dy / len * thoughtDrift * dt
                if (len < 0.07f) {
                    // Gedanke erreicht das Licht
                    g.gone = true
                    mistake(penalty * 0.7f)
                }
            }
            if (g.x < -0.12f || g.x > 1.12f || g.y < -0.1f || g.y > 1.1f) {
                // sanft hinausgeschoben → losgelassen
                if (g.grabbed || g.alpha >= 1f) energy = (energy + 0.03f).coerceAtMost(1f)
                g.gone = true
                if (g === grabbed) grabbed = null
            }
        }

        // Balance: Wind und Wellen lassen den Punkt wegdriften, der linke Daumen zieht ihn zurueck
        if (!thoughtsMode) {
            val strength = 0.12f + 0.45f * d
            val noise = (sin(t * 0.41f) * 0.6f + sin(t * 1.13f + 2f) * 0.4f) * strength
            driftVel += (noise + pushVel * 1.5f) * dt
            driftVel *= (1f - min(1f, dt * 0.8f))
            drift += driftVel * dt
            bal = (drift + thumbOffset).coerceIn(-1f, 1f)
            tilt = bal * 12f
            levelGood = (1f - abs(bal) / 0.45f).coerceIn(0f, 1f)
            if (!out) {
                if (abs(bal) < 0.15f) energy = (energy + dt * 0.02f).coerceAtMost(1f)
                else if (abs(bal) > 0.55f) energy -= dt * 0.05f * abs(bal)
            }
        }

        // Wellen / Wind schieben das Licht
        if (t > nextWave) {
            waveT = t
            nextWave = t + 12f - d * 7f + rnd.nextFloat() * 4f
            pushVel += (if (rnd.nextBoolean()) 1f else -1f) * (0.1f + 0.25f * d)
        }
        pushVel += (-push * 2.2f - pushVel * 1.4f) * dt
        push += pushVel * dt

        // Blitze im Sturm
        if (level >= 9 && t > nextLightning) {
            lightning = 1f
            nextLightning = t + 10f + rnd.nextFloat() * 12f
        }

        // Verlockungen: glaenzende Lichter, die angetippt werden wollen
        if (t > nextLure && !out) {
            nextLure = 999f
            val life = 3.2f
            val lx = if (lureWarnX >= 0f) lureWarnX else 0.15f + rnd.nextFloat() * 0.7f
            val ly = if (lureWarnY >= 0f) lureWarnY else 0.2f + rnd.nextFloat() * 0.45f
            lures += Lure(lx, ly, life, life)
            lureWarnX = -1f
            lureWarnY = -1f
        }
        val it = lures.iterator()
        while (it.hasNext()) {
            val l = it.next()
            l.life -= dt
            if (l.life <= 0f) {
                // ignoriert → ein wenig Licht als Belohnung (wenn nicht angetippt)
                if (!l.tapped) energy = (energy + 0.03f).coerceAtMost(1f)
                it.remove()
            }
        }

        scoreAcc += dt * ((if (out) 0f else energy) * 0.7f + levelGood * 0.3f)
        if (t >= ROUND_SECONDS) {
            finished = true
            finishT = 0f
        }
    }
}

// ==========================================================================================
// Zeichnen
// ==========================================================================================

private fun DrawScope.drawCalm(s: CalmState, measurer: androidx.compose.ui.text.TextMeasurer) {
    val w = size.width
    val h = size.height
    val progress = (s.t / ROUND_SECONDS).coerceIn(0f, 1f)

    if (s.level <= 5) {
        withTransform({ rotate(s.tilt, pivot = Offset(w / 2f, h * 0.55f)); scale(1.12f, 1.12f, pivot = Offset(w / 2f, h * 0.55f)) }) {
            when (s.level) {
                1 -> drawLake(s.t, progress, evening = false)
                2 -> drawLake(s.t, progress, evening = true)
                else -> drawBeach(s.t, progress, s)
            }
        }
    } else {
        drawBoat(s.t, progress, s, storm = s.level >= 9)
    }

    // Licht + Atemring
    val center = Offset(w / 2f + s.push * w, h * 0.42f)
    val base = min(w, h) * 0.07f
    val e = s.shown
    val bloomR = s.bloom * min(w, h) * 0.6f
    val glowR = base * (2.2f + 2.5f * e) + bloomR
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFE6B0).copy(alpha = 0.55f * e + 0.4f * s.bloom), Color.Transparent), center = center, radius = glowR),
        radius = glowR, center = center, blendMode = BlendMode.Plus,
    )
    drawCircle(Color(0xFFFFF4DA).copy(alpha = (0.25f + 0.75f * e).coerceIn(0f, 1f)), radius = base * (0.35f + 0.25f * e), center = center)
    if (!s.finished) {
        val ringR = base * (1.4f + 1.8f * s.breath)
        val ringCol = if (s.inhale) AmbientColors.ArtyText else AmbientColors.Gold
        drawCircle(ringCol.copy(alpha = if (s.out) 0.15f else 0.55f), radius = ringR, center = center, style = Stroke(width = 1.6f * density))
        // dezente Rueckmeldung: haelt der Finger gerade?
        if (s.holding) drawCircle(ringCol.copy(alpha = 0.18f), radius = ringR, center = center, blendMode = BlendMode.Plus)
    }

    // Verlockungen
    for (l in s.lures) {
        val a = (min(l.life, l.max - l.life) / 0.6f).coerceIn(0f, 1f)
        val c = Offset(l.x * w, l.y * h)
        val pulse = 1f + 0.25f * sin(s.t * 9f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFD36A).copy(alpha = 0.7f * a), Color.Transparent), center = c, radius = base * 1.6f * pulse),
            radius = base * 1.6f * pulse, center = c, blendMode = BlendMode.Plus,
        )
        drawCircle(Color.White.copy(alpha = a), radius = base * 0.18f, center = c)
    }

    // Vorahnung: Welle → feiner Lichtstreif am Horizont; Verlockung → leiser Ring an der Stelle
    if (s.waveWarning > 0f) {
        val y = h * 0.5f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, AmbientColors.ArtyText.copy(alpha = 0.22f * s.waveWarning), Color.Transparent), startY = y - h * 0.05f, endY = y + h * 0.05f),
            blendMode = BlendMode.Plus,
        )
    }
    if (s.lureWarning > 0f && s.lureWarnX >= 0f) {
        val c = Offset(s.lureWarnX * w, s.lureWarnY * h)
        drawCircle(AmbientColors.ArtyText.copy(alpha = 0.35f * s.lureWarning), radius = base * (2.2f - s.lureWarning), center = c, style = Stroke(width = 1.2f * density))
    }
    // Schild: Ring aus Ruhe blitzt um das Licht auf
    if (s.shieldFlash > 0f) {
        val r = base * (3.2f + (1f - s.shieldFlash) * 1.5f)
        drawCircle(AmbientColors.ArtyText.copy(alpha = 0.7f * s.shieldFlash), radius = r, center = center, style = Stroke(width = 3f * density))
        drawCircle(AmbientColors.Arty.copy(alpha = 0.25f * s.shieldFlash), radius = r, center = center, blendMode = BlendMode.Plus)
    }

    // Fehler: kurzes, weiches Abdunkeln
    if (s.mistakeFlash > 0f) drawRect(Color(0xFF200A10).copy(alpha = 0.25f * s.mistakeFlash))
    // Blitz
    if (s.lightning > 0f) drawRect(Color.White.copy(alpha = 0.55f * s.lightning.pow(2)))

    drawBreathLine(s)
    drawGoal(s, progress)
    if (s.thoughtsMode) drawThoughts(s, measurer) else drawSpiritLevel(s)

    // Texte
    val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = 1.sp, color = AmbientColors.Light)
    val small = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 6.sp, color = AmbientColors.Light)
    fun centered(text: String, y: Float, alpha: Float, st: TextStyle) {
        if (alpha <= 0.01f) return
        val layout = measurer.measure(text, st)
        drawText(layout, topLeft = Offset((w - layout.size.width) / 2f, y), alpha = alpha.coerceIn(0f, 1f))
    }
    // Einstieg
    val intro = (1f - ((s.t - 5f) / 1.5f)).coerceIn(0f, 1f) * (s.t / 1f).coerceIn(0f, 1f)
    centered(LEVEL_NAMES[s.level - 1], h * 0.12f, intro, small)
    val hint = (1f - ((s.t - 10f) / 2f)).coerceIn(0f, 1f) * ((s.t - 1f) / 1.5f).coerceIn(0f, 1f)
    if (s.level == 1 || s.level == 6) {
        val sideStyle = style.copy(fontSize = 15.sp)
        fun at(text: String, x: Float, y: Float) {
            val layout = measurer.measure(text, sideStyle)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y), alpha = hint)
        }
        at("Rechts: Finger auflegen,", w * 0.75f, h * 0.56f)
        at("wenn der Ring wächst.", w * 0.75f, h * 0.56f + 24.dp.toPx())
        at("Links: den Punkt mit dem Daumen", w * 0.25f, h * 0.56f)
        at("in der Mitte halten (ziehen).", w * 0.25f, h * 0.56f + 24.dp.toPx())
    }
    // Licht aus → ruhige Saetze
    if (s.out) {
        val a1 = smoothIn(s.outT, 0.8f, 1.8f) * (1f - smoothIn(s.outT, 4f, 5f))
        val a2 = smoothIn(s.outT, 4.2f, 5.4f) * (1f - smoothIn(s.outT, 7.2f, 8f))
        centered("Halte kurz inne.", h * 0.64f, a1, style)
        centered("Atme tief ein.", h * 0.64f, a2, style)
    }

}

/**
 * Atem-Linie: fein die ideale Kurve, darueber deine eigene (blau). Liegen sie aufeinander,
 * leuchtet deine Linie – das ist die Balance.
 */
private fun DrawScope.drawBreathLine(s: CalmState) {
    val w = size.width
    val h = size.height
    val left = w * 0.3f
    val right = w * 0.7f
    val top = h * 0.74f
    val height = h * 0.08f
    val ideal = Path()
    val user = Path()
    var diff = 0f
    for (i in 0 until HIST) {
        val idx = (s.histPos + i) % HIST
        val x = left + (right - left) * i / (HIST - 1)
        val yi = top + height * (1f - s.histIdeal[idx])
        val yu = top + height * (1f - s.histUser[idx])
        if (i == 0) { ideal.moveTo(x, yi); user.moveTo(x, yu) } else { ideal.lineTo(x, yi); user.lineTo(x, yu) }
        if (i > HIST - 40) diff += abs(s.histIdeal[idx] - s.histUser[idx])
    }
    val balance = (1f - diff / 40f * 2.2f).coerceIn(0f, 1f)
    drawPath(ideal, Color.White.copy(alpha = 0.22f), style = Stroke(width = 1.2f * density))
    if (balance > 0.5f) {
        drawPath(user, AmbientColors.ArtyText.copy(alpha = 0.35f * balance), style = Stroke(width = 7f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
    drawPath(user, lerp(AmbientColors.Muted, AmbientColors.ArtyText, balance), style = Stroke(width = 2f * density, cap = StrokeCap.Round))
}

/** Gedanken-Woelkchen mit Wort; schimmern rot, wenn sie einem hastig entgleiten. */
private fun DrawScope.drawThoughts(s: CalmState, measurer: androidx.compose.ui.text.TextMeasurer) {
    val w = size.width
    val h = size.height
    val st = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF2A2E3A))
    for (g in s.thoughts) {
        val a = (g.alpha * g.fade).coerceIn(0f, 1f)
        if (a <= 0.01f) continue
        val c = Offset(g.x * w, g.y * h)
        val layout = measurer.measure(g.text, st)
        val rw = layout.size.width / 2f + 22.dp.toPx()
        val rh = layout.size.height / 2f + 14.dp.toPx()
        val base = if (g.slip > 0f) lerp(Color.White, Color(0xFFFFB0A8), g.slip) else Color.White
        for (k in 0 until 4) {
            val ox = (k - 1.5f) * rw * 0.45f
            val oy = (k % 2) * -rh * 0.35f
            drawCircle(base.copy(alpha = 0.55f * a), radius = rh * (1.05f + (k % 2) * 0.2f), center = Offset(c.x + ox, c.y + oy))
        }
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f), alpha = a)
        if (g.grabbed) drawCircle(AmbientColors.ArtyText.copy(alpha = 0.5f * a), radius = rw * 1.05f, center = c, style = Stroke(1.4f * density))
    }
}

/** Wasserwaage unten links: Bogen mit Lichtpunkt – in der Mitte = im Gleichgewicht. */
private fun DrawScope.drawSpiritLevel(s: CalmState) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.17f, h * 0.84f)
    val r = min(w, h) * 0.13f
    val good = s.levelGood
    drawArc(
        Color.White.copy(alpha = 0.25f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f), style = Stroke(1.4f * density),
    )
    // Mitte markieren
    drawLine(Color.White.copy(alpha = 0.4f), Offset(c.x, c.y - r - 6f * density), Offset(c.x, c.y - r + 6f * density), strokeWidth = 1.4f * density)
    val ang = (270f + s.bal * 62f) * PI.toFloat() / 180f
    val dot = Offset(c.x + cos(ang) * r, c.y + sin(ang) * r)
    val col = lerp(Color(0xFFE08A6A), AmbientColors.ArtyText, good)
    // Mittelzone (hier soll der Punkt sein)
    drawArc(
        AmbientColors.ArtyText.copy(alpha = 0.45f), startAngle = 261f, sweepAngle = 18f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f), style = Stroke(5f * density, cap = StrokeCap.Round),
    )
    drawCircle(col.copy(alpha = 0.35f), radius = 16f * density, center = dot, blendMode = BlendMode.Plus)
    drawCircle(col, radius = 7f * density, center = dot)
}

/** Ziel: ein Lichtpunkt wandert unten zum leuchtenden Ziel – man sieht, wie weit es noch ist. */
private fun DrawScope.drawGoal(s: CalmState, progress: Float) {
    val w = size.width
    val h = size.height
    val y = h * 0.9f
    val x0 = w * 0.3f
    val x1 = w * 0.7f
    drawLine(Color.White.copy(alpha = 0.18f), Offset(x0, y), Offset(x1, y), strokeWidth = 1.2f * density, cap = StrokeCap.Round)
    val px = x0 + (x1 - x0) * progress
    drawLine(AmbientColors.Gold.copy(alpha = 0.7f), Offset(x0, y), Offset(px, y), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
    val goal = Offset(x1, y)
    val gp = 0.6f + 0.4f * sin(s.t * 2f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6B0).copy(alpha = 0.5f * gp), Color.Transparent), center = goal, radius = 22f * density), radius = 22f * density, center = goal, blendMode = BlendMode.Plus)
    drawCircle(Color(0xFFFFF4DA), radius = 3.5f * density, center = goal)
    drawCircle(AmbientColors.Light, radius = 4f * density, center = Offset(px, y))
}

private fun smoothIn(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

// ---------- Level 1: Stiller See ----------
private fun DrawScope.drawLake(t: Float, p: Float, evening: Boolean) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.58f
    val lift = 0.25f + 0.75f * p // wird im Lauf der Runde heller
    val top = if (evening) lerp(Color(0xFF2A1E40), Color(0xFF6A5A9A), lift) else lerp(Color(0xFF1C2440), Color(0xFF6E86B8), lift)
    val hor = if (evening) lerp(Color(0xFF6A3A50), Color(0xFFF0926A), lift) else lerp(Color(0xFF5A4A6A), Color(0xFFF2C394), lift)
    drawRect(Brush.verticalGradient(listOf(top, hor), startY = 0f, endY = horizon))
    // Sonne knapp ueber dem Horizont
    val sun = Offset(w * 0.5f, horizon - h * 0.04f * lift)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD9A0).copy(alpha = 0.6f * lift), Color.Transparent), center = sun, radius = w * 0.25f), radius = w * 0.25f, center = sun, blendMode = BlendMode.Plus)
    // Berge
    ridge(lerp(Color(0xFF2A2440), Color(0xFF7E7FA6), lift * 0.7f), horizon, h * 0.12f, 2.1f, 3)
    ridge(lerp(Color(0xFF1C1A30), Color(0xFF5E6488), lift * 0.6f), horizon, h * 0.07f, 3.3f, 7)
    // Wasser: gespiegelter Himmel + ruhige Lichtlinien
    drawRect(Brush.verticalGradient(listOf(hor, top), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
    for (i in 0 until 26) {
        val y = horizon + (h - horizon) * ((i + 0.5f) / 26f).pow(1.6f)
        val len = w * (0.05f + 0.25f * ((i * 37) % 11) / 11f)
        val x = ((i * 0.37f + t * 0.004f * (1 + i % 3)) % 1f) * w
        drawLine(Color.White.copy(alpha = 0.08f + 0.06f * sin(t * 0.8f + i)), Offset(x - len / 2f, y), Offset(x + len / 2f, y), strokeWidth = (1f + i * 0.08f) * density)
    }
    // Nebel ueber dem Wasser
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent), startY = horizon - h * 0.06f, endY = horizon + h * 0.08f))
}

// ---------- Level 2: Strand ----------
private fun DrawScope.drawBeach(t: Float, p: Float, s: CalmState) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.5f
    val lift = 0.4f + 0.6f * p
    drawRect(Brush.verticalGradient(listOf(lerp(Color(0xFF3A5E8C), Color(0xFF7FB6DE), lift), lerp(Color(0xFFB9D4E4), Color(0xFFEAF2F2), lift)), startY = 0f, endY = horizon))
    // Meer
    val sea1 = Color(0xFF2F6F8E)
    val sea2 = Color(0xFF4FA3B5)
    drawRect(Brush.verticalGradient(listOf(sea1, sea2), startY = horizon, endY = h * 0.72f), topLeft = Offset(0f, horizon), size = Size(w, h * 0.72f - horizon))
    // Wellenlinien, die zum Ufer laufen
    for (k in 0 until 7) {
        val q = ((t * 0.06f + k / 7f) % 1f)
        val y = horizon + (h * 0.72f - horizon) * q.pow(1.5f)
        val path = Path()
        var x = 0f
        path.moveTo(0f, y)
        while (x <= w) {
            path.lineTo(x, y + sin(x * 0.01f + t * 0.8f + k) * (2f + q * 6f) * density)
            x += w / 60f
        }
        drawPath(path, Color.White.copy(alpha = 0.12f + 0.25f * q), style = Stroke(width = (1f + q * 2.5f) * density))
    }
    // Sand
    val sand = lerp(Color(0xFFB89C78), Color(0xFFE9D7B6), lift)
    drawRect(sand, topLeft = Offset(0f, h * 0.72f), size = Size(w, h * 0.28f))
    // Brandung: Schaumlinie laeuft den Strand hoch (staerker bei Wellen-Ereignis)
    val since = s.t - s.waveT
    val surge = (if (since in 0f..4f) sin(since / 4f * PI.toFloat()) else 0f) * (0.6f + (s.level - 3) * 0.3f)
    val shore = h * 0.72f + (sin(t * 2f * PI.toFloat() / 8f) * 0.5f + 0.5f) * h * 0.05f + surge * h * 0.06f
    drawRect(lerp(sand, Color(0xFF8E7A5E), 0.35f), topLeft = Offset(0f, h * 0.72f), size = Size(w, shore - h * 0.72f))
    val foam = Path()
    var fx = 0f
    foam.moveTo(0f, shore)
    while (fx <= w) {
        foam.lineTo(fx, shore + sin(fx * 0.02f + t * 1.3f) * 3f * density + sin(fx * 0.007f) * 6f * density)
        fx += w / 80f
    }
    drawPath(foam, Color.White.copy(alpha = 0.7f), style = Stroke(width = 3f * density, cap = StrokeCap.Round))
    // Duenengras links, im Lueftchen
    val gust = (sin(t * 0.25f) * 0.5f + 0.5f).pow(3) * (if (s.level >= 4) 1f else 0.3f)
    for (i in 0 until 22) {
        val bx = w * 0.02f + i * w * 0.006f
        val by = h * 0.98f
        val len = h * (0.08f + (i % 5) * 0.02f)
        val a = -0.2f + (i % 3) * 0.1f + sin(t * 1.2f + i) * 0.05f + gust * 0.25f
        drawLine(Color(0xFF6E7A4A), Offset(bx, by), Offset(bx + sin(a) * len, by - cos(a) * len), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
    }
}

// ---------- Level 3 + 4: Boot (Ich-Perspektive), ruhig bzw. im Sturm ----------
private fun DrawScope.drawBoat(t: Float, p: Float, s: CalmState, storm: Boolean) {
    val w = size.width
    val h = size.height
    val rough = ((s.level - 6) / 4f).coerceIn(0f, 1f) // 0 = Station 6 … 1 = Station 10
    val roll = (sin(t * (0.5f + 0.25f * rough)) * (2.2f + 5f * rough) + sin(t * 1.2f) * (0.8f + 2f * rough)) * 0.4f + s.tilt
    val bob = (if (storm) 0.02f else 0.01f) * h * sin(t * (if (storm) 1.1f else 0.8f))
    val horizon = h * 0.46f + bob

    withTransform({ rotate(roll, pivot = Offset(w / 2f, h)) }) {
        // Himmel
        val skyTop = if (storm) Color(0xFF14161E) else lerp(Color(0xFF4A5A70), Color(0xFF8DA6BE), 0.3f + 0.7f * p)
        val skyHor = if (storm) Color(0xFF2E3442) else lerp(Color(0xFFA6B4C0), Color(0xFFDCE4E8), 0.3f + 0.7f * p)
        drawRect(Brush.verticalGradient(listOf(skyTop, skyHor), startY = -h * 0.3f, endY = horizon), topLeft = Offset(-w * 0.2f, -h * 0.3f), size = Size(w * 1.4f, horizon + h * 0.3f))
        // Wolken (ziehen)
        val cloud = if (storm) Color(0xFF0E1016) else Color.White
        for (i in 0 until 6) {
            val cx = ((i * 0.23f + t * (if (storm) 0.02f else 0.006f)) % 1.4f - 0.2f) * w
            val cy = horizon * (0.15f + (i % 3) * 0.18f)
            val r = w * (0.12f + (i % 2) * 0.06f)
            drawCircle(Brush.radialGradient(listOf(cloud.copy(alpha = if (storm) 0.55f else 0.25f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        }
        // Meer mit Wellenkaemmen
        val seaTop = if (storm) Color(0xFF1C2C34) else Color(0xFF2E5868)
        val seaBot = if (storm) Color(0xFF0C161C) else Color(0xFF1C3A48)
        drawRect(Brush.verticalGradient(listOf(seaTop, seaBot), startY = horizon, endY = h * 1.2f), topLeft = Offset(-w * 0.2f, horizon), size = Size(w * 1.4f, h * 1.2f - horizon))
        val amp = 0.35f + 0.75f * rough
        for (k in 0 until 12) {
            val q = ((t * (if (storm) 0.09f else 0.05f) + k / 12f) % 1f)
            val y = horizon + (h - horizon) * q.pow(1.7f)
            val path = Path()
            var x = -w * 0.2f
            path.moveTo(x, y)
            while (x <= w * 1.2f) {
                path.lineTo(x, y + sin(x * 0.006f + t * 1.2f + k * 1.7f) * (4f + q * 26f) * amp * density)
                x += w / 50f
            }
            drawPath(path, Color.White.copy(alpha = (0.06f + 0.22f * q) * (if (storm) 1.2f else 1f)), style = Stroke(width = (1f + q * 3f) * density))
        }
        // Gischt bei Wellen-Ereignis
        val since = s.t - s.waveT
        if (since in 0f..1.5f) {
            val a = 1f - since / 1.5f
            val r = Random(s.waveT.toInt())
            for (i in 0 until 40) {
                val sx = w * (0.3f + r.nextFloat() * 0.4f)
                val sy = h * 0.75f - since * h * (0.15f + r.nextFloat() * 0.2f)
                drawCircle(Color.White.copy(alpha = 0.5f * a), radius = (1f + r.nextFloat() * 2.5f) * density, center = Offset(sx, sy))
            }
        }
    }

    // Regen im Sturm
    if (storm) {
        val r = Random(5)
        for (i in 0 until 160) {
            val speed = 1400f + r.nextFloat() * 600f
            val len = 26f + r.nextFloat() * 30f
            val x0 = r.nextFloat() * (w + 300f)
            val y = ((r.nextFloat() * (h + len) + t * speed) % (h + len)) - len
            val x = ((x0 - t * speed * 0.3f) % (w + 300f) + w + 300f) % (w + 300f) - 150f
            drawLine(Color(0xFFBFD0E0).copy(alpha = 0.22f), Offset(x, y), Offset(x - len * 0.3f, y + len), strokeWidth = 1.2f * density)
        }
    }

    // Bug des Boots (Ich-Perspektive): zwei Holzbordwaende laufen zur Spitze zusammen
    val wood = if (storm) Color(0xFF3A2A20) else Color(0xFF6A4A32)
    val woodLight = if (storm) Color(0xFF4E3828) else Color(0xFF8A6444)
    val tip = Offset(w * 0.5f + roll * w * 0.002f, h * 0.74f + bob)
    val hull = Path().apply {
        moveTo(0f, h)
        lineTo(w * 0.08f, h * 0.86f)
        lineTo(tip.x, tip.y)
        lineTo(w * 0.92f, h * 0.86f)
        lineTo(w, h)
        close()
    }
    drawPath(hull, Brush.verticalGradient(listOf(woodLight, wood), startY = tip.y, endY = h))
    // Planken
    for (k in 1..4) {
        val f = k / 5f
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(w * 0.08f * (1 - f) + tip.x * f, h * 0.86f + (tip.y - h * 0.86f) * f), Offset(w * 0.92f * (1 - f) + tip.x * f, h * 0.86f + (tip.y - h * 0.86f) * f), strokeWidth = 1.2f * density)
    }
    // Bordkante
    drawLine(woodLight, Offset(0f, h * 0.99f), tip, strokeWidth = 7f * density, cap = StrokeCap.Round)
    drawLine(woodLight, Offset(w, h * 0.99f), tip, strokeWidth = 7f * density, cap = StrokeCap.Round)
    // Sitzbank
    drawRect(lerp(wood, Color.Black, 0.2f), topLeft = Offset(w * 0.2f, h * 0.93f), size = Size(w * 0.6f, h * 0.03f))
}

private fun DrawScope.ridge(color: Color, baseY: Float, amp: Float, freq: Float, seed: Int) {
    val w = size.width
    val path = Path()
    path.moveTo(0f, baseY)
    val steps = 100
    for (i in 0..steps) {
        val x = w * i / steps
        val u = x / w * freq
        val y = baseY - (sin(u * 1.3f + seed) * 0.5f + sin(u * 2.9f + seed * 2.1f) * 0.25f + sin(u * 6.7f + seed * 0.7f) * 0.1f + 0.6f) * amp
        path.lineTo(x, y)
    }
    path.lineTo(w, baseY)
    path.close()
    drawPath(path, color)
}


/** Abschluss einer Runde: Ruhe in Prozent, Ziel, geschafft oder nicht – dann „Weiter“. */
@Composable
private fun ResultCard(passed: Boolean, percent: Int, goal: Int, station: Int, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
    val accent = if (passed) AmbientColors.ArtyText else AmbientColors.Gold
    androidx.compose.foundation.layout.Column(
        modifier
            .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); scaleX = 0.85f + 0.15f * a.value; scaleY = 0.85f + 0.15f * a.value }
            .clip(shape)
            .background(Color(0xF0101116))
            .border(1.dp, accent.copy(alpha = 0.8f), shape)
            .padding(horizontal = 44.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            if (passed) "GESCHAFFT" else "NOCH NICHT GANZ",
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 6.sp, color = accent),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
        androidx.compose.material3.Text(
            "$percent %",
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 54.sp, color = AmbientColors.Light),
        )
        androidx.compose.material3.Text(
            "Ruhe · Ziel $goal %",
            style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, letterSpacing = 1.sp, color = AmbientColors.Muted),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.material3.Text(
            if (passed) "+$station Punkte · weiter zur nächsten Station" else "Diese Station kommt beim nächsten Mal noch einmal.",
            style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = AmbientColors.Light),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(22.dp))
        Box(
            Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(Color(0xFF1E2A55))
                .border(1.dp, AmbientColors.Arty, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .clickable(onClick = onContinue)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) {
            androidx.compose.material3.Text(
                "WEITER",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 4.sp, color = AmbientColors.Light),
            )
        }
    }
}
