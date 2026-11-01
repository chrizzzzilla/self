@file:OptIn(ExperimentalTextApi::class)

package com.chrizzzzilla.self.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ==========================================================================================
// Farben + Schrift (Stil wie Pitch-Motion: fast schwarz, warmes Licht, Weiss/Gold/Arty-Blau)
// ==========================================================================================

object AmbientColors {
    val Ground = Color(0xFF0C0D0F)
    val Glow = Color(0xFF3C3832)
    val Light = Color(0xFFECE8E0)
    val Gold = Color(0xFFC8B48C)
    val Arty = Color(0xFF6888EC)
    val ArtyText = Color(0xFF9FB4F0)
    val Muted = Color(0xFF8C8A85)
}

val Manrope: FontFamily = FontFamily(
    Font(R.font.manrope, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val AmbientScheme = darkColorScheme(
    primary = AmbientColors.ArtyText,
    onPrimary = Color(0xFF0C1230),
    primaryContainer = Color(0xFF1E2A55),
    onPrimaryContainer = AmbientColors.Light,
    secondary = AmbientColors.Gold,
    background = AmbientColors.Ground,
    onBackground = AmbientColors.Light,
    surface = Color(0xFF15161A),
    onSurface = AmbientColors.Light,
    surfaceVariant = Color(0xFF22232A),
    onSurfaceVariant = AmbientColors.Muted,
    surfaceContainer = Color(0xFF15161A),
)

/** Dunkles, ruhiges Theme fuer die App-Oberflaeche (Bibliothek, Navigation). */
@Composable
fun AmbientTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val t = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Manrope),
        headlineMedium = base.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Light),
        titleLarge = base.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Normal),
        titleMedium = base.titleMedium.copy(fontFamily = Manrope),
        titleSmall = base.titleSmall.copy(fontFamily = Manrope),
        bodyLarge = base.bodyLarge.copy(fontFamily = Manrope),
        bodyMedium = base.bodyMedium.copy(fontFamily = Manrope),
        bodySmall = base.bodySmall.copy(fontFamily = Manrope),
        labelLarge = base.labelLarge.copy(fontFamily = Manrope),
        labelMedium = base.labelMedium.copy(fontFamily = Manrope),
        labelSmall = base.labelSmall.copy(fontFamily = Manrope),
    )
    MaterialTheme(colorScheme = AmbientScheme, typography = t, content = content)
}

// ==========================================================================================
// Leuchtpunkte (vorgerenderte Sprites in vielen Groessen → weiche, ruckelfreie Bewegung)
// ==========================================================================================

private class SpriteSet(val sizes: FloatArray, val images: Array<ImageBitmap>) {
    fun index(diameter: Float): Int {
        var i = 0
        while (i < sizes.size - 1 && sizes[i] < diameter) i++
        return i
    }
}

private fun buildSprites(color: Color, density: Float): SpriteSet {
    val sizes = FloatArray(28) { (3f * 1.17f.pow(it)) } // 3 px … ~210 px
    val images = Array(sizes.size) { i ->
        val s = sizes[i].toInt().coerceAtLeast(2)
        val img = ImageBitmap(s, s)
        CanvasDrawScope().draw(
            androidx.compose.ui.unit.Density(density), LayoutDirection.Ltr, GCanvas(img), Size(s.toFloat(), s.toFloat()),
        ) {
            val c = Offset(s / 2f, s / 2f)
            drawCircle(
                Brush.radialGradient(
                    0f to color,
                    0.18f to color.copy(alpha = 0.55f),
                    0.45f to color.copy(alpha = 0.12f),
                    1f to color.copy(alpha = 0f),
                    center = c,
                    radius = s / 2f,
                ),
                radius = s / 2f,
                center = c,
            )
        }
        img
    }
    return SpriteSet(sizes, images)
}

@Composable
private fun rememberSprites(color: Color): SpriteSet {
    val d = LocalDensity.current.density
    return remember(color, d) { buildSprites(color, d) }
}

/** Zeichnet einen Leuchtpunkt mit Mittelpunkt [x],[y] und Radius [r] (Schein reicht bis 2r). */
private fun DrawScope.glow(sprites: SpriteSet, x: Float, y: Float, r: Float, alpha: Float, blend: BlendMode = BlendMode.Plus) {
    if (alpha < 0.012f) return
    val dia = r * 4f
    val img = sprites.images[sprites.index(dia)]
    val scale = dia / img.width
    if (abs(scale - 1f) < 0.12f) {
        drawImage(img, topLeft = Offset(x - img.width / 2f, y - img.height / 2f), alpha = alpha.coerceAtMost(1f), blendMode = blend)
    } else {
        // selten: zwischen zwei Groessen → leicht skaliert zeichnen
        val w = img.width * scale
        drawImage(
            img,
            dstOffset = androidx.compose.ui.unit.IntOffset((x - w / 2f).toInt(), (y - w / 2f).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), w.toInt().coerceAtLeast(1)),
            alpha = alpha.coerceAtMost(1f),
            blendMode = blend,
        )
    }
}

