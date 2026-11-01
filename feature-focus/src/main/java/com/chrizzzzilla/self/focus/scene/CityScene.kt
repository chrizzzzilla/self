package com.chrizzzzilla.self.focus.scene

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Nachtstadt aus der Vogelperspektive (Kamera schraeg von rechts oben), komplett gerendert:
 * echte Perspektive, ~2000 Gebaeude mit Fenstern, Strassen mit Natriumlicht, Neon,
 * fahrende Autos (Scheinwerfer/Ruecklichter mit Spiegelung auf nasser Strasse),
 * blinkende Flugwarnlichter, Fenster die an- und ausgehen, flackernde Fernseher,
 * Sternenhimmel mit Funkeln, Milchstrasse und Sternschnuppen, Wolken, Dunst, Regen
 * und eine langsame Kamerafahrt mit Parallaxe (Himmel bewegt sich weniger als die Stadt).
 *
 * Statisches (Stadt, Himmel) wird einmal pro Bildschirmgroesse in Bitmaps gerendert,
 * pro Frame kommen nur die bewegten Teile dazu.
 */
@Composable
fun CityScene(
    rain: Boolean,
    pulse: () -> Float,
    level: () -> Float,
    modifier: Modifier = Modifier,
    hour: () -> Float = { 22f },
    season: Season = Season.SUMMER,
) {
    val time = remember { mutableFloatStateOf(0f) }
    val glow = remember { mutableFloatStateOf(0f) }
    val sparkle = remember { mutableFloatStateOf(0f) }
    val rainAmount = remember { mutableFloatStateOf(if (rain) 1f else 0f) }
    val model = remember { CityModel.generate(seed = 2026) }

    LaunchedEffect(rain) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                time.floatValue += dt
                glow.floatValue += (pulse() - glow.floatValue) * (dt * 7f).coerceAtMost(1f)
                sparkle.floatValue += (level() - sparkle.floatValue) * (dt * 3f).coerceAtMost(1f)
                val target = if (rain) 1f else 0f
                rainAmount.floatValue += (target - rainAmount.floatValue) * (dt * 0.7f).coerceAtMost(1f)
            }
        }
    }

    Spacer(
        modifier.drawWithCache {
            val scene = SceneCache.build(this, model)
            onDrawBehind {
                drawFrame(scene, time.floatValue, glow.floatValue, sparkle.floatValue, rainAmount.floatValue, Daylight(hour(), season))
            }
        },
    )
}

// ==========================================================================================
// Welt
// ==========================================================================================

private const val CAM_X = 40f
private const val CAM_Y = 40f
private const val CAM_Z = -26f
private const val TARGET_X = -24f
private const val TARGET_Z = 76f
private const val MAX_DIST = 360f

private class Building(
    val x0: Float, val z0: Float, val x1: Float, val z1: Float, val h: Float,
    val tone: Float, val litRate: Float, val seed: Int, val neon: Int, val beacon: Boolean,
) {
    val dist: Float = sqrt(((x0 + x1) / 2f - CAM_X).pow(2) + ((z0 + z1) / 2f - CAM_Z).pow(2))
}

private class Street(
    val alongX: Boolean, val fixed: Float, val from: Float, val to: Float, val main: Boolean,
) {
    val length = to - from
    fun point(s: Float, lane: Float, out: FloatArray) {
        if (alongX) { out[0] = from + s; out[1] = fixed + lane } else { out[0] = fixed + lane; out[1] = from + s }
    }
}

private class CityModel(val buildings: List<Building>, val streets: List<Street>) {
    companion object {
        private const val BLOCK = 7f
        private const val ROAD = 1.7f
        private const val MIN_X = -280f
        private const val MAX_X = 150f
        private const val MIN_Z = -30f
        private const val MAX_Z = 340f

        private fun inView(x: Float, z: Float, margin: Float): Boolean {
            val dx = x - CAM_X
            val dz = z - CAM_Z
            val d = sqrt(dx * dx + dz * dz)
            if (d > MAX_DIST || d < 6f) return false
            val dirA = atan2(TARGET_X - CAM_X, TARGET_Z - CAM_Z)
            var diff = atan2(dx, dz) - dirA
            while (diff > PI) diff -= (2 * PI).toFloat()
            while (diff < -PI) diff += (2 * PI).toFloat()
            return abs(diff) < 1.05f + margin / d
        }

        fun generate(seed: Int): CityModel {
            val rnd = Random(seed)
            val buildings = ArrayList<Building>()
            val downtownX = -30f
            val downtownZ = 120f
            var bx = MIN_X
            while (bx < MAX_X) {
                var bz = MIN_Z
                while (bz < MAX_Z) {
                    val cx = bx + BLOCK / 2f
                    val cz = bz + BLOCK / 2f
                    if (inView(cx, cz, 12f)) {
                        val d = sqrt((cx - downtownX).pow(2) + (cz - downtownZ).pow(2))
                        val core = exp(-d / 55f)
                        // Parks: ab und zu ein leerer Block
                        if (rnd.nextFloat() > 0.06f) {
                            val ax0 = bx + ROAD / 2f
                            val az0 = bz + ROAD / 2f
                            val size = BLOCK - ROAD
                            val splitX = 1 + rnd.nextInt(if (core > 0.4f) 2 else 3)
                            val splitZ = 1 + rnd.nextInt(if (core > 0.4f) 2 else 3)
                            for (i in 0 until splitX) for (k in 0 until splitZ) {
                                if (rnd.nextFloat() < 0.08f) continue
                                val lw = size / splitX
                                val ld = size / splitZ
                                val m = 0.18f + rnd.nextFloat() * 0.25f
                                val x0 = ax0 + i * lw + m
                                val z0 = az0 + k * ld + m
                                val x1 = ax0 + (i + 1) * lw - m
                                val z1 = az0 + (k + 1) * ld - m
                                var h = (2.2f + 9f * core) * (0.45f + rnd.nextFloat() * 1.2f)
                                if (rnd.nextFloat() < 0.05f + 0.25f * core) h = 14f + rnd.nextFloat() * (18f + 34f * core)
                                val neon = if (h in 4f..22f && rnd.nextFloat() < 0.05f) 1 + rnd.nextInt(3) else 0
                                buildings += Building(
                                    x0, z0, x1, z1, h,
                                    tone = rnd.nextFloat(),
                                    litRate = 0.12f + rnd.nextFloat() * 0.3f,
                                    seed = rnd.nextInt(),
                                    neon = neon,
                                    beacon = h > 26f,
                                )
                            }
                        }
                    }
                    bz += BLOCK
                }
                bx += BLOCK
            }
            buildings.sortByDescending { it.dist }

            val streets = ArrayList<Street>()
            var i = 0
            var x = MIN_X
            while (x <= MAX_X) {
                streets += Street(alongX = false, fixed = x, from = MIN_Z, to = MAX_Z, main = i % 4 == 0)
                x += BLOCK; i++
            }
            i = 0
            var z = MIN_Z
            while (z <= MAX_Z) {
                streets += Street(alongX = true, fixed = z, from = MIN_X, to = MAX_X, main = i % 4 == 2)
                z += BLOCK; i++
            }
            return CityModel(buildings, streets)
        }
    }
}

