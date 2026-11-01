package com.chrizzzzilla.self.focus.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Jahreszeiten fuer die Szenen. */
enum class Season(val label: String) {
    SPRING("Frühling"), SUMMER("Sommer"), AUTUMN("Herbst"), WINTER("Winter")
}

/** Die waehlbaren Bilder (Hintergruende beim Lesen und im Fokusmodus). */
enum class Scene(val label: String) {
    CITY("Nachtstadt"), MOUNTAIN("Berg & Schaukel"), FOREST("Dunkler Wald"), FIELD("Weizenfeld")
}

internal fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Licht und Farben fuer eine Uhrzeit + Jahreszeit. Alle Szenen leiten ihre Farben hieraus ab,
 * deshalb passen Himmel, Landschaft und Nebel immer zusammen.
 */
internal class Daylight(val hour: Float, val season: Season) {
    val sunrise: Float = when (season) { Season.SUMMER -> 5.4f; Season.WINTER -> 8.0f; else -> 6.6f }
    val sunset: Float = when (season) { Season.SUMMER -> 21.4f; Season.WINTER -> 16.6f; else -> 19.4f }

    /** Sonnenhoehe: 1 = Mittag, 0 = Horizont, negativ = Nacht. */
    val elevation: Float = run {
        val h = ((hour % 24f) + 24f) % 24f
        if (h in sunrise..sunset) {
            sin(PI.toFloat() * (h - sunrise) / (sunset - sunrise)) * (if (season == Season.WINTER) 0.7f else 1f)
        } else {
            val nightLen = 24f - (sunset - sunrise)
            val into = if (h > sunset) h - sunset else h + 24f - sunset
            -sin(PI.toFloat() * into / nightLen) * 0.8f
        }
    }
    /** 0 = Nacht, 1 = voller Tag */
    val day: Float = smooth(-0.12f, 0.3f, elevation)
    /** Daemmerung (nahe am Horizont) */
    val twilight: Float = (1f - abs(elevation) / 0.28f).coerceIn(0f, 1f)
    val night: Float = 1f - day
    val stars: Float = 1f - smooth(-0.32f, 0.02f, elevation)
    val morning: Boolean = ((hour % 24f) + 24f) % 24f < 12.5f

    /** Fortschritt der Sonne ueber den Himmel 0..1 (fuer die x-Position). */
    val sunProgress: Float = run {
        val h = ((hour % 24f) + 24f) % 24f
        ((h - sunrise) / (sunset - sunrise)).coerceIn(-0.2f, 1.2f)
    }

    val skyTop: Color
    val skyHorizon: Color
    val sunColor: Color
    /** Farbe, in die ferne Dinge im Dunst uebergehen. */
    val haze: Color
    /** Grundton fuer Silhouetten bei Nacht */
    val nightInk = Color(0xFF0A0E1C)

    init {
        val dayTop = when (season) {
            Season.WINTER -> Color(0xFF86A9CC); Season.AUTUMN -> Color(0xFF6E9AC8); else -> Color(0xFF5B9BD8)
        }
        val dayHorizon = when (season) {
            Season.WINTER -> Color(0xFFDCE6EE); Season.AUTUMN -> Color(0xFFE6DCC8); else -> Color(0xFFCDE7F3)
        }
        val nightTop = Color(0xFF050815)
        val nightHorizon = Color(0xFF1B1A3A)
        val warm = if (morning) Color(0xFFF6A46C) else Color(0xFFEE8A5C)
        val dusk = if (morning) Color(0xFF7A6FB0) else Color(0xFF6A4F94)

        var top = lerp(nightTop, dayTop, day)
        var hor = lerp(nightHorizon, dayHorizon, day)
        top = lerp(top, dusk, twilight * 0.55f)
        hor = lerp(hor, warm, twilight * 0.85f)
        skyTop = top
        skyHorizon = hor
        sunColor = lerp(Color(0xFFFFF3D6), Color(0xFFFFB46A), twilight)
        haze = lerp(hor, top, 0.25f)
    }

    /** Silhouetten-Farbe: Tagfarbe [dayColor], je nach [distance] (0 nah … 1 fern) im Dunst, nachts dunkel. */
    fun tint(dayColor: Color, distance: Float): Color {
        val lit = lerp(dayColor, haze, (distance * 0.75f).coerceIn(0f, 0.9f))
        val darkNight = lerp(nightInk, Color(0xFF1C2140), distance * 0.6f)
        var c = lerp(darkNight, lit, 0.12f + 0.88f * day)
        // warmer Saum in der Daemmerung
        c = lerp(c, lerp(c, sunColor, 0.35f), twilight * (1f - distance) * 0.5f)
        return c
    }
}

