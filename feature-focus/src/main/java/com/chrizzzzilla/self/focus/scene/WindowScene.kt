package com.chrizzzzilla.self.focus.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animierte Szene: Blick aus einem Zimmerfenster auf eine Altstadt am Hang.
 * Tageszeit folgt der echten Uhrzeit (Tag, Abendrot, Nacht mit Sternen und Lichtern).
 * Komplett mit Canvas gezeichnet – keine Bilder, keine Videos.
 *
 * @param pulse liefert 0..1 aus der Musik (Kick), laesst das Lampenlicht leicht atmen.
 */
@Composable
fun WindowScene(
    rain: Boolean,
    pulse: () -> Float,
    modifier: Modifier = Modifier,
    seed: Int = 7,
) {
    val time = remember { mutableFloatStateOf(0f) }
    val glow = remember { mutableFloatStateOf(0f) }
    val rainAmount = remember { mutableFloatStateOf(if (rain) 1f else 0f) }
    val city = remember(seed) { City.generate(seed) }
    val drops = remember { RainDrops.generate(110) }
    val stars = remember { Stars.generate(70) }

    LaunchedEffect(rain) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                time.floatValue += dt
                // weich nachfuehren, damit nichts springt
                glow.floatValue += (pulse() - glow.floatValue) * (dt * 6f).coerceAtMost(1f)
                val target = if (rain) 1f else 0f
                rainAmount.floatValue += (target - rainAmount.floatValue) * (dt * 0.8f).coerceAtMost(1f)
            }
        }
    }

    Canvas(modifier) {
        val t = time.floatValue
        val now = LocalTime.now()
        val hour = now.hour + now.minute / 60f
        val sky = SkyPalette.at(hour, rainAmount.floatValue)
        drawRoom(t, sky, city, drops, stars, glow.floatValue, rainAmount.floatValue)
    }
}

// ------------------------------------------------------------------------------------------
// Tageszeit
// ------------------------------------------------------------------------------------------

