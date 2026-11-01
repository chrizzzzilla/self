package com.chrizzzzilla.self.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.sin

private val labelStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.5.sp, color = AmbientColors.Light)
private val headStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 4.sp, color = AmbientColors.Light)

/**
 * Ehrgeiz · „Die Werkstatt“ (Steinzeit). Eine Hand reicht:
 *  Rohstoffe in der Landschaft antippen (sammeln) → Teile aus dem Lager in die Ablage tippen →
 *  passt es zu einem Rezept, erscheint der Arbeitskreis → klopfen / kreisen / reiben / halten.
 * Kraft verbraucht sich beim Arbeiten und laedt sich wieder auf. Die Sonne zeigt die 10 Minuten.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkshopGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WorkshopStore(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<WorkshopData?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val tray = remember { mutableStateListOf<Item>() }
    val kraft = remember { mutableFloatStateOf(1f) }
    val progress = remember { mutableFloatStateOf(0f) }
    val holding = remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }
    var book by remember { mutableStateOf(false) }
    val time = remember { mutableFloatStateOf(0f) }
    val fig = remember { FigureState() }
    val target = remember { mutableStateOf<Node?>(null) }
    val flyers = remember { mutableStateListOf<Flyer>() }
    val craftFx = remember { mutableStateOf<Pair<Item, Float>?>(null) }
    val pulse = remember { mutableFloatStateOf(0f) }

    fun say(msg: String) { toast = msg; toastKey++ }

    LaunchedEffect(Unit) { data = store.load() }

    fun exit() {
        val d = data
        if (d != null) {
            tray.forEach { d.inventory[it] = (d.inventory[it] ?: 0) + 1 }
            tray.clear()
            scope.launch { store.save(d); onExit() }
        } else onExit()
    }
    BackHandler { exit() }

    // Zeit, Kraft, Halten, Speichern
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        var last = 0L
        var sinceSave = 0f
        var arrived: Node? = null
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                time.floatValue += dt
                if (d.secondsToday < DAILY_SECONDS) {
                    d.secondsToday += dt
                    if (d.secondsToday >= DAILY_SECONDS) version++
                }
                kraft.floatValue = (kraft.floatValue + dt * 0.07f).coerceAtMost(1f)
                val r = match(tray, d)
                if (holding.value && r != null && r.work == Work.HOLD && kraft.floatValue > 0.02f) {
                    progress.floatValue += dt
                    kraft.floatValue -= dt * 0.03f
                    fig.work = 0.3f
                }
                sinceSave += dt
                // Spielfigur
                fig.moving = false
                target.value?.let { n ->
                    val tx = n.x
                    val ty = (n.y + 0.16f).coerceAtMost(0.95f)
                    val dx = tx - fig.x
                    val dy = ty - fig.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val step = 0.42f * dt
                    if (dist <= step) {
                        fig.x = tx; fig.y = ty
                        target.value = null
                        arrived = n
                    } else {
                        fig.x += dx / dist * step
                        fig.y += dy / dist * step
                        fig.walk += dt * 11f
                        fig.moving = true
                        if (abs(dx) > 0.002f) fig.facing = sign(dx)
                    }
                }
                fig.work = (fig.work - dt).coerceAtLeast(0f)
                fig.gather = (fig.gather - dt).coerceAtLeast(0f)
                pulse.floatValue = (pulse.floatValue - dt).coerceAtLeast(0f)
                flyers.removeAll { time.floatValue - it.t0 > 0.75f }
                craftFx.value?.let { if (time.floatValue - it.second > 1.9f) craftFx.value = null }
            }
            arrived?.let { n ->
                arrived = null
                val left = d.nodes[n.item] ?: 0
                when {
                    n.item != Item.WASSER && left <= 0 -> say("${n.item.label}: für heute leer.")
                    kraft.floatValue < 0.03f -> say("Kurz verschnaufen …")
                    else -> {
                        if (n.item != Item.WASSER) d.nodes[n.item] = left - 1
                        d.inventory[n.item] = (d.inventory[n.item] ?: 0) + 1
                        kraft.floatValue -= 0.03f
                        fig.gather = 0.4f
                        flyers += Flyer(n.item, n.x, n.y, time.floatValue)
                        version++
                    }
                }
            }
            if (sinceSave > 5f) {
                sinceSave = 0f
                store.save(d)
            }
            val r = match(tray, d)
            if (r != null && progress.floatValue >= r.amount) {
                craft(d, r, tray).let { say(it) }
                craftFx.value = r.output to time.floatValue
                fig.work = 0f
                progress.floatValue = 0f
                version++
                store.save(d)
            }
        }
    }

    LaunchedEffect(toastKey) {
        if (toast != null) { delay(2600); toast = null }
    }

    val d = data
    Box(Modifier.fillMaxSize().background(AmbientColors.Ground)) {
        if (d == null) return@Box
        if (version < 0) return@Box // Neuaufbau bei Aenderungen
        val dayOver = d.secondsToday >= DAILY_SECONDS
        val recipe = match(tray, d)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sceneH = maxHeight * 0.66f
            // Landschaft + Sonne + gebaute Einrichtungen
            Canvas(Modifier.fillMaxWidth().height(sceneH)) {
                drawLandscape(time.floatValue, (d.secondsToday / DAILY_SECONDS).coerceIn(0f, 1f), d, fig)
            }
            // Sammelstellen
            for (n in NODES) {
                val left = (d.nodes[n.item] ?: 0)
                if (n.rare && left <= 0) continue
                val locked = n.needs != null && !d.has(n.needs)
                NodeSpot(
                    item = n.item, left = if (n.item == Item.WASSER) null else left, locked = locked,
                    modifier = Modifier.offset(x = maxWidth * n.x - 34.dp, y = sceneH * n.y - 34.dp),
                ) {
                    when {
                        dayOver -> say("Feierabend – morgen geht es weiter.")
                        locked -> say("Dafür brauchst du: ${n.needs!!.label}")
                        left <= 0 -> say("${n.item.label}: für heute leer.")
                        else -> target.value = n
                    }
                }
            }

            // Kopf: Kraft · Rezeptbuch · Schliessen
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KraftMeter(kraft)
                Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    GoalHint(d)
                }
                RoundButton(onClick = { book = true }) {
                    Canvas(Modifier.size(18.dp)) {
                        val w = size.width; val h = size.height
                        drawRect(AmbientColors.Light, topLeft = Offset(w * 0.1f, h * 0.15f), size = Size(w * 0.8f, h * 0.7f), style = Stroke(1.4f * density))
                        drawLine(AmbientColors.Light, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f), strokeWidth = 1.4f * density)
                    }
                }
                Spacer(Modifier.width(12.dp))
                RoundButton(onClick = { exit() }) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                    }
                }
            }

            // Unten: Lager | Ablage + Arbeitskreis
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(maxHeight - sceneH)
                    .background(Brush.verticalGradient(listOf(Color(0xF0141519), Color(0xFA0C0D10))))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text("LAGER", style = headStyle)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        Modifier.verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Item.entries.filter { !it.isStation && (d.inventory[it] ?: 0) > 0 }.forEach { item ->
                            ItemChip(item, d.inventory[item] ?: 0) {
                                if (tray.size < 8 && !dayOver) {
                                    d.inventory[item] = (d.inventory[item] ?: 0) - 1
                                    tray += item
                                    progress.floatValue = 0f
                                    version++
                                }
                            }
                        }
                        if (Item.entries.none { (d.inventory[it] ?: 0) > 0 }) {
                            Text("Tippe oben auf Äste, Steine, Gras …", style = labelStyle.copy(color = AmbientColors.Muted, fontSize = 12.sp, letterSpacing = 0.sp))
                        }
                    }
                }
                Column(Modifier.weight(1.2f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ABLAGE", style = headStyle)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tray.toList().forEachIndexed { i, item ->
                            ItemChip(item, null, compact = true) {
                                tray.removeAt(i)
                                d.inventory[item] = (d.inventory[item] ?: 0) + 1
                                progress.floatValue = 0f
                                version++
                            }
                        }
                        if (tray.isEmpty()) Text("Teile aus dem Lager antippen", style = labelStyle.copy(color = AmbientColors.Muted, letterSpacing = 0.sp, fontSize = 12.sp))
                    }
                    Spacer(Modifier.height(10.dp))
                    val near = nearMatch(tray, d)
                    when {
                        recipe != null -> WorkCircle(
                            recipe = recipe,
                            discovered = recipe.id in d.discovered,
                            progress = progress,
                            pulse = pulse,
                            onTap = {
                                if (recipe.work == Work.TAP) work(kraft, progress, 1f, 0.05f) { say("Kurz verschnaufen …") }
                                fig.work = 0.45f; pulse.floatValue = 0.18f
                            },
                            onTurn = { turns ->
                                if (recipe.work == Work.CIRCLE) { work(kraft, progress, turns, 0.06f) { say("Kurz verschnaufen …") }; fig.work = 0.3f }
                            },
                            onRub = {
                                if (recipe.work == Work.RUB) { work(kraft, progress, 1f, 0.03f) { say("Kurz verschnaufen …") }; fig.work = 0.3f; pulse.floatValue = 0.1f }
                            },
                            onHold = { holding.value = it },
                        )
                        near != null || partial(tray, d) != null -> Text(
                            near ?: partial(tray, d)!!,
                            textAlign = TextAlign.Center,
                            style = labelStyle.copy(color = AmbientColors.Gold, fontSize = 12.sp, letterSpacing = 0.5.sp),
                        )
                        tray.isNotEmpty() -> Text(
                            "Daraus entsteht (noch) nichts.",
                            style = labelStyle.copy(color = AmbientColors.Muted, fontSize = 12.sp, letterSpacing = 0.5.sp),
                        )
                    }
                }
            }

            Flyers(flyers, time, maxWidth, sceneH)
            craftFx.value?.let { fx ->
                CraftBurst(fx, time, Modifier.align(Alignment.TopCenter).offset(y = sceneH * 0.5f - 110.dp))
            }

            // Meldung
            toast?.let {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 84.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE6141519))
                        .border(1.dp, AmbientColors.Arty.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(it, style = labelStyle.copy(fontSize = 13.sp, letterSpacing = 0.5.sp))
                }
            }

            if (dayOver) {
                Box(Modifier.align(Alignment.Center).padding(bottom = maxHeight * 0.3f)) {
                    Text(
                        "FEIERABEND · MORGEN GEHT ES WEITER",
                        style = headStyle.copy(fontSize = 14.sp, color = AmbientColors.Gold),
                    )
                }
            }
        }

        if (book) RecipeBook(d) { book = false }
    }
}

// ------------------------------------------------------------------------------------------
// Logik
// ------------------------------------------------------------------------------------------

private fun match(tray: List<Item>, d: WorkshopData): Recipe? {
    if (tray.isEmpty()) return null
    val counts = tray.groupingBy { it }.eachCount()
    return RECIPES.firstOrNull { r ->
        r.inputs == counts && (r.tool == null || d.has(r.tool)) && (r.station == null || r.station in d.built) &&
            !(r.output.isStation && r.output in d.built)
    }
}

/** Naechstes Rezept, das man jetzt herstellen koennte (Werkzeug/Einrichtung vorhanden). */
private fun nextGoal(d: WorkshopData): Recipe? = RECIPES.firstOrNull { r ->
    r.id !in d.discovered && !(r.output.isStation && r.output in d.built) &&
        (r.tool == null || d.has(r.tool)) && (r.station == null || r.station in d.built)
}

