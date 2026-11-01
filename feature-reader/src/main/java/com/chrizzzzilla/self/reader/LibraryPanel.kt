package com.chrizzzzilla.self.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import com.chrizzzzilla.self.core.design.WidthClass
import com.chrizzzzilla.self.core.library.LibraryItem
import com.chrizzzzilla.self.core.library.MediaKind
import kotlinx.coroutines.launch
import kotlin.math.sin

private val headStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 4.sp, color = AmbientColors.Light)
private val chipStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 2.sp)

private const val FILTER_ALL = "Alle"
private const val FILTER_COMICS = "Manga & Comics"
private const val FILTER_NONE = "Ohne Genre"

/**
 * Bibliothek als Fenster vor dem Bild: Suche, Genre-Filter, Raster aller Buecher,
 * oben rechts „+“ (Buch hinzufuegen), Schliessen mit X oder Tippen daneben.
 * Lange druecken auf ein Buch: entfernen.
 */
@Composable
internal fun LibraryPanel(
    items: List<LibraryItem>,
    widthClass: WidthClass,
    onOpen: (LibraryItem) -> Unit,
    onRemove: (LibraryItem) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FILTER_ALL) }

    val genres = remember(items) {
        buildList {
            add(FILTER_ALL)
            if (items.any { it.kind == MediaKind.COMIC }) add(FILTER_COMICS)
            items.mapNotNull { it.genre?.takeIf { g -> g.isNotBlank() } }.distinct().sorted().forEach { add(it) }
            if (items.any { it.kind == MediaKind.BOOK && it.genre.isNullOrBlank() }) add(FILTER_NONE)
        }
    }
    val shown = items.filter { item ->
        val q = query.trim()
        val matchesQuery = q.isEmpty() ||
            item.title.contains(q, ignoreCase = true) ||
            (item.creator?.contains(q, ignoreCase = true) == true) ||
            (item.genre?.contains(q, ignoreCase = true) == true)
        val matchesFilter = when (filter) {
            FILTER_ALL -> true
            FILTER_COMICS -> item.kind == MediaKind.COMIC
            FILTER_NONE -> item.kind == MediaKind.BOOK && item.genre.isNullOrBlank()
            else -> item.genre == filter
        }
        matchesQuery && matchesFilter
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A).copy(alpha = 0.55f * p.value.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .graphicsLayer {
                    alpha = p.value.coerceIn(0f, 1f)
                    scaleX = 0.9f + 0.1f * p.value
                    scaleY = 0.9f + 0.1f * p.value
                    translationY = (1f - p.value) * 60f * density
                }
                .fillMaxSize()
                .padding(if (widthClass == WidthClass.COMPACT) 12.dp else 36.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF2141519), Color(0xF20C0D10))))
                .border(1.dp, AmbientColors.Light.copy(alpha = 0.10f), shape)
                // Klicks im Fenster nicht zum Hintergrund durchreichen
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            // Kopf: X · Titel · Suche · +
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(onClick = onClose) {
                    Canvas(Modifier.size(14.dp)) {
                        drawLine(AmbientColors.Light, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text("BIBLIOTHEK", style = headStyle)
                Spacer(Modifier.width(24.dp))
                SearchField(query, { query = it }, Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                RoundIcon(onClick = onAdd, accent = true) {
                    Canvas(Modifier.size(16.dp)) {
                        drawLine(AmbientColors.Light, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
                        drawLine(AmbientColors.Light, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
                    }
                }
            }
            // Genres
            Row(
                Modifier
                    .padding(vertical = 14.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { g ->
                    val sel = g == filter
                    val cs = RoundedCornerShape(50)
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(cs)
                            .background(if (sel) Color(0xFF1E2A55) else Color(0x14FFFFFF))
                            .border(1.dp, if (sel) AmbientColors.Arty else AmbientColors.Light.copy(alpha = 0.14f), cs)
                            .clickable { filter = g }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(g.uppercase(), style = chipStyle.copy(color = if (sel) AmbientColors.Light else AmbientColors.Muted))
                    }
                }
            }
            // Buecher
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (items.isEmpty()) "Noch keine Bücher – oben rechts auf + tippen." else "Nichts gefunden.",
                        style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = AmbientColors.Muted),
                    )
                }
            } else {
                val minCell = if (widthClass == WidthClass.COMPACT) 120.dp else 150.dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = minCell),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(shown, key = { it.id }) { item ->
                        BookCard(item = item, onOpen = { onOpen(item) }, onLongPress = { onRemove(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, accent: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (accent) Color(0xFF1E2A55) else Color(0x14FFFFFF))
            .border(1.dp, if (accent) AmbientColors.Arty else AmbientColors.Light.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(42.dp)
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.12f), shape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text("Suchen: Titel, Autor, Genre", style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = AmbientColors.Muted))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Manrope, fontSize = 14.sp, color = AmbientColors.Light),
            cursorBrush = SolidColor(AmbientColors.ArtyText),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Bibliotheks-Knopf (unten rechts auf „Soul“): Glas wie das Raster-Menue, darin drei Buchruecken
 * und ein angelehntes Buch als feine Linien; ein kleiner Lichtpunkt atmet.
 */
@Composable
internal fun LibraryButton(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .size(size)
            .clip(shape)
            .background(Color(0xCC17181D))
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.18f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch {
                    bounce.snapTo(0.9f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                }
                onClick()
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val w = this.size.width
            val h = this.size.height
            val base = h * 0.7f
            val line = AmbientColors.Light.copy(alpha = 0.9f)
            val stroke = 1.4f * density
            val glowC = Offset(w * 0.5f, h * 0.5f)
            drawCircle(
                Brush.radialGradient(listOf(AmbientColors.Arty.copy(alpha = 0.18f + 0.06f * sin(t * 1.1f)), Color.Transparent), center = glowC, radius = w * 0.5f),
                radius = w * 0.5f, center = glowC,
            )
            val bw = w * 0.1f
            val heights = floatArrayOf(0.36f, 0.42f, 0.32f)
            var x = w * 0.27f
            for (k in 0 until 3) {
                val bh = h * heights[k]
                drawRoundRect(
                    line, topLeft = Offset(x, base - bh), size = Size(bw, bh),
                    cornerRadius = CornerRadius(bw * 0.25f), style = Stroke(stroke),
                )
                x += bw + w * 0.025f
            }
            // angelehntes Buch
            rotate(18f, pivot = Offset(x + bw * 0.2f, base)) {
                drawRoundRect(
                    AmbientColors.ArtyText, topLeft = Offset(x, base - h * 0.34f), size = Size(bw, h * 0.34f),
                    cornerRadius = CornerRadius(bw * 0.25f), style = Stroke(stroke),
                )
            }
            drawLine(line.copy(alpha = 0.5f), Offset(w * 0.2f, base + stroke), Offset(w * 0.8f, base + stroke), strokeWidth = stroke, cap = StrokeCap.Round)
            val dot = Offset(w * 0.27f + bw * 0.5f, base - h * 0.36f - h * 0.06f)
            drawCircle(AmbientColors.Arty.copy(alpha = 0.5f + 0.3f * sin(t * 1.4f)), radius = w * 0.06f, center = dot, blendMode = BlendMode.Plus)
            drawCircle(AmbientColors.ArtyText, radius = w * 0.022f, center = dot)
        }
    }
}