private class SkyPalette(
    val top: Color,
    val bottom: Color,
    /** 0 = Tag, 1 = tiefe Nacht */
    val night: Float,
    val dusk: Float,
    val wall: Color,
) {
    companion object {
        private fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        fun at(hour: Float, rain: Float): SkyPalette {
            val day = smooth(5.5f, 8f, hour) * (1f - smooth(18f, 21f, hour))
            val dusk = maxOf(
                1f - abs(hour - 19.5f) / 1.6f,
                1f - abs(hour - 6.8f) / 1.3f,
            ).coerceIn(0f, 1f)
            val night = 1f - day

            val dayTop = Color(0xFF7FC0E3)
            val dayBottom = Color(0xFFCFE9F1)
            val nightTop = Color(0xFF0B1230)
            val nightBottom = Color(0xFF2D2550)
            val duskTop = Color(0xFF5B5794)
            val duskBottom = Color(0xFFF2A37A)

            var top = lerp(nightTop, dayTop, day)
            var bottom = lerp(nightBottom, dayBottom, day)
            top = lerp(top, duskTop, dusk * 0.7f)
            bottom = lerp(bottom, duskBottom, dusk * 0.8f)
            // Regen: Himmel grauer
            val grey = lerp(Color(0xFF8C97A3), Color(0xFF1C2230), night)
            top = lerp(top, grey, rain * 0.55f)
            bottom = lerp(bottom, grey, rain * 0.45f)

            val wall = lerp(Color(0xFF2B1F1B), Color(0xFF6B4E3E), day * 0.8f)
            return SkyPalette(top, bottom, night, dusk, wall)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Stadt (deterministisch erzeugt)
// ------------------------------------------------------------------------------------------

private class House(
    val x: Float, val w: Float, val base: Float, val h: Float,
    val facade: Color, val roof: Color, val roofKind: Int, val row: Int,
    val windows: List<Window>,
)

private class Window(val cx: Float, val cy: Float, val litSeed: Int, val shutters: Boolean)

private class City(val houses: List<House>) {
    companion object {
        private val facades = listOf(
            0xFFEAD7B7, 0xFFF1CDA4, 0xFFDDA57F, 0xFFEFE0CB, 0xFFCF9372, 0xFFF4E6C9, 0xFFD8B891, 0xFFE9C3B0,
        ).map { Color(it) }
        private val roofs = listOf(0xFFB5553C, 0xFFA2472F, 0xFF8E3D2B, 0xFFC4694C, 0xFF9C5A45).map { Color(it) }

        fun generate(seed: Int): City {
            val rnd = Random(seed)
            val houses = mutableListOf<House>()
            val rows = 6
            for (row in 0 until rows) {
                // hintere Reihen stehen hoeher am Hang
                val base = 0.34f + row * 0.115f
                var x = -0.06f + rnd.nextFloat() * 0.04f
                while (x < 1.06f) {
                    val w = 0.06f + rnd.nextFloat() * 0.07f + row * 0.006f
                    val h = 0.11f + rnd.nextFloat() * 0.12f + row * 0.01f
                    val floors = (h / 0.042f).toInt().coerceAtLeast(2)
                    val cols = (w / 0.026f).toInt().coerceAtLeast(1)
                    val windows = buildList {
                        for (f in 0 until floors) for (c in 0 until cols) {
                            if (rnd.nextFloat() < 0.88f) {
                                add(
                                    Window(
                                        cx = (c + 0.5f) / cols,
                                        cy = 0.18f + (f + 0.5f) / floors * 0.78f,
                                        litSeed = rnd.nextInt(),
                                        shutters = rnd.nextFloat() < 0.4f,
                                    ),
                                )
                            }
                        }
                    }
                    houses += House(
                        x = x, w = w, base = base, h = h,
                        facade = facades[rnd.nextInt(facades.size)],
                        roof = roofs[rnd.nextInt(roofs.size)],
                        roofKind = rnd.nextInt(3),
                        row = row,
                        windows = windows,
                    )
                    x += w * (0.92f + rnd.nextFloat() * 0.1f)
                }
            }
            // von hinten (oben am Hang) nach vorne zeichnen
            return City(houses.sortedBy { it.row })
        }
    }
}

private class RainDrops(val x: FloatArray, val speed: FloatArray, val len: FloatArray, val offset: FloatArray) {
    companion object {
        fun generate(n: Int): RainDrops {
            val r = Random(42)
            return RainDrops(
                FloatArray(n) { r.nextFloat() },
                FloatArray(n) { 0.5f + r.nextFloat() * 0.6f },
                FloatArray(n) { 0.025f + r.nextFloat() * 0.05f },
                FloatArray(n) { r.nextFloat() * 2f },
            )
        }
    }
}

private class Stars(val x: FloatArray, val y: FloatArray, val phase: FloatArray, val size: FloatArray) {
    companion object {
        fun generate(n: Int): Stars {
            val r = Random(99)
            return Stars(
                FloatArray(n) { r.nextFloat() },
                FloatArray(n) { r.nextFloat() * 0.3f },
                FloatArray(n) { r.nextFloat() * 6.28f },
                FloatArray(n) { 0.6f + r.nextFloat() * 1.4f },
            )
        }
    }
}

private fun hash01(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}

// ------------------------------------------------------------------------------------------
// Zeichnen
// ------------------------------------------------------------------------------------------

private fun DrawScope.drawRoom(
    t: Float,
    sky: SkyPalette,
    city: City,
    drops: RainDrops,
    stars: Stars,
    glow: Float,
    rain: Float,
) {
    val w = size.width
    val h = size.height
    val portrait = h > w

    // Fensterausschnitt (Glas)
    val win = if (portrait) {
        Rect(w * 0.08f, h * 0.07f, w * 0.92f, h * 0.62f)
    } else {
        Rect(w * 0.16f, h * 0.06f, w * 0.84f, h * 0.70f)
    }

    // --- Wand ---
    drawRect(sky.wall)

    // --- Blick nach draussen ---
    clipRect(win.left, win.top, win.right, win.bottom) {
        drawRect(Brush.verticalGradient(listOf(sky.top, sky.bottom), startY = win.top, endY = win.bottom))

        // Sterne + Mond nachts
        if (sky.night > 0.3f) {
            val a = ((sky.night - 0.3f) / 0.7f) * (1f - rain * 0.8f)
            for (i in stars.x.indices) {
                val tw = 0.55f + 0.45f * sin(t * 1.3f + stars.phase[i])
                drawCircle(
                    Color.White.copy(alpha = (a * tw).coerceIn(0f, 1f)),
                    radius = stars.size[i] * density * 0.8f,
                    center = Offset(win.left + stars.x[i] * win.width, win.top + stars.y[i] * win.height),
                )
            }
            val moon = Offset(win.left + win.width * 0.78f, win.top + win.height * 0.14f)
            val mr = win.minDimension * 0.05f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x55FFF4D6), Color.Transparent), center = moon, radius = mr * 4f,
                ),
                radius = mr * 4f, center = moon, alpha = a,
            )
            drawCircle(Color(0xFFFFF3D2).copy(alpha = a), radius = mr, center = moon)
        }

        // Wolken (ziehen langsam)
        val cloudAlpha = (0.55f - sky.night * 0.35f) + rain * 0.3f
        for (i in 0 until 5) {
            val speed = 0.004f + i * 0.0015f
            val cx = ((i * 0.27f + t * speed) % 1.4f) - 0.2f
            val cy = 0.08f + (i % 3) * 0.07f
            val cw = 0.18f + (i % 2) * 0.1f
            val base = Offset(win.left + cx * win.width, win.top + cy * win.height)
            val cloudColor = lerp(Color.White, Color(0xFF9AA3B5), maxOf(sky.night, rain * 0.6f))
                .copy(alpha = cloudAlpha.coerceIn(0f, 0.85f))
            for (k in 0 until 3) {
                drawOval(
                    cloudColor,
                    topLeft = Offset(base.x + k * cw * win.width * 0.28f, base.y - (k % 2) * win.height * 0.02f),
                    size = Size(cw * win.width * 0.5f, win.height * 0.05f),
                )
            }
        }

        drawCity(t, city, win, sky)

        // Leichter Dunst ueber der Stadt
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, sky.bottom.copy(alpha = 0.18f)),
                startY = win.top + win.height * 0.3f, endY = win.bottom,
            ),
            topLeft = win.topLeft, size = win.size,
        )

        // Regen an der Scheibe
        if (rain > 0.01f) {
            val dropColor = Color.White.copy(alpha = 0.32f * rain)
            for (i in drops.x.indices) {
                val y = ((t * drops.speed[i] * 0.6f + drops.offset[i]) % 1.25f) - 0.1f
                val x = drops.x[i]
                val start = Offset(win.left + x * win.width, win.top + y * win.height)
                val end = Offset(start.x - drops.len[i] * win.width * 0.12f, start.y + drops.len[i] * win.height)
                drawLine(dropColor, start, end, strokeWidth = 1.3f * density, cap = StrokeCap.Round)
            }
            // stehende Tropfen auf dem Glas
            for (i in 0 until 40) {
                val px = hash01(i, 3)
                val py = (hash01(i, 7) + t * 0.004f * hash01(i, 11)) % 1f
                drawCircle(
                    Color.White.copy(alpha = 0.22f * rain),
                    radius = (1.2f + 2f * hash01(i, 5)) * density,
                    center = Offset(win.left + px * win.width, win.top + py * win.height),
                )
            }
        }
    }

    // --- Fensterrahmen ---
    val frame = Color(0xFF6A4834)
    val frameDark = Color(0xFF4A3125)
    val fw = win.minDimension * 0.025f
    drawRect(frameDark, topLeft = Offset(win.left - fw, win.top - fw), size = Size(win.width + 2 * fw, fw), alpha = 1f)
    drawRect(frame, topLeft = Offset(win.left - fw, win.top), size = Size(fw, win.height))
    drawRect(frame, topLeft = Offset(win.right, win.top), size = Size(fw, win.height))
    drawRect(frame, topLeft = Offset(win.center.x - fw * 0.4f, win.top), size = Size(fw * 0.8f, win.height))
    drawRect(frame, topLeft = Offset(win.left, win.top + win.height * 0.42f), size = Size(win.width, fw * 0.7f))

    // --- Fensterbrett ---
    val sillTop = win.bottom
    val sillH = h * 0.035f
    drawRect(Color(0xFF8A6246), topLeft = Offset(win.left - fw * 2.5f, sillTop), size = Size(win.width + fw * 5f, sillH))
    drawRect(Color(0xFF5E4130), topLeft = Offset(win.left - fw * 2.5f, sillTop + sillH), size = Size(win.width + fw * 5f, sillH * 0.35f))

    // --- Pflanze links auf dem Brett ---
    val potW = win.width * 0.09f
    val potH = potW * 0.85f
    val potX = win.left + win.width * 0.06f
    val potTop = sillTop - potH
    val potPath = Path().apply {
        moveTo(potX, potTop)
        lineTo(potX + potW, potTop)
        lineTo(potX + potW * 0.85f, sillTop)
        lineTo(potX + potW * 0.15f, sillTop)
        close()
    }
    val stemBase = Offset(potX + potW / 2f, potTop)
    val sway = sin(t * 0.7f) * 3f
    for (i in 0 until 7) {
        val angle = -70f + i * 23f + sway
        val leafLen = potW * (0.9f + (i % 3) * 0.25f)
        rotate(angle, pivot = stemBase) {
            drawOval(
                lerp(Color(0xFF4F7A3E), Color(0xFF6E9A4F), (i % 3) / 2f).copy(alpha = 0.95f),
                topLeft = Offset(stemBase.x - leafLen * 0.18f, stemBase.y - leafLen),
                size = Size(leafLen * 0.36f, leafLen),
            )
        }
    }
    drawPath(potPath, Color(0xFFB0573C))
    drawRect(Color(0xFF8F432D), topLeft = Offset(potX - potW * 0.04f, potTop), size = Size(potW * 1.08f, potH * 0.18f))

    // --- Tasse rechts mit Dampf ---
    val mugW = win.width * 0.06f
    val mugH = mugW * 1.05f
    val mugX = win.right - win.width * 0.16f
    val mugTop = sillTop - mugH
    drawRoundRect(Color(0xFFE9E2D6), topLeft = Offset(mugX, mugTop), size = Size(mugW, mugH), cornerRadius = CornerRadius(mugW * 0.12f))
    drawArc(
        Color(0xFFE9E2D6), startAngle = -80f, sweepAngle = 160f, useCenter = false,
        topLeft = Offset(mugX + mugW * 0.75f, mugTop + mugH * 0.2f), size = Size(mugW * 0.5f, mugH * 0.5f),
        style = Stroke(width = mugW * 0.11f),
    )
    drawRect(Color(0xFFB9775A), topLeft = Offset(mugX, mugTop + mugH * 0.42f), size = Size(mugW, mugH * 0.12f))
    for (k in 0 until 3) {
        val phase = (t * 0.25f + k / 3f) % 1f
        val steam = Path()
        val sx = mugX + mugW * (0.3f + k * 0.2f)
        val baseY = mugTop - phase * mugH * 1.6f
        steam.moveTo(sx, baseY)
        for (s in 1..6) {
            val yy = baseY - s * mugH * 0.12f
            val xx = sx + sin(t * 1.5f + s * 0.9f + k) * mugW * 0.12f
            steam.lineTo(xx, yy)
        }
        drawPath(
            steam, Color.White.copy(alpha = (0.28f * (1f - phase)).coerceIn(0f, 1f)),
            style = Stroke(width = 2.2f * density, cap = StrokeCap.Round),
        )
    }

    // --- Warmes Lampenlicht von links oben (atmet leicht mit der Musik) ---
    val lampCenter = Offset(w * 0.06f, h * 0.12f)
    val lampAlpha = (0.16f + sky.night * 0.28f) * (0.9f + glow * 0.25f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFC27A).copy(alpha = lampAlpha.coerceIn(0f, 1f)), Color.Transparent),
            center = lampCenter, radius = maxOf(w, h) * 0.75f,
        ),
        radius = maxOf(w, h) * 0.75f, center = lampCenter,
    )

    // --- Vignette ---
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            center = Offset(w / 2f, h * 0.45f), radius = maxOf(w, h) * 0.75f,
        ),
    )
}