/** Ablage ist ein Teil eines Rezepts? Dann sagen, was noch fehlt. */
private fun partial(tray: List<Item>, d: WorkshopData): String? {
    if (tray.isEmpty()) return null
    val counts = tray.groupingBy { it }.eachCount()
    val candidates = RECIPES.filter { r -> counts.all { (k, v) -> (r.inputs[k] ?: 0) >= v } && r.inputs != counts }
    val r = candidates.firstOrNull { it == nextGoal(d) } ?: candidates.firstOrNull { it.id in d.discovered } ?: candidates.firstOrNull()
        ?: return null
    val missing = r.inputs.mapNotNull { (k, v) -> val m = v - (counts[k] ?: 0); if (m > 0) (if (m > 1) "${m}× " else "") + k.label else null }
    return "Gut so – es fehlt noch: " + missing.joinToString(", ")
}

/** Passt die Ablage zu einem Rezept, fehlt aber Werkzeug/Einrichtung? Dann ein Hinweis. */
private fun nearMatch(tray: List<Item>, d: WorkshopData): String? {
    if (tray.isEmpty()) return null
    val counts = tray.groupingBy { it }.eachCount()
    val r = RECIPES.firstOrNull { it.inputs == counts } ?: return null
    if (r.output.isStation && r.output in d.built) return "${r.output.label} steht schon."
    val missing = listOfNotNull(
        r.tool?.takeIf { !d.has(it) }?.label,
        r.station?.takeIf { it !in d.built }?.label,
    )
    return if (missing.isEmpty()) null else "Dafür brauchst du noch: " + missing.joinToString(", ")
}

