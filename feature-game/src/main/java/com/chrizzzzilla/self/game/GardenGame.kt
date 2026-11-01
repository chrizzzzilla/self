package com.chrizzzzilla.self.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val gLabel = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp, color = AmbientColors.Light)
private val gHead = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 4.sp, color = AmbientColors.Light)

private enum class GardenTool { CAN, HAND, SEED }

private class Hidden(val thing: SmallThing, var x: Float, var y: Float, val seed: Float, val seedKind: PlantKind? = null) {
    var reveal = 0f
}

private class Finger {
    var down = false
    var x = 0f
    var y = 0f
    var speed = 0f
}

private class Sparkle(val x: Float, val y: Float, val color: Color, val t0: Float, val big: Boolean)

/**
 * Demut · „Der Garten“. Eine Hand:
 *  Werkzeug unten waehlen (Giessen / Hand / Samen) → auf ein Beet tippen bzw. halten.
 *  Giessen: halten, bis die Anzeige im hellen Feld steht – zu viel und die Pflanze ruht heute.
 *  Hand: Unkraut zupfen, Blueten ernten. Gewachsen wird nur ueber Nacht.
 *  Bemerken: den Finger langsam ueber den Garten fuehren – kleine Dinge leuchten auf. Hastig verschwinden sie.
 */