private fun DrawScope.drawCity(t: Float, city: City, win: Rect, sky: SkyPalette) {
    val litTick = (t / 9f).toInt() // alle paar Sekunden gehen Lichter an/aus
    for (house in city.houses) {
        // hinten blasser (Luftperspektive)
        val haze = (5 - house.row) * 0.07f + sky.night * 0.45f
        val hazeColor = lerp(sky.bottom, Color(0xFF151226), sky.night * 0.6f)
        val facade = lerp(house.facade, hazeColor, haze.coerceIn(0f, 0.85f))
        val roof = lerp(house.roof, hazeColor, (haze * 0.9f).coerceIn(0f, 0.85f))

        val left = win.left + house.x * win.width
        val right = left + house.w * win.width
        val bottom = win.top + (1f - (1f - house.base) * 0.55f) * win.height
        val top = bottom - house.h * win.height
        // Hausbody bis zum unteren Fensterrand verlaengern, damit keine Luecken entstehen
        drawRect(facade, topLeft = Offset(left, top), size = Size(right - left, win.bottom - top))

        // Dach
        val roofH = (right - left) * 0.28f
        when (house.roofKind) {
            0 -> drawPath(Path().apply {
                moveTo(left - 2f, top); lineTo(right + 2f, top)
                lineTo(right - (right - left) * 0.12f, top - roofH); lineTo(left + (right - left) * 0.12f, top - roofH); close()
            }, roof)
            1 -> drawPath(Path().apply {
                moveTo(left - 2f, top); lineTo(right + 2f, top); lineTo((left + right) / 2f, top - roofH * 1.2f); close()
            }, roof)
            else -> drawRect(roof, topLeft = Offset(left - 2f, top - roofH * 0.25f), size = Size(right - left + 4f, roofH * 0.25f))
        }

        // Fenster
        val bw = (right - left)
        val bh = bottom - top
        val ww = minOf(bw * 0.16f, win.width * 0.012f)
        val wh = ww * 1.5f
        for ((i, wnd) in house.windows.withIndex()) {
            val cx = left + wnd.cx * bw
            val cy = top + wnd.cy * bh
            val lit = hash01(wnd.litSeed, litTick + (i % 3)) < 0.42f
            val dark = lerp(Color(0xFF51606B), Color(0xFF151226), sky.night)
            val color = if (lit && sky.night > 0.25f) {
                lerp(dark, Color(0xFFFFCB73), ((sky.night - 0.25f) / 0.75f).coerceIn(0f, 1f))
            } else {
                dark
            }
            drawRect(color, topLeft = Offset(cx - ww / 2f, cy - wh / 2f), size = Size(ww, wh))
            if (wnd.shutters && sky.night < 0.7f) {
                val sh = lerp(Color(0xFF6E8C6A), Color(0xFF2E3A33), sky.night)
                drawRect(sh, topLeft = Offset(cx - ww * 1.05f, cy - wh / 2f), size = Size(ww * 0.45f, wh))
                drawRect(sh, topLeft = Offset(cx + ww * 0.6f, cy - wh / 2f), size = Size(ww * 0.45f, wh))
            }
        }
    }
}