// ==========================================================================================
// Kamera (Lochkamera mit Perspektive)
// ==========================================================================================

private class Camera(val w: Float, val h: Float) {
    val f = 0.62f * min(w, h * 1.45f)
    val cx = w * 0.5f
    val cy = h * 0.63f
    private val fx: Float
    private val fy: Float
    private val fz: Float
    private val rx: Float
    private val rz: Float
    private val ux: Float
    private val uy: Float
    private val uz: Float

    init {
        var dx = TARGET_X - CAM_X
        var dy = -CAM_Y
        var dz = TARGET_Z - CAM_Z
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        dx /= len; dy /= len; dz /= len
        fx = dx; fy = dy; fz = dz
        // rechts = up × forward
        val rl = sqrt(dz * dz + dx * dx)
        rx = dz / rl; rz = -dx / rl
        // oben = forward × rechts
        ux = fy * rz
        uy = fz * rx - fx * rz
        uz = -fy * rx
    }

    /** Schreibt Bildschirm-x, -y und Tiefe nach [out] ab [o]. false = hinter der Kamera. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray, o: Int = 0): Boolean {
        val dx = x - CAM_X
        val dy = y - CAM_Y
        val dz = z - CAM_Z
        val zc = dx * fx + dy * fy + dz * fz
        if (zc < 0.8f) return false
        val xc = dx * rx + dz * rz
        val yc = dx * ux + dy * uy + dz * uz
        out[o] = cx + f * xc / zc
        out[o + 1] = cy - f * yc / zc
        out[o + 2] = zc
        return true
    }

    /** y-Position des Horizonts. */
    val horizonY: Float
        get() {
            val tmp = FloatArray(3)
            val dx = TARGET_X - CAM_X
            val dz = TARGET_Z - CAM_Z
            project(CAM_X + dx * 400f, 0f, CAM_Z + dz * 400f, tmp)
            return tmp[1]
        }
}

// ==========================================================================================
// Vorberechnete Szene (pro Bildschirmgroesse)
// ==========================================================================================

private class Car(
    val street: Int, val lane: Float, val speed: Float, val offset: Float, val head: Boolean,
)