private fun work(
    kraft: androidx.compose.runtime.MutableFloatState,
    progress: androidx.compose.runtime.MutableFloatState,
    amount: Float,
    costPerUnit: Float,
    onTired: () -> Unit,
) {
    val cost = costPerUnit * amount
    if (kraft.floatValue < cost) { onTired(); return }
    kraft.floatValue -= cost
    progress.floatValue += amount
}

/** Ablage verbrauchen, Ergebnis gutschreiben. Gibt eine Meldung zurueck. */
private fun craft(d: WorkshopData, r: Recipe, tray: MutableList<Item>): String {
    tray.clear()
    if (r.output.isStation) d.built += r.output else d.inventory[r.output] = (d.inventory[r.output] ?: 0) + r.count
    val first = d.discovered.add(r.id)
    val what = if (r.count > 1) "${r.count}× ${r.output.label}" else r.output.label
    return if (first) "Neu entdeckt: $what" else what
}

// ------------------------------------------------------------------------------------------
// Bausteine
// ------------------------------------------------------------------------------------------

@Composable
private fun GoalHint(d: WorkshopData) {
    val goal = nextGoal(d)
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Color(0x99101116))
            .border(1.dp, AmbientColors.Gold.copy(alpha = 0.35f), shape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (goal == null) {
            Text("Alles entdeckt · bald kommt Metall", style = labelStyle.copy(fontSize = 12.sp, letterSpacing = 0.5.sp, color = AmbientColors.Gold))
            return@Row
        }
        Text("ZIEL   ", style = headStyle.copy(fontSize = 10.sp, color = AmbientColors.Gold))
        goal.inputs.entries.forEachIndexed { i, (item, n) ->
            if (i > 0) Text("  +  ", style = labelStyle.copy(fontSize = 12.sp))
            val have = (d.inventory[item] ?: 0) >= n
            ItemIcon(item, 22.dp, dim = !have)
            Text(
                (if (n > 1) " ${n}× " else " ") + item.label,
                maxLines = 1,
                style = labelStyle.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = if (have) AmbientColors.Light else AmbientColors.Muted),
            )
        }
        val extra = listOfNotNull(goal.tool?.let { "mit ${it.label}" }, goal.station?.let { "am ${it.label}" })
        if (extra.isNotEmpty()) Text("   · " + extra.joinToString(" · "), maxLines = 1, style = labelStyle.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = AmbientColors.Muted))
        Text("   →  ?", style = headStyle.copy(fontSize = 12.sp, color = AmbientColors.Gold))
    }
}

@Composable
private fun Flyers(list: List<Flyer>, time: androidx.compose.runtime.MutableFloatState, w: Dp, sceneH: Dp) {
    val now = time.floatValue
    for (f in list) {
        val e = ((now - f.t0) / 0.7f).coerceIn(0f, 1f)
        val ease = e * e * (3f - 2f * e)
        val x = w * f.x * (1f - ease) + 60.dp * ease
        val y = sceneH * f.y * (1f - ease) + (sceneH + 56.dp) * ease - sceneH * (sin(e * PI.toFloat()) * 0.18f)
        Box(
            Modifier
                .offset(x = x - 18.dp, y = y - 18.dp)
                .graphicsLayer { alpha = 1f - e * 0.3f; val s = 1.25f - 0.45f * e; scaleX = s; scaleY = s },
        ) { ItemIcon(f.item, 36.dp) }
    }
}

