package com.chrizzzzilla.self.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val pLabel = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp, color = AmbientColors.Light)
private val pHead = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 4.sp, color = AmbientColors.Light)

private val WORDS = listOf(
    "Du wächst.", "Ich bin da.", "Langsam ist gut.", "Wir haben Zeit.", "Danke.",
    "Du bist schön, so wie du bist.", "Ein Blatt nach dem anderen.", "Alles darf werden.",
)

/** Positionen, die beim Zeichnen entstehen und fuer das Antippen gebraucht werden. */
private class Geo {
    var pot = Rect.Zero
    var plant = Rect.Zero
    var window = Rect.Zero
    var top = Offset.Zero
    val stem = ArrayList<Offset>()
    /** type: 0 Wildtrieb, 1 welkes Blatt, 2 Bluete, 3 verbluehte Bluete */
    val cuts = ArrayList<Pair<Int, Offset>>()
}

private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color, val size: Float)
private class Word(val text: String, val x: Float, val y: Float, val t0: Float)
private class GiftFly(val gift: Gift, val x: Float, val y: Float, val t0: Float)

private fun nowHour(): Float = LocalTime.now().let { it.hour + it.minute / 60f }

/**
 * Demut · „Die Pflanze am Fenster“. Eine Hand: unten einen Handgriff waehlen, dann an der Pflanze tun.
 * Was gerade gebraucht wird, leuchtet blau (Werkzeug + Stelle), ist alles gut, liegt ein gruener Schleier ueber allem.
 */
