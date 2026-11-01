package com.chrizzzzilla.self.reader

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope

/**
 * Woher kommt das Buch? Felder federn nacheinander auf:
 * DRM-freie Shops/Bibliotheken (oeffnen im eingebauten Browser) und „Eigene Datei“.
 * Tippen daneben schliesst.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceChooser(
    compact: Boolean,
    onSource: (BookSource) -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    val tile = if (compact) 150.dp else 184.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A).copy(alpha = 0.6f * p.value.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        FlowRow(
            modifier = Modifier
                .widthIn(max = tile * 3 + 40.dp)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BookSource.entries.forEachIndexed { i, src ->
                SourceTile(
                    icon = src.icon,
                    title = stringResource(src.title),
                    hint = stringResource(src.hint),
                    size = tile,
                    progress = p.value,
                    delay = i * 0.06f,
                    onClick = { onSource(src) },
                )
            }
            SourceTile(
                icon = R.drawable.reader_ic_folder,
                title = stringResource(R.string.reader_source_files),
                hint = stringResource(R.string.reader_source_files_hint),
                size = tile,
                progress = p.value,
                delay = BookSource.entries.size * 0.06f,
                onClick = onFiles,
            )
        }
    }
}

@Composable
private fun SourceTile(
    @DrawableRes icon: Int,
    title: String,
    hint: String,
    size: Dp,
    progress: Float,
    delay: Float,
    onClick: () -> Unit,
) {
    val local = ((progress - delay) / (1f - delay)).coerceIn(0f, 1.25f)
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .graphicsLayer {
                alpha = local.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * local
                scaleY = 0.6f + 0.4f * local
                translationY = (1f - local) * 40f * density
            }
            .size(size)
            .clip(shape)
            .background(Color(0xF0141519))
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = AmbientColors.ArtyText, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            title.uppercase(),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 2.2.sp,
                color = AmbientColors.Light,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 11.sp, color = AmbientColors.Muted),
        )
    }
}