/** Herstellen: Lichtblitz, Funken, das neue Teil springt gross heraus. */
@Composable
private fun CraftBurst(fx: Pair<Item, Float>, time: androidx.compose.runtime.MutableFloatState, modifier: Modifier) {
    val e = time.floatValue - fx.second
    if (e < 0f || e > 1.9f) return
    val grow = (e / 0.18f).coerceIn(0f, 1f)
    val scale = grow * (1f + 0.3f * exp(-4f * e) * sin(e * 14f))
    val fade = if (e > 1.35f) (1f - (e - 1.35f) / 0.55f).coerceIn(0f, 1f) else 1f
    Box(modifier.size(220.dp).graphicsLayer { alpha = fade }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val big = size.minDimension / 2f
            val gr = big * min(1f, 0.3f + e * 2f)
            drawCircle(
                Brush.radialGradient(listOf(AmbientColors.Gold.copy(alpha = 0.5f), Color.Transparent), center = c, radius = gr),
                radius = gr, center = c, blendMode = BlendMode.Plus,
            )
            if (e < 1f) drawCircle(AmbientColors.Gold.copy(alpha = (1f - e) * 0.7f), radius = big * (0.25f + e * 0.7f), center = c, style = Stroke(2f * density))
            for (k in 0 until 18) {
                val ang = k / 18f * 2f * PI.toFloat() + k * 0.37f
                val dist = big * (0.2f + 0.8f * (1f - exp(-3f * e))) * (0.7f + (k % 3) * 0.15f)
                val a = (1f - e / 1.4f).coerceIn(0f, 1f)
                drawCircle(
                    lerp(AmbientColors.Gold, Color(0xFFFFF0CC), (k % 2).toFloat()).copy(alpha = a),
                    radius = 3f * density * (1f - e / 2f),
                    center = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist),
                    blendMode = BlendMode.Plus,
                )
            }
        }
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { ItemIcon(fx.first, 84.dp) }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
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
private fun KraftMeter(state: androidx.compose.runtime.MutableFloatState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(40.dp)) {
            val value = state.floatValue
            drawCircle(Color(0x33FFFFFF), style = Stroke(4f * density))
            drawArc(
                lerp(Color(0xFFE08A6A), AmbientColors.Gold, value), startAngle = -90f, sweepAngle = 360f * value.coerceIn(0f, 1f),
                useCenter = false, style = Stroke(4f * density, cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("KRAFT", style = headStyle.copy(fontSize = 10.sp))
    }
}

@Composable
private fun NodeSpot(item: Item, left: Int?, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "node")
    val p by inf.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1700 + item.ordinal * 90), RepeatMode.Reverse), label = "puls")
    Box(
        modifier
            .size(68.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            if (!locked) glow(center, r * 1.15f * p, item.color, 0.35f)
            drawCircle(Color(0x66000000), radius = r * 0.62f)
            drawCircle(
                (if (locked) Color.White.copy(alpha = 0.15f) else item.color.copy(alpha = 0.55f * p)),
                radius = r * 0.62f, style = Stroke(1.2f * density),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ItemIcon(item, 26.dp, dim = locked)
            if (left != null) Text("$left", style = labelStyle.copy(fontSize = 9.sp, color = if (left > 0) AmbientColors.Light else AmbientColors.Muted))
        }
    }
}

@Composable
private fun ItemChip(item: Item, count: Int?, compact: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(if (compact) 58.dp else 72.dp)
            .clip(shape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, item.color.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemIcon(item, if (compact) 28.dp else 32.dp)
        Text(
            item.label + (if (count != null && count > 1) " ×$count" else ""),
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = labelStyle.copy(fontSize = 9.sp, letterSpacing = 0.sp),
        )
    }
}

/** Arbeitskreis: zeigt, was entsteht (oder „?“), welche Arbeit noetig ist und den Fortschritt. */
@Composable
private fun WorkCircle(
    recipe: Recipe,
    discovered: Boolean,
    progress: androidx.compose.runtime.MutableFloatState,
    pulse: androidx.compose.runtime.MutableFloatState,
    onTap: () -> Unit,
    onTurn: (Float) -> Unit,
    onRub: () -> Unit,
    onHold: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer { val p = 1f + pulse.floatValue * 0.5f; scaleX = p; scaleY = p }
                .pointerInput(recipe.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onHold(true)
                        val c = Offset(size.width / 2f, size.height / 2f)
                        var lastAngle = atan2(down.position.y - c.y, down.position.x - c.x)
                        var lastX = down.position.x
                        var dir = 0f
                        var moved = 0f
                        val start = down.uptimeMillis
                        var upTime = start
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { upTime = ch.uptimeMillis; break }
                            val p = ch.position
                            moved += (p - ch.previousPosition).getDistance()
                            // Drehen
                            val a = atan2(p.y - c.y, p.x - c.x)
                            var da = a - lastAngle
                            if (da > PI) da -= (2 * PI).toFloat()
                            if (da < -PI) da += (2 * PI).toFloat()
                            lastAngle = a
                            if (abs(da) < 1f) onTurn(abs(da) / (2f * PI.toFloat()))
                            // Reiben: Richtungswechsel zaehlen
                            val dx = p.x - lastX
                            if (abs(dx) > 6f) {
                                val nd = if (dx > 0) 1f else -1f
                                if (dir != 0f && nd != dir) onRub()
                                dir = nd
                                lastX = p.x
                            }
                            ch.consume()
                        }
                        onHold(false)
                        if (moved < 18f && upTime - start < 350) onTap()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f - 6f * density
                drawCircle(Color(0x22FFFFFF), radius = r)
                drawCircle(AmbientColors.Light.copy(alpha = 0.25f), radius = r, style = Stroke(1.2f * density))
                val frac = (progress.floatValue / recipe.amount).coerceIn(0f, 1f)
                drawArc(
                    AmbientColors.Gold, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                    topLeft = Offset(size.width / 2f - r, size.height / 2f - r), size = Size(r * 2f, r * 2f),
                    style = Stroke(4f * density, cap = StrokeCap.Round),
                )
            }
            if (discovered) ItemIcon(recipe.output, 44.dp) else Text("?", style = headStyle.copy(fontSize = 30.sp, color = AmbientColors.Gold))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(if (discovered) recipe.output.label.uppercase() else "UNBEKANNT", style = headStyle.copy(fontSize = 11.sp))
            Spacer(Modifier.height(4.dp))
            Text(recipe.work.hint, style = labelStyle.copy(fontSize = 13.sp, letterSpacing = 0.5.sp, color = AmbientColors.Gold))
        }
    }
}