private class SceneCache(
    val w: Float,
    val h: Float,
    val camera: Camera,
    val model: CityModel,
    val sky: ImageBitmap,
    val city: ImageBitmap,
    val horizonY: Float,
    // Sterne (dynamisch)
    val starX: FloatArray, val starY: FloatArray, val starR: FloatArray, val starPhase: FloatArray,
    val starSpeed: FloatArray, val starTint: IntArray,
    // Autos + Sichtbarkeit der Strassen
    val cars: List<Car>,
    val streetVis: Array<BooleanArray>,
    val visStep: Float,
    // Fenster, die an- und ausgehen
    val flickX: FloatArray, val flickY: FloatArray, val flickW: FloatArray, val flickH: FloatArray,
    val flickColor: IntArray, val flickSeed: IntArray, val flickTv: BooleanArray,
    // Flugwarnlichter
    val beaconX: FloatArray, val beaconY: FloatArray, val beaconR: FloatArray, val beaconPhase: FloatArray,
) {
    companion object {
        fun build(scope: CacheDrawScope, model: CityModel): SceneCache {
            val w = scope.size.width
            val h = scope.size.height
            val cam = Camera(w, h)
            val horizon = cam.horizonY
            val rnd = Random(77)

            // ---------- Projektion aller Gebaeude ----------
            val n = model.buildings.size
            val corners = Array(n) { FloatArray(8 * 3) }
            val ok = BooleanArray(n)
            val minX = FloatArray(n); val maxX = FloatArray(n)
            val minY = FloatArray(n); val maxY = FloatArray(n)
            val minD = FloatArray(n)
            for ((bi, b) in model.buildings.withIndex()) {
                val c = corners[bi]
                val pts = arrayOf(
                    floatArrayOf(b.x0, 0f, b.z0), floatArrayOf(b.x1, 0f, b.z0),
                    floatArrayOf(b.x1, 0f, b.z1), floatArrayOf(b.x0, 0f, b.z1),
                    floatArrayOf(b.x0, b.h, b.z0), floatArrayOf(b.x1, b.h, b.z0),
                    floatArrayOf(b.x1, b.h, b.z1), floatArrayOf(b.x0, b.h, b.z1),
                )
                var all = true
                for (k in 0 until 8) {
                    if (!cam.project(pts[k][0], pts[k][1], pts[k][2], c, k * 3)) { all = false; break }
                }
                ok[bi] = all
                if (!all) continue
                var ax = Float.MAX_VALUE; var bxm = -Float.MAX_VALUE
                var ay = Float.MAX_VALUE; var bym = -Float.MAX_VALUE
                var md = Float.MAX_VALUE
                for (k in 0 until 8) {
                    ax = min(ax, c[k * 3]); bxm = max(bxm, c[k * 3])
                    ay = min(ay, c[k * 3 + 1]); bym = max(bym, c[k * 3 + 1])
                    md = min(md, c[k * 3 + 2])
                }
                minX[bi] = ax; maxX[bi] = bxm; minY[bi] = ay; maxY[bi] = bym; minD[bi] = md
                if (bxm < -50f || ax > w + 50f || bym < -50f || ay > h + 50f) ok[bi] = false
            }

            // ---------- Raster fuer Verdeckungs-Tests ----------
            val gx = 48
            val gy = 32
            val cells = Array(gx * gy) { ArrayList<Int>(8) }
            for (bi in 0 until n) {
                if (!ok[bi]) continue
                val c0 = ((minX[bi] / w) * gx).toInt().coerceIn(0, gx - 1)
                val c1 = ((maxX[bi] / w) * gx).toInt().coerceIn(0, gx - 1)
                val r0 = ((minY[bi] / h) * gy).toInt().coerceIn(0, gy - 1)
                val r1 = ((maxY[bi] / h) * gy).toInt().coerceIn(0, gy - 1)
                for (r in r0..r1) for (cc in c0..c1) cells[r * gx + cc].add(bi)
            }
            fun occluded(sx: Float, sy: Float, depth: Float, self: Int): Boolean {
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) return true
                val cell = cells[((sy / h) * gy).toInt().coerceIn(0, gy - 1) * gx + ((sx / w) * gx).toInt().coerceIn(0, gx - 1)]
                for (bi in cell) {
                    if (bi == self) continue
                    if (minD[bi] < depth - 0.6f && sx >= minX[bi] && sx <= maxX[bi] && sy >= minY[bi] && sy <= maxY[bi]) {
                        // genauere Pruefung: liegt der Punkt unterhalb der Dachkante?
                        return true
                    }
                }
                return false
            }

            // ---------- Himmel (halbe Aufloesung, ist weich) ----------
            val skyBmp = ImageBitmap((w / 2).toInt().coerceAtLeast(1), (h / 2).toInt().coerceAtLeast(1))
            CanvasDrawScope().draw(scope, scope.layoutDirection, Canvas(skyBmp), Size(w / 2, h / 2)) {
                drawSky(this, horizon / 2f, rnd)
            }

            // ---------- Stadt (volle Aufloesung) ----------
            val flick = FlickBuilder()
            val beacons = ArrayList<FloatArray>()
            val cityBmp = ImageBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
            CanvasDrawScope().draw(scope, scope.layoutDirection, Canvas(cityBmp), Size(w, h)) {
                drawGround(this, horizon)
                drawHorizon(this, cam, horizon, rnd)
                drawStreets(this, cam, model)
                for ((bi, b) in model.buildings.withIndex()) {
                    if (!ok[bi]) continue
                    drawBuilding(this, cam, b, corners[bi], flick, beacons, bi, ::occluded)
                }
                // leichter Dunst ueber der Ferne
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0x00000000),
                        0.5f to Color(0x332A1F45),
                        1f to Color(0x00000000),
                        startY = horizon - h * 0.05f,
                        endY = horizon + h * 0.22f,
                    ),
                )
            }

            // ---------- Strassen: wo sind Autos sichtbar? ----------
            val visStep = 1.5f
            val tmp = FloatArray(3)
            val p2 = FloatArray(2)
            val streetVis = Array(model.streets.size) { si ->
                val st = model.streets[si]
                val count = (st.length / visStep).toInt() + 1
                BooleanArray(count) { k ->
                    st.point(k * visStep, 0f, p2)
                    val dx = p2[0] - CAM_X
                    val dz = p2[1] - CAM_Z
                    if (dx * dx + dz * dz > 220f * 220f) false
                    else if (!cam.project(p2[0], 0.15f, p2[1], tmp)) false
                    else !occluded(tmp[0], tmp[1], tmp[2], -1)
                }
            }
            val usable = model.streets.indices.filter { si -> streetVis[si].count { it } > 6 }
            val cars = ArrayList<Car>()
            if (usable.isNotEmpty()) {
                repeat(260) {
                    val si = usable[rnd.nextInt(usable.size)]
                    val st = model.streets[si]
                    val weight = if (st.main) 1f else 0.45f
                    if (rnd.nextFloat() > weight) return@repeat
                    val forward = rnd.nextBoolean()
                    // Faehrt das Auto auf die Kamera zu? Dann sieht man Scheinwerfer.
                    st.point(0f, 0f, p2)
                    val dStart = (p2[0] - CAM_X).pow(2) + (p2[1] - CAM_Z).pow(2)
                    st.point(st.length, 0f, p2)
                    val dEnd = (p2[0] - CAM_X).pow(2) + (p2[1] - CAM_Z).pow(2)
                    val towardCam = if (forward) dEnd < dStart else dStart < dEnd
                    cars += Car(
                        street = si,
                        lane = if (forward) 0.38f else -0.38f,
                        speed = (if (forward) 1f else -1f) * (3.5f + rnd.nextFloat() * (if (st.main) 6f else 3f)),
                        offset = rnd.nextFloat() * st.length,
                        head = towardCam,
                    )
                }
            }

            // ---------- Sterne ----------
            val starCount = 230
            val sx = FloatArray(starCount); val sy = FloatArray(starCount); val sr = FloatArray(starCount)
            val sp = FloatArray(starCount); val ss = FloatArray(starCount); val st = IntArray(starCount)
            for (i in 0 until starCount) {
                sx[i] = rnd.nextFloat() * w
                val v = rnd.nextFloat()
                sy[i] = v * v * (horizon * 0.95f)
                sr[i] = (0.45f + rnd.nextFloat().pow(3) * 1.1f) * scope.density
                sp[i] = rnd.nextFloat() * 6.28f
                ss[i] = 0.6f + rnd.nextFloat() * 2.4f
                st[i] = when {
                    rnd.nextFloat() < 0.12f -> 0xFFFFD9B0.toInt()
                    rnd.nextFloat() < 0.18f -> 0xFFB9D4FF.toInt()
                    else -> 0xFFFFFFFF.toInt()
                }
            }

            return SceneCache(
                w, h, cam, model, skyBmp, cityBmp, horizon,
                sx, sy, sr, sp, ss, st,
                cars, streetVis, visStep,
                flick.x.toFloatArray(), flick.y.toFloatArray(), flick.w.toFloatArray(), flick.h.toFloatArray(),
                flick.color.toIntArray(), flick.seed.toIntArray(), flick.tv.toBooleanArray(),
                FloatArray(beacons.size) { beacons[it][0] }, FloatArray(beacons.size) { beacons[it][1] },
                FloatArray(beacons.size) { beacons[it][2] }, FloatArray(beacons.size) { beacons[it][3] },
            )
        }
    }
}

