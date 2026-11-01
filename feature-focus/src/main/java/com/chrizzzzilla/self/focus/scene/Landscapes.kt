package com.chrizzzzilla.self.focus.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gemalte Landschaften (alles Canvas, keine Bilder): Berg mit Schaukel, dunkler Wald, Weizenfeld.
 * Licht kommt aus [Daylight] (Uhrzeit + Jahreszeit), Wind blaest in Boeen, ab und zu passiert etwas.
 */
@Composable
internal fun LandscapeScene(scene: Scene, hour: () -> Float, season: Season, rain: Boolean, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val sky = remember(scene) { SkyLayer(scene.ordinal * 17 + 3) }
    val flakes = remember { Particles(140, 9) }
    Canvas(modifier) {
        val t = time.floatValue
        val light = Daylight(hour(), season)
        val wind = Wind(t)
        when (scene) {
            Scene.MOUNTAIN -> drawMountain(t, light, season, sky, wind)
            Scene.FOREST -> drawForest(t, light, season, sky, wind)
            Scene.FIELD -> drawField(t, light, season, sky, wind)
            Scene.CITY -> Unit
        }
        // Wetter
        if (season == Season.WINTER) drawSnow(flakes, t, wind, light)
        if (season == Season.AUTUMN) drawLeaves(flakes, t, wind, light)
        if (rain) drawLightRain(t, light)
        // weiche Vignette
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color(0x99000000)),
                center = Offset(size.width / 2f, size.height * 0.5f),
                radius = max(size.width, size.height) * 0.8f,
            ),
        )
    }
}

/** Grundbewegung + gelegentliche Boeen (0..1). */
internal class Wind(t: Float) {
    val gust: Float = run {
        val g = sin(t * 0.11f + sin(t * 0.043f) * 2.2f)
        smooth(0.55f, 1f, g).pow(1.5f)
    }
    val sway: Float = sin(t * 0.7f) * 0.25f + sin(t * 1.9f) * 0.08f
    /** Neigung fuer Halme/Wipfel, abhaengig von der Position x (Wellen laufen durch). */
    fun bend(x: Float, t: Float, strength: Float): Float =
        (sway * 0.3f + sin(x * 0.012f - t * 1.6f) * (0.15f + gust * 0.6f) + gust * 0.5f) * strength
}

internal class Particles(n: Int, seed: Int) {
    private val r = Random(seed)
    val x = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { r.nextFloat() }
    val s = FloatArray(n) { 0.4f + r.nextFloat() * 0.8f }
    val p = FloatArray(n) { r.nextFloat() * 6.28f }
}

// ==========================================================================================
// Berg mit Schaukel
// ==========================================================================================

private fun seasonGrass(season: Season): Color = when (season) {
    Season.SPRING -> Color(0xFF7DBE5C)
    Season.SUMMER -> Color(0xFF5E9E45)
    Season.AUTUMN -> Color(0xFFA89A48)
    Season.WINTER -> Color(0xFFE8EEF4)
}

