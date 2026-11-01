package com.chrizzzzilla.self

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientBackground
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import com.chrizzzzilla.self.core.design.WidthClass
import com.chrizzzzilla.self.core.design.currentWidthClass
import com.chrizzzzilla.self.focus.RestScreen
import com.chrizzzzilla.self.game.GameScreen
import com.chrizzzzilla.self.mind.BodyScreen
import com.chrizzzzilla.self.mind.MindScreen
import com.chrizzzzilla.self.reader.LibraryScreen
import kotlin.math.sin

/** Die Bereiche der App. Neue Bereiche = neuer Eintrag + Platz im Menue-Raster. */
enum class Section(@StringRes val label: Int) {
    READ(R.string.app_section_read),  // „Soul“
    GAME(R.string.app_section_game),  // „Path“
    MIND(R.string.app_section_mind),  // „Mind“ – Abendfragen
    REST(R.string.app_section_rest),  // „Rest“ – Ambiente mit Schlaf-Timer
    BODY(R.string.app_section_body),  // „Body“ – Morgenroutine
}

/**
 * Rahmen der App: Inhalt immer bildschirmfuellend (Arty bleibt genau in der Mitte),
 * Navigation ueber den Raster-Knopf unten links (gut mit dem linken Daumen erreichbar).
 */
@Composable
fun SelfAppShell(incomingBook: Uri?, onIncomingBookHandled: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.READ) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }
    val widthClass = currentWidthClass()

    // Kommt ein Buch über „Öffnen mit“, immer in den Lesebereich wechseln.
    LaunchedEffect(incomingBook) {
        if (incomingBook != null) section = Section.READ
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())

        when (section) {
            Section.READ -> LibraryScreen(
                widthClass = widthClass,
                incomingBook = incomingBook,
                onIncomingBookHandled = onIncomingBookHandled,
                modifier = Modifier.fillMaxSize(),
                onOverlayChanged = {
                    overlay = it
                    if (it) menuOpen = false
                },
            )
            Section.GAME -> GameScreen(
                modifier = Modifier.fillMaxSize(),
                onOverlayChanged = {
                    overlay = it
                    if (it) menuOpen = false
                },
            )
            Section.MIND -> MindScreen(Modifier.fillMaxSize(), onOverlayChanged = { overlay = it; if (it) menuOpen = false })
            Section.REST -> RestScreen(Modifier.fillMaxSize(), onOverlayChanged = { overlay = it; if (it) menuOpen = false })
            Section.BODY -> BodyScreen(Modifier.fillMaxSize(), onOverlayChanged = { overlay = it; if (it) menuOpen = false })
        }

        // Tippen irgendwo anders schliesst das Menue
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        menuOpen = false
                    },
            )
        }

        if (!overlay) GridMenu(
            open = menuOpen,
            current = section,
            cell = if (widthClass == WidthClass.COMPACT) 68.dp else 84.dp,
            onToggle = { menuOpen = !menuOpen },
            onSelect = {
                section = it
                menuOpen = false
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        )
    }
}

/**
 * Raster-Menue: Der Knopf bleibt unten links liegen und wird grau; darueber und daneben
 * federt ein grosses Quadrat mit 2×2 Feldern auf – Lesen (oben links), Spiel (oben rechts),
 * unten rechts frei fuer spaeter.
 */
@Composable
private fun GridMenu(
    open: Boolean,
    current: Section,
    cell: Dp,
    onToggle: () -> Unit,
    onSelect: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = 12.dp
    val pad = 10.dp
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        progress.animateTo(
            if (open) 1f else 0f,
            tween(if (open) 520 else 420, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
    }
    val p = progress.value
    val grey by animateFloatAsState(if (open) 1f else 0f, tween(300), label = "grau")

    val fullW = cell * 3 + gap * 2 + pad * 2
    val fullH = cell * 2 + gap + pad * 2
    Box(modifier.size(fullW, fullH), contentAlignment = Alignment.BottomStart) {
        // Die Flaeche waechst ruhig aus dem Knopf heraus
        if (p > 0.001f) {
            Box(
                Modifier
                    .size(
                        (cell + pad * 2 + (fullW - cell - pad * 2) * p).coerceAtLeast(1.dp),
                        (cell + pad * 2 + (fullH - cell - pad * 2) * p).coerceAtLeast(1.dp),
                    )
                    .graphicsLayer { alpha = (p * 1.4f).coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xE61A1B21), Color(0xF0111216))))
                    .border(1.dp, AmbientColors.Light.copy(alpha = 0.10f), RoundedCornerShape(26.dp)),
            )
        }

        // Felder (erscheinen kurz nacheinander)
        val tiles = listOf(
            Triple(0, 1, Section.READ), // oben links
            Triple(1, 1, Section.GAME), // oben Mitte
            Triple(2, 1, Section.MIND), // oben rechts
            Triple(1, 0, Section.REST), // unten Mitte
            Triple(2, 0, Section.BODY), // unten rechts
        )
        tiles.forEachIndexed { i, (col, row, target) ->
            val local = ((p - i * 0.06f) / (1f - i * 0.06f)).coerceIn(0f, 1f)
            if (local > 0.001f) {
                Box(
                    Modifier
                        .padding(start = pad + (cell + gap) * col, bottom = pad + (cell + gap) * row)
                        .size(cell)
                        .graphicsLayer {
                            alpha = local.coerceIn(0f, 1f)
                            scaleX = local
                            scaleY = local
                            transformOrigin = TransformOrigin(0f, 1f)
                        },
                ) {
                    if (target != null) {
                        MenuTile(
                            label = stringResource(target.label),
                            section = target,
                            selected = target == current,
                            onClick = { onSelect(target) },
                        )
                    } else {
                        EmptyTile()
                    }
                }
            }
        }

        // Der Knopf selbst (bleibt unten links, wird beim Oeffnen grau)
        Box(Modifier.padding(start = pad, bottom = pad)) {
            GridButton(buttonSize = cell, grey = grey, onClick = onToggle)
        }
    }
}