@Composable
private fun RecipeBook(d: WorkshopData, onClose: () -> Unit) {
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
                .background(Color(0xF2121318))
                .border(1.dp, AmbientColors.Light.copy(alpha = 0.12f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("REZEPTBUCH · ${d.discovered.size} / ${RECIPES.size}", style = headStyle)
                Spacer(Modifier.weight(1f))
                RoundButton(onClick = onClose) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (r in RECIPES) {
                    val known = r.id in d.discovered
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (known) {
                            r.inputs.forEach { (item, n) ->
                                ItemIcon(item, 26.dp)
                                Text(if (n > 1) " ×$n  " else "  ", style = labelStyle)
                            }
                        } else {
                            val parts = r.inputs.values.sum()
                            Text(List(parts) { "?" }.joinToString(" + "), style = labelStyle.copy(fontSize = 13.sp, color = AmbientColors.Muted))
                        }
                        Text("  →  ", style = labelStyle.copy(fontSize = 14.sp))
                        ItemIcon(r.output, 28.dp, dim = !known)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (known) r.output.label else "???",
                            style = labelStyle.copy(fontSize = 13.sp, letterSpacing = 0.5.sp),
                        )
                        if (known) {
                            val extra = listOfNotNull(r.tool?.let { "mit ${it.label}" }, r.station?.let { "am ${it.label}" }, r.work.hint)
                            Text("   · " + extra.joinToString(" · "), style = labelStyle.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = AmbientColors.Muted))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Zeichnungen
// ------------------------------------------------------------------------------------------

@Composable
internal fun ItemIcon(item: Item, size: Dp, dim: Boolean = false) {
    Canvas(Modifier.size(size)) {
        if (!dim) glow(center, this.size.minDimension * 0.62f, item.color, 0.32f)
        drawItem(item, if (dim) 0.25f else 1f)
    }
}

private fun DrawScope.drawItem(item: Item, alpha: Float) {
    val w = size.width
    val h = size.height
    val c = item.color.copy(alpha = alpha)
    val dark = lerp(item.color, Color.Black, 0.35f).copy(alpha = alpha)
    val sw = w * 0.12f
    fun poly(vararg pts: Float, color: Color = c) {
        val p = Path()
        p.moveTo(pts[0] * w, pts[1] * h)
        var i = 2
        while (i < pts.size) { p.lineTo(pts[i] * w, pts[i + 1] * h); i += 2 }
        p.close()
        drawPath(p, color)
    }
    when (item) {
        Item.AST -> { drawLine(c, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.85f, h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round); drawLine(c, Offset(w * 0.55f, h * 0.45f), Offset(w * 0.75f, h * 0.6f), strokeWidth = sw * 0.7f, cap = StrokeCap.Round) }
        Item.STOCK -> drawLine(c, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.8f, h * 0.2f), strokeWidth = sw, cap = StrokeCap.Round)
        Item.STAMM -> { drawRoundRect(c, topLeft = Offset(w * 0.08f, h * 0.35f), size = Size(w * 0.84f, h * 0.3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.15f)); drawCircle(dark, radius = h * 0.1f, center = Offset(w * 0.8f, h * 0.5f)) }
        Item.STEIN -> drawOval(c, topLeft = Offset(w * 0.15f, h * 0.3f), size = Size(w * 0.7f, h * 0.45f))
        Item.BEHAUENER_STEIN -> poly(0.2f, 0.35f, 0.5f, 0.2f, 0.8f, 0.35f, 0.75f, 0.7f, 0.25f, 0.7f)
        Item.FEUERSTEIN -> { poly(0.2f, 0.6f, 0.45f, 0.2f, 0.8f, 0.45f, 0.6f, 0.8f); drawLine(Color.White.copy(alpha = 0.5f * alpha), Offset(w * 0.45f, h * 0.3f), Offset(w * 0.6f, h * 0.45f), strokeWidth = w * 0.04f) }
        Item.FASERN -> for (k in 0 until 3) { val x = w * (0.3f + k * 0.2f); drawLine(c, Offset(x, h * 0.85f), Offset(x + w * 0.05f * (k - 1), h * 0.2f), strokeWidth = w * 0.06f, cap = StrokeCap.Round) }
        Item.SEIL -> { val p = Path(); p.moveTo(w * 0.15f, h * 0.5f); for (k in 0..6) p.lineTo(w * (0.15f + k * 0.12f), h * (if (k % 2 == 0) 0.4f else 0.6f)); drawPath(p, c, style = Stroke(w * 0.08f, cap = StrokeCap.Round)) }
        Item.LEHM -> drawOval(c, topLeft = Offset(w * 0.15f, h * 0.35f), size = Size(w * 0.7f, h * 0.4f))
        Item.LEHMZIEGEL -> { drawRect(c, topLeft = Offset(w * 0.15f, h * 0.35f), size = Size(w * 0.7f, h * 0.32f)); drawLine(dark, Offset(w * 0.5f, h * 0.35f), Offset(w * 0.5f, h * 0.67f), strokeWidth = w * 0.03f) }
        Item.SAND -> for (k in 0 until 14) drawCircle(c, radius = w * 0.045f, center = Offset(w * (0.2f + (k * 37 % 60) / 100f), h * (0.45f + (k * 23 % 35) / 100f)))
        Item.WASSER, Item.HARZ, Item.PECH -> { val p = Path(); p.moveTo(w * 0.5f, h * 0.12f); p.cubicTo(w * 0.85f, h * 0.55f, w * 0.75f, h * 0.88f, w * 0.5f, h * 0.88f); p.cubicTo(w * 0.25f, h * 0.88f, w * 0.15f, h * 0.55f, w * 0.5f, h * 0.12f); drawPath(p, c) }
        Item.HOLZSCHEIT -> poly(0.15f, 0.7f, 0.45f, 0.25f, 0.85f, 0.35f, 0.6f, 0.8f)
        Item.HOLZKOHLE -> poly(0.2f, 0.65f, 0.3f, 0.35f, 0.6f, 0.28f, 0.82f, 0.5f, 0.65f, 0.75f)
        Item.FAUSTKEIL -> poly(0.5f, 0.12f, 0.75f, 0.6f, 0.5f, 0.88f, 0.25f, 0.6f)
        Item.STEINAXT -> { drawLine(Item.STOCK.color.copy(alpha = alpha), Offset(w * 0.3f, h * 0.9f), Offset(w * 0.6f, h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round); poly(0.45f, 0.15f, 0.85f, 0.25f, 0.8f, 0.45f, 0.5f, 0.4f) }
        Item.STEINHAMMER -> { drawLine(Item.STOCK.color.copy(alpha = alpha), Offset(w * 0.5f, h * 0.9f), Offset(w * 0.5f, h * 0.3f), strokeWidth = sw, cap = StrokeCap.Round); drawRoundRect(c, topLeft = Offset(w * 0.25f, h * 0.12f), size = Size(w * 0.5f, h * 0.25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f)) }
        Item.FEUERBOHRER -> { drawLine(c, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.9f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round); drawArc(Item.SEIL.color.copy(alpha = alpha), 200f, 140f, false, topLeft = Offset(w * 0.15f, h * 0.35f), size = Size(w * 0.7f, h * 0.4f), style = Stroke(w * 0.05f)) }
        Item.FACKEL -> { drawLine(Item.STOCK.color.copy(alpha = alpha), Offset(w * 0.5f, h * 0.9f), Offset(w * 0.5f, h * 0.4f), strokeWidth = sw, cap = StrokeCap.Round); drawCircle(c, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.28f)) }
        Item.LAGERFEUER -> { for (k in 0 until 5) drawCircle(Item.STEIN.color.copy(alpha = alpha), radius = w * 0.08f, center = Offset(w * (0.2f + k * 0.15f), h * 0.8f)); poly(0.3f, 0.75f, 0.5f, 0.15f, 0.7f, 0.75f) }
        Item.WERKBANK -> { drawRect(c, topLeft = Offset(w * 0.1f, h * 0.35f), size = Size(w * 0.8f, h * 0.12f)); drawLine(dark, Offset(w * 0.2f, h * 0.47f), Offset(w * 0.2f, h * 0.85f), strokeWidth = sw); drawLine(dark, Offset(w * 0.8f, h * 0.47f), Offset(w * 0.8f, h * 0.85f), strokeWidth = sw) }
    }
}

/** Weicher Lichtfleck (additiv). */
private fun DrawScope.glow(c: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center = c, radius = radius),
        radius = radius, center = c, blendMode = BlendMode.Plus,
    )
}