private fun DrawScope.drawMountain(t: Float, L: Daylight, season: Season, sky: SkyLayer, wind: Wind) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.64f
    sky.draw(this, L, horizon, t, cloudiness = 0.6f)

    // Ferne Bergketten (blau im Dunst), Schneekappen im Winter und oben immer etwas
    drawRidge(L.tint(Color(0xFF9DB4D2), 0.95f), h * 0.60f, h * 0.15f, 3.1f, 11, 1f)
    drawRidge(L.tint(Color(0xFF7894B6), 0.8f), h * 0.66f, h * 0.12f, 2.4f, 23, 1f)
    // Sonnenstrahlen ueber den Graten bei tiefer Sonne
    if (L.twilight > 0.05f && L.elevation > -0.1f) {
        val sx = w * (0.08f + 0.84f * L.sunProgress)
        val sy = horizon - L.elevation * horizon * 0.85f
        for (k in 0 until 7) {
            val a = -PI.toFloat() / 2f + (k - 3) * 0.22f + sin(t * 0.07f + k) * 0.03f
            rotate(a * 180f / PI.toFloat() + 90f, pivot = Offset(sx, sy)) {
                drawRect(
                    Brush.verticalGradient(listOf(L.sunColor.copy(alpha = 0.10f * L.twilight), Color.Transparent), startY = sy, endY = sy + h),
                    topLeft = Offset(sx - w * 0.012f, sy), size = Size(w * 0.024f, h),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
    drawRidge(L.tint(lerp(seasonGrass(season), Color(0xFF51708E), 0.55f), 0.55f), h * 0.73f, h * 0.06f, 1.8f, 37, 0.7f)
    // Nebel im Tal
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, L.haze.copy(alpha = 0.45f), Color.Transparent),
            startY = h * 0.62f, endY = h * 0.82f,
        ),
    )

    // Vordergrund-Huegel mit Gras, rechts hoeher
    val grass = L.tint(seasonGrass(season), 0.05f)
    val hill = Path()
    val topX = w * 0.68f
    val topY = h * 0.74f
    fun hillY(x: Float): Float {
        val u = (x - topX) / w
        return topY + u * u * h * 1.6f + sin(x * 0.01f) * h * 0.004f
    }
    hill.moveTo(0f, h)
    var x = 0f
    while (x <= w) { hill.lineTo(x, hillY(x)); x += w / 120f }
    hill.lineTo(w, h); hill.close()
    drawPath(hill, grass)
    // Grashalme entlang der Kuppe
    val blade = L.tint(lerp(seasonGrass(season), Color.Black, 0.2f), 0.02f)
    val rnd = Random(4)
    for (i in 0 until 260) {
        val bx = rnd.nextFloat() * w
        val by = hillY(bx) + rnd.nextFloat() * h * 0.05f
        val len = h * (0.012f + rnd.nextFloat() * 0.02f)
        val a = wind.bend(bx, t, 0.5f) + (rnd.nextFloat() - 0.5f) * 0.3f
        drawLine(blade, Offset(bx, by), Offset(bx + sin(a) * len, by - cos(a) * len), strokeWidth = 1.2f * density, cap = StrokeCap.Round)
    }
    // Fruehling: kleine Blueten
    if (season == Season.SPRING || season == Season.SUMMER) {
        val fr = Random(8)
        for (i in 0 until 50) {
            val bx = fr.nextFloat() * w
            val by = hillY(bx) + fr.nextFloat() * h * 0.08f
            val col = listOf(Color(0xFFFFE07A), Color(0xFFF7A8C4), Color.White)[i % 3]
            drawCircle(L.tint(col, 0f), radius = 1.6f * density, center = Offset(bx, by))
        }
    }

    // Schaukelgestell (Seitenansicht: umgedrehtes V) + Figur auf der Schaukel
    val ink = L.tint(Color(0xFF3B2F2A), 0f)
    val baseY = hillY(topX)
    val frameH = h * 0.24f
    val apex = Offset(topX, baseY - frameH)
    drawLine(ink, Offset(topX - frameH * 0.38f, baseY + 2f), apex, strokeWidth = 4f * density, cap = StrokeCap.Round)
    drawLine(ink, Offset(topX + frameH * 0.38f, baseY + 2f), apex, strokeWidth = 4f * density, cap = StrokeCap.Round)
    drawCircle(ink, radius = 3.5f * density, center = apex)
    val swing = 0.26f * sin(t * 1.05f) + wind.gust * 0.04f * sin(t * 2.1f)
    val rope = frameH * 0.82f
    val seat = Offset(apex.x + sin(swing) * rope, apex.y + cos(swing) * rope)
    drawLine(ink.copy(alpha = 0.9f), apex, seat, strokeWidth = 1.4f * density)
    // Figur: sitzt, schaut nach links (zur Morgensonne)
    rotate(-swing * 180f / PI.toFloat() * 0.6f, pivot = seat) {
        val s = frameH * 0.30f
        // Sitzbrett
        drawLine(ink, Offset(seat.x - s * 0.22f, seat.y), Offset(seat.x + s * 0.22f, seat.y), strokeWidth = 3f * density, cap = StrokeCap.Round)
        // Oberkoerper (leicht nach hinten gelehnt)
        val hip = Offset(seat.x + s * 0.05f, seat.y - s * 0.06f)
        val shoulder = Offset(hip.x + s * 0.08f, hip.y - s * 0.62f)
        drawLine(ink, hip, shoulder, strokeWidth = s * 0.28f, cap = StrokeCap.Round)
        // Kopf
        drawCircle(ink, radius = s * 0.17f, center = Offset(shoulder.x - s * 0.04f, shoulder.y - s * 0.24f))
        // Arm zum Seil
        drawLine(ink, Offset(shoulder.x, shoulder.y + s * 0.05f), Offset(seat.x + s * 0.02f, seat.y - s * 0.5f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
        // Beine nach vorne (links), Unterschenkel baumeln mit
        val knee = Offset(hip.x - s * 0.42f, hip.y + s * 0.02f)
        val legSwing = sin(t * 1.05f + 0.6f) * 0.25f
        val foot = Offset(knee.x - sin(legSwing) * s * 0.4f, knee.y + cos(legSwing) * s * 0.42f)
        drawLine(ink, hip, knee, strokeWidth = s * 0.14f, cap = StrokeCap.Round)
        drawLine(ink, knee, foot, strokeWidth = s * 0.12f, cap = StrokeCap.Round)
    }

    drawBirds(t, L, horizon)
}

// ==========================================================================================
// Dunkler Wald
// ==========================================================================================

private fun seasonForest(season: Season): Color = when (season) {
    Season.SPRING -> Color(0xFF3F6E44)
    Season.SUMMER -> Color(0xFF2C5A38)
    Season.AUTUMN -> Color(0xFF4A5A34)
    Season.WINTER -> Color(0xFF2A3A3C)
}

private fun DrawScope.drawPine(base: Offset, height: Float, color: Color, lean: Float, snow: Color?) {
    rotate(lean, pivot = base) {
        val tiers = 5
        val path = Path()
        for (k in 0 until tiers) {
            val top = base.y - height * (0.25f + 0.75f * (k + 1) / tiers)
            val bottom = base.y - height * (0.12f + 0.75f * k / tiers)
            val half = height * 0.26f * (1f - k / tiers.toFloat() * 0.75f)
            path.moveTo(base.x, top)
            path.lineTo(base.x + half, bottom)
            path.lineTo(base.x - half, bottom)
            path.close()
        }
        drawRect(color, topLeft = Offset(base.x - height * 0.02f, base.y - height * 0.15f), size = Size(height * 0.04f, height * 0.15f))
        drawPath(path, color)
        if (snow != null) {
            for (k in 0 until tiers) {
                val top = base.y - height * (0.25f + 0.75f * (k + 1) / tiers)
                val half = height * 0.26f * (1f - k / tiers.toFloat() * 0.75f) * 0.45f
                val p2 = Path().apply {
                    moveTo(base.x, top); lineTo(base.x + half, top + half * 0.9f); lineTo(base.x - half, top + half * 0.9f); close()
                }
                drawPath(p2, snow)
            }
        }
    }
}

private fun DrawScope.drawForest(t: Float, L: Daylight, season: Season, sky: SkyLayer, wind: Wind) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.6f
    sky.draw(this, L, horizon, t, cloudiness = 0.3f)
    val snow = if (season == Season.WINTER) L.tint(Color(0xFFEFF4F8), 0.3f) else null

    // Lichtstrahlen durch die Baeume (Mond oder Sonne)
    val beam = if (L.day > 0.3f) L.sunColor else Color(0xFFBFD0FF)
    val beamA = 0.05f + 0.07f * L.day
    for (k in 0 until 5) {
        val bx = w * (0.15f + k * 0.18f) + sin(t * 0.05f + k) * w * 0.01f
        rotate(18f, pivot = Offset(bx, 0f)) {
            drawRect(
                Brush.verticalGradient(listOf(beam.copy(alpha = beamA), Color.Transparent), startY = 0f, endY = h),
                topLeft = Offset(bx, 0f), size = Size(w * 0.035f, h * 1.2f), blendMode = BlendMode.Plus,
            )
        }
    }

    // 5 Baumreihen von hinten nach vorne, dazwischen Nebel
    for (layer in 0 until 5) {
        val dist = 1f - layer / 4f
        val col = L.tint(seasonForest(season), dist)
        val r = Random(layer * 31 + 5)
        val baseY = h * (0.58f + layer * 0.075f)
        val treeH = h * (0.18f + layer * 0.11f)
        val spacing = w * (0.035f + layer * 0.03f)
        var x = -spacing + r.nextFloat() * spacing
        while (x < w + spacing) {
            val th = treeH * (0.7f + r.nextFloat() * 0.6f)
            val lean = wind.bend(x, t, 2.5f + layer * 0.8f) * (0.5f + r.nextFloat() * 0.5f)
            drawPine(Offset(x, baseY + r.nextFloat() * h * 0.02f), th, col, lean, snow)
            x += spacing * (0.6f + r.nextFloat() * 0.8f)
        }
        // Boden dieser Reihe
        drawRect(col, topLeft = Offset(0f, baseY), size = Size(w, h - baseY))
        if (layer < 4) {
            val fogY = baseY - treeH * 0.15f
            val drift = sin(t * 0.05f + layer) * w * 0.05f
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, L.haze.copy(alpha = 0.28f), Color.Transparent), startY = fogY - h * 0.08f, endY = fogY + h * 0.08f),
                topLeft = Offset(drift - w * 0.1f, fogY - h * 0.08f), size = Size(w * 1.2f, h * 0.16f),
            )
        }
    }

    // Eule auf einem Ast rechts
    val ink = L.tint(Color(0xFF2A221C), 0f)
    val branchY = h * 0.42f
    drawLine(ink, Offset(w * 0.86f, branchY), Offset(w, branchY - h * 0.03f), strokeWidth = 6f * density, cap = StrokeCap.Round)
    val owl = Offset(w * 0.9f, branchY - h * 0.035f)
    val os = h * 0.045f
    drawOval(ink, topLeft = Offset(owl.x - os * 0.55f, owl.y - os * 1.1f), size = Size(os * 1.1f, os * 1.3f))
    drawCircle(ink, radius = os * 0.48f, center = Offset(owl.x, owl.y - os * 1.15f))
    // Federohren
    drawLine(ink, Offset(owl.x - os * 0.3f, owl.y - os * 1.45f), Offset(owl.x - os * 0.42f, owl.y - os * 1.75f), strokeWidth = os * 0.16f, cap = StrokeCap.Round)
    drawLine(ink, Offset(owl.x + os * 0.3f, owl.y - os * 1.45f), Offset(owl.x + os * 0.42f, owl.y - os * 1.75f), strokeWidth = os * 0.16f, cap = StrokeCap.Round)
    // Augen: leuchten nachts, blinzeln, schauen ab und zu zur Seite
    val blink = (t % 5.3f) < 0.16f
    val look = sin(floor(t / 9f) * 2.3f) * os * 0.1f
    if (!blink) {
        val eyeCol = lerp(Color(0xFFC79A3C), Color(0xFFFFC34A), L.night)
        for (side in intArrayOf(-1, 1)) {
            val e = Offset(owl.x + side * os * 0.2f + look, owl.y - os * 1.15f)
            if (L.night > 0.3f) {
                drawCircle(eyeCol.copy(alpha = 0.35f * L.night), radius = os * 0.25f, center = e, blendMode = BlendMode.Plus)
            }
            drawCircle(eyeCol, radius = os * 0.1f, center = e)
        }
    }

    // Fuchs: alle ~28 s laeuft er ueber den Waldboden
    val cycle = 28f
    val n = floor(t / cycle).toInt()
    val local = t - n * cycle
    if (local < 10f) {
        val p = local / 10f
        val dir = if (n % 2 == 0) 1f else -1f
        val fx = if (dir > 0) -w * 0.1f + p * w * 1.2f else w * 1.1f - p * w * 1.2f
        val fy = h * 0.9f
        drawFox(Offset(fx, fy), h * 0.06f, dir, local, L)
    }

    // Gluehwuermchen nachts / Staub im Licht tagsueber
    val r = Random(77)
    for (i in 0 until 46) {
        val px = (r.nextFloat() * w + sin(t * 0.3f + i) * w * 0.03f)
        val py = h * (0.45f + r.nextFloat() * 0.5f) + cos(t * 0.4f + i * 1.7f) * h * 0.02f
        val on = (0.5f + 0.5f * sin(t * (1f + i % 3 * 0.4f) + i)).pow(3)
        val col = if (L.night > 0.5f) Color(0xFFD9FF7A) else Color(0xFFFFF4D0)
        val a = if (L.night > 0.5f) on * L.night else 0.25f * L.day
        drawCircle(col.copy(alpha = (a * 0.4f).coerceIn(0f, 1f)), radius = 6f * density, center = Offset(px, py), blendMode = BlendMode.Plus)
        drawCircle(col.copy(alpha = a.coerceIn(0f, 1f)), radius = 1.4f * density, center = Offset(px, py))
    }
}