private class FlickBuilder {
    val x = ArrayList<Float>(); val y = ArrayList<Float>(); val w = ArrayList<Float>(); val h = ArrayList<Float>()
    val color = ArrayList<Int>(); val seed = ArrayList<Int>(); val tv = ArrayList<Boolean>()
}

private fun hash01(a: Int, b: Int): Float {
    var x = a * 374761393 + b * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

// ==========================================================================================
// Statische Ebenen
// ==========================================================================================

private val WARM = Color(0xFFFFC978)
private val WARM2 = Color(0xFFFFB25C)
private val COOL = Color(0xFFDDE9FF)
private val TEAL = Color(0xFF7FF0FF)
private val SODIUM = Color(0xFFFF9E45)
private val NEONS = listOf(Color(0xFFFF3FD0), Color(0xFF34E9FF), Color(0xFFB36BFF))

private fun drawSky(d: DrawScope, horizon: Float, rnd: Random) = with(d) {
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF03050F),
            0.45f to Color(0xFF0A0F2C),
            0.8f to Color(0xFF1E1840),
            1f to Color(0xFF3A2048),
            startY = 0f,
            endY = horizon * 1.05f,
        ),
    )
    // Milchstrasse: diagonales, weiches Band aus vielen feinen Punkten
    for (i in 0 until 9) {
        val t = i / 8f
        val c = Offset(size.width * (0.05f + t * 0.95f), horizon * (0.15f + t * 0.55f) + sin(t * 5f) * 12f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0x1A9C8CFF), Color.Transparent), center = c, radius = size.width * 0.12f),
            radius = size.width * 0.12f, center = c,
        )
    }
    repeat(1400) {
        val t = rnd.nextFloat()
        val spread = (rnd.nextFloat() - 0.5f) * (rnd.nextFloat()) * horizon * 0.35f
        val px = size.width * t
        val py = horizon * (0.15f + t * 0.55f) + spread
        if (py < horizon) {
            val a = 0.15f + rnd.nextFloat() * 0.45f
            drawCircle(Color.White.copy(alpha = a * (1f - abs(spread) / (horizon * 0.2f)).coerceIn(0f, 1f)), radius = 0.35f + rnd.nextFloat() * 0.5f, center = Offset(px, py))
        }
    }
    // feine Hintergrundsterne
    repeat(700) {
        val v = rnd.nextFloat()
        drawCircle(
            Color.White.copy(alpha = 0.12f + rnd.nextFloat() * 0.35f),
            radius = 0.3f + rnd.nextFloat() * 0.4f,
            center = Offset(rnd.nextFloat() * size.width, v * v * horizon),
        )
    }
}

