package com.chrizzzzilla.self.game

import androidx.activity.compose.BackHandler
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
                }
                sinceSave += dt
            }
            if (sinceSave > 5f) {
                sinceSave = 0f
                store.save(d)
            }
            val r = match(tray, d)
            if (r != null && progress.floatValue >= r.amount) {
                craft(d, r, tray)?.let { say(it) }
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
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1014))) {
        if (d == null) return@Box
        if (version < 0) return@Box // Neuaufbau bei Aenderungen
        val dayOver = d.secondsToday >= DAILY_SECONDS
        val recipe = match(tray, d)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sceneH = maxHeight * 0.66f
            // Landschaft + Sonne + gebaute Einrichtungen
            Canvas(Modifier.fillMaxWidth().height(sceneH)) {
                drawLandscape(time.floatValue, (d.secondsToday / DAILY_SECONDS).coerceIn(0f, 1f), d)
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
                        kraft.floatValue < 0.03f -> say("Kurz verschnaufen …")
                        else -> {
                            if (n.item != Item.WASSER) d.nodes[n.item] = left - 1
                            d.inventory[n.item] = (d.inventory[n.item] ?: 0) + 1
                            kraft.floatValue -= 0.03f
                            version++
                        }
                    }
                }
            }

            // Kopf: Kraft · Rezeptbuch · Schliessen
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KraftMeter(kraft.floatValue)
                Spacer(Modifier.weight(1f))
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
                                if (tray.size < 6 && !dayOver) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            progress = progress.floatValue,
                            onTap = {
                                if (recipe.work == Work.TAP) work(kraft, progress, 1f, 0.05f) { say("Kurz verschnaufen …") }
                            },
                            onTurn = { turns ->
                                if (recipe.work == Work.CIRCLE) work(kraft, progress, turns, 0.06f) { say("Kurz verschnaufen …") }
                            },
                            onRub = {
                                if (recipe.work == Work.RUB) work(kraft, progress, 1f, 0.03f) { say("Kurz verschnaufen …") }
                            },
                            onHold = { holding.value = it },
                        )
                        near != null -> Text(
                            near,
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
private fun KraftMeter(value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(40.dp)) {
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
    Column(
        modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Color(0x33000000))
            .border(1.dp, if (locked) Color(0x33FFFFFF) else item.color.copy(alpha = 0.7f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ItemIcon(item, 30.dp, dim = locked)
        if (left != null) Text("$left", style = labelStyle.copy(fontSize = 10.sp, color = if (left > 0) AmbientColors.Light else AmbientColors.Muted))
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
    progress: Float,
    onTap: () -> Unit,
    onTurn: (Float) -> Unit,
    onRub: () -> Unit,
    onHold: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(112.dp)
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
                val frac = (progress / recipe.amount).coerceIn(0f, 1f)
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
    Canvas(Modifier.size(size)) { drawItem(item, if (dim) 0.25f else 1f) }
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

/** Landschaft: Himmel nach Tageszeit (Morgen → Abend ueber die 10 Minuten), Sonne, Wald, Felsen, Fluss. */
private fun DrawScope.drawLandscape(t: Float, day: Float, d: WorkshopData) {
    val w = size.width
    val h = size.height
    val evening = smoothstep(0.7f, 1f, day)
    val morning = 1f - smoothstep(0f, 0.25f, day)
    val top = lerp(lerp(Color(0xFF5C9AD6), Color(0xFF7A8CC8), morning), Color(0xFF3A3470), evening)
    val hor = lerp(lerp(Color(0xFFCFE6F2), Color(0xFFF4C9A0), morning), Color(0xFFF08A5A), evening)
    val horizon = h * 0.42f
    drawRect(Brush.verticalGradient(listOf(top, hor), startY = 0f, endY = horizon))
    // Sonne (Restzeit)
    val sx = w * (0.08f + 0.84f * day)
    val sy = horizon - sin(day * PI.toFloat()) * horizon * 0.75f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE2A8).copy(alpha = 0.6f), Color.Transparent), center = Offset(sx, sy), radius = h * 0.25f), radius = h * 0.25f, center = Offset(sx, sy), blendMode = BlendMode.Plus)
    drawCircle(Color(0xFFFFF0CC), radius = h * 0.04f, center = Offset(sx, sy))
    // Huegel
    val hill = lerp(Color(0xFF6E9A5A), Color(0xFF3E4A40), evening)
    val p = Path(); p.moveTo(0f, horizon + h * 0.05f)
    var x = 0f
    while (x <= w) { p.lineTo(x, horizon - sin(x / w * 5f) * h * 0.03f); x += w / 60f }
    p.lineTo(w, h); p.lineTo(0f, h); p.close()
    drawPath(p, hill)
    drawRect(lerp(Color(0xFF7FAE5E), Color(0xFF465440), evening), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
    // Wald links
    val tree = lerp(Color(0xFF2E5A38), Color(0xFF1A2A22), evening)
    for (k in 0 until 7) {
        val tx = w * (0.02f + k * 0.045f)
        val th = h * (0.22f + (k % 3) * 0.05f)
        val sway = sin(t * 0.6f + k) * w * 0.003f
        drawLine(Color(0xFF4A3424), Offset(tx, h * 0.55f), Offset(tx, h * 0.55f - th * 0.4f), strokeWidth = w * 0.006f)
        drawCircle(tree, radius = th * 0.32f, center = Offset(tx + sway, h * 0.55f - th * 0.55f))
    }
    // Felsen Mitte
    drawOval(lerp(Color(0xFF8E8E8A), Color(0xFF4A4A4C), evening), topLeft = Offset(w * 0.42f, h * 0.5f), size = Size(w * 0.14f, h * 0.12f))
    // Fluss rechts
    val river = Path(); river.moveTo(w * 0.84f, h * 0.5f); river.cubicTo(w * 0.8f, h * 0.7f, w * 0.95f, h * 0.8f, w * 0.88f, h); river.lineTo(w, h); river.lineTo(w, h * 0.5f); river.close()
    drawPath(river, lerp(Color(0xFF5EA8D8), Color(0xFF2A4A6A), evening))
    // Sandbank
    drawOval(lerp(Color(0xFFE2C98E), Color(0xFF7A6A4E), evening), topLeft = Offset(w * 0.74f, h * 0.56f), size = Size(w * 0.12f, h * 0.08f))
    // Gebaute Einrichtungen in der Werkstatt-Mitte
    if (Item.LAGERFEUER in d.built) {
        val c = Offset(w * 0.62f, h * 0.88f)
        for (k in 0 until 6) drawCircle(Color(0xFF8E8E8A), radius = h * 0.015f, center = Offset(c.x + (k - 2.5f) * h * 0.028f, c.y))
        val fl = 0.8f + 0.2f * sin(t * 9f) + 0.1f * sin(t * 13f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB050).copy(alpha = 0.6f), Color.Transparent), center = c, radius = h * 0.12f * fl), radius = h * 0.12f * fl, center = c, blendMode = BlendMode.Plus)
        val flame = Path(); flame.moveTo(c.x - h * 0.03f, c.y); flame.lineTo(c.x, c.y - h * 0.08f * fl); flame.lineTo(c.x + h * 0.03f, c.y); flame.close()
        drawPath(flame, Color(0xFFFFA040))
    }
    if (Item.WERKBANK in d.built) {
        val bx = w * 0.3f
        val by = h * 0.86f
        drawRect(Color(0xFF9A7048), topLeft = Offset(bx, by), size = Size(w * 0.1f, h * 0.025f))
        drawLine(Color(0xFF6A4A30), Offset(bx + w * 0.01f, by), Offset(bx + w * 0.01f, by + h * 0.08f), strokeWidth = w * 0.006f)
        drawLine(Color(0xFF6A4A30), Offset(bx + w * 0.09f, by), Offset(bx + w * 0.09f, by + h * 0.08f), strokeWidth = w * 0.006f)
    }
    // Daemmerung am Abend
    if (evening > 0f) drawRect(Color(0xFF0A0A20).copy(alpha = 0.25f * evening))
}

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Suppress("unused")
private fun keepImports() = cos(0f) + min(0f, 0f)