private fun DrawScope.drawFox(pos: Offset, s: Float, dir: Float, t: Float, L: Daylight) {
    val body = L.tint(Color(0xFFC4652C), 0.05f)
    val dark = L.tint(Color(0xFF3A2418), 0.05f)
    val gait = t * 9f
    // Beine
    for (k in 0 until 4) {
        val lx = pos.x + dir * s * (-0.55f + (k / 2) * 1.0f) + (k % 2) * dir * s * 0.12f
        val swing = sin(gait + k * PI.toFloat() * 0.5f) * s * 0.18f
        drawLine(dark, Offset(lx, pos.y - s * 0.35f), Offset(lx + dir * swing, pos.y), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
    }
    // Koerper
    drawOval(body, topLeft = Offset(pos.x - s * 0.75f, pos.y - s * 0.72f), size = Size(s * 1.5f, s * 0.5f))
    // Schwanz (buschig, wippt)
    val tailBase = Offset(pos.x - dir * s * 0.7f, pos.y - s * 0.55f)
    val tailTip = Offset(tailBase.x - dir * s * 0.8f, tailBase.y + s * 0.1f + sin(gait * 0.5f) * s * 0.06f)
    drawLine(body, tailBase, tailTip, strokeWidth = s * 0.32f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFF2E6D8).copy(alpha = 0.9f * (0.3f + 0.7f * L.day)), radius = s * 0.1f, center = tailTip)
    // Kopf + Ohren + Schnauze
    val head = Offset(pos.x + dir * s * 0.82f, pos.y - s * 0.78f + sin(gait) * s * 0.02f)
    drawCircle(body, radius = s * 0.22f, center = head)
    val snout = Path().apply {
        moveTo(head.x + dir * s * 0.12f, head.y - s * 0.08f)
        lineTo(head.x + dir * s * 0.45f, head.y + s * 0.06f)
        lineTo(head.x + dir * s * 0.08f, head.y + s * 0.14f)
        close()
    }
    drawPath(snout, body)
    for (e in floatArrayOf(-0.08f, 0.1f)) {
        val ear = Path().apply {
            moveTo(head.x + dir * s * e - s * 0.08f, head.y - s * 0.12f)
            lineTo(head.x + dir * s * e, head.y - s * 0.42f)
            lineTo(head.x + dir * s * e + s * 0.08f, head.y - s * 0.12f)
            close()
        }
        drawPath(ear, dark)
    }
}