private fun hash(i: Int): Float {
    val v = sin(i * 12.9898f + 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

/** Huegelkette als dunkle Silhouette, Oberkante leuchtet dort, wo das Licht steht. */
private fun DrawScope.ridge(baseY: Float, amp: Float, freq: Float, phase: Float, fill: Color, edge: Float, light: Color, lightX: Float) {
    val w = size.width
    val top = Path()
    val body = Path()
    val n = 90
    for (i in 0..n) {
        val x = w * i / n
        val y = baseY - (sin(x / w * freq + phase) * 0.6f + sin(x / w * freq * 2.3f + phase * 1.7f) * 0.4f) * amp
        if (i == 0) { top.moveTo(x, y); body.moveTo(x, y) } else { top.lineTo(x, y); body.lineTo(x, y) }
    }
    body.lineTo(w, size.height); body.lineTo(0f, size.height); body.close()
    drawPath(body, fill)
    drawPath(
        top,
        Brush.horizontalGradient(
            listOf(light.copy(alpha = edge * 0.15f), light.copy(alpha = edge), light.copy(alpha = edge * 0.15f)),
            startX = lightX - w * 0.55f, endX = lightX + w * 0.55f,
        ),
        style = Stroke(1.3f * density),
        blendMode = BlendMode.Plus,
    )
}

/** Werkstatt im Licht-Stil: dunkle Landschaft, wanderndes Tageslicht (= Restzeit), Lichtkanten, Staub, Glut. */
private fun DrawScope.drawLandscape(t: Float, day: Float, d: WorkshopData, fig: FigureState) {
    val w = size.width
    val h = size.height
    val evening = smoothstep(0.65f, 1f, day)
    val horizon = h * 0.46f
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF06070A), lerp(Color(0xFF1C1813), Color(0xFF221219), evening), Color(0xFF0A0A0D)),
            startY = 0f, endY = h,
        ),
    )
    // Tageslicht
    val sx = w * (0.08f + 0.84f * day)
    val sy = horizon - sin(day * PI.toFloat()) * horizon * 0.72f
    val sun = lerp(Color(0xFFFFE6B0), Color(0xFFFF8A50), evening)
    glow(Offset(sx, sy), h * 0.6f, sun, 0.16f)
    glow(Offset(sx, sy), h * 0.17f, sun, 0.5f)
    drawCircle(sun.copy(alpha = 0.95f), radius = h * 0.02f, center = Offset(sx, sy))
    // Sterne gegen Abend
    if (evening > 0f) {
        for (k in 0 until 46) {
            val tw = 0.5f + 0.5f * sin(t * (0.5f + hash(k)) + k)
            drawCircle(
                Color.White.copy(alpha = 0.55f * evening * tw), radius = 1.1f * density,
                center = Offset(hash(k * 3 + 1) * w, hash(k * 7 + 2) * horizon * 0.85f),
            )
        }
    }
    // Huegel (hinten → vorn)
    ridge(horizon - h * 0.03f, h * 0.06f, 3.1f, 0.7f, Color(0xFF16151A), 0.22f, sun, sx)
    ridge(horizon + h * 0.04f, h * 0.045f, 5.3f, 2.1f, Color(0xFF111115), 0.32f, sun, sx)
    // Wald links: Nadelbaum-Silhouetten mit Lichtkante
    for (k in 0 until 9) {
        val tx = w * (0.015f + k * 0.034f + hash(k + 40) * 0.01f)
        val th = h * (0.2f + hash(k + 50) * 0.12f)
        val foot = h * (0.56f + hash(k + 60) * 0.03f)
        val sway = sin(t * 0.5f + k) * w * 0.002f
        val tree = Path().apply {
            moveTo(tx + sway, foot - th)
            lineTo(tx + th * 0.22f, foot)
            lineTo(tx - th * 0.22f, foot)
            close()
        }
        drawPath(tree, Color(0xFF0B0B0F))
        drawLine(sun.copy(alpha = 0.22f), Offset(tx + sway, foot - th), Offset(tx + th * 0.22f, foot), strokeWidth = 1f * density, blendMode = BlendMode.Plus)
    }
    ridge(h * 0.6f, h * 0.02f, 4.2f, 0.3f, Color(0xFF0D0D10), 0.42f, sun, sx)
    // Felsen
    val rock = Offset(w * 0.49f, h * 0.57f)
    drawOval(Color(0xFF18181C), topLeft = Offset(rock.x - w * 0.07f, rock.y - h * 0.05f), size = Size(w * 0.14f, h * 0.1f))
    drawArc(
        sun.copy(alpha = 0.35f), startAngle = 200f, sweepAngle = 120f, useCenter = false,
        topLeft = Offset(rock.x - w * 0.07f, rock.y - h * 0.05f), size = Size(w * 0.14f, h * 0.1f),
        style = Stroke(1.2f * density), blendMode = BlendMode.Plus,
    )
    // Fluss rechts mit Lichtglitzern
    val river = Path().apply {
        moveTo(w * 0.84f, h * 0.58f); cubicTo(w * 0.8f, h * 0.72f, w * 0.95f, h * 0.82f, w * 0.88f, h)
        lineTo(w, h); lineTo(w, h * 0.58f); close()
    }
    drawPath(river, Color(0xFF0C1622))
    for (k in 0 until 16) {
        val ph = (t * 0.12f + hash(k + 90)) % 1f
        val ry = h * (0.6f + ph * 0.4f)
        val rx = w * (0.9f + hash(k + 70) * 0.08f) - (ry - h * 0.6f) * 0.05f
        val a = sin(ph * PI.toFloat()) * 0.5f
        drawLine(Color(0xFF9FD0FF).copy(alpha = a), Offset(rx, ry), Offset(rx + w * 0.015f, ry), strokeWidth = 1.2f * density, blendMode = BlendMode.Plus)
    }
    // Sandbank
    drawOval(Color(0xFF241F17), topLeft = Offset(w * 0.74f, h * 0.6f), size = Size(w * 0.12f, h * 0.06f))
    // Lagerfeuer mit Glut
    if (Item.LAGERFEUER in d.built) {
        val c = Offset(w * 0.62f, h * 0.88f)
        val fl = 0.85f + 0.15f * sin(t * 9f) + 0.08f * sin(t * 13.7f)
        glow(c, h * 0.34f * fl, Color(0xFFFF9A40), 0.32f)
        for (k in 0 until 6) drawCircle(Color(0xFF2A2A30), radius = h * 0.014f, center = Offset(c.x + (k - 2.5f) * h * 0.026f, c.y))
        for (layer in 0 until 3) {
            val hh = h * (0.09f - layer * 0.025f) * fl
            val ww = h * (0.032f - layer * 0.008f)
            val col = listOf(Color(0xFFFF6A20), Color(0xFFFFA040), Color(0xFFFFE6A0))[layer]
            val flame = Path().apply {
                moveTo(c.x - ww, c.y)
                quadraticTo(c.x - ww * 0.6f, c.y - hh * 0.6f, c.x + sin(t * 7f + layer) * ww * 0.3f, c.y - hh)
                quadraticTo(c.x + ww * 0.6f, c.y - hh * 0.6f, c.x + ww, c.y)
                close()
            }
            drawPath(flame, col.copy(alpha = 0.9f), blendMode = BlendMode.Plus)
        }
        for (k in 0 until 14) {
            val ph = (t * (0.35f + hash(k + 11) * 0.3f) + hash(k + 12)) % 1f
            val e = Offset(c.x + sin(ph * 6f + k) * h * 0.03f, c.y - h * 0.05f - ph * h * 0.28f)
            drawCircle(Color(0xFFFFB060).copy(alpha = (1f - ph) * 0.9f), radius = 1.6f * density * (1f - ph * 0.5f), center = e, blendMode = BlendMode.Plus)
        }
    }
    // Werkbank (Silhouette, Lichtkante, kleine Laterne)
    if (Item.WERKBANK in d.built) {
        val bx = w * 0.3f
        val by = h * 0.86f
        drawRect(Color(0xFF1A1714), topLeft = Offset(bx, by), size = Size(w * 0.1f, h * 0.025f))
        drawLine(Color(0xFF1A1714), Offset(bx + w * 0.01f, by), Offset(bx + w * 0.01f, by + h * 0.08f), strokeWidth = w * 0.006f)
        drawLine(Color(0xFF1A1714), Offset(bx + w * 0.09f, by), Offset(bx + w * 0.09f, by + h * 0.08f), strokeWidth = w * 0.006f)
        drawLine(Color(0xFFC8B48C).copy(alpha = 0.5f), Offset(bx, by), Offset(bx + w * 0.1f, by), strokeWidth = 1.2f * density, blendMode = BlendMode.Plus)
        glow(Offset(bx + w * 0.085f, by - h * 0.015f), h * 0.06f, Color(0xFFFFC870), 0.45f)
    }
    // Schwebender Staub im Licht
    for (k in 0 until 55) {
        val sp = 0.004f + hash(k + 200) * 0.01f
        val px = ((hash(k + 100) + t * sp) % 1f) * w
        val py = (hash(k + 150) * 0.9f + sin(t * 0.3f + k) * 0.02f) * h
        val a = (0.12f + hash(k + 250) * 0.3f) * (0.6f + 0.4f * sin(t * 0.8f + k * 1.3f))
        drawCircle(Color(0xFFFFE2B0).copy(alpha = a), radius = (0.8f + hash(k + 300) * 1.6f) * density, center = Offset(px, py), blendMode = BlendMode.Plus)
    }
    drawFigure(fig, t, evening)
    if (evening > 0f) drawRect(Color(0xFF05050C).copy(alpha = 0.2f * evening))
}