@Composable
internal fun GardenGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { GardenStore(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<GardenData?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(GardenTool.CAN) }
    var seed by remember { mutableStateOf<PlantKind?>(null) }
    val time = remember { mutableFloatStateOf(0f) }
    val fig = remember { FigureState().apply { x = 0.5f; y = 0.92f } }
    val target = remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val watering = remember { mutableStateOf<Int?>(null) }
    val hidden = remember { mutableStateListOf<Hidden>() }
    val finger = remember { Finger() }
    val sparkles = remember { mutableStateListOf<Sparkle>() }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }
    var book by remember { mutableStateOf(false) }
    val rnd = remember { Random(System.nanoTime()) }
    /** 0 = Garten, 1 = Wildnis (Samen suchen) */
    var sceneIdx by remember { mutableIntStateOf(0) }
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(sceneIdx) {
        watering.value = null
        slide.animateTo(sceneIdx.toFloat(), androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 120f))
    }

    fun say(msg: String) { toast = msg; toastKey++ }

    LaunchedEffect(Unit) {
        val d = store.load()
        repeat(4) { hidden += spawnHidden(d, rnd) }
        data = d
        say("Nach links wischen: in der Wildnis findest du Samen.")
    }
    LaunchedEffect(toastKey) { if (toast != null) { delay(3000); toast = null } }

    fun exit() {
        val d = data
        if (d != null) scope.launch { store.save(d); onExit() } else onExit()
    }
    BackHandler { exit() }

    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        var last = 0L
        var sinceSave = 0f
        var nextSpawn = 0f
        while (true) {
            var tooMuchNow = false
            val foundNow = mutableListOf<Hidden>()
            var scared: Hidden? = null
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                time.floatValue += dt
                if (d.secondsToday < GARDEN_SECONDS) {
                    d.secondsToday += dt
                    if (d.secondsToday >= GARDEN_SECONDS) { watering.value = null; version++ }
                }
                // Giessen
                watering.value?.let { i ->
                    val p = d.plots[i]
                    val k = p.kind
                    if (k != null && !p.tooMuch) {
                        p.water += dt * 0.3f
                        fig.work = 0.3f
                        if (p.water > k.ideal + WATER_BAND) {
                            p.tooMuch = true
                            watering.value = null
                            tooMuchNow = true
                        }
                    }
                }
                // Bemerken
                for (hd in hidden) {
                    val dx = (finger.x - hd.x) / 0.07f
                    val dy = (finger.y - hd.y) / 0.11f
                    val near = finger.down && sceneIdx == 1 && slide.value > 0.95f && dx * dx + dy * dy < 1f
                    when {
                        near && finger.speed < 0.15f -> hd.reveal += dt * 0.75f
                        near && finger.speed > 0.7f -> { if (hd.reveal > 0.15f) scared = hd; hd.reveal -= dt * 3f }
                        near -> hd.reveal -= dt * 0.2f
                        else -> hd.reveal -= dt * 0.25f
                    }
                    hd.reveal = hd.reveal.coerceIn(0f, 1f)
                    if (hd.reveal >= 1f) foundNow += hd
                }
                // Figur
                fig.moving = false
                target.value?.let { (tx, ty) ->
                    val ddx = tx - fig.x
                    val ddy = ty - fig.y
                    val dist = sqrt(ddx * ddx + ddy * ddy)
                    val step = 0.4f * dt
                    if (dist <= step) { fig.x = tx; fig.y = ty; target.value = null } else {
                        fig.x += ddx / dist * step; fig.y += ddy / dist * step
                        fig.walk += dt * 11f; fig.moving = true
                        if (abs(ddx) > 0.002f) fig.facing = sign(ddx)
                    }
                }
                fig.work = (fig.work - dt).coerceAtLeast(0f)
                fig.gather = (fig.gather - dt).coerceAtLeast(0f)
                sparkles.removeAll { time.floatValue - it.t0 > 1.6f }
                sinceSave += dt
            }
            if (tooMuchNow) { say("Zu viel Wasser – heute ruht sie. Morgen wieder."); version++ }
            scared?.let { hd ->
                hidden.remove(hd)
                hidden += spawnHidden(d, rnd)
                say("Zu hastig – es ist verschwunden.")
            }
            for (hd in foundNow) {
                hidden.remove(hd)
                val sk = hd.seedKind
                if (sk != null) {
                    d.seeds[sk] = (d.seeds[sk] ?: 0) + 1
                    sparkles += Sparkle(hd.x + 1f, hd.y, sk.color, time.floatValue, true)
                    if (d.unlocked.add(sk)) say("Neue Art entdeckt: Samen von ${sk.label}!") else say("Samen gefunden: ${sk.label}")
                } else {
                    val first = d.found.add(hd.thing)
                    sparkles += Sparkle(hd.x + 1f, hd.y, AmbientColors.Gold, time.floatValue, true)
                    say((if (first) "" else "Wieder: ") + "${hd.thing.label}. ${hd.thing.line}")
                }
                nextSpawn = time.floatValue + 12f
                version++
            }
            if (hidden.size < 3 && time.floatValue > nextSpawn) {
                hidden += spawnHidden(d, rnd)
                nextSpawn = time.floatValue + 12f
            }
            if (sinceSave > 5f) { sinceSave = 0f; store.save(d) }
        }
    }

    val d = data
    Box(Modifier.fillMaxSize().background(AmbientColors.Ground)) {
        if (d == null) return@Box
        if (version < 0) return@Box
        val dayOver = d.secondsToday >= GARDEN_SECONDS

        fun plotDown(i: Int) {
            val p = d.plots[i]
            val (px, py) = PLOT_POS[i]
            target.value = (px - fig.facing * 0.0f + 0.05f) to (py + 0.1f).coerceAtMost(0.96f)
            if (dayOver) { say("Feierabend – der Garten wartet bis morgen."); return }
            val k = p.kind
            if (k == null) {
                val s = seed?.takeIf { (d.seeds[it] ?: 0) > 0 } ?: PlantKind.entries.firstOrNull { (d.seeds[it] ?: 0) > 0 }
                if (s == null) { say("Keine Samen mehr – ernten bringt neue."); return }
                d.seeds[s] = (d.seeds[s] ?: 0) - 1
                d.plots[i].apply { kind = s; stage = 0; water = 0f; tooMuch = false; weed = false }
                fig.gather = 0.4f
                sparkles += Sparkle(px, py - 0.02f, s.color, time.floatValue, false)
                say("${s.label} gepflanzt – jetzt gießen.")
                if (tool == GardenTool.SEED && (d.seeds[s] ?: 0) <= 0) tool = GardenTool.CAN
                version++
                return
            }
            when (tool) {
                GardenTool.CAN -> when {
                    k == null -> say("Hier wächst noch nichts – erst einen Samen pflanzen.")
                    p.blooming -> say("${k.label} blüht – mit der Hand ernten.")
                    p.tooMuch -> say("Für heute hat sie mehr als genug.")
                    else -> watering.value = i
                }
                GardenTool.HAND -> when {
                    p.weed -> {
                        p.weed = false
                        fig.gather = 0.4f
                        sparkles += Sparkle(px, py - 0.03f, Color(0xFF9AB08A), time.floatValue, false)
                        say("Unkraut gezupft.")
                        version++
                    }
                    k != null && p.blooming -> {
                        fig.gather = 0.4f
                        d.harvest[k] = (d.harvest[k] ?: 0) + 1
                        d.seeds[k] = (d.seeds[k] ?: 0) + 1 + rnd.nextInt(2)
                        d.plots[i].apply { kind = null; stage = 0; water = 0f; tooMuch = false }
                        sparkles += Sparkle(px, py - 0.18f, k.color, time.floatValue, true)
                        val next = PlantKind.entries.getOrNull(k.ordinal + 1)
                        if (next != null && next !in d.unlocked) {
                            d.unlocked += next
                            d.seeds[next] = (d.seeds[next] ?: 0) + 2
                            say("Geerntet: ${k.label} · ${k.gives}. Darin: Samen von ${next.label}!")
                        } else say("Geerntet: ${k.label} · ${k.gives}")
                        version++
                    }
                    k == null -> say("Leeres Beet – unten einen Samen wählen.")
                    else -> say("${k.label} wächst – das braucht seine Zeit.")
                }
                GardenTool.SEED -> {
                    val s = seed
                    when {
                        k != null -> say("Hier wächst schon ${k.label}.")
                        s == null || (d.seeds[s] ?: 0) <= 0 -> say("Keine Samen davon übrig.")
                        else -> {
                            d.seeds[s] = (d.seeds[s] ?: 0) - 1
                            d.plots[i].apply { kind = s; stage = 0; water = 0f; tooMuch = false; weed = false }
                            fig.gather = 0.4f
                            sparkles += Sparkle(px, py - 0.02f, s.color, time.floatValue, false)
                            say("${s.label} gepflanzt – jetzt gießen.")
                            if ((d.seeds[s] ?: 0) <= 0) tool = GardenTool.CAN
                            version++
                        }
                    }
                }
            }
        }

        fun plotUp(i: Int) {
            if (watering.value != i) return
            watering.value = null
            val p = d.plots[i]
            if (p.ok) {
                sparkles += Sparkle(PLOT_POS[i].first, PLOT_POS[i].second - 0.06f, Color(0xFF8FE0E8), time.floatValue, false)
                say("Genau richtig. Über Nacht wächst sie.")
            } else if (!p.tooMuch) say("Noch etwas mehr Wasser …")
            version++
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sceneH = maxHeight * 0.76f
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(sceneH)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            finger.down = true
                            finger.speed = 0f
                            finger.x = down.position.x / size.width
                            finger.y = down.position.y / size.height
                            var lastT = down.uptimeMillis
                            var lp = down.position
                            val startX = down.position.x
                            val startT = down.uptimeMillis
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val dtm = (ch.uptimeMillis - lastT).coerceAtLeast(1L).toFloat()
                                val v = (ch.position - lp).getDistance() / dtm / density
                                finger.speed = finger.speed * 0.75f + v * 0.25f
                                lastT = ch.uptimeMillis
                                lp = ch.position
                                finger.x = ch.position.x / size.width
                                finger.y = ch.position.y / size.height
                            }
                            finger.down = false
                            val dxTot = lp.x - startX
                            if (abs(dxTot) > size.width * 0.18f && lastT - startT < 800) {
                                if (dxTot < 0f && sceneIdx == 0) sceneIdx = 1
                                if (dxTot > 0f && sceneIdx == 1) sceneIdx = 0
                            }
                        }
                    },
            ) {
                drawGardenWorld(time.floatValue, (d.secondsToday / GARDEN_SECONDS).coerceIn(0f, 1f), d, fig, hidden, watering.value, tool == GardenTool.CAN, sparkles, slide.value, dayOver)
            }
            // Beete (Trefferflaechen)
            if (sceneIdx == 0 && slide.value < 0.05f) PLOT_POS.forEachIndexed { i, (px, py) ->
                Box(
                    Modifier
                        .offset(x = maxWidth * px - 60.dp, y = sceneH * py - 110.dp)
                        .size(120.dp, 130.dp)
                        .pointerInput(i, tool, seed, dayOver) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                plotDown(i)
                                waitForUpOrCancellation()
                                plotUp(i)
                            }
                        },
                )
            }

            // Kopf: Hinweis · Sammlung · Schliessen
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(56.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { SceneSwitch(sceneIdx) { sceneIdx = it } }
                GRound(onClick = { book = true }) {
                    Canvas(Modifier.size(18.dp)) {
                        val w = size.width; val h = size.height
                        drawRect(AmbientColors.Light, topLeft = Offset(w * 0.1f, h * 0.15f), size = Size(w * 0.8f, h * 0.7f), style = Stroke(1.4f * density))
                        drawLine(AmbientColors.Light, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f), strokeWidth = 1.4f * density)
                    }
                }
                Spacer(Modifier.width(12.dp))
                GRound(onClick = { exit() }) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                    }
                }
            }

            // Unten: Werkzeuge · Samen · Vorrat
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(maxHeight - sceneH)
                    .background(Brush.verticalGradient(listOf(Color(0xF0101116), Color(0xFA0A0B0E))))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolChip("GIESSEN", tool == GardenTool.CAN, onClick = { tool = GardenTool.CAN }) { CanGlyph() }
                ToolChip("HAND", tool == GardenTool.HAND, onClick = { tool = GardenTool.HAND }) { HandGlyph() }
                Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(Color(0x22FFFFFF)))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlantKind.entries.filter { it in d.unlocked }.forEach { k ->
                        val n = d.seeds[k] ?: 0
                        ToolChip(
                            "${k.label} · $n", tool == GardenTool.SEED && seed == k,
                            onClick = {
                                if (n > 0) { tool = GardenTool.SEED; seed = k } else say("Keine Samen von ${k.label} – ernten bringt neue.")
                            },
                            dim = n <= 0,
                        ) { SeedGlyph(k) }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(Color(0x22FFFFFF)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VORRAT", style = gHead.copy(fontSize = 9.sp))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val have = PlantKind.entries.filter { (d.harvest[it] ?: 0) > 0 }
                        if (have.isEmpty()) Text("noch leer", style = gLabel.copy(color = AmbientColors.Muted))
                        have.forEach { k ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PlantIcon(k, 26.dp, bloom = true)
                                Text("${d.harvest[k]}", style = gLabel.copy(fontSize = 10.sp))
                            }
                        }
                    }
                }
            }

            toast?.let {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 84.dp, start = 40.dp, end = 40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE6101116))
                        .border(1.dp, AmbientColors.Arty.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(it, textAlign = TextAlign.Center, style = gLabel.copy(fontSize = 13.sp))
                }
            }
        }

        if (book) SmallThingsBook(d) { book = false }
    }
}