// ==========================================================================================
// Weizenfeld mit Weg
// ==========================================================================================

private fun wheatColor(season: Season): Color = when (season) {
    Season.SPRING -> Color(0xFF8FBF5A)
    Season.SUMMER -> Color(0xFFE2B657)
    Season.AUTUMN -> Color(0xFFB98A4E)
    Season.WINTER -> Color(0xFFE9EEF3)
}

private fun DrawScope.drawField(t: Float, L: Daylight, season: Season, sky: SkyLayer, wind: Wind) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.5f
    sky.draw(this, L, horizon, t, cloudiness = 0.8f)

    drawRidge(L.tint(Color(0xFF8AA0B8), 0.9f), h * 0.5f, h * 0.05f, 2.2f, 5, 0.8f)
    // Baumreihe in der Ferne
    val trees = L.tint(seasonForest(season), 0.65f)
    val tr = Random(12)
    var x = 0f
    while (x < w) {
        val r = h * (0.012f + tr.nextFloat() * 0.018f)
        drawCircle(trees, radius = r, center = Offset(x, h * 0.515f - r * 0.6f))
        x += r * (1.1f + tr.nextFloat())
    }
    drawRect(trees, topLeft = Offset(0f, h * 0.515f), size = Size(w, h * 0.01f))
    // Einzelner grosser Baum rechts
    val big = L.tint(seasonForest(season), 0.35f)
    val trunk = Offset(w * 0.8f, h * 0.56f)
    drawLine(L.tint(Color(0xFF3A2C22), 0.35f), trunk, Offset(trunk.x, trunk.y - h * 0.09f), strokeWidth = h * 0.012f)
    val lean = wind.bend(trunk.x, t, 1.2f)
    rotate(lean, pivot = trunk) {
        for (k in 0 until 6) {
            val c = Offset(trunk.x + (k - 2.5f) * h * 0.022f, trunk.y - h * (0.11f + (k % 2) * 0.03f))
            drawCircle(big, radius = h * 0.045f, center = c)
        }
    }

    // Feld: Grundflaeche + Windwellen als helle Baender
    val fieldTop = h * 0.53f
    val fieldBottom = h * 0.8f
    val wheat = L.tint(wheatColor(season), 0.1f)
    drawRect(
        Brush.verticalGradient(listOf(L.tint(wheatColor(season), 0.6f), wheat), startY = fieldTop, endY = fieldBottom),
        topLeft = Offset(0f, fieldTop), size = Size(w, fieldBottom - fieldTop),
    )
    val waveCol = lerp(wheat, Color.White, 0.35f)
    for (k in 0 until 9) {
        val y = fieldTop + (fieldBottom - fieldTop) * ((k + 0.5f) / 9f).pow(1.3f)
        val path = Path()
        path.moveTo(0f, y)
        var xx = 0f
        while (xx <= w) {
            path.lineTo(xx, y + sin(xx * 0.008f - t * 1.4f + k) * (2f + k * 1.2f) * density)
            xx += w / 80f
        }
        val a = (0.07f + wind.gust * 0.12f) * (0.4f + 0.6f * L.day)
        drawPath(path, waveCol.copy(alpha = a), style = Stroke(width = (1f + k * 0.9f) * density))
    }
    // vordere Halme einzeln (mit Aehren)
    if (season != Season.WINTER) {
        val stalk = L.tint(lerp(wheatColor(season), Color.Black, 0.15f), 0f)
        val ear = L.tint(lerp(wheatColor(season), Color.White, 0.1f), 0f)
        val rr = Random(21)
        for (row in 0 until 6) {
            val y = fieldBottom - row * h * 0.012f
            val len = h * (0.05f - row * 0.005f) * (if (season == Season.AUTUMN) 0.5f else 1f)
            var sx = rr.nextFloat() * 8f
            while (sx < w) {
                val a = wind.bend(sx, t, 0.6f) + (rr.nextFloat() - 0.5f) * 0.15f
                val tip = Offset(sx + sin(a) * len, y - cos(a) * len)
                drawLine(stalk, Offset(sx, y), tip, strokeWidth = 1.1f * density, cap = StrokeCap.Round)
                if (season != Season.AUTUMN) {
                    rotate(a * 180f / PI.toFloat(), pivot = tip) {
                        drawOval(ear, topLeft = Offset(tip.x - 1.6f * density, tip.y - len * 0.22f), size = Size(3.2f * density, len * 0.24f))
                    }
                }
                sx += (5f + rr.nextFloat() * 6f) * density
            }
        }
    }

    // Feldweg im Vordergrund
    val roadTop = h * 0.83f
    val roadBottom = h * 0.93f
    drawRect(L.tint(lerp(seasonGrass(season), Color.Black, 0.1f), 0f), topLeft = Offset(0f, fieldBottom), size = Size(w, h - fieldBottom))
    drawRect(L.tint(if (season == Season.WINTER) Color(0xFFD9DFE6) else Color(0xFFB79C78), 0f), topLeft = Offset(0f, roadTop), size = Size(w, roadBottom - roadTop))
    val track = L.tint(if (season == Season.WINTER) Color(0xFFBCC5CF) else Color(0xFF9C8262), 0f)
    drawRect(track, topLeft = Offset(0f, roadTop + (roadBottom - roadTop) * 0.28f), size = Size(w, (roadBottom - roadTop) * 0.12f))
    drawRect(track, topLeft = Offset(0f, roadTop + (roadBottom - roadTop) * 0.62f), size = Size(w, (roadBottom - roadTop) * 0.12f))

    // Reh: alle ~37 s tritt es aus dem Feld, quert den Weg und verschwindet
    run {
        val cycle = 37f
        val n = floor((t + 12f) / cycle).toInt()
        val local = t + 12f - n * cycle
        if (local < 14f) {
            val p = local / 14f
            val startX = w * (0.2f + (n % 3) * 0.25f)
            val dir = if (n % 2 == 0) 1f else -1f
            val emerge = smooth(0f, 0.2f, p)
            val px = startX + dir * p * w * 0.35f
            val py = mixF(fieldBottom - h * 0.01f, roadBottom + h * 0.02f, smooth(0.15f, 0.9f, p))
            drawDeer(Offset(px, py), h * 0.07f * (0.85f + 0.3f * smooth(0.15f, 1f, p)), dir, local, L, emerge)
        }
    }
    // Spaziergaenger: alle ~45 s laeuft jemand den Weg entlang (nachts mit Laterne)
    run {
        val cycle = 45f
        val n = floor(t / cycle).toInt()
        val local = t - n * cycle
        if (local < 18f) {
            val p = local / 18f
            val dir = if (n % 2 == 0) 1f else -1f
            val px = if (dir > 0) -w * 0.05f + p * w * 1.1f else w * 1.05f - p * w * 1.1f
            drawWalker(Offset(px, roadTop + (roadBottom - roadTop) * 0.55f), h * 0.12f, dir, local, L)
        }
    }

    drawBirds(t, L, horizon)
}

