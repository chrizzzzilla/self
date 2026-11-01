package com.chrizzzzilla.self.reader

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import com.chrizzzzilla.self.core.library.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * „Weiterlesen“: das zuletzt geoeffnete Buch gross und mittig – Cover mit sanftem Schein,
 * darunter Titel, Autor und eine feine Fortschrittslinie. Antippen oeffnet es an der letzten Stelle.
 */
@Composable
internal fun ContinueReading(item: LibraryItem, compact: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val cover by produceState<ImageBitmap?>(initialValue = null, item.coverPath) {
        value = item.coverPath?.let { path -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() } }
    }
    val appear = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
    val w = if (compact) 150.dp else 200.dp

    Column(
        modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 30f * density
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "WEITERLESEN",
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 5.sp, color = AmbientColors.ArtyText),
        )
        Spacer(Modifier.height(14.dp))
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .size(width = w, height = w * 1.5f)
                .drawBehind {
                    // weicher, blauer Schein hinter dem Cover
                    drawCircle(
                        Brush.radialGradient(
                            listOf(AmbientColors.Arty.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.maxDimension * 0.85f,
                        ),
                        radius = size.maxDimension * 0.85f,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
                .clip(shape)
                .background(Color(0xFF1A1B20))
                .border(1.dp, AmbientColors.Light.copy(alpha = 0.14f), shape),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = cover
            if (bmp != null) {
                Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    item.title,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Manrope, fontSize = 15.sp, color = AmbientColors.Light),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = AmbientColors.Light),
            modifier = Modifier.width(w * 1.6f),
        )
        item.creator?.let {
            Text(
                it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Manrope, fontSize = 12.sp, color = AmbientColors.Muted),
            )
        }
        Spacer(Modifier.height(10.dp))
        // feine Fortschrittslinie
        Box(
            Modifier
                .width(w)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0x22FFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(item.progress.toFloat().coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(AmbientColors.ArtyText),
            )
        }
    }
}