private fun spawnHidden(d: GardenData, rnd: Random): Hidden {
    val x = 0.05f + rnd.nextFloat() * 0.9f
    val y = 0.25f + rnd.nextFloat() * 0.7f
    // gut die Haelfte sind Samen – meist bekannte Arten, manchmal eine neue
    if (rnd.nextFloat() < 0.55f) {
        val next = PlantKind.entries.firstOrNull { it !in d.unlocked }
        val kind = if (next != null && rnd.nextFloat() < 0.2f) next else (d.unlocked.randomOrNull(rnd) ?: PlantKind.LICHTBLUETE)
        return Hidden(SmallThing.SAMEN, x, y, rnd.nextFloat() * 10f, kind)
    }
    val open = SmallThing.entries.filter { it !in d.found && it != SmallThing.SAMEN }
    val thing = if (open.isNotEmpty() && rnd.nextFloat() < 0.8f) open.random(rnd)
    else listOf(SmallThing.FUNKE, SmallThing.TAU, SmallThing.FEDER).random(rnd)
    return Hidden(thing, x, y, rnd.nextFloat() * 10f)
}

@Composable
private fun SceneSwitch(current: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Color(0x66101116))
            .border(1.dp, Color(0x22FFFFFF), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("GARTEN", "WILDNIS").forEachIndexed { i, label ->
            Box(
                Modifier
                    .clip(shape)
                    .background(if (i == current) Color(0x336888EC) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(label, style = gHead.copy(fontSize = 10.sp, letterSpacing = 3.sp, color = if (i == current) AmbientColors.Light else AmbientColors.Muted))
            }
        }
    }
}