private fun DrawScope.drawDeer(pos: Offset, s: Float, dir: Float, t: Float, L: Daylight, emerge: Float) {
    val body = L.tint(Color(0xFF8E5B36), 0f).copy(alpha = emerge)
    val gait = t * 4.2f
    for (k in 0 until 4) {
        val lx = pos.x + dir * s * (-0.5f + (k / 2) * 0.95f) + (k % 2) * dir * s * 0.1f
        val swing = sin(gait + k * PI.toFloat() * 0.5f) * s * 0.14f
        drawLine(body, Offset(lx, pos.y - s * 0.55f), Offset(lx + dir * swing, pos.y), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
    drawOval(body, topLeft = Offset(pos.x - s * 0.62f, pos.y - s * 0.95f), size = Size(s * 1.25f, s * 0.48f))
    val neckBase = Offset(pos.x + dir * s * 0.45f, pos.y - s * 0.85f)
    val head = Offset(neckBase.x + dir * s * 0.2f, neckBase.y - s * 0.55f + sin(gait * 0.5f) * s * 0.02f)
    drawLine(body, neckBase, head, strokeWidth = s * 0.16f, cap = StrokeCap.Round)
    drawOval(body, topLeft = Offset(head.x - s * 0.1f + dir * s * 0.05f, head.y - s * 0.09f), size = Size(s * 0.3f, s * 0.16f))
    drawLine(body, head, Offset(head.x - dir * s * 0.08f, head.y - s * 0.18f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
}

private fun DrawScope.drawWalker(pos: Offset, s: Float, dir: Float, t: Float, L: Daylight) {
    val ink = L.tint(Color(0xFF2B2622), 0f)
    val gait = t * 5f
    val hip = Offset(pos.x, pos.y - s * 0.45f)
    for (k in 0 until 2) {
        val a = sin(gait + k * PI.toFloat()) * 0.35f
        drawLine(ink, hip, Offset(hip.x + sin(a) * s * 0.45f, hip.y + cos(a) * s * 0.45f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
    val shoulder = Offset(hip.x + dir * s * 0.02f, hip.y - s * 0.36f)
    drawLine(ink, hip, shoulder, strokeWidth = s * 0.13f, cap = StrokeCap.Round)
    drawCircle(ink, radius = s * 0.075f, center = Offset(shoulder.x, shoulder.y - s * 0.12f))
    val armA = sin(gait + PI.toFloat()) * 0.3f
    val hand = Offset(shoulder.x + sin(armA) * s * 0.33f, shoulder.y + cos(armA) * s * 0.33f)
    drawLine(ink, shoulder, hand, strokeWidth = s * 0.05f, cap = StrokeCap.Round)
    // Laterne in der Dunkelheit
    if (L.night > 0.5f) {
        drawCircle(Color(0xFFFFC46A).copy(alpha = 0.35f * L.night), radius = s * 0.3f, center = hand, blendMode = BlendMode.Plus)
        drawCircle(Color(0xFFFFD48A), radius = s * 0.035f, center = hand)
    }
}

// ==========================================================================================
// Gemeinsames: Voegel, Schnee, Laub, Regen
// ==========================================================================================

private fun DrawScope.drawBirds(t: Float, L: Daylight, horizon: Float) {
    if (L.day < 0.2f) return
    val cycle = 33f
    val n = floor(t / cycle).toInt()
    val local = t - n * cycle
    if (local > 16f) return
    val p = local / 16f
    val w = size.width
    val baseX = -w * 0.1f + p * w * 1.2f
    val baseY = horizon * (0.25f + (n % 3) * 0.12f)
    val ink = L.tint(Color(0xFF2A2A30), 0.3f)
    for (k in 0 until 5) {
        val bx = baseX - k * w * 0.018f
        val by = baseY + abs(k - 2) * horizon * 0.02f
        val flap = sin(t * 8f + k) * 4f * density
        val sz = 6f * density
        drawLine(ink, Offset(bx - sz, by - flap), Offset(bx, by), strokeWidth = 1.3f * density, cap = StrokeCap.Round)
        drawLine(ink, Offset(bx, by), Offset(bx + sz, by - flap), strokeWidth = 1.3f * density, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSnow(pt: Particles, t: Float, wind: Wind, L: Daylight) {
    val w = size.width
    val h = size.height
    val col = lerp(Color(0xFF9FAABF), Color.White, 0.3f + 0.7f * L.day)
    for (i in pt.x.indices) {
        val y = ((pt.y[i] + t * 0.03f * pt.s[i]) % 1f) * h
        val x = ((pt.x[i] + sin(t * 0.5f + pt.p[i]) * 0.01f + t * 0.01f * (1f + wind.gust * 3f)) % 1f) * w
        drawCircle(col.copy(alpha = 0.75f), radius = pt.s[i] * 2.2f * density, center = Offset(x, y))
    }
}

private fun DrawScope.drawLeaves(pt: Particles, t: Float, wind: Wind, L: Daylight) {
    val w = size.width
    val h = size.height
    for (i in 0 until 26) {
        val y = ((pt.y[i] + t * 0.025f * pt.s[i]) % 1f) * h
        val x = ((pt.x[i] + t * 0.015f * (1f + wind.gust * 4f) + sin(t + pt.p[i]) * 0.02f) % 1f) * w
        val col = L.tint(listOf(Color(0xFFD9822B), Color(0xFFB8452A), Color(0xFFE2B04A))[i % 3], 0f)
        rotate(sin(t * 2f + pt.p[i]) * 70f, pivot = Offset(x, y)) {
            drawOval(col, topLeft = Offset(x - 4f * density, y - 2f * density), size = Size(8f * density, 4f * density))
        }
    }
}

private fun DrawScope.drawLightRain(t: Float, L: Daylight) {
    val w = size.width
    val h = size.height
    val col = lerp(Color(0xFF8E9CC0), Color(0xFFDDE6FF), L.day).copy(alpha = 0.18f)
    val r = Random(3)
    for (i in 0 until 130) {
        val speed = 900f + r.nextFloat() * 500f
        val len = 16f + r.nextFloat() * 22f
        val x0 = r.nextFloat() * (w + 200f)
        val y = ((r.nextFloat() * (h + len) + t * speed) % (h + len)) - len
        val x = ((x0 - t * speed * 0.15f) % (w + 200f) + w + 200f) % (w + 200f) - 100f
        drawLine(col, Offset(x, y), Offset(x - len * 0.15f, y + len), strokeWidth = 1f * density)
    }
}

private fun mixF(a: Float, b: Float, t: Float): Float = a + (b - a) * t