@Composable
internal fun PlantGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PlantStore(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<PlantData?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(Care.WATER) }
    val time = remember { mutableFloatStateOf(0f) }
    val hour = remember { mutableFloatStateOf(nowHour()) }
    val geo = remember { Geo() }
    val holding = remember { mutableStateOf<Care?>(null) }
    val bits = remember { mutableStateListOf<Bit>() }
    val words = remember { mutableStateListOf<Word>() }
    val flies = remember { mutableStateListOf<GiftFly>() }
    val curtainPos = remember { Animatable(0f) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }
    var stash by remember { mutableStateOf(false) }
    val rnd = remember { Random(System.nanoTime()) }
    val measurer = rememberTextMeasurer()

    fun say(msg: String) { toast = msg; toastKey++ }

    LaunchedEffect(Unit) {
        val d = store.load()
        curtainPos.snapTo(if (d.curtain) 1f else 0f)
        data = d
    }
    LaunchedEffect(toastKey) { if (toast != null) { delay(3000); toast = null } }

    fun exit() {
        val d = data
        if (d != null) scope.launch { store.save(d); onExit() } else onExit()
    }
    BackHandler { exit() }

    fun give(d: PlantData, g: Gift, at: Offset) {
        d.gifts[g] = (d.gifts[g] ?: 0) + 1
        flies += GiftFly(g, at.x, at.y, time.floatValue)
        say("Sie schenkt dir: ${g.label} · ${g.meaning}")
        version++
    }

    fun burst(at: Offset, color: Color, n: Int, spread: Float, up: Float = 0f) {
        repeat(n) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val v = spread * (0.3f + rnd.nextFloat())
            bits += Bit(at.x, at.y, cos(a) * v, sin(a) * v - up, 0.8f + rnd.nextFloat() * 0.6f, color, 1.5f + rnd.nextFloat() * 2f)
        }
    }

    val tmp = remember { Tmp() }

    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        var last = 0L
        var sinceSave = 0f
        var hourT = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                time.floatValue += dt
                hourT += dt
                if (hourT > 30f) { hourT = 0f; hour.floatValue = nowHour() }
                if (d.seconds < PLANT_SECONDS) {
                    d.seconds += dt
                    if (d.seconds >= PLANT_SECONDS) { holding.value = null; version++ }
                }
                when (holding.value) {
                    Care.WATER -> {
                        d.water += dt * 0.22f
                        if (rnd.nextFloat() < 0.5f) {
                            val p = geo.pot
                            bits += Bit(p.left + p.width * (0.3f + rnd.nextFloat() * 0.4f), p.top - p.height * 1.2f, 0f, 260f, 0.5f, Color(0xFF9FD8FF), 1.6f)
                        }
                        if (d.water > 0.95f && !d.soggy) {
                            d.soggy = true
                            say("Zu viel Wasser – die Erde ist nass. Heute ruht sie.")
                            holding.value = null
                        }
                        d.water = d.water.coerceAtMost(1f)
                    }
                    Care.TALK -> {
                        d.mood = (d.mood + dt * 0.1f).coerceAtMost(1f)
                        tmp.talkWordT += dt
                        if (tmp.talkWordT > 1.6f) {
                            tmp.talkWordT = 0f
                            words += Word(WORDS.random(rnd), geo.top.x + (rnd.nextFloat() - 0.5f) * geo.plant.width, geo.top.y + geo.plant.height * 0.3f, time.floatValue)
                        }
                        if (d.mood >= 1f && !d.talkedFull) {
                            d.talkedFull = true
                            val night = hour.floatValue < 6f || hour.floatValue >= 21f
                            if (rnd.nextFloat() < 0.6f) give(d, if (night) Gift.MONDTROPFEN else Gift.DUFT, geo.top)
                        }
                    }
                    Care.REPOT -> {
                        tmp.repotT += dt
                        if (tmp.repotT > 1.8f) {
                            holding.value = null
                            tmp.repotT = 0f
                            if (d.potTooSmall) {
                                d.pot++
                                burst(Offset(geo.pot.center.x, geo.pot.top), Color(0xFFC89A6A), 18, 160f, 80f)
                                say("Neuer Topf – mehr Raum für die Wurzeln.")
                                give(d, Gift.WURZEL, Offset(geo.pot.center.x, geo.pot.top))
                            } else say("Der Topf passt noch. Sie braucht erst mehr Wurzeln.")
                        }
                    }
                    else -> {}
                }
                for (b in bits) { b.x += b.vx * dt; b.y += b.vy * dt; b.vy += 60f * dt; b.life -= dt }
                bits.removeAll { it.life <= 0f }
                words.removeAll { time.floatValue - it.t0 > 3f }
                flies.removeAll { time.floatValue - it.t0 > 1.2f }
                sinceSave += dt
            }
            if (sinceSave > 5f) { sinceSave = 0f; store.save(d) }
        }
    }

    val d = data
    Box(Modifier.fillMaxSize().background(AmbientColors.Ground)) {
        if (d == null) return@Box
        if (version < 0) return@Box
        val dayOver = d.seconds >= PLANT_SECONDS
        val needs = d.needs(hour.floatValue)

        fun onHoldStart(c: Care) {
            if (dayOver) { say("Feierabend – sie wartet bis morgen."); return }
            when (c) {
                Care.WATER -> { if (d.soggy) { say("Die Erde ist noch nass."); return }; tmp.holdStartWater = d.water }
                Care.REPOT -> tmp.repotT = 0f
                Care.TALK -> tmp.talkWordT = 1.2f
                else -> {}
            }
            holding.value = c
        }

        fun onHoldEnd() {
            val c = holding.value ?: return
            holding.value = null
            if (c == Care.WATER) {
                if (d.water in 0.4f..0.9f) {
                    say("Gut gegossen.")
                    if (tmp.holdStartWater < 0.3f && rnd.nextFloat() < 0.4f) give(d, Gift.TAU, geo.top)
                }
                version++
            }
            if (c == Care.REPOT && tmp.repotT > 0f) say("Halten, bis der neue Topf da ist …")
        }

        fun onTap(p: Offset) {
            if (dayOver) { say("Feierabend – sie wartet bis morgen."); return }
            val onPlant = geo.plant.inflate(30f).contains(p) || geo.pot.inflate(30f).contains(p)
            when (tool) {
                Care.FEED -> if (onPlant) {
                    d.nutrients += 0.25f
                    burst(Offset(geo.pot.center.x, geo.pot.top), AmbientColors.Gold, 10, 90f, 40f)
                    if (d.nutrients > 1.05f && !d.burnt) { d.burnt = true; say("Zu viel Dünger – die Spitzen werden braun.") } else say("Gedüngt.")
                    d.nutrients = d.nutrients.coerceAtMost(1.2f)
                    if (rnd.nextFloat() < 0.12f) give(d, Gift.HUMUS, Offset(geo.pot.center.x, geo.pot.top))
                    version++
                }
                Care.PRUNE -> {
                    val hit = geo.cuts.minByOrNull { (it.second - p).getDistance() }
                    if (hit == null || (hit.second - p).getDistance() > 70f) { say("Hier gibt es nichts zu schneiden."); return }
                    burst(hit.second, Color(0xFFB8F07A), 8, 80f)
                    when (hit.first) {
                        0 -> { d.wild--; say("Wildtrieb gestutzt."); give(d, if (rnd.nextFloat() < 0.6f) Gift.LICHTBLATT else Gift.STECKLING, hit.second) }
                        1 -> { d.wilted--; say("Welkes Blatt entfernt."); if (rnd.nextFloat() < 0.5f) give(d, Gift.HUMUS, hit.second) }
                        2 -> { d.flowers--; give(d, Gift.BLUETE, hit.second) }
                        else -> { d.faded--; give(d, Gift.SAMEN, hit.second) }
                    }
                    version++
                }
                Care.TIE -> if (onPlant) {
                    when {
                        d.neededTies == 0 -> say("Sie ist noch klein genug, um allein zu stehen.")
                        d.ties >= d.neededTies -> say("Sie steht gut.")
                        else -> {
                            d.ties++
                            burst(p, AmbientColors.Gold, 8, 60f)
                            say("Angebunden – sie steht sicherer.")
                            if (rnd.nextFloat() < 0.25f) give(d, Gift.SAFT, p)
                            version++
                        }
                    }
                }
                Care.MIST -> if (onPlant) {
                    val before = d.humidity
                    d.humidity = (d.humidity + 0.15f).coerceAtMost(1f)
                    burst(geo.plant.center, Color(0xFFCFEFFF), 22, geo.plant.width * 0.6f)
                    if (before < 0.7f && d.humidity >= 0.7f && rnd.nextFloat() < 0.25f) give(d, Gift.TAU, geo.top)
                    version++
                }
                Care.CURTAIN -> {
                    d.curtain = !d.curtain
                    scope.launch { curtainPos.animateTo(if (d.curtain) 1f else 0f, tween(900)) }
                    say(if (d.curtain) "Vorhang zu." else "Vorhang auf.")
                    version++
                }
                Care.WATER, Care.TALK, Care.REPOT -> say(if (tool == Care.REPOT) "Auf dem Topf halten." else "Halten, nicht nur tippen.")
                Care.TURN -> say("Zum Drehen seitlich über die Pflanze wischen.")
                Care.DUST -> say("Zum Abstauben über die Blätter wischen.")
            }
        }

        fun onDrag(delta: Offset, at: Offset, startedOnPlant: Boolean) {
            if (dayOver || !startedOnPlant) return
            when (tool) {
                Care.TURN -> {
                    val before = d.lean
                    d.lean = (d.lean - abs(delta.x) / geo.window.width.coerceAtLeast(1f) * 1.2f).coerceAtLeast(0f)
                    if (before > 0f && d.lean == 0f) {
                        say("Gedreht – sie wächst wieder gerade.")
                        if (tmp.turnStart > 0.3f && rnd.nextFloat() < 0.35f) give(d, Gift.LICHTFADEN, geo.top)
                        version++
                    }
                }
                Care.DUST -> if (geo.plant.inflate(20f).contains(at)) {
                    val before = d.dust
                    d.dust = (d.dust - delta.getDistance() / geo.window.width.coerceAtLeast(1f) * 0.9f).coerceAtLeast(0f)
                    if (rnd.nextFloat() < 0.3f) bits += Bit(at.x, at.y, (rnd.nextFloat() - 0.5f) * 60f, -30f, 0.7f, Color(0xFFB8B0A8), 2f)
                    if (before > 0f && d.dust == 0f) {
                        say("Die Blätter glänzen.")
                        if (tmp.dustStart > 0.3f && rnd.nextFloat() < 0.35f) give(d, Gift.GLANZ, geo.top)
                        version++
                    }
                }
                else -> {}
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sceneH = maxHeight * 0.8f
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(sceneH)
                    .pointerInput(tool, dayOver) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val p0 = down.position
                            val onPot = geo.pot.inflate(40f).contains(p0)
                            val onPlant = onPot || geo.plant.inflate(30f).contains(p0)
                            tmp.turnStart = d.lean
                            tmp.dustStart = d.dust
                            when (tool) {
                                Care.WATER, Care.TALK -> if (onPlant) onHoldStart(tool)
                                Care.REPOT -> if (onPot) onHoldStart(tool)
                                else -> {}
                            }
                            var last = p0
                            var moved = 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val delta = ch.position - last
                                moved += delta.getDistance()
                                last = ch.position
                                onDrag(delta, ch.position, onPlant)
                                ch.consume()
                            }
                            val wasHold = holding.value != null
                            onHoldEnd()
                            if (!wasHold && moved < 24.dp.toPx()) onTap(p0)
                        }
                    },
            ) {
                drawRoom(
                    d, time.floatValue, hour.floatValue, geo, curtainPos.value, tool, needs, holding.value,
                    bits, words, flies, dayOver, measurer,
                )
            }

            // Kopf
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("STUFE ${d.stage} · TOPF ${d.pot}", style = pHead.copy(fontSize = 10.sp, color = AmbientColors.Muted))
                Spacer(Modifier.weight(1f))
                PRound(onClick = { exit() }) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                    }
                }
            }

            // Unten: Handgriffe · Vorrat
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(maxHeight - sceneH)
                    .background(Brush.verticalGradient(listOf(Color(0xF0101116), Color(0xFA0A0B0E))))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Care.entries.forEach { c ->
                        CareChip(c, selected = tool == c, needed = c in needs && !dayOver, t = time) { tool = c }
                    }
                }
                Box(Modifier.width(1.dp).height(44.dp).background(Color(0x22FFFFFF)))
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x14FFFFFF))
                        .clickable { stash = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.size(30.dp)) {
                        pGlow(center, size.minDimension * 0.6f, AmbientColors.Gold, 0.3f)
                        drawCircle(AmbientColors.Gold, radius = size.minDimension * 0.18f, center = center, style = Stroke(1.6f * density))
                    }
                    Text("VORRAT · ${d.giftCount}", style = pLabel.copy(fontSize = 10.sp))
                }
            }

            toast?.let {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 76.dp, start = 40.dp, end = 40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE6101116))
                        .border(1.dp, AmbientColors.Arty.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(it, textAlign = TextAlign.Center, style = pLabel.copy(fontSize = 13.sp))
                }
            }
        }

        if (stash) GiftStash(d) { stash = false }
    }
}