/** Braucht dieses Beet gerade etwas? */
private fun needsCare(p: Plot, d: GardenData): Boolean =
    p.weed || p.blooming || (p.kind != null && !p.ok && !p.tooMuch) || (p.kind == null && d.seeds.values.any { it > 0 })

/** Garten und Wildnis nebeneinander, per Wischen verschoben; dazu der Schleier (gruen = versorgt, blau = etwas zu tun). */
private fun DrawScope.drawGardenWorld(
    t: Float, day: Float, d: GardenData, fig: FigureState, hidden: List<Hidden>,
    watering: Int?, canSelected: Boolean, sparkles: List<Sparkle>, slide: Float, dayOver: Boolean,
) {
    val w = size.width
    val h = size.height
    if (slide < 0.999f) translate(left = -slide * w) {
        drawGarden(t, day, d, fig, emptyList(), watering, canSelected, emptyList())
        // Beete, die etwas brauchen, leuchten blau
        if (!dayOver) d.plots.forEachIndexed { i, p ->
            if (needsCare(p, d)) {
                val (px, py) = PLOT_POS[i]
                gGlow(Offset(px * w, py * h - h * 0.04f), h * 0.11f, AmbientColors.Arty, 0.16f + 0.08f * sin(t * 2.2f + i))
            }
        }
    }
    if (slide > 0.001f) translate(left = (1f - slide) * w) { drawWild(t, day, hidden) }
    // Funkeln (x ≥ 1 gehoert zur Wildnis)
    translate(left = -slide * w) {
        for (s in sparkles) {
            val e = t - s.t0
            val c = Offset(s.x * w, s.y * h)
            val big = if (s.big) 1f else 0.55f
            gGlow(c, h * 0.12f * big * (0.4f + e), s.color, (0.5f * (1f - e / 1.6f)).coerceAtLeast(0f))
            for (k in 0 until 12) {
                val ang = k / 12f * 2f * PI.toFloat() + k * 0.4f
                val dist = h * 0.1f * big * (1f - kotlin.math.exp(-3f * e))
                drawCircle(
                    s.color.copy(alpha = (1f - e / 1.4f).coerceIn(0f, 1f)), radius = 2.2f * density,
                    center = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist - e * h * 0.03f), blendMode = BlendMode.Plus,
                )
            }
        }
    }
    // Schleier im Garten
    val inGarden = 1f - slide
    if (inGarden > 0.01f && !dayOver) {
        val todo = d.plots.any { needsCare(it, d) }
        val col = if (todo) AmbientColors.Arty else Color(0xFF7FE0A0)
        val a = (if (todo) 0.22f + 0.08f * sin(t * 1.6f) else 0.16f) * inGarden
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to col.copy(alpha = a),
                center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f,
            ),
            blendMode = BlendMode.Plus,
        )
    }
}