private fun drawHorizon(d: DrawScope, cam: Camera, horizon: Float, rnd: Random) = with(d) {
    // Lichtglocke der Stadt am Horizont
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.55f to Color(0x33B0577A),
            0.8f to Color(0x55D9785A),
            1f to Color(0x44FFB27A),
            startY = horizon - size.height * 0.16f,
            endY = horizon + 2f,
        ),
        topLeft = Offset(0f, horizon - size.height * 0.16f),
        size = Size(size.width, size.height * 0.16f + 2f),
    )
    // Ferne Skyline-Silhouetten vom Horizont bis zum Rand der gerenderten Stadt
    val far = FloatArray(3)
    val dx = TARGET_X - CAM_X
    val dz = TARGET_Z - CAM_Z
    val dl = sqrt(dx * dx + dz * dz)
    val farOk = cam.project(CAM_X + dx / dl * MAX_DIST * 0.93f, 0f, CAM_Z + dz / dl * MAX_DIST * 0.93f, far)
    val baseY = if (farOk) max(far[1], horizon + 4f) else horizon + size.height * 0.05f
    val band = baseY - horizon
    drawRect(Color(0xFF110E22), topLeft = Offset(0f, horizon), size = Size(size.width, band + 2f))
    var x = -10f
    while (x < size.width + 10f) {
        val bw = 4f + rnd.nextFloat() * 18f
        val bh = band * (0.4f + rnd.nextFloat() * 0.8f) + rnd.nextFloat().pow(2.5f) * size.height * 0.05f
        drawRect(Color(0xFF120F26), topLeft = Offset(x, baseY - bh), size = Size(bw, bh + 2f))
        repeat((bw * bh / 22f).toInt()) {
            if (rnd.nextFloat() < 0.45f) {
                drawRect(
                    WARM.copy(alpha = 0.35f + rnd.nextFloat() * 0.4f),
                    topLeft = Offset(x + rnd.nextFloat() * bw, baseY - rnd.nextFloat() * bh),
                    size = Size(1f, 1f),
                )
            }
        }
        x += bw * (0.7f + rnd.nextFloat() * 0.5f)
    }
}

private fun drawGround(d: DrawScope, horizon: Float) = with(d) {
    val top = horizon
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF1A1430),
            0.25f to Color(0xFF0E0D1E),
            1f to Color(0xFF07070F),
            startY = top,
            endY = size.height,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, size.height - top),
    )
}

private fun drawStreets(d: DrawScope, cam: Camera, model: CityModel) = with(d) {
    val a = FloatArray(3); val b = FloatArray(3); val c = FloatArray(3); val e = FloatArray(3)
    val p = FloatArray(2)
    val path = Path()
    val segment = 8f
    for (st in model.streets) {
        var s = 0f
        while (s < st.length) {
            val s2 = min(st.length, s + segment)
            st.point(s, 0f, p)
            val mx = p[0]; val mz = p[1]
            val dx = mx - CAM_X; val dz = mz - CAM_Z
            if (dx * dx + dz * dz < MAX_DIST * MAX_DIST) {
                for (pass in 0 until 2) {
                    val half = if (pass == 0) 2.4f else 0.85f
                    st.point(s, -half, p); val ok1 = cam.project(p[0], 0f, p[1], a)
                    st.point(s2, -half, p); val ok2 = cam.project(p[0], 0f, p[1], b)
                    st.point(s2, half, p); val ok3 = cam.project(p[0], 0f, p[1], c)
                    st.point(s, half, p); val ok4 = cam.project(p[0], 0f, p[1], e)
                    if (ok1 && ok2 && ok3 && ok4) {
                        path.reset()
                        path.moveTo(a[0], a[1]); path.lineTo(b[0], b[1]); path.lineTo(c[0], c[1]); path.lineTo(e[0], e[1]); path.close()
                        if (pass == 0) {
                            drawPath(path, (if (st.main) SODIUM else WARM2).copy(alpha = if (st.main) 0.08f else 0.04f), blendMode = BlendMode.Plus)
                        } else {
                            drawPath(path, Color(0xFF1B1626))
                            drawPath(path, SODIUM.copy(alpha = if (st.main) 0.13f else 0.07f), blendMode = BlendMode.Plus)
                        }
                    }
                }
                // Strassenlaternen links und rechts
                var ls = s
                while (ls < s2) {
                    for (side in intArrayOf(-1, 1)) {
                        st.point(ls, side * 1.0f, p)
                        if (cam.project(p[0], 0.6f, p[1], a) && a[2] < 170f) {
                            val r = (0.16f * cam.f / a[2]).coerceIn(0.6f, 3.5f)
                            drawCircle(Color(0xFFFFD9A8).copy(alpha = 0.7f), radius = r * 0.8f, center = Offset(a[0], a[1]))
                        }
                    }
                    ls += 3.5f
                }
            }
            s = s2
        }
    }
}