@Composable
private fun PRound(onClick: () -> Unit, content: @Composable () -> Unit) {
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
private fun CareChip(c: Care, selected: Boolean, needed: Boolean, t: androidx.compose.runtime.MutableFloatState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0x336888EC) else Color(0x14FFFFFF))
            .border(if (selected || needed) 1.5.dp else 1.dp, if (needed) AmbientColors.Arty else if (selected) AmbientColors.ArtyText else Color(0x22FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(30.dp)) {
            if (needed) pGlow(center, size.minDimension * 0.75f, AmbientColors.Arty, 0.3f + 0.2f * sin(t.floatValue * 3f))
            drawCareGlyph(c)
        }
        Spacer(Modifier.height(4.dp))
        Text(c.label, maxLines = 1, style = pLabel.copy(fontSize = 9.sp, letterSpacing = 1.sp))
    }
}

@Composable
private fun GiftStash(d: PlantData, onClose: () -> Unit) {
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
                Text("GESCHENKE DER PFLANZE · FÜR ME", style = pHead)
                Spacer(Modifier.weight(1f))
                PRound(onClick = onClose) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (g in Gift.entries) {
                    val n = d.gifts[g] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(26.dp)) { drawGift(g, center, size.minDimension * 0.5f, if (n > 0) 1f else 0.25f) }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (n > 0) "${g.label} ×$n" else "· · ·",
                            style = pLabel.copy(fontSize = 14.sp, color = if (n > 0) AmbientColors.Light else AmbientColors.Muted),
                        )
                        if (n > 0) Text("   ${g.meaning}", style = pLabel.copy(fontSize = 12.sp, color = AmbientColors.Muted))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Zeichnen (Licht-Stil)
