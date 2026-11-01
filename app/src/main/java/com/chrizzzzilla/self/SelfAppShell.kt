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
import androidx.compose.ui.graphics.TransformOrigin
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
import com.chrizzzzilla.self.game.GameScreen
import com.chrizzzzilla.self.reader.LibraryScreen
import kotlin.math.sin

/** Die Bereiche der App. Neue Bereiche = neuer Eintrag + Platz im Menue-Raster. */
enum class Section(@StringRes val label: Int, @DrawableRes val icon: Int) {
    READ(R.string.app_section_read, R.drawable.app_ic_read),
    GAME(R.string.app_section_game, R.drawable.app_ic_game),
}

/**
 * Rahmen der App: Inhalt immer bildschirmfuellend (Arty bleibt genau in der Mitte),
 * Navigation ueber den Raster-Knopf unten links (gut mit dem linken Daumen erreichbar).
 */
@Composable
fun SelfAppShell(incomingBook: Uri?, onIncomingBookHandled: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.READ) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
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
            )
            Section.GAME -> GameScreen(modifier = Modifier.fillMaxSize())
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

        GridMenu(
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
            if (open) spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow) else tween(260),
        )
    }
    val p = progress.value
    val grey by animateFloatAsState(if (open) 1f else 0f, tween(300), label = "grau")

    val full = cell * 2 + gap + pad * 2
    Box(modifier.size(full), contentAlignment = Alignment.BottomStart) {
        // Das grosse Quadrat waechst aus dem Knopf heraus (mit Ueberschwingen)
        if (p > 0.001f) {
            val grow = (full - cell - pad * 2) * p
            Box(
                Modifier
                    .size((cell + pad * 2 + grow).coerceAtLeast(1.dp))
                    .graphicsLayer { alpha = (p * 1.4f).coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xE61A1B21), Color(0xF0111216))))
                    .border(1.dp, AmbientColors.Light.copy(alpha = 0.10f), RoundedCornerShape(26.dp)),
            )
        }

        // Felder (erscheinen kurz nacheinander)
        val tiles = listOf(
            Triple(0, 1, Section.READ), // oben links
            Triple(1, 1, Section.GAME), // oben rechts
            Triple(1, 0, null),         // unten rechts: noch frei
        )
        tiles.forEachIndexed { i, (col, row, target) ->
            val local = ((p - i * 0.08f) / (1f - i * 0.08f)).coerceIn(0f, 1.2f)
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
                            icon = target.icon,
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
            GridButton(size = cell, grey = grey, onClick = onToggle)
        }
    }
}

@Composable
private fun MenuTile(label: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
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
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (selected) AmbientColors.ArtyText else AmbientColors.Light.copy(alpha = 0.85f),
            modifier = Modifier.size(26.dp),
        )
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
private fun GridButton(size: Dp, grey: Float, onClick: () -> Unit) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .size(size)
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