private fun drawBuilding(
    d: DrawScope,
    cam: Camera,
    b: Building,
    c: FloatArray,
    flick: FlickBuilder,
    beacons: ArrayList<FloatArray>,
    index: Int,
    occluded: (Float, Float, Float, Int) -> Boolean,
) = with(d) {
    fun px(k: Int) = c[k * 3]
    fun py(k: Int) = c[k * 3 + 1]

    val base = lerp(Color(0xFF151A33), Color(0xFF221B3A), b.tone)
    val lit = lerp(base, Color(0xFF6A3A3A), 0.35f)
    val path = Path()

    // Seitenflaechen: Index-Quads (unten a,b – oben b',a')
    val faces = listOf(
        Face(0, 1, CAM_Z < b.z0, 0.0f, 0), // Sued
        Face(1, 2, CAM_X > b.x1, 0.18f, 1), // Ost
        Face(3, 0, CAM_X < b.x0, -0.05f, 1), // West
        Face(2, 3, CAM_Z > b.z1, -0.08f, 0), // Nord
    )
    for (fc in faces) {
        if (!fc.visible) continue
        val a0 = fc.a; val b0 = fc.b; val b1 = fc.b + 4; val a1 = fc.a + 4
        path.reset()
        path.moveTo(px(a0), py(a0)); path.lineTo(px(b0), py(b0)); path.lineTo(px(b1), py(b1)); path.lineTo(px(a1), py(a1)); path.close()
        val top = min(py(a1), py(b1))
        val bottom = max(py(a0), py(b0))
        val faceTop = lerp(base, Color(0xFF3A4570), 0.2f + fc.light)
        drawPath(
            path,
            Brush.verticalGradient(listOf(faceTop, lerp(lit, Color(0xFF8A4A35), 0.15f + fc.light * 0.5f)), startY = top, endY = bottom),
        )

        // Fenster
        val faceH = bottom - top
        val faceW = abs(px(b0) - px(a0))
        val len = if (fc.along == 0) b.x1 - b.x0 else b.z1 - b.z0
        val floors = (b.h / 1.05f).toInt()
        val cols = (len / 0.95f).toInt().coerceAtLeast(1)
        val depth = (c[a0 * 3 + 2] + c[b0 * 3 + 2]) / 2f
        if (faceH > 26f && faceW > 6f && floors > 0) {
            val tmp = FloatArray(3)
            val scale = cam.f / depth
            val ww = (0.26f * scale).coerceAtLeast(0.8f)
            val wh = (0.42f * scale).coerceAtLeast(0.8f)
            for (fl in 0 until floors) for (co in 0 until cols) {
                val u = (co + 0.5f) / cols
                val wy = (fl + 0.55f) * b.h / floors
                val wx: Float
                val wz: Float
                when {
                    fc.a == 0 -> { wx = b.x0 + u * (b.x1 - b.x0); wz = b.z0 - 0.02f }
                    fc.a == 1 -> { wx = b.x1 + 0.02f; wz = b.z0 + u * (b.z1 - b.z0) }
                    fc.a == 3 -> { wx = b.x0 - 0.02f; wz = b.z1 - u * (b.z1 - b.z0) }
                    else -> { wx = b.x1 - u * (b.x1 - b.x0); wz = b.z1 + 0.02f }
                }
                if (!cam.project(wx, wy, wz, tmp)) continue
                val r = hash01(b.seed, fl * 131 + co)
                val isLit = r < b.litRate
                val colorPick = hash01(b.seed + 7, fl * 31 + co * 7)
                val color = when {
                    colorPick < 0.62f -> WARM
                    colorPick < 0.9f -> COOL
                    else -> TEAL
                }
                // Ein paar nahe Fenster gehen spaeter dynamisch an und aus
                val candidate = tmp[2] < 140f && ww > 1.6f && hash01(b.seed + 3, fl * 17 + co) < 0.06f
                if (candidate && !occluded(tmp[0], tmp[1], tmp[2], index)) {
                    flick.x += tmp[0] - ww / 2f; flick.y += tmp[1] - wh / 2f
                    flick.w += ww; flick.h += wh
                    flick.color += color.toArgbInt()
                    flick.seed += b.seed xor (fl * 977 + co)
                    flick.tv += hash01(b.seed + 11, fl + co) < 0.2f
                    drawRect(Color(0x22273A66), topLeft = Offset(tmp[0] - ww / 2f, tmp[1] - wh / 2f), size = Size(ww, wh))
                    continue
                }
                if (isLit) {
                    val a = 0.4f + 0.35f * hash01(b.seed + 5, fl * 3 + co)
                    drawRect(color.copy(alpha = a), topLeft = Offset(tmp[0] - ww / 2f, tmp[1] - wh / 2f), size = Size(ww, wh))
                } else if (ww > 2.5f) {
                    drawRect(Color(0x1A3A4A7A), topLeft = Offset(tmp[0] - ww / 2f, tmp[1] - wh / 2f), size = Size(ww, wh))
                }
            }
        } else if (faceH > 2f && faceW > 1f) {
            // Ferne Gebaeude: nur Lichtpunkte
            val count = (faceH * faceW / 30f * b.litRate).toInt().coerceAtMost(60)
            for (k in 0 until count) {
                val u = hash01(b.seed + k, 1)
                val v = hash01(b.seed + k, 2) * 0.92f + 0.05f
                val xb = px(a0) + (px(b0) - px(a0)) * u
                val yb = py(a0) + (py(b0) - py(a0)) * u
                val xt = px(a1) + (px(b1) - px(a1)) * u
                val yt = py(a1) + (py(b1) - py(a1)) * u
                val wx = xb + (xt - xb) * v
                val wy = yb + (yt - yb) * v
                val col = if (hash01(b.seed, k) < 0.7f) WARM else COOL
                drawRect(col.copy(alpha = 0.55f + 0.4f * hash01(k, b.seed)), topLeft = Offset(wx, wy), size = Size(1.1f, 1.1f))
            }
        }

        // Neon-Streifen an der Hausecke
        if (b.neon > 0 && faceH > 20f) {
            val neon = NEONS[(b.neon - 1) % NEONS.size]
            val t0 = 0.3f; val t1 = 0.85f
            val ex = px(b0); val ey0 = py(b0) + (py(b1) - py(b0)) * t0; val ey1 = py(b0) + (py(b1) - py(b0)) * t1
            val strokeW = (0.25f * cam.f / depth).coerceIn(1f, 6f)
            drawLine(neon.copy(alpha = 0.12f), Offset(ex, ey0), Offset(ex, ey1), strokeWidth = strokeW * 4f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            drawLine(neon.copy(alpha = 0.7f), Offset(ex, ey0), Offset(ex, ey1), strokeWidth = strokeW, cap = StrokeCap.Round)
        }
    }

    // Dach
    path.reset()
    path.moveTo(px(4), py(4)); path.lineTo(px(5), py(5)); path.lineTo(px(6), py(6)); path.lineTo(px(7), py(7)); path.close()
    drawPath(path, lerp(base, Color(0xFF39406A), 0.35f))
    drawPath(path, Color(0x33A9B8FF), style = Stroke(width = 0.8f))

    // Flugwarnlicht oben auf Hochhaeusern
    if (b.beacon) {
        val tmp = FloatArray(3)
        if (cam.project((b.x0 + b.x1) / 2f, b.h + 1.2f, (b.z0 + b.z1) / 2f, tmp)) {
            // Antenne
            val baseP = FloatArray(3)
            if (cam.project((b.x0 + b.x1) / 2f, b.h, (b.z0 + b.z1) / 2f, baseP)) {
                drawLine(Color(0x88A0A8C8), Offset(baseP[0], baseP[1]), Offset(tmp[0], tmp[1]), strokeWidth = 1f)
            }
            beacons += floatArrayOf(tmp[0], tmp[1], (0.25f * cam.f / tmp[2]).coerceIn(1.2f, 4f), hash01(b.seed, 99) * 6.28f)
        }
    }
}

private class Face(val a: Int, val b: Int, val visible: Boolean, val light: Float, val along: Int)

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255).toInt(); val r = (red * 255).toInt(); val g = (green * 255).toInt(); val bl = (blue * 255).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or bl
}

