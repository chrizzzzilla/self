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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope

/**
 * Woher kommt das Buch? Zwei Felder federn auf:
 *  - Orell Füssli · Tolino → Website im Browser (dort angemeldet bleiben, EPUB laden, „Öffnen mit Self“)
 *  - Eigene Datei → Dateiauswahl
 * Tippen daneben schliesst.
 */
@Composable
internal fun SourceChooser(
    onStore: () -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A).copy(alpha = 0.55f * p.value.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SourceTile(
                icon = R.drawable.reader_ic_globe,
                title = stringResource(R.string.reader_source_store),
                hint = stringResource(R.string.reader_source_store_hint),
                progress = p.value,
                delay = 0f,
                onClick = onStore,
            )
            SourceTile(
                icon = R.drawable.reader_ic_folder,
                title = stringResource(R.string.reader_source_files),
                hint = stringResource(R.string.reader_source_files_hint),
                progress = p.value,
                delay = 0.1f,
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
            .width(200.dp)
            .height(200.dp)
            .clip(shape)
            .background(Color(0xF0141519))
            .border(1.dp, AmbientColors.Light.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = AmbientColors.ArtyText, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            title.uppercase(),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 2.5.sp,
                color = AmbientColors.Light,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            hint,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = AmbientColors.Muted,
            ),
        )
    }
}