@Composable
private fun MenuTile(label: String, section: Section, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (selected) Color(0xFF1C2238) else Color(0xFF1A1B20))
            .border(
                1.dp,
                if (selected) AmbientColors.Arty.copy(alpha = 0.7f) else AmbientColors.Light.copy(alpha = 0.10f),
                shape,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SectionGlyph(section, selected, Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            label.uppercase(),
            style = TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 2.5.sp,
                color = if (selected) AmbientColors.ArtyText else AmbientColors.Light.copy(alpha = 0.8f),
            ),
        )
    }
}

@Composable
private fun EmptyTile() {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.06f), shape),
    )
}

/** Der Raster-Knopf: vier kleine, leuchtende Quadrate auf Glas, die sanft atmen. Offen: grau, gedreht. */
@Composable
private fun GridButton(buttonSize: Dp, grey: Float, onClick: () -> Unit) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .size(buttonSize)
            .clip(shape)
            .background(Color(0xCC17181D))
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.12f + 0.06f * (1f - grey)), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val dim = size.minDimension
            val sq = dim * 0.17f
            val gapPx = dim * 0.08f
            val total = sq * 2 + gapPx
            val ox = (size.width - total) / 2f
            val oy = (size.height - total) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                Brush.radialGradient(
                    listOf(AmbientColors.Arty.copy(alpha = (0.22f + 0.08f * sin(t * 1.1f)) * (1f - grey)), Color.Transparent),
                    center = c,
                    radius = dim * 0.55f,
                ),
                radius = dim * 0.55f,
                center = c,
            )
            rotate(45f * grey, pivot = c) {
                for (k in 0 until 4) {
                    val col = k % 2
                    val row = k / 2
                    val breathe = 0.78f + 0.22f * sin(t * 1.2f + k * 1.3f)
                    val base = if (k == 0) AmbientColors.ArtyText else AmbientColors.Light
                    val color = lerp(base.copy(alpha = breathe), AmbientColors.Muted.copy(alpha = 0.6f), grey)
                    drawRoundRect(
                        color,
                        topLeft = Offset(ox + col * (sq + gapPx), oy + row * (sq + gapPx)),
                        size = Size(sq, sq),
                        cornerRadius = CornerRadius(sq * 0.3f),
                    )
                }
            }
        }
    }
}

/**
 * Eigene, animierte Linien-Symbole im Stil der App:
 *  Soul – eine Aura: feiner Ring, langsam kreisender Lichtbogen, atmender Kern.
 *  Path – ein geschwungener Weg aus Schritten nach oben zu einem Stern; ein Lichtpunkt wandert ihn entlang.
 */