// ==========================================================================================
// Pro Frame
// ==========================================================================================

private fun DrawScope.drawFrame(s: SceneCache, t: Float, glow: Float, sparkle: Float, rain: Float, light: Daylight) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2f, h * 0.6f)

    // Langsame Kamerafahrt: Stadt bewegt sich mehr als der Himmel (Parallaxe)
    val zoom = 1.05f + 0.03f * sin(t * (2f * PI.toFloat()) / 85f)
    val panX = w * 0.014f * sin(t * (2f * PI.toFloat()) / 120f)
    val panY = h * 0.008f * sin(t * (2f * PI.toFloat()) / 97f + 1f)

    // ---------- Himmel ----------
    translate(panX * 0.3f, panY * 0.3f) {
        scale(1f + (zoom - 1f) * 0.35f, pivot = center) {
            // Himmel nach Tageszeit; Nachthimmel (Milchstrasse, Sterne) blendet mit der Dunkelheit ein
            drawRect(Brush.verticalGradient(listOf(light.skyTop, light.skyHorizon), startY = 0f, endY = s.horizonY))
            if (light.stars > 0.02f) {
                drawImage(
                    s.sky,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(s.sky.width, s.sky.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    alpha = light.stars,
                    filterQuality = FilterQuality.Low,
                )
                drawStars(s, t, sparkle, light.stars)
                if (light.stars > 0.8f) drawShootingStar(s, t)
            }
            drawClouds(s, t)
        }
    }

    // ---------- Stadt ----------
    translate(panX, panY) {
        scale(zoom, pivot = center) {
            // Tagsueber: Gebaeude aufhellen (Tageslicht), nachts wie gerendert
            val dayAdd = lerp(Color.Black, Color(0xFF6C7890), light.day)
            drawImage(
                s.city,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(s.city.width, s.city.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(s.city.width, s.city.height),
                filterQuality = FilterQuality.Medium,
                colorFilter = if (light.day > 0.02f) ColorFilter.lighting(Color.White, dayAdd) else null,
            )
            if (light.night > 0.2f) drawFlicker(s, t)
            drawCars(s, t, rain)
            // insgesamt etwas dunkler (nachts)
            drawRect(Color(0xFF02030A).copy(alpha = 0.22f * light.night))
            // Daemmerung: warmer Schimmer ueber der Stadt
            if (light.twilight > 0.02f) {
                drawRect(
                    Brush.verticalGradient(listOf(light.sunColor.copy(alpha = 0.18f * light.twilight), Color.Transparent), startY = s.horizonY - h * 0.1f, endY = h),
                    blendMode = BlendMode.Plus,
                )
            }
            // Musik: Stadtlicht atmet leicht im Beat
            if (glow > 0.01f) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color(0xFFFF9E5A).copy(alpha = 0.07f * glow),
                        startY = s.horizonY,
                        endY = h,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }

    // ---------- Regen (vor der Kamera, ohne Kamerafahrt) ----------
    if (rain > 0.01f) drawRain(t, rain)

    // ---------- Vignette ----------
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color(0xAA02030A)),
            center = Offset(w / 2f, h * 0.5f),
            radius = max(w, h) * 0.78f,
        ),
    )
}

private fun DrawScope.drawStars(s: SceneCache, t: Float, sparkle: Float, visible: Float = 1f) {
    for (i in s.starX.indices) {
        // ruhiges, sanftes Funkeln – ohne Schein und ohne Kreuze
        val tw = 0.6f + 0.4f * sin(t * s.starSpeed[i] * 0.7f + s.starPhase[i])
        val a = (0.3f + 0.5f * tw + sparkle * 0.1f).coerceIn(0f, 0.9f)
        drawCircle(Color(s.starTint[i]).copy(alpha = a * visible), radius = s.starR[i] * 0.8f, center = Offset(s.starX[i], s.starY[i]))
    }
}