/** Wildnis bei Nacht: hohes Gras, ein paar Baeume, versteckte Samen und kleine Dinge. */
private fun DrawScope.drawWild(t: Float, day: Float, hidden: List<Hidden>) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.42f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF04070A), Color(0xFF0B1A1C), Color(0xFF14262A), Color(0xFF080B0C)), startY = 0f, endY = h))
    val mx = w * (0.1f + 0.8f * day)
    val my = horizon - sin(day * PI.toFloat()) * horizon * 0.7f
    gGlow(Offset(mx, my), h * 0.45f, Color(0xFFC8F0E0), 0.1f)
    gGlow(Offset(mx, my), h * 0.1f, Color(0xFFE8FFF6), 0.4f)
    drawCircle(Color(0xFFF2FFF8), radius = h * 0.018f, center = Offset(mx, my))
    for (k in 0 until 50) {
        val tw = 0.5f + 0.5f * sin(t * (0.4f + gHash(k + 500)) + k)
        drawCircle(Color.White.copy(alpha = 0.4f * tw), radius = 1f * density, center = Offset(gHash(k * 3 + 501) * w, gHash(k * 7 + 502) * horizon * 0.9f))
    }
    val edge = Color(0xFFA8F0C8).copy(alpha = 0.22f)
    gRidge(horizon, h * 0.06f, 2.6f, 0.2f, Color(0xFF0F1A18), edge)
    // Baeume (rund, weich)
    for (k in 0 until 5) {
        val tx = w * (0.08f + k * 0.22f + gHash(k + 600) * 0.06f)
        val ty = h * (0.5f + gHash(k + 610) * 0.04f)
        val r = h * (0.08f + gHash(k + 620) * 0.05f)
        drawLine(Color(0xFF0A100E), Offset(tx, ty), Offset(tx, ty - r * 1.2f), strokeWidth = r * 0.15f)
        drawCircle(Color(0xFF0C1412), radius = r, center = Offset(tx, ty - r * 1.6f))
        drawArc(
            edge.copy(alpha = 0.35f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(tx - r, ty - r * 2.6f), size = Size(r * 2f, r * 2f), style = Stroke(1.2f * density), blendMode = BlendMode.Plus,
        )
    }
    gRidge(h * 0.56f, h * 0.02f, 3.4f, 1.3f, Color(0xFF0A110F), Color(0xFFA8F0C8).copy(alpha = 0.3f))
    // hohes Gras, sanft im Wind
    for (k in 0 until 140) {
        val gx = gHash(k + 700) * w
        val gy = h * (0.6f + gHash(k + 800) * 0.4f)
        val len = h * (0.05f + gHash(k + 900) * 0.07f) * (0.6f + (gy / h - 0.6f) * 1.5f)
        val bend = sin(t * 0.7f + gx / w * 4f) * len * 0.25f
        val col = Color(0xFF16261F)
        drawLine(col, Offset(gx, gy), Offset(gx + bend, gy - len), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
        if (k % 4 == 0) drawLine(Color(0xFFA8F0C8).copy(alpha = 0.18f), Offset(gx + bend * 0.6f, gy - len * 0.6f), Offset(gx + bend, gy - len), strokeWidth = 1f * density, blendMode = BlendMode.Plus)
    }
    // Versteckte Dinge: kaum sichtbar, bis man langsam darueber faehrt
    for (hd in hidden) {
        val c = Offset(hd.x * w, hd.y * h)
        val col = hd.seedKind?.color ?: AmbientColors.Gold
        val idle = 0.05f + 0.06f * (0.5f + 0.5f * sin(t * 1.3f + hd.seed))
        val a = idle + hd.reveal * 0.9f
        gGlow(c, h * (0.02f + hd.reveal * 0.05f), col, a * 0.7f)
        drawCircle(Color(0xFFFFF0CC).copy(alpha = a.coerceAtMost(1f)), radius = (1.2f + hd.reveal * 2f) * density, center = c)
        if (hd.reveal > 0.05f) {
            drawCircle(col.copy(alpha = 0.5f * hd.reveal), radius = h * (0.03f + 0.03f * hd.reveal), center = c, style = Stroke(1f * density), blendMode = BlendMode.Plus)
        }
    }
    for (k in 0 until 30) {
        val px = ((gHash(k + 1000) + t * (0.003f + gHash(k + 1100) * 0.008f)) % 1f) * w
        val py = (gHash(k + 1200) * 0.9f + sin(t * 0.3f + k) * 0.02f) * h
        drawCircle(Color(0xFFE0FFF0).copy(alpha = 0.15f + 0.1f * sin(t + k)), radius = 1.2f * density, center = Offset(px, py), blendMode = BlendMode.Plus)
    }
}

/** Was ist gerade sinnvoll? Damit man nie festhaengt. */
private fun gardenHint(d: GardenData, dayOver: Boolean): String {
    if (dayOver) return "Feierabend · der Garten wartet bis morgen"
    d.plots.firstOrNull { it.weed }?.let { return "Unkraut zupfen · HAND wählen, aufs Beet tippen" }
    d.plots.firstOrNull { it.blooming }?.let { return "${it.kind!!.label} blüht · mit der HAND ernten" }
    d.plots.firstOrNull { it.kind != null && !it.ok && !it.tooMuch }?.let {
        return "${it.kind!!.label} gießen · Beet halten, bis die Anzeige im hellen Feld ist"
    }
    val hasSeeds = d.seeds.values.any { it > 0 }
    if (hasSeeds && d.plots.any { it.kind == null }) return "Auf ein leeres Beet tippen zum Pflanzen"
    return "Alles versorgt · führe den Finger langsam durch den Garten"
}

@Composable
private fun GardenHint(d: GardenData, dayOver: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(Color(0x99101116))
            .border(1.dp, AmbientColors.Gold.copy(alpha = 0.35f), shape)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(gardenHint(d, dayOver), maxLines = 1, style = gLabel.copy(fontSize = 12.sp, color = AmbientColors.Gold))
    }
}

@Composable
private fun GRound(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit, dim: Boolean = false, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0x336888EC) else Color(0x14FFFFFF))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) AmbientColors.Arty else Color(0x22FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(4.dp))
        Text(label, maxLines = 1, style = gLabel.copy(fontSize = 10.sp, color = if (dim) AmbientColors.Muted else AmbientColors.Light))
    }
}