private fun ioCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** Gemeinsame Zeit fuer eine Animation (Sekunden seit Start). */
@Composable
private fun rememberClock(): MutableFloatState {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> t.floatValue = (now - start) / 1e9f }
    }
    return t
}

// ==========================================================================================
// Hintergrund: Staub mit Tiefe, schwingende Lichtfaeden, warmes Licht in der Mitte
// ==========================================================================================

private class Dust(val x: FloatArray, val y: FloatArray, val z: FloatArray, val g: FloatArray, val ph: FloatArray)

@Composable
fun AmbientBackground(modifier: Modifier = Modifier, threads: Boolean = true) {
    val time = rememberClock()
    val white = rememberSprites(AmbientColors.Light)
    val dust = remember {
        val r = Random(5)
        val n = 420
        Dust(
            FloatArray(n) { r.nextFloat() * 8400f - 4200f },
            FloatArray(n) { r.nextFloat() * 5800f - 2600f },
            FloatArray(n) { r.nextFloat() * 8400f - 4200f },
            FloatArray(n) { 0.4f + r.nextFloat() * 0.9f },
            FloatArray(n) { r.nextFloat() * 6.28f },
        )
    }
    Canvas(modifier) {
        val s = time.floatValue
        val w = size.width
        val h = size.height
        val sc = min(w, h) / 900f
        drawRect(AmbientColors.Ground)
        drawRect(
            Brush.radialGradient(
                listOf(AmbientColors.Glow.copy(alpha = 0.35f), AmbientColors.Ground.copy(alpha = 0f)),
                center = Offset(w / 2f, h / 2f),
                radius = max(w, h) * 0.6f,
            ),
        )
        val cam = Cam(yaw = s * 0.045f, pitch = 0.3f, w = w, h = h, sc = sc)
        val out = FloatArray(4)
        // Staub
        for (i in dust.x.indices) {
            if (!cam.project(dust.x[i], dust.y[i] + sin(s * 0.1f + dust.ph[i]) * 30f, dust.z[i], out)) continue
            val px = out[0]; val py = out[1]
            if (px < -20 || px > w + 20 || py < -20 || py > h + 20) continue
            val a = 0.16f + 0.12f * sin(s * 0.9f + dust.ph[i])
            val r = max(0.5f * density, dust.g[i] * out[2] * 2.2f)
            glow(white, px, py, r, a)
        }
        // Schwingende Lichtfaeden um die Mitte
        if (threads) {
            val path = Path()
            val specs = arrayOf(
                floatArrayOf(3f, 0.35f, 0f, 22f, 0.30f, 0f),
                floatArrayOf(4f, 0.28f, 2.1f, 18f, 0.26f, 1f),
                floatArrayOf(5f, 0.42f, 4.2f, 14f, 0.18f, 0f),
            )
            for ((k, f) in specs.withIndex()) {
                path.reset()
                val n = 180
                var first = true
                for (i in 0..n) {
                    val th = -PI.toFloat() / 2f + i / n.toFloat() * 2f * PI.toFloat() + s * 0.02f
                    val rr = 1000f + sin(th * f[0] + s * f[1] + f[2]) * f[3] + sin(th * (f[0] + 3) - s * f[1] * 0.7f) * f[3] * 0.4f
                    if (!cam.project(cos(th) * rr, sin(th * 2f + s * 0.3f + k) * 10f, sin(th) * rr, out)) { first = true; continue }
                    if (first) { path.moveTo(out[0], out[1]); first = false } else path.lineTo(out[0], out[1])
                }
                drawPath(
                    path,
                    (if (f[5] > 0f) AmbientColors.Gold else AmbientColors.Light).copy(alpha = f[4]),
                    style = Stroke(width = 1f * density),
                )
            }
        }
    }
}

/** Einfache Perspektiv-Kamera wie in Pitch-Motion. */
private class Cam(yaw: Float, pitch: Float, private val w: Float, private val h: Float, private val sc: Float) {
    private val cy = cos(yaw); private val sy = sin(yaw)
    private val cp = cos(pitch); private val sp = sin(pitch)
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val x1 = x * cy - z * sy
        val z1 = x * sy + z * cy
        val y1 = y * cp + z1 * sp
        val z2 = -y * sp + z1 * cp
        val d = 1100f + z2
        if (d < 60f) return false
        val s = 1000f / d * sc
        out[0] = w / 2f + x1 * s
        out[1] = h / 2f - y1 * s
        out[2] = s
        out[3] = d
        return true
    }
}

// ==========================================================================================
// Arty: blauer Partikelschwarm, schwebt wie eine Wolke, sammelt sich beim Erscheinen
// ==========================================================================================