private fun DrawScope.drawShootingStar(s: SceneCache, t: Float) {
    val period = 55f
    val slot = floor(t / period).toInt()
    val local = t - slot * period
    val start = 3f + hash01(slot, 1) * 12f
    val dur = 0.9f
    if (local < start || local > start + dur) return
    val p = (local - start) / dur
    val x0 = s.w * (0.15f + hash01(slot, 2) * 0.7f)
    val y0 = s.horizonY * (0.05f + hash01(slot, 3) * 0.35f)
    val dir = if (hash01(slot, 4) < 0.5f) -1f else 1f
    val dx = dir * s.w * 0.22f
    val dy = s.horizonY * 0.28f
    val head = Offset(x0 + dx * p, y0 + dy * p)
    val tail = Offset(x0 + dx * (p - 0.25f).coerceAtLeast(0f), y0 + dy * (p - 0.25f).coerceAtLeast(0f))
    val alpha = sin(p * PI.toFloat())
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = alpha * 0.7f)), start = tail, end = head),
        tail, head, strokeWidth = 1.2f * density, cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawClouds(s: SceneCache, t: Float) {
    for (i in 0 until 5) {
        val speed = 4f + i * 2.2f
        val span = s.w * 1.6f
        val x = ((i * 0.31f * span + t * speed) % span) - s.w * 0.3f
        val y = s.horizonY * (0.25f + (i % 3) * 0.18f)
        for (k in 0 until 6) {
            val c = Offset(x + k * s.w * 0.045f, y + sin(k * 1.7f + i) * s.horizonY * 0.03f)
            val r = s.w * (0.05f + 0.025f * ((k + i) % 3))
            drawCircle(
                Brush.radialGradient(listOf(Color(0x332E2650), Color.Transparent), center = c, radius = r),
                radius = r, center = c,
            )
            // Unterseite leicht von der Stadt angestrahlt
            drawCircle(
                Brush.radialGradient(listOf(Color(0x14FF9A6A), Color.Transparent), center = c.copy(y = c.y + r * 0.3f), radius = r * 0.8f),
                radius = r * 0.8f, center = c.copy(y = c.y + r * 0.3f), blendMode = BlendMode.Plus,
            )
        }
    }
}

private fun DrawScope.drawFlicker(s: SceneCache, t: Float) {
    for (i in s.flickX.indices) {
        val col = Color(s.flickColor[i])
        if (s.flickTv[i]) {
            // Fernseher: unruhiges, blaeuliches Flimmern
            val n = 0.55f + 0.25f * sin(t * 7.3f + i) + 0.2f * sin(t * 13.1f + i * 2.3f)
            val tvCol = lerp(Color(0xFF7FA4FF), Color(0xFFBFD4FF), (sin(t * 3.1f + i) + 1f) / 2f)
            if (hash01(s.flickSeed[i], (t / 20f).toInt()) < 0.6f) {
                drawRect(tvCol.copy(alpha = (n * 0.6f).coerceIn(0f, 1f)), topLeft = Offset(s.flickX[i], s.flickY[i]), size = Size(s.flickW[i], s.flickH[i]))
            }
            continue
        }
        val period = 7f + hash01(s.flickSeed[i], 0) * 12f
        val slot = floor((t + hash01(s.flickSeed[i], 1) * period) / period).toInt()
        val on = hash01(s.flickSeed[i], slot) < 0.55f
        if (on) {
            drawRect(col.copy(alpha = 0.6f), topLeft = Offset(s.flickX[i], s.flickY[i]), size = Size(s.flickW[i], s.flickH[i]))
        }
    }
}

private val HEAD = Color(0xFFFFF1D2)
private val TAIL = Color(0xFFFF2E3A)

private fun DrawScope.drawCars(s: SceneCache, t: Float, rain: Float) {
    val p = FloatArray(2)
    val out = FloatArray(3)
    for (car in s.cars) {
        val st = s.model.streets[car.street]
        var pos = (car.offset + t * car.speed) % st.length
        if (pos < 0) pos += st.length
        val vis = s.streetVis[car.street]
        val idx = (pos / s.visStep).toInt().coerceIn(0, vis.size - 1)
        if (!vis[idx]) continue
        st.point(pos, car.lane, p)
        if (!s.camera.project(p[0], 0.25f, p[1], out)) continue
        val r = (0.16f * s.camera.f / out[2]).coerceIn(0.6f, 3.2f)
        val col = if (car.head) HEAD else TAIL
        val c = Offset(out[0], out[1])
        drawCircle(col.copy(alpha = 0.12f), radius = r * 2.2f, center = c, blendMode = BlendMode.Plus)
        drawCircle(col, radius = r, center = c)
        // Spiegelung auf nasser Strasse
        if (rain > 0.05f || car.head) {
            drawLine(
                col.copy(alpha = 0.18f * (0.4f + rain)), c, Offset(c.x, c.y + r * 7f),
                strokeWidth = r * 1.2f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
            )
        }
    }
}

private fun DrawScope.drawRain(t: Float, amount: Float) {
    val w = size.width
    val h = size.height
    // ferne, feine Faeden
    val far = Color(0xFFB8C8FF).copy(alpha = 0.10f * amount)
    for (i in 0 until 170) {
        val speed = 900f + hash01(i, 1) * 500f
        val len = 14f + hash01(i, 2) * 18f
        val x0 = hash01(i, 3) * (w + 200f)
        val y = ((hash01(i, 4) * (h + len) + t * speed) % (h + len)) - len
        val x = (x0 - (t * speed * 0.18f) % (w + 200f) + w + 200f) % (w + 200f) - 100f
        drawLine(far, Offset(x, y), Offset(x - len * 0.18f, y + len), strokeWidth = 0.9f * density)
    }
    // nahe, groessere Tropfen
    val near = Color(0xFFD0DCFF).copy(alpha = 0.16f * amount)
    for (i in 0 until 34) {
        val speed = 1700f + hash01(i, 5) * 700f
        val len = 50f + hash01(i, 6) * 50f
        val x0 = hash01(i, 7) * (w + 300f)
        val y = ((hash01(i, 8) * (h + len) + t * speed) % (h + len)) - len
        val x = (x0 - (t * speed * 0.18f) % (w + 300f) + w + 300f) % (w + 300f) - 150f
        drawLine(near, Offset(x, y), Offset(x - len * 0.18f, y + len), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
    }
    // feuchter Schleier unten
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x14A8B8E0).copy(alpha = 0.08f * amount)), startY = h * 0.5f, endY = h),
    )
}