// ------------------------------------------------------------------------------------------

private fun DrawScope.pGlow(c: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), color.copy(alpha = (alpha * 0.35f).coerceIn(0f, 1f)), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c, blendMode = BlendMode.Plus,
    )
}

private fun pHash(i: Int): Float {
    val v = sin(i * 12.9898f + 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

private fun DrawScope.drawGift(g: Gift, c: Offset, r: Float, alpha: Float) {
    pGlow(c, r * 1.4f, g.color, 0.4f * alpha)
    drawCircle(g.color.copy(alpha = alpha), radius = r * 0.32f, center = c)
    drawCircle(Color.White.copy(alpha = 0.6f * alpha), radius = r * 0.12f, center = c - Offset(r * 0.1f, r * 0.1f))
}

private fun DrawScope.drawCareGlyph(c: Care) {
    val w = size.width
    val h = size.height
    val col = AmbientColors.Light
    val sw = 1.5f * density
    when (c) {
        Care.WATER -> {
            drawRoundRect(Color(0xFF9FD8FF), topLeft = Offset(w * 0.2f, h * 0.4f), size = Size(w * 0.45f, h * 0.42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(sw))
            drawLine(Color(0xFF9FD8FF), Offset(w * 0.65f, h * 0.52f), Offset(w * 0.9f, h * 0.3f), strokeWidth = sw, cap = StrokeCap.Round)
            drawArc(Color(0xFF9FD8FF), 180f, 180f, false, topLeft = Offset(w * 0.25f, h * 0.2f), size = Size(w * 0.35f, h * 0.38f), style = Stroke(sw))
        }
        Care.FEED -> {
            drawRoundRect(AmbientColors.Gold, topLeft = Offset(w * 0.32f, h * 0.3f), size = Size(w * 0.36f, h * 0.55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(sw))
            drawLine(AmbientColors.Gold, Offset(w * 0.42f, h * 0.18f), Offset(w * 0.58f, h * 0.18f), strokeWidth = sw * 1.6f, cap = StrokeCap.Round)
            drawCircle(AmbientColors.Gold, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.58f))
        }
        Care.PRUNE -> {
            drawCircle(col, radius = w * 0.1f, center = Offset(w * 0.3f, h * 0.72f), style = Stroke(sw))
            drawCircle(col, radius = w * 0.1f, center = Offset(w * 0.7f, h * 0.72f), style = Stroke(sw))
            drawLine(col, Offset(w * 0.36f, h * 0.64f), Offset(w * 0.75f, h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, Offset(w * 0.64f, h * 0.64f), Offset(w * 0.25f, h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        Care.TIE -> {
            drawLine(Color(0xFFC8A070), Offset(w * 0.62f, h * 0.9f), Offset(w * 0.62f, h * 0.1f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
            drawLine(Color(0xFF8FE0A0), Offset(w * 0.4f, h * 0.9f), Offset(w * 0.45f, h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round)
            drawOval(AmbientColors.Gold, topLeft = Offset(w * 0.36f, h * 0.38f), size = Size(w * 0.32f, h * 0.14f), style = Stroke(sw))
        }
        Care.TURN -> {
            drawArc(col, 200f, 260f, false, topLeft = Offset(w * 0.2f, h * 0.2f), size = Size(w * 0.6f, h * 0.6f), style = Stroke(sw, cap = StrokeCap.Round))
            drawLine(col, Offset(w * 0.22f, h * 0.38f), Offset(w * 0.12f, h * 0.24f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, Offset(w * 0.22f, h * 0.38f), Offset(w * 0.36f, h * 0.32f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        Care.MIST -> {
            drawRoundRect(Color(0xFFCFEFFF), topLeft = Offset(w * 0.45f, h * 0.4f), size = Size(w * 0.3f, h * 0.48f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = Stroke(sw))
            drawLine(Color(0xFFCFEFFF), Offset(w * 0.5f, h * 0.3f), Offset(w * 0.7f, h * 0.3f), strokeWidth = sw, cap = StrokeCap.Round)
            for (k in 0 until 4) drawCircle(Color(0xFFCFEFFF), radius = w * 0.03f, center = Offset(w * (0.15f + (k % 2) * 0.12f), h * (0.2f + k * 0.08f)))
        }
        Care.DUST -> for (k in 0 until 3) {
            val y = h * (0.35f + k * 0.15f)
            val p = Path().apply { moveTo(w * 0.15f, y); cubicTo(w * 0.35f, y - h * 0.1f, w * 0.6f, y + h * 0.1f, w * 0.85f, y) }
            drawPath(p, col.copy(alpha = 0.9f - k * 0.2f), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Care.TALK -> for (k in 0 until 3) {
            drawArc(Color(0xFFD8B8FF), -45f, 90f, false, topLeft = Offset(w * (0.1f - k * 0.12f), h * (0.3f - k * 0.12f)), size = Size(w * (0.4f + k * 0.24f), h * (0.4f + k * 0.24f)), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Care.REPOT -> {
            val p = Path().apply { moveTo(w * 0.22f, h * 0.5f); lineTo(w * 0.78f, h * 0.5f); lineTo(w * 0.68f, h * 0.9f); lineTo(w * 0.32f, h * 0.9f); close() }
            drawPath(p, Color(0xFFC88A5A), style = Stroke(sw))
            drawLine(col, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.1f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.38f, h * 0.22f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.62f, h * 0.22f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        Care.CURTAIN -> {
            drawRect(col, topLeft = Offset(w * 0.15f, h * 0.15f), size = Size(w * 0.7f, h * 0.7f), style = Stroke(sw))
            for (k in 0 until 3) drawLine(Color(0xFFB8A8D0), Offset(w * (0.55f + k * 0.1f), h * 0.15f), Offset(w * (0.55f + k * 0.1f), h * 0.85f), strokeWidth = sw)
        }
    }
}

/** Zimmer mit Fenster (Himmel nach Uhrzeit), Fensterbank, Topf, Pflanze und alle Effekte. */
private fun DrawScope.drawRoom(
    d: PlantData, t: Float, hour: Float, geo: Geo, curtain: Float, tool: Care, needs: Set<Care>, holding: Care?,
    bits: List<Bit>, words: List<Word>, flies: List<GiftFly>, dayOver: Boolean, measurer: TextMeasurer,
) {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF08090C))
    // ---- Fenster ----
    val win = Rect(w * 0.14f, h * 0.06f, w * 0.68f, h * 0.68f)
    geo.window = win
    val night = hour < 6f || hour >= 21f
    val sky = when {
        night -> listOf(Color(0xFF03050C), Color(0xFF0A1024))
        hour < 8f -> listOf(Color(0xFF141830), Color(0xFF4A3442))
        hour < 17f -> listOf(Color(0xFF16304A), Color(0xFF3E5E7A))
        else -> listOf(Color(0xFF221A34), Color(0xFF6A3E36))
    }
    val sunNow = d.sunNow(hour)
    clipRect(win.left, win.top, win.right, win.bottom) {
        drawRect(Brush.verticalGradient(sky, startY = win.top, endY = win.bottom), topLeft = win.topLeft, size = win.size)
        if (night) {
            for (k in 0 until 40) {
                val tw = 0.5f + 0.5f * sin(t * (0.4f + pHash(k)) + k)
                drawCircle(Color.White.copy(alpha = 0.5f * tw), radius = 1f * density, center = Offset(win.left + pHash(k * 3 + 1) * win.width, win.top + pHash(k * 7 + 2) * win.height * 0.7f))
            }
            pGlow(Offset(win.left + win.width * 0.75f, win.top + win.height * 0.22f), win.height * 0.25f, Color(0xFFE8EEFF), 0.3f)
            drawCircle(Color(0xFFF2F4FF), radius = win.height * 0.03f, center = Offset(win.left + win.width * 0.75f, win.top + win.height * 0.22f))
        }
        if (sunNow) {
            val sc = Offset(win.left + win.width * 0.7f, win.top + win.height * 0.25f)
            pGlow(sc, win.height * 0.8f, Color(0xFFFFE6B0), 0.35f)
            pGlow(sc, win.height * 0.15f, Color(0xFFFFF4D8), 0.8f)
        }
        // Daecher draussen
        val roofs = Path()
        roofs.moveTo(win.left, win.bottom)
        var x = win.left
        var k = 0
        while (x < win.right) {
            val bw = win.width * (0.08f + pHash(k + 30) * 0.1f)
            val bh = win.height * (0.12f + pHash(k + 40) * 0.18f)
            roofs.lineTo(x, win.bottom - bh)
            roofs.lineTo(x + bw * 0.5f, win.bottom - bh - win.height * 0.04f)
            roofs.lineTo(x + bw, win.bottom - bh)
            x += bw
            k++
        }
        roofs.lineTo(win.right, win.bottom)
        roofs.close()
        drawPath(roofs, Color(0xFF0C0D12))
        if (night) for (j in 0 until 7) {
            val lx = win.left + pHash(j + 60) * win.width
            val ly = win.bottom - win.height * (0.04f + pHash(j + 70) * 0.1f)
            pGlow(Offset(lx, ly), win.height * 0.025f, Color(0xFFFFC870), 0.6f)
        }
        // Vorhang
        if (curtain > 0.01f) {
            val cw = win.width * 0.85f * curtain
            for (f in 0 until 8) {
                val fx = win.right - cw + cw * f / 8f
                drawRect(
                    Brush.horizontalGradient(listOf(Color(0xF02A2432), Color(0xF0352D3E), Color(0xF02A2432)), startX = fx, endX = fx + cw / 8f),
                    topLeft = Offset(fx, win.top), size = Size(cw / 8f + 1f, win.height),
                )
            }
            drawLine(Color(0xFFB8A8D0).copy(alpha = 0.25f), Offset(win.right - cw, win.top), Offset(win.right - cw, win.bottom), strokeWidth = 1.2f * density, blendMode = BlendMode.Plus)
        }
    }
    // Rahmen
    val frame = Color(0xFF15161C)
    drawRect(frame, topLeft = win.topLeft, size = win.size, style = Stroke(10f * density))
    drawLine(frame, Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), strokeWidth = 7f * density)
    drawLine(frame, Offset(win.left, win.top + win.height * 0.42f), Offset(win.right, win.top + win.height * 0.42f), strokeWidth = 7f * density)
    drawRect(Color(0xFFE8EEFF).copy(alpha = 0.12f), topLeft = win.topLeft, size = win.size, style = Stroke(1f * density), blendMode = BlendMode.Plus)
    // Lichtfall ins Zimmer
    val light = (if (sunNow) 0.22f else if (night) 0.04f else 0.1f) * (1f - curtain * 0.8f)
    val shaft = Path().apply {
        moveTo(win.left, win.bottom); lineTo(win.right, win.bottom)
        lineTo(win.right + w * 0.18f, h); lineTo(win.left + w * 0.08f, h); close()
    }
    drawPath(shaft, Brush.verticalGradient(listOf(Color(0xFFFFF0D0).copy(alpha = light), Color.Transparent), startY = win.bottom, endY = h), blendMode = BlendMode.Plus)
    // Fensterbank
    val sillY = h * 0.74f
    drawRect(Color(0xFF17151B), topLeft = Offset(w * 0.08f, sillY), size = Size(w * 0.68f, h * 0.035f))
    drawLine(Color(0xFFE8DCC8).copy(alpha = 0.3f + light), Offset(w * 0.08f, sillY), Offset(w * 0.76f, sillY), strokeWidth = 1.2f * density, blendMode = BlendMode.Plus)

    // ---- Topf ----
    val potCx = w * 0.41f
    val pw = h * (0.09f + 0.025f * d.pot)
    val ph = h * (0.08f + 0.012f * d.pot)
    val potTop = sillY - ph
    geo.pot = Rect(potCx - pw / 2f, potTop, potCx + pw / 2f, sillY)
    val pot = Path().apply {
        moveTo(potCx - pw / 2f, potTop); lineTo(potCx + pw / 2f, potTop)
        lineTo(potCx + pw * 0.39f, sillY); lineTo(potCx - pw * 0.39f, sillY); close()
    }
    if (d.potTooSmall) {
        for (r in 0 until 5) {
            val rx = potCx + (r - 2f) * pw * 0.12f
            drawLine(Color(0xFFC89A6A).copy(alpha = 0.7f), Offset(rx, sillY - ph * 0.1f), Offset(rx + sin(r * 2f) * pw * 0.1f, sillY + h * 0.02f), strokeWidth = 1.2f * density)
        }
    }
    drawPath(pot, Color(0xFF2E1E16))
    drawPath(pot, Color(0xFFC88A5A).copy(alpha = 0.45f), style = Stroke(1.3f * density), blendMode = BlendMode.Plus)
    drawRect(Color(0xFF3A281C), topLeft = Offset(potCx - pw * 0.53f, potTop - ph * 0.08f), size = Size(pw * 1.06f, ph * 0.14f))
    val soil = Color(0xFF120D0A)
    drawOval(soil, topLeft = Offset(potCx - pw * 0.46f, potTop - ph * 0.06f), size = Size(pw * 0.92f, ph * 0.14f))
    if (d.water > 0.45f) drawOval(Color(0xFF9FD8FF).copy(alpha = 0.18f * d.water), topLeft = Offset(potCx - pw * 0.4f, potTop - ph * 0.04f), size = Size(pw * 0.8f, ph * 0.1f), blendMode = BlendMode.Plus)

    // ---- Pflanze ----
    val base = Offset(potCx, potTop)
    val stage = d.stage
    val height = (h * (0.08f + 0.04f * stage)).coerceAtMost(h * 0.56f)
    val sag = (d.neededTies - d.ties).coerceAtLeast(0) * 0.14f
    val leanX = -d.lean * 0.3f
    val sway = sin(t * 0.6f) * h * 0.002f
    fun stemAt(s: Float) = Offset(
        base.x + leanX * height * s + sag * height * s * s + sway * s * 4f,
        base.y - height * s * (1f - 0.18f * sag * s),
    )
    geo.stem.clear()
    val stemPath = Path()
    for (i in 0..24) {
        val p = stemAt(i / 24f)
        geo.stem += p
        if (i == 0) stemPath.moveTo(p.x, p.y) else stemPath.lineTo(p.x, p.y)
    }
    val top = stemAt(1f)
    geo.top = top
    geo.plant = Rect(base.x - height * 0.45f, top.y - h * 0.04f, base.x + height * 0.45f, base.y)
    geo.cuts.clear()
    val green = Color(0xFF8FE0A0)
    val happy = d.mood
    // Stuetzstab + Bindungen
    if (d.neededTies > 0 || d.ties > 0) {
        val sx = base.x + pw * 0.22f
        drawLine(Color(0xFF4A3C2A), Offset(sx, base.y), Offset(sx, base.y - height * 0.85f), strokeWidth = 3f * density, cap = StrokeCap.Round)
        drawLine(Color(0xFFC8A070).copy(alpha = 0.35f), Offset(sx, base.y), Offset(sx, base.y - height * 0.85f), strokeWidth = 1f * density, blendMode = BlendMode.Plus)
        for (j in 0 until d.ties) {
            val s = (j + 1f) / (d.neededTies + 1f) * 0.8f
            val sp = stemAt(s)
            drawOval(AmbientColors.Gold.copy(alpha = 0.8f), topLeft = Offset(minOf(sp.x, sx) - 3f, sp.y - 3f * density), size = Size(abs(sx - sp.x) + 6f, 6f * density), style = Stroke(1.2f * density))
        }
    }
    drawPath(stemPath, green.copy(alpha = 0.2f), style = Stroke(9f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    drawPath(stemPath, Color(0xFF2E5A3A), style = Stroke(3.2f * density, cap = StrokeCap.Round))
    drawPath(stemPath, green.copy(alpha = 0.7f), style = Stroke(1f * density, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    // Blaetter
    val leaves = (2 + stage * 2).coerceAtMost(26)
    for (i in 0 until leaves) {
        val s = 0.12f + 0.84f * (i + 1f) / (leaves + 1f)
        val p = stemAt(s)
        val side = if (i % 2 == 0) 1f else -1f
        val wilted = i / 2 < d.wilted && i % 2 == 0
        val l = h * 0.038f * (0.8f + stage * 0.04f) * (1.15f - s * 0.45f)
        val droop = if (wilted) 0.9f else (0.35f - happy * 0.2f + (if (d.soggy) 0.3f else 0f))
        val ang = side * (1.05f - droop * 0.5f) + sin(t * 0.8f + i) * 0.04f
        val dir = Offset(sin(ang), -cos(ang) + droop * 0.6f)
        val tip = p + dir * l
        val perp = Offset(-dir.y, dir.x) * (l * 0.32f)
        val mid = p + dir * (l * 0.5f)
        val leaf = Path().apply {
            moveTo(p.x, p.y)
            quadraticTo(mid.x + perp.x, mid.y + perp.y, tip.x, tip.y)
            quadraticTo(mid.x - perp.x, mid.y - perp.y, p.x, p.y)
            close()
        }
        val fill = if (wilted) Color(0xFF3E3220) else Color(0xFF16301E)
        val edge = if (wilted) Color(0xFFC8A060) else green
        drawPath(leaf, fill)
        drawPath(leaf, edge.copy(alpha = if (wilted) 0.5f else 0.35f + 0.3f * happy), style = Stroke(1.1f * density), blendMode = BlendMode.Plus)
        drawLine(edge.copy(alpha = 0.3f), p, tip, strokeWidth = 0.8f * density, blendMode = BlendMode.Plus)
        if (d.burnt && !wilted) drawCircle(Color(0xFF8A5A30), radius = 2f * density, center = tip)
        if (d.dust > 0.15f && !wilted) {
            for (k in 0 until 3) {
                val q = p + dir * (l * (0.3f + k * 0.2f)) + perp * ((pHash(i * 7 + k) - 0.5f) * 0.8f)
                drawCircle(Color(0xFF8A847C).copy(alpha = d.dust * 0.8f), radius = 1.3f * density, center = q)
            }
        }
        if (wilted) geo.cuts += 1 to mid
    }
    // Wildtriebe
    for (k in 0 until d.wild) {
        val s = 0.3f + k * 0.15f
        val p = stemAt(s)
        val side = if (k % 2 == 0) 1f else -1f
        val end = p + Offset(side * h * 0.12f, -h * 0.07f + sin(t + k) * h * 0.004f)
        val shoot = Path().apply { moveTo(p.x, p.y); quadraticTo(p.x + side * h * 0.08f, p.y + h * 0.01f, end.x, end.y) }
        drawPath(shoot, Color(0xFFD8F0A0).copy(alpha = 0.7f), style = Stroke(1.4f * density, cap = StrokeCap.Round))
        drawCircle(Color(0xFFD8F0A0).copy(alpha = 0.6f), radius = 3f * density, center = end)
        geo.cuts += 0 to (p + (end - p) * 0.3f)
    }
    // Blueten
    val flowerSpots = listOf(top, stemAt(0.82f) + Offset(h * 0.035f, 0f), stemAt(0.7f) - Offset(h * 0.04f, 0f), stemAt(0.9f) - Offset(h * 0.03f, 0f))
    var fi = 0
    repeat(d.flowers.coerceAtMost(4)) {
        val c = flowerSpots[fi++ % flowerSpots.size]
        val pulse = 0.85f + 0.15f * sin(t * 2f + fi)
        pGlow(c, h * 0.07f * pulse, Color(0xFFFF9ABE), 0.45f)
        for (j in 0 until 5) {
            rotate(72f * j + t * 3f, pivot = c) {
                drawOval(Color(0xFFFF9ABE).copy(alpha = 0.6f), topLeft = Offset(c.x - h * 0.009f, c.y - h * 0.026f), size = Size(h * 0.018f, h * 0.026f), blendMode = BlendMode.Plus)
            }
        }
        drawCircle(AmbientColors.Gold, radius = h * 0.006f, center = c)
        geo.cuts += 2 to c
    }
    repeat(d.faded.coerceAtMost(4)) {
        val c = flowerSpots[fi++ % flowerSpots.size] + Offset(0f, h * 0.01f)
        for (j in 0 until 4) {
            rotate(90f * j + 30f, pivot = c) {
                drawOval(Color(0xFF6A5A50), topLeft = Offset(c.x - h * 0.006f, c.y - h * 0.018f), size = Size(h * 0.012f, h * 0.018f))
            }
        }
        geo.cuts += 3 to c
    }
    // Glueck: leises Leuchten um die Pflanze
    pGlow(Offset(base.x, base.y - height * 0.6f), height * 0.9f, green, 0.05f + 0.1f * happy)

    // ---- blaue Stellen fuer das gewaehlte Werkzeug ----
    if (!dayOver && tool in needs) {
        val pulse = 0.25f + 0.15f * sin(t * 3f)
        when (tool) {
            Care.WATER, Care.FEED, Care.REPOT -> pGlow(Offset(potCx, potTop), pw * 0.9f, AmbientColors.Arty, pulse)
            Care.PRUNE -> geo.cuts.filter { it.first != 2 }.forEach {
                drawCircle(AmbientColors.Arty.copy(alpha = 0.8f), radius = 12f * density, center = it.second, style = Stroke(1.6f * density), blendMode = BlendMode.Plus)
            }
            Care.CURTAIN -> pGlow(win.center, win.width * 0.5f, AmbientColors.Arty, pulse * 0.6f)
            else -> pGlow(Offset(base.x, base.y - height * 0.5f), height * 0.6f, AmbientColors.Arty, pulse)
        }
    }
    // Anzeigen am Topf
    if (tool == Care.WATER || tool == Care.FEED) {
        val gx = potCx + pw * 0.72f
        val gh = h * 0.14f
        val (value, lo, hi, col) = if (tool == Care.WATER) Quad(d.water, 0.4f, 0.9f, Color(0xFF9FD8FF)) else Quad(d.nutrients / 1.2f, 0.25f / 1.2f, 1.0f / 1.2f, AmbientColors.Gold)
        drawLine(Color.White.copy(alpha = 0.14f), Offset(gx, sillY), Offset(gx, sillY - gh), strokeWidth = 4f * density, cap = StrokeCap.Round)
        drawLine(Color(0xFF7FE0A0).copy(alpha = 0.45f), Offset(gx, sillY - gh * lo), Offset(gx, sillY - gh * hi), strokeWidth = 6f * density)
        drawLine(col, Offset(gx, sillY), Offset(gx, sillY - gh * value.coerceIn(0f, 1f)), strokeWidth = 2.5f * density, cap = StrokeCap.Round)
    }
    // Umtopfen-Fortschritt / Zureden-Ringe
    if (holding == Care.TALK) {
        for (k in 0 until 3) {
            val q = (t * 0.5f + k / 3f) % 1f
            drawCircle(Color(0xFFD8B8FF).copy(alpha = (1f - q) * 0.35f), radius = height * (0.2f + q * 0.6f), center = Offset(base.x, base.y - height * 0.55f), style = Stroke(1.2f * density), blendMode = BlendMode.Plus)
        }
    }
    if (holding == Care.REPOT) pGlow(Offset(potCx, potTop + ph * 0.5f), pw * (1f + 0.2f * sin(t * 8f)), Color(0xFFC88A5A), 0.45f)
    // Partikel, Worte, Geschenke
    for (b in bits) drawCircle(b.color.copy(alpha = (b.life).coerceIn(0f, 1f) * 0.8f), radius = b.size * density, center = Offset(b.x, b.y), blendMode = BlendMode.Plus)
    for (wd in words) {
        val e = t - wd.t0
        val a = (if (e < 0.4f) e / 0.4f else 1f - ((e - 2f) / 1f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val lay = measurer.measure(wd.text, TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 15.sp, color = Color(0xFFE8DCFF).copy(alpha = a)))
        drawText(lay, topLeft = Offset(wd.x - lay.size.width / 2f, wd.y - e * h * 0.04f))
    }
    for (f in flies) {
        val e = ((t - f.t0) / 1.1f).coerceIn(0f, 1f)
        val ease = e * e * (3f - 2f * e)
        val tx = w * 0.92f
        val ty = h * 1.05f
        val c = Offset(f.x + (tx - f.x) * ease, f.y + (ty - f.y) * ease - sin(e * PI.toFloat()) * h * 0.15f)
        drawGift(f.gift, c, h * 0.03f * (1.3f - 0.5f * e), 1f)
    }
    // Schleier: gruen = alles gut, blau = etwas zu tun
    if (!dayOver) {
        val todo = needs.isNotEmpty()
        val col = if (todo) AmbientColors.Arty else Color(0xFF7FE0A0)
        val a = if (todo) 0.2f + 0.07f * sin(t * 1.6f) else 0.16f
        drawRect(
            Brush.radialGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to col.copy(alpha = a), center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f),
            blendMode = BlendMode.Plus,
        )
    } else {
        drawRect(Color(0xFF020306).copy(alpha = 0.35f))
    }
}

private class Tmp {
    var holdStartWater = 0f
    var repotT = 0f
    var talkWordT = 0f
    var turnStart = 0f
    var dustStart = 0f
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Color)

@Suppress("unused")
private fun keepPlantImports() = min(0f, 0f)