/** Sterne (fest pro Szene), Sonne/Mond, Himmel und weiche Wolken. */
internal class SkyLayer(seed: Int) {
    private val r = Random(seed)
    private val sx = FloatArray(160) { r.nextFloat() }
    private val sy = FloatArray(160) { r.nextFloat().let { it * it } }
    private val sp = FloatArray(160) { r.nextFloat() * 6.28f }
    private val ss = FloatArray(160) { 0.4f + r.nextFloat().let { it * it * it } * 1.2f }
    private val cx = FloatArray(6) { r.nextFloat() }
    private val cy = FloatArray(6) { 0.08f + r.nextFloat() * 0.3f }
    private val cw = FloatArray(6) { 0.18f + r.nextFloat() * 0.22f }

    fun draw(d: DrawScope, light: Daylight, horizonY: Float, t: Float, cloudiness: Float = 0.5f) = with(d) {
        val w = size.width
        drawRect(Brush.verticalGradient(listOf(light.skyTop, light.skyHorizon), startY = 0f, endY = horizonY))
        // Sterne
        if (light.stars > 0.02f) {
            for (i in sx.indices) {
                val a = light.stars * (0.45f + 0.35f * sin(t * (0.6f + i % 5 * 0.2f) + sp[i]))
                drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), radius = ss[i] * density, center = Offset(sx[i] * w, sy[i] * horizonY * 0.9f))
            }
        }
        // Sonne bzw. Mond
        val p = light.sunProgress
        val arcX = w * (0.08f + 0.84f * p)
        if (light.elevation > -0.12f) {
            val sunY = horizonY - light.elevation * horizonY * 0.85f
            val c = Offset(arcX, sunY)
            val r0 = min(w, horizonY) * 0.045f
            drawCircle(
                Brush.radialGradient(listOf(light.sunColor.copy(alpha = 0.55f), light.sunColor.copy(alpha = 0f)), center = c, radius = r0 * 9f),
                radius = r0 * 9f, center = c, blendMode = BlendMode.Plus,
            )
            drawCircle(light.sunColor, radius = r0, center = c)
        } else {
            // Mond auf der Gegenseite
            val np = ((light.hour - light.sunset + 24f) % 24f) / (24f - (light.sunset - light.sunrise))
            val c = Offset(w * (0.12f + 0.76f * np), horizonY * (0.22f + 0.5f * abs(np - 0.5f)))
            val r0 = min(w, horizonY) * 0.032f
            drawCircle(
                Brush.radialGradient(listOf(Color(0x33DDE6FF), Color.Transparent), center = c, radius = r0 * 6f),
                radius = r0 * 6f, center = c,
            )
            drawCircle(Color(0xFFF1EEDC).copy(alpha = 0.92f), radius = r0, center = c)
            drawCircle(light.skyTop.copy(alpha = 0.9f), radius = r0 * 0.92f, center = Offset(c.x + r0 * 0.42f, c.y - r0 * 0.15f))
        }
        // Wolken: weiche, langsam ziehende Baender
        val cloudCol = lerp(lerp(Color(0xFF1E2238), Color.White, light.day), light.sunColor, light.twilight * 0.45f)
        for (i in cx.indices) {
            val x = ((cx[i] + t * (0.004f + i * 0.0012f)) % 1.4f - 0.2f) * w
            val y = cy[i] * horizonY
            val cwPx = cw[i] * w
            for (k in 0 until 4) {
                val c = Offset(x + k * cwPx * 0.25f, y + sin(k * 1.9f + i) * cwPx * 0.05f)
                val rr = cwPx * (0.18f + 0.06f * ((k + i) % 3))
                drawCircle(
                    Brush.radialGradient(listOf(cloudCol.copy(alpha = 0.22f * cloudiness), Color.Transparent), center = c, radius = rr),
                    radius = rr, center = c,
                )
            }
        }
    }
}

/** Einfaches 1D-Rauschen aus Sinus-Summen (fuer Bergruecken, Huegel, Waldkanten). */
internal fun ridge(x: Float, seed: Int, rough: Float): Float {
    val s = seed * 1.37f
    return (sin(x * 1.3f + s) * 0.5f + sin(x * 2.9f + s * 2.1f) * 0.25f * rough +
        sin(x * 6.7f + s * 0.7f) * 0.12f * rough + sin(x * 13.1f + s * 3.3f) * 0.05f * rough)
}

/** Fuellt eine Huegel-/Bergsilhouette zwischen y-Werten bis zum unteren Rand. */
internal fun DrawScope.drawRidge(color: Color, baseY: Float, amp: Float, freq: Float, seed: Int, rough: Float, shift: Float = 0f) {
    val path = androidx.compose.ui.graphics.Path()
    val w = size.width
    val steps = 120
    path.moveTo(0f, size.height)
    for (i in 0..steps) {
        val x = w * i / steps
        val y = baseY - ridge((x / w + shift) * freq, seed, rough) * amp
        path.lineTo(x, y)
    }
    path.lineTo(w, size.height)
    path.close()
    drawPath(path, color)
}