internal class FigureState {
    var x = 0.5f
    var y = 0.86f
    var walk = 0f
    var facing = 1f
    var moving = false
    var work = 0f
    var gather = 0f
}

internal class Flyer(val item: Item, val x: Float, val y: Float, val t0: Float)

/** Spielfigur als Silhouette mit Arty-blauer Lichtkante und kleiner Laterne. */
private fun DrawScope.drawFigure(f: FigureState, t: Float, evening: Float) {
    val x = f.x * size.width
    val y = f.y * size.height
    val s = size.height * (0.10f + 0.06f * f.y)
    val sw = if (f.moving) sin(f.walk) else 0f
    val bob = if (f.moving) abs(cos(f.walk)) * s * 0.035f else sin(t * 1.6f) * s * 0.008f
    val bend = if (f.gather > 0f) sin(f.gather / 0.4f * PI.toFloat()) * s * 0.12f else 0f
    val dark = Color(0xFF0E0F15)
    val rim = AmbientColors.Arty
    drawOval(Color.Black.copy(alpha = 0.45f), topLeft = Offset(x - s * 0.24f, y - s * 0.04f), size = Size(s * 0.48f, s * 0.08f))
    val hip = Offset(x, y - s * 0.45f - bob + bend * 0.3f)
    val legA = Offset(x + sw * s * 0.17f, y)
    val legB = Offset(x - sw * s * 0.17f, y)
    drawLine(dark, hip, legA, strokeWidth = s * 0.08f, cap = StrokeCap.Round)
    drawLine(dark, hip, legB, strokeWidth = s * 0.08f, cap = StrokeCap.Round)
    val sh = Offset(x + f.facing * bend * 0.6f, y - s * 0.82f - bob + bend)
    val cloak = Path().apply {
        moveTo(sh.x - s * 0.11f, sh.y)
        lineTo(sh.x + s * 0.11f, sh.y)
        lineTo(hip.x + s * 0.2f - f.facing * sw * s * 0.03f, hip.y + s * 0.1f)
        lineTo(hip.x - s * 0.2f - f.facing * sw * s * 0.03f, hip.y + s * 0.1f)
        close()
    }
    drawPath(cloak, dark)
    drawPath(cloak, rim.copy(alpha = 0.75f), style = Stroke(1.3f * density), blendMode = BlendMode.Plus)
    val a = when {
        f.work > 0f -> 0.9f + abs(sin(f.work * 16f)) * 1.7f
        f.gather > 0f -> 0.7f
        f.moving -> 0.15f + sw * 0.45f
        else -> 0.12f
    }
    val armLen = s * (if (f.gather > 0f) 0.42f else 0.34f)
    val shoulder = Offset(sh.x, sh.y + s * 0.04f)
    val hand = Offset(sh.x + sin(a) * f.facing * armLen, shoulder.y + cos(a) * armLen)
    drawLine(dark, shoulder, hand, strokeWidth = s * 0.075f, cap = StrokeCap.Round)
    drawLine(rim.copy(alpha = 0.5f), shoulder, hand, strokeWidth = 1f * density, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
    if (f.work > 0f) {
        val dir = Offset(sin(a + 1.4f) * f.facing, cos(a + 1.4f))
        val tip = hand + dir * (s * 0.22f)
        drawLine(dark, hand, tip, strokeWidth = s * 0.045f, cap = StrokeCap.Round)
        drawCircle(dark, radius = s * 0.065f, center = tip)
        drawCircle(AmbientColors.Gold.copy(alpha = 0.6f), radius = s * 0.065f, center = tip, style = Stroke(1f * density), blendMode = BlendMode.Plus)
        // Funken beim Zuschlagen
        if (abs(sin(f.work * 16f)) < 0.25f) for (k in 0 until 5) {
            val ang = k * 1.3f + t * 3f
            drawCircle(Color(0xFFFFD080).copy(alpha = 0.8f), radius = 1.4f * density, center = tip + Offset(cos(ang), sin(ang)) * (s * 0.12f), blendMode = BlendMode.Plus)
        }
    }
    // Kopf mit Kapuze
    val head = Offset(sh.x + f.facing * s * 0.01f, sh.y - s * 0.13f)
    drawCircle(dark, radius = s * 0.115f, center = head)
    drawArc(
        rim.copy(alpha = 0.8f), startAngle = 180f + f.facing * 10f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(head.x - s * 0.115f, head.y - s * 0.115f), size = Size(s * 0.23f, s * 0.23f),
        style = Stroke(1.3f * density), blendMode = BlendMode.Plus,
    )
    // Laterne am Guertel
    val lc = Offset(hip.x + f.facing * s * 0.22f, hip.y + s * 0.02f + sin(f.walk) * s * 0.02f)
    glow(lc, s * (0.45f + 0.35f * evening), Color(0xFFFFC870), 0.45f)
    drawCircle(Color(0xFFFFE6B0), radius = s * 0.028f, center = lc)
}

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Suppress("unused")
private fun keepImports() = cos(0f) + min(0f, 0f)