@Composable
private fun SectionGlyph(section: Section, selected: Boolean, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val line = if (selected) AmbientColors.ArtyText else AmbientColors.Light.copy(alpha = 0.85f)
    val accent = if (selected) AmbientColors.Arty else AmbientColors.Gold
    Canvas(modifier) {
        val t = time.floatValue
        val w = size.width
        val c = Offset(w / 2f, size.height / 2f)
        val stroke = 1.3f * density
        when (section) {
            Section.READ -> {
                // Aura
                drawCircle(line.copy(alpha = 0.35f), radius = w * 0.44f, center = c, style = Stroke(stroke))
                rotate(t * 40f, pivot = c) {
                    drawArc(
                        line, startAngle = -90f, sweepAngle = 110f, useCenter = false,
                        topLeft = Offset(c.x - w * 0.44f, c.y - w * 0.44f), size = Size(w * 0.88f, w * 0.88f),
                        style = Stroke(stroke * 1.4f, cap = StrokeCap.Round),
                    )
                }
                drawCircle(line.copy(alpha = 0.5f), radius = w * 0.27f, center = c, style = Stroke(stroke * 0.8f))
                val breathe = 0.75f + 0.25f * sin(t * 1.3f)
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.9f * breathe), Color.Transparent), center = c, radius = w * 0.2f),
                    radius = w * 0.2f, center = c,
                )
                drawCircle(line, radius = w * 0.055f * (0.9f + 0.2f * breathe), center = c)
            }
            Section.MIND -> {
                // Sternbild: vier verbundene Sterne, einer atmet
                val pts = listOf(Offset(w * 0.18f, size.height * 0.7f), Offset(w * 0.42f, size.height * 0.35f), Offset(w * 0.68f, size.height * 0.55f), Offset(w * 0.84f, size.height * 0.2f))
                for (i in 1 until pts.size) drawLine(line.copy(alpha = 0.5f), pts[i - 1], pts[i], strokeWidth = stroke * 0.8f)
                pts.forEachIndexed { i, pt ->
                    val br = 0.7f + 0.3f * sin(t * 1.4f + i * 1.7f)
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.7f * br), Color.Transparent), center = pt, radius = w * 0.14f), radius = w * 0.14f, center = pt)
                    drawCircle(line, radius = w * 0.04f * br, center = pt)
                }
            }
            Section.REST -> {
                // Mondsichel ueber ruhigen Wellen
                val mc = Offset(w * 0.5f, size.height * 0.42f)
                val mr = w * 0.3f
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f + 0.15f * sin(t)), Color.Transparent), center = mc, radius = mr * 1.6f), radius = mr * 1.6f, center = mc)
                val moon = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(mc.x - mr, mc.y - mr, mc.x + mr, mc.y + mr))
                }
                val cut = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(mc.x - mr * 0.55f, mc.y - mr * 1.15f, mc.x + mr * 1.45f, mc.y + mr * 0.85f))
                }
                val crescent = Path().apply { op(moon, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
                drawPath(crescent, line, style = Stroke(stroke))
                for (k in 0 until 2) {
                    val y = size.height * (0.82f + k * 0.1f)
                    val wave = Path().apply {
                        moveTo(w * 0.12f, y)
                        cubicTo(w * 0.35f, y - w * 0.05f, w * 0.6f, y + w * 0.05f, w * 0.88f, y + sin(t + k) * w * 0.02f)
                    }
                    drawPath(wave, line.copy(alpha = 0.6f - k * 0.2f), style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
                }
            }
            Section.BODY -> {
                // aufgehende Sonne mit Strahlen
                val sc = Offset(w * 0.5f, size.height * 0.72f)
                val sr = w * 0.24f
                val rise = 0.9f + 0.1f * sin(t * 1.1f)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f * rise), Color.Transparent), center = sc, radius = sr * 2.2f), radius = sr * 2.2f, center = sc)
                drawArc(line, 180f, 180f, false, topLeft = Offset(sc.x - sr, sc.y - sr), size = Size(sr * 2, sr * 2), style = Stroke(stroke))
                for (k in 0 until 5) {
                    val a = Math.PI.toFloat() + (k + 0.5f) / 5f * Math.PI.toFloat()
                    val d1 = sr * 1.35f
                    val d2 = sr * (1.6f + 0.15f * rise)
                    drawLine(line, Offset(sc.x + kotlin.math.cos(a) * d1, sc.y + sin(a) * d1), Offset(sc.x + kotlin.math.cos(a) * d2, sc.y + sin(a) * d2), strokeWidth = stroke, cap = StrokeCap.Round)
                }
                drawLine(line.copy(alpha = 0.7f), Offset(w * 0.08f, sc.y), Offset(w * 0.92f, sc.y), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            Section.GAME -> {
                // Weg von unten links nach oben rechts
                val p = Path().apply {
                    moveTo(w * 0.12f, size.height * 0.92f)
                    cubicTo(w * 0.75f, size.height * 0.88f, w * 0.15f, size.height * 0.45f, w * 0.62f, size.height * 0.38f)
                    cubicTo(w * 0.82f, size.height * 0.35f, w * 0.78f, size.height * 0.2f, w * 0.8f, size.height * 0.14f)
                }
                drawPath(
                    p, line.copy(alpha = 0.9f),
                    style = Stroke(stroke * 1.2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * density, 3.5f * density))),
                )
                // Stern am Ziel
                val star = Offset(w * 0.8f, size.height * 0.1f)
                val sr = w * 0.09f * (0.85f + 0.15f * sin(t * 1.6f))
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.8f), Color.Transparent), center = star, radius = sr * 2.4f), radius = sr * 2.4f, center = star)
                drawLine(line, Offset(star.x - sr, star.y), Offset(star.x + sr, star.y), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(line, Offset(star.x, star.y - sr), Offset(star.x, star.y + sr), strokeWidth = stroke, cap = StrokeCap.Round)
                // wandernder Lichtpunkt
                val m = PathMeasure()
                m.setPath(p, false)
                val pos = m.getPosition(m.length * ((t * 0.18f) % 1f))
                drawCircle(Brush.radialGradient(listOf(accent, Color.Transparent), center = pos, radius = w * 0.12f), radius = w * 0.12f, center = pos)
                drawCircle(line, radius = w * 0.04f, center = pos)
            }
        }
    }
}