/** Merkt sich (fuer diesen App-Start), ob Arty seinen Auftritt schon hatte. */
private object ArtyIntro {
    @Volatile var played = false
}

private class Swarm(
    val lx: FloatArray, val ly: FloatArray, val lz: FloatArray,
    val bx: FloatArray, val by: FloatArray, val bz: FloatArray,
    val verz: FloatArray, val bogen: FloatArray, val hell: FloatArray, val ph: FloatArray,
    val vx: FloatArray, val vy: FloatArray,
)

private fun makeSwarm(n: Int, radius: Float, seed: Int): Swarm {
    val r = Random(seed)
    val lx = FloatArray(n); val ly = FloatArray(n); val lz = FloatArray(n)
    for (i in 0 until n) {
        val a = r.nextFloat() * 6.28f
        val b = acos(r.nextFloat() * 2f - 1f)
        val rr = r.nextFloat().pow(0.55f) * radius
        lx[i] = sin(b) * cos(a) * rr; ly[i] = cos(b) * rr; lz[i] = sin(b) * sin(a) * rr
    }
    return Swarm(
        lx, ly, lz,
        FloatArray(n) { r.nextFloat() * 2f - 1f }, FloatArray(n) { r.nextFloat() * 2f - 1f }, FloatArray(n) { r.nextFloat() * 2f - 1f },
        FloatArray(n) { r.nextFloat() * 0.45f }, FloatArray(n) { (r.nextFloat() * 2f - 1f) * 260f },
        FloatArray(n) { 0.45f + r.nextFloat() * 0.55f }, FloatArray(n) { r.nextFloat() * 6.28f },
        FloatArray(n) { Float.NaN }, FloatArray(n) { Float.NaN },
    )
}

/**
 * Arty als schwebender Partikelschwarm.
 * @param pulse 0..1 – kurzes Aufleuchten/Ausdehnen (z. B. beim Antippen).
 */
@Composable
fun ArtySwarm(
    modifier: Modifier = Modifier,
    particles: Int = 900,
    pulse: () -> Float = { 0f },
    /** true auf hellem Grund (Hell/Sepia): kraeftiges Blau, normal gemischt statt additiv. */
    onLight: Boolean = false,
) {
    val time = rememberClock()
    val blue = rememberSprites(if (onLight) Color(0xFF2F55C8) else AmbientColors.Arty)
    val white = rememberSprites(if (onLight) Color(0xFF1E3A9A) else AmbientColors.Light)
    val blend = if (onLight) BlendMode.SrcOver else BlendMode.Plus
    val swarm = remember(particles) { makeSwarm(particles, 150f, 11) }
    // Der Auftritt (Partikel sammeln sich) nur einmal pro App-Start, danach steht Arty sofort da.
    val appear = remember { Animatable(if (ArtyIntro.played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!ArtyIntro.played) {
            ArtyIntro.played = true
            appear.animateTo(1f, tween(3600))
        }
    }

    Canvas(modifier) {
        val s = time.floatValue
        val w = size.width
        val h = size.height
        val sc = min(w, h) / 420f
        val p = appear.value
        val pl = pulse()
        val cam = Cam(yaw = 0f, pitch = 0f, w = w, h = h, sc = sc)
        val out = FloatArray(4)
        // langsames Schweben wie eine Wolke im Wind
        val driftX = sin(s * 0.13f) * 18f + sin(s * 0.051f) * 10f
        val driftY = sin(s * 0.17f + 1f) * 12f
        val yaw = s * 0.5f
        val tilt = 0.2f
        val ca = cos(yaw); val sa = sin(yaw); val ck = cos(tilt); val sk = sin(tilt)
        for (i in swarm.lx.indices) {
            val q = clamp01((p - swarm.verz[i]) / 0.55f)
            if (q <= 0f) { swarm.vx[i] = Float.NaN; continue }
            val e = ioCubic(q)
            val x0 = swarm.lx[i] * ca + swarm.lz[i] * sa
            val z0 = -swarm.lx[i] * sa + swarm.lz[i] * ca
            val y0 = swarm.ly[i]
            val ly = y0 * ck - z0 * sk
            val lz = y0 * sk + z0 * ck
            val n = (1f + 0.12f * sin(s * 1.3f + swarm.ph[i] * 3f)) * (1f + pl * 0.18f)
            val wx = x0 * n + sin(s * 0.9f + swarm.ph[i]) * 10f + driftX
            val wy = ly * n + cos(s * 0.7f + swarm.ph[i] * 2f) * 10f + driftY
            val wz = lz * n
            val bog = sin(PI.toFloat() * e) * swarm.bogen[i]
            if (!cam.project(wx * e + swarm.bx[i] * bog, wy * e, wz * e + swarm.bz[i] * bog, out)) continue
            val x = out[0]; val y = out[1]
            val front = 0.5f + 0.5f * clamp01(0.5f - lz / 300f)
            val a = 0.62f * swarm.hell[i] * min(1f, q * 2f) * front * (1f + pl * 0.6f)
            val r = max(1.1f, 2.3f * out[2])
            val sprites = if (swarm.hell[i] > 0.9f) white else blue
            glow(sprites, x, y, r, a, blend)
            // Leuchtspur bei schneller Bewegung (beim Erscheinen)
            val px = swarm.vx[i]
            if (!px.isNaN()) {
                val dx = x - px; val dy = y - swarm.vy[i]
                val l2 = dx * dx + dy * dy
                if (l2 > 9f && l2 < 40000f) {
                    drawLine(
                        AmbientColors.Arty.copy(alpha = (a * 0.55f).coerceAtMost(1f)),
                        Offset(px, swarm.vy[i]), Offset(x, y),
                        strokeWidth = max(0.6f, r * 0.45f), cap = StrokeCap.Round, blendMode = BlendMode.Plus,
                    )
                }
            }
            swarm.vx[i] = x; swarm.vy[i] = y
        }
    }
}

// ==========================================================================================
// Schreibmaschinen-Schrift mit Bounce; die Buchstaben schweben danach leicht
// ==========================================================================================

@Composable
fun TypewriterLabel(
    text: String,
    modifier: Modifier = Modifier,
    startDelayMs: Long = 1800,
    color: Color = AmbientColors.ArtyText,
    fontSize: Float = 15f,
    letterSpacing: Float = 6f,
) {
    val time = rememberClock()
    var shown by remember(text) { mutableIntStateOf(0) }
    val scales = remember(text) { text.map { Animatable(0f) } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(text) {
        delay(startDelayMs)
        for (i in text.indices) {
            shown = i + 1
            scope.launch {
                scales[i].animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessLow))
            }
            delay(if (text[i] == ' ') 140 else 85)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            val sc = scales[i].value
            val t = time.floatValue
            Text(
                text = ch.toString(),
                style = TextStyle(
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize.sp,
                    letterSpacing = letterSpacing.sp,
                    color = color,
                ),
                modifier = Modifier
                    .alpha(if (i < shown) (0.75f + 0.25f * sin(t * 0.8f + i * 0.6f)) * clamp01(sc * 1.5f) else 0f)
                    .graphicsLayer {
                        scaleX = sc
                        scaleY = sc
                        translationY = (1f - sc) * 14f * density + sin(t * 0.9f + i * 0.55f) * 2.2f * density
                        rotationZ = sin(t * 0.6f + i * 0.9f) * 2f
                    },
            )
        }
    }
}