@Composable
private fun CanGlyph() {
    Canvas(Modifier.size(30.dp)) {
        val w = size.width; val h = size.height
        val c = Color(0xFF8FE0E8)
        gGlow(center, w * 0.6f, c, 0.25f)
        drawRoundRect(c, topLeft = Offset(w * 0.2f, h * 0.38f), size = Size(w * 0.45f, h * 0.45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(1.6f * density))
        drawLine(c, Offset(w * 0.65f, h * 0.5f), Offset(w * 0.9f, h * 0.3f), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
        drawArc(c, 180f, 180f, false, topLeft = Offset(w * 0.25f, h * 0.18f), size = Size(w * 0.35f, h * 0.38f), style = Stroke(1.6f * density))
    }
}

@Composable
private fun HandGlyph() {
    Canvas(Modifier.size(30.dp)) {
        val w = size.width; val h = size.height
        val c = Color(0xFFE8D8C0)
        gGlow(center, w * 0.6f, c, 0.2f)
        drawRoundRect(c, topLeft = Offset(w * 0.28f, h * 0.48f), size = Size(w * 0.44f, h * 0.36f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = Stroke(1.6f * density))
        for (k in 0 until 4) {
            val x = w * (0.33f + k * 0.11f)
            drawLine(c, Offset(x, h * 0.5f), Offset(x, h * (0.18f + abs(k - 1.5f) * 0.06f)), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
        }
        drawLine(c, Offset(w * 0.28f, h * 0.62f), Offset(w * 0.14f, h * 0.44f), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
    }
}

@Composable
private fun SeedGlyph(k: PlantKind) {
    Canvas(Modifier.size(30.dp)) {
        gGlow(center, size.minDimension * 0.55f, k.color, 0.35f)
        drawOval(k.color, topLeft = Offset(size.width * 0.38f, size.height * 0.3f), size = Size(size.width * 0.24f, size.height * 0.4f))
        drawLine(Color.White.copy(alpha = 0.6f), Offset(size.width * 0.5f, size.height * 0.36f), Offset(size.width * 0.5f, size.height * 0.62f), strokeWidth = 1f * density)
    }
}

@Composable
internal fun PlantIcon(k: PlantKind, size: Dp, bloom: Boolean) {
    Canvas(Modifier.size(size)) {
        val h = this.size.height
        drawPlant(k, if (bloom) k.days else (k.days / 2).coerceAtLeast(1), Offset(this.size.width / 2f, h * 0.95f), h * 4.2f, 0f, 0)
    }
}

@Composable
private fun SmallThingsBook(d: GardenData, onClose: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(40.dp)
                .clip(shape)
                .background(Color(0xF2101116))
                .border(1.dp, AmbientColors.Light.copy(alpha = 0.12f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DIE KLEINEN DINGE · ${d.found.size} / ${SmallThing.entries.size}", style = gHead)
                Spacer(Modifier.weight(1f))
                GRound(onClick = onClose) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (s in SmallThing.entries) {
                    val known = s in d.found
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(22.dp)) {
                            if (known) gGlow(center, size.minDimension * 0.6f, AmbientColors.Gold, 0.5f)
                            drawCircle(if (known) Color(0xFFFFF0CC) else Color(0x33FFFFFF), radius = 3f * density)
                        }
                        Spacer(Modifier.width(12.dp))
                        if (known) {
                            Column {
                                Text(s.label, style = gLabel.copy(fontSize = 14.sp))
                                Text(s.line, style = gLabel.copy(fontSize = 12.sp, color = AmbientColors.Muted, fontWeight = FontWeight.Light))
                            }
                        } else {
                            Text("· · ·", style = gLabel.copy(fontSize = 14.sp, color = AmbientColors.Muted))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Sie zeigen sich nur, wenn man den Finger langsam durch den Garten führt.",
                style = gLabel.copy(fontSize = 12.sp, color = AmbientColors.Muted),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------
// Zeichnen (Licht-Stil)
// ------------------------------------------------------------------------------------------

private fun DrawScope.gGlow(c: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c, blendMode = BlendMode.Plus,
    )
}

private fun gHash(i: Int): Float {
    val v = sin(i * 12.9898f + 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

private fun DrawScope.gRidge(baseY: Float, amp: Float, freq: Float, phase: Float, fill: Color, edge: Color) {
    val w = size.width
    val top = Path()
    val body = Path()
    val n = 80
    for (i in 0..n) {
        val x = w * i / n
        val y = baseY - (sin(x / w * freq + phase) * 0.6f + sin(x / w * freq * 2.3f + phase * 1.7f) * 0.4f) * amp
        if (i == 0) { top.moveTo(x, y); body.moveTo(x, y) } else { top.lineTo(x, y); body.lineTo(x, y) }
    }
    body.lineTo(w, size.height); body.lineTo(0f, size.height); body.close()
    drawPath(body, fill)
    drawPath(top, edge, style = Stroke(1.2f * density), blendMode = BlendMode.Plus)
}

/** Pflanze: leuchtender Stiel, Blaetter je Stufe, Knospe, Bluete mit aufsteigendem Licht. */
internal fun DrawScope.drawPlant(k: PlantKind, stage: Int, base: Offset, h: Float, t: Float, i: Int) {
    if (stage <= 0) {
        gGlow(base, h * 0.03f, k.color, 0.5f)
        drawCircle(k.color, radius = h * 0.006f, center = Offset(base.x, base.y - h * 0.004f))
        return
    }
    val g = (stage.toFloat() / k.days).coerceIn(0f, 1f)
    val height = h * (0.05f + 0.16f * g)
    val sway = sin(t * 0.8f + i) * h * 0.006f * g
    val top = Offset(base.x + sway, base.y - height)
    val stem = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(base.x + sway * 0.2f, base.y - height * 0.5f, top.x, top.y)
    }
    drawPath(stem, k.color.copy(alpha = 0.22f), style = Stroke(h * 0.012f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    drawPath(stem, k.color.copy(alpha = 0.9f), style = Stroke(h * 0.003f, cap = StrokeCap.Round))
    val leaves = stage.coerceAtMost(4)
    for (j in 0 until leaves) {
        val f = (j + 1f) / (leaves + 1f)
        val p = Offset(base.x + (top.x - base.x) * f, base.y + (top.y - base.y) * f)
        val side = if (j % 2 == 0) 1f else -1f
        val l = h * 0.035f * (0.6f + g * 0.6f)
        val leaf = Path().apply {
            moveTo(p.x, p.y)
            quadraticTo(p.x + side * l * 0.6f, p.y - l * 0.6f, p.x + side * l, p.y - l * 0.15f)
            quadraticTo(p.x + side * l * 0.5f, p.y + l * 0.25f, p.x, p.y)
            close()
        }
        drawPath(leaf, k.color.copy(alpha = 0.3f), blendMode = BlendMode.Plus)
        drawPath(leaf, k.color.copy(alpha = 0.7f), style = Stroke(h * 0.002f))
    }
    if (stage >= k.days) {
        val pulse = 0.85f + 0.15f * sin(t * 2f + i)
        gGlow(top, h * 0.09f * pulse, k.color, 0.45f)
        val pw = h * 0.02f
        val ph = h * 0.045f
        for (j in 0 until 6) {
            rotate(360f / 6f * j + t * 4f, pivot = top) {
                drawOval(k.color.copy(alpha = 0.55f), topLeft = Offset(top.x - pw / 2f, top.y - ph), size = Size(pw, ph), blendMode = BlendMode.Plus)
            }
        }
        drawCircle(Color(0xFFFFF6E0), radius = h * 0.008f, center = top)
        for (m in 0 until 4) {
            val q = (t * 0.3f + gHash(i * 10 + m)) % 1f
            val mp = Offset(top.x + sin(q * 6f + m) * h * 0.02f, top.y - q * h * 0.12f)
            drawCircle(k.color.copy(alpha = (1f - q) * 0.8f), radius = h * 0.003f, center = mp, blendMode = BlendMode.Plus)
        }
    } else if (stage == k.days - 1) {
        gGlow(top, h * 0.025f, k.color, 0.5f)
        drawCircle(k.color, radius = h * 0.007f, center = top)
    }
}

private fun DrawScope.drawGarden(
    t: Float, day: Float, d: GardenData, fig: FigureState, hidden: List<Hidden>,
    watering: Int?, canSelected: Boolean, sparkles: List<Sparkle>,
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.44f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05060C), Color(0xFF0E1022), Color(0xFF1A1626), Color(0xFF0A0B0E)), startY = 0f, endY = h))
    // Mond = Restzeit
    val mx = w * (0.1f + 0.8f * day)
    val my = horizon - sin(day * PI.toFloat()) * horizon * 0.7f
    gGlow(Offset(mx, my), h * 0.45f, Color(0xFFC8D4FF), 0.12f)
    gGlow(Offset(mx, my), h * 0.1f, Color(0xFFE8EEFF), 0.45f)
    drawCircle(Color(0xFFF2F4FF), radius = h * 0.018f, center = Offset(mx, my))
    for (k in 0 until 60) {
        val tw = 0.5f + 0.5f * sin(t * (0.4f + gHash(k)) + k)
        drawCircle(Color.White.copy(alpha = 0.45f * tw), radius = 1f * density, center = Offset(gHash(k * 3 + 1) * w, gHash(k * 7 + 2) * horizon * 0.9f))
    }
    val edge = Color(0xFFB8A8FF).copy(alpha = 0.22f)
    gRidge(horizon, h * 0.05f, 3.3f, 1.1f, Color(0xFF14131C), edge)
    gRidge(horizon + h * 0.06f, h * 0.035f, 5.1f, 0.4f, Color(0xFF0F0F15), edge.copy(alpha = 0.3f))
    // Nebelband
    val mist = sin(t * 0.07f) * w * 0.05f
    drawOval(
        Brush.radialGradient(listOf(Color(0x18C8C0FF), Color.Transparent), center = Offset(w * 0.5f + mist, h * 0.56f), radius = w * 0.6f),
        topLeft = Offset(-w * 0.1f + mist, h * 0.5f), size = Size(w * 1.2f, h * 0.12f),
    )
    gRidge(h * 0.6f, h * 0.015f, 4f, 2f, Color(0xFF0C0D11), Color(0xFFC8B48C).copy(alpha = 0.3f))

    // Beete + Pflanzen
    d.plots.forEachIndexed { i, p ->
        val (px, py) = PLOT_POS[i]
        val c = Offset(px * w, py * h)
        val bw = w * 0.11f
        val bh = h * 0.045f
        drawOval(Color(0xFF15110D), topLeft = Offset(c.x - bw / 2f, c.y - bh / 2f), size = Size(bw, bh))
        val wet = p.water > 0f
        drawOval(
            (if (wet) Color(0xFF8FE0E8) else Color(0xFFC8B48C)).copy(alpha = if (wet) 0.45f else 0.22f),
            topLeft = Offset(c.x - bw / 2f, c.y - bh / 2f), size = Size(bw, bh), style = Stroke(1.2f * density), blendMode = BlendMode.Plus,
        )
        if (p.weed) {
            for (k in 0 until 6) {
                val bx = c.x + (k - 2.5f) * bw * 0.12f
                val a = (k - 2.5f) * 0.25f + sin(t + k) * 0.05f
                drawLine(Color(0xFF4A5A3A), Offset(bx, c.y), Offset(bx + sin(a) * h * 0.05f, c.y - cos(a) * h * 0.05f), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
            }
        }
        p.kind?.let { k -> drawPlant(k, p.stage, Offset(c.x, c.y - bh * 0.1f), h, t, i) }
        // Giess-Anzeige
        val k = p.kind
        if (canSelected && k != null && !p.blooming) {
            val gx = c.x + bw * 0.62f
            val gy0 = c.y
            val gh = h * 0.13f
            drawLine(Color.White.copy(alpha = 0.14f), Offset(gx, gy0), Offset(gx, gy0 - gh), strokeWidth = 4f * density, cap = StrokeCap.Round)
            drawLine(
                AmbientColors.Gold.copy(alpha = 0.55f),
                Offset(gx, gy0 - gh * (k.ideal - WATER_BAND)), Offset(gx, gy0 - gh * (k.ideal + WATER_BAND)),
                strokeWidth = 6f * density,
            )
            if (p.water > 0f) {
                val col = when {
                    p.tooMuch -> Color(0xFFE08A6A)
                    p.ok -> Color(0xFFA8F0B0)
                    else -> Color(0xFF8FE0E8)
                }
                drawLine(col, Offset(gx, gy0), Offset(gx, gy0 - gh * p.water.coerceAtMost(1f)), strokeWidth = 2.5f * density, cap = StrokeCap.Round)
            }
        }
        if (watering == i) {
            for (m in 0 until 8) {
                val q = (t * 1.6f + gHash(m + i * 20)) % 1f
                val dx = (gHash(m + 7) - 0.5f) * bw * 0.6f
                val y0 = c.y - h * 0.3f + q * h * 0.28f
                drawLine(Color(0xFF9FD8FF).copy(alpha = 0.7f * (1f - q * 0.5f)), Offset(c.x + dx, y0), Offset(c.x + dx, y0 + h * 0.015f), strokeWidth = 1.4f * density, blendMode = BlendMode.Plus)
            }
        }
    }
    // Kleine Dinge (kaum sichtbar, bis man langsam darueber faehrt)
    for (hd in hidden) {
        val c = Offset(hd.x * w, hd.y * h)
        val idle = 0.05f + 0.06f * (0.5f + 0.5f * sin(t * 1.3f + hd.seed))
        val a = idle + hd.reveal * 0.9f
        gGlow(c, h * (0.02f + hd.reveal * 0.05f), AmbientColors.Gold, a * 0.7f)
        drawCircle(Color(0xFFFFF0CC).copy(alpha = a.coerceAtMost(1f)), radius = (1.2f + hd.reveal * 2f) * density, center = c)
        if (hd.reveal > 0.05f) {
            drawCircle(
                AmbientColors.Gold.copy(alpha = 0.5f * hd.reveal), radius = h * (0.03f + 0.03f * hd.reveal), center = c,
                style = Stroke(1f * density), blendMode = BlendMode.Plus,
            )
        }
    }
    // Staub
    for (k in 0 until 40) {
        val px = ((gHash(k + 100) + t * (0.003f + gHash(k + 200) * 0.008f)) % 1f) * w
        val py = (gHash(k + 150) * 0.9f + sin(t * 0.3f + k) * 0.02f) * h
        val a = (0.1f + gHash(k + 250) * 0.25f) * (0.6f + 0.4f * sin(t * 0.8f + k))
        drawCircle(Color(0xFFE8E0FF).copy(alpha = a), radius = (0.8f + gHash(k + 300) * 1.4f) * density, center = Offset(px, py), blendMode = BlendMode.Plus)
    }
    // Funkeln (Ernte, Pflanzen, richtig gegossen, Fund)
    for (s in sparkles) {
        val e = t - s.t0
        val c = Offset(s.x * w, s.y * h)
        val big = if (s.big) 1f else 0.55f
        gGlow(c, h * 0.12f * big * (0.4f + e), s.color, (0.5f * (1f - e / 1.6f)).coerceAtLeast(0f))
        for (k in 0 until 12) {
            val ang = k / 12f * 2f * PI.toFloat() + k * 0.4f
            val dist = h * 0.1f * big * (1f - kotlin.math.exp(-3f * e))
            drawCircle(
                s.color.copy(alpha = (1f - e / 1.4f).coerceIn(0f, 1f)), radius = 2.2f * density,
                center = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist - e * h * 0.03f), blendMode = BlendMode.Plus,
            )
        }
    }
}