/**
 * Grosser, ruhiger "Buch hinzufuegen"-Knopf: Arty schwebt, darunter tippt sich der Text.
 * Antippen laesst Arty kurz aufleuchten und ruft [onClick] auf.
 */
@Composable
fun ArtyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 300.dp,
) {
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            scope.launch {
                pulse.snapTo(1f)
                pulse.animateTo(0f, tween(900))
            }
            onClick()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtySwarm(Modifier.size(orbSize), pulse = { pulse.value })
        Spacer(Modifier.height(8.dp))
        TypewriterLabel(label.uppercase())
    }
}

/** Kleiner Arty fuer Ecken (z. B. "Buch hinzufuegen", wenn schon Buecher da sind). */
@Composable
fun ArtyMini(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 84.dp) {
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { pulse.snapTo(1f); pulse.animateTo(0f, tween(900)) }
                onClick()
            },
    ) {
        ArtySwarm(Modifier.fillMaxSize(), particles = 260, pulse = { pulse.value })
    }
}


/**
 * Arty als aufgehende Sonne: grosser Schwarm, dessen Mitte genau auf dem unteren Rand liegt –
 * nur die obere Haelfte ist zu sehen. Darueber optional eine Schreibmaschinen-Beschriftung.
 * Antippen laesst ihn aufleuchten und ruft [onClick] auf.
 */
@Composable
fun ArtySun(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 420.dp,
    label: String? = null,
) {
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label != null) {
            TypewriterLabel(label.uppercase(), startDelayMs = 2200)
            Spacer(Modifier.height(4.dp))
        }
        // sichtbar ist nur die obere Haelfte; der Rest liegt unter dem Bildschirmrand
        Box(
            Modifier
                .size(width = diameter, height = diameter / 2)
                .clipToBounds()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    scope.launch { pulse.snapTo(1f); pulse.animateTo(0f, tween(900)) }
                    onClick()
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            ArtySwarm(
                Modifier
                    .requiredSize(diameter)
                    .graphicsLayer { translationY = diameter.toPx() / 4f },
                particles = 1100,
                pulse = { pulse.value },
            )
        }
    }
}

