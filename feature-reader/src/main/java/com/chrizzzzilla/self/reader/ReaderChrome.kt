package com.chrizzzzilla.self.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chrizzzzilla.self.core.settings.AppSettings
import com.chrizzzzilla.self.core.settings.AppTheme
import org.readium.r2.shared.publication.Link
import kotlin.math.roundToInt

private const val CHROME_ALPHA = 0.96f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopChrome(
    ui: ReaderUiState,
    title: String,
    tableOfContents: List<Link>,
    readingOrder: List<Link>,
    focusAvailable: Boolean,
    onBack: () -> Unit,
    onMusic: () -> Unit,
    onLinkSelected: (Link) -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onFontScaleSelected: (Double) -> Unit,
) {
    AnimatedVisibility(visible = ui.chromeVisible.value, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.reader_ic_back), stringResource(R.string.reader_back))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                // Platz fuer die spaetere Fokus-Musik (laeuft dann im Hintergrund weiter).
                IconButton(onClick = onMusic) {
                    Icon(
                        painterResource(R.drawable.reader_ic_music),
                        stringResource(R.string.reader_music),
                        tint = if (focusAvailable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
                IconButton(onClick = { ui.showToc.value = true }) {
                    Icon(painterResource(R.drawable.reader_ic_toc), stringResource(R.string.reader_toc))
                }
                IconButton(onClick = { ui.showDisplaySettings.value = true }) {
                    Icon(painterResource(R.drawable.reader_ic_text), stringResource(R.string.reader_display))
                }
            }
        }
    }

    if (ui.showToc.value) {
        ModalBottomSheet(onDismissRequest = { ui.showToc.value = false }) {
            TableOfContents(
                tableOfContents = tableOfContents,
                readingOrder = readingOrder,
                onLinkSelected = onLinkSelected,
            )
        }
    }

    if (ui.showDisplaySettings.value) {
        ModalBottomSheet(onDismissRequest = { ui.showDisplaySettings.value = false }) {
            DisplaySettings(
                settings = ui.settings.value,
                onThemeSelected = onThemeSelected,
                onFontScaleSelected = onFontScaleSelected,
            )
        }
    }
}

@Composable
internal fun ReaderBottomChrome(ui: ReaderUiState, onSeek: (Double) -> Unit) {
    AnimatedVisibility(visible = ui.chromeVisible.value, enter = fadeIn(), exit = fadeOut()) {
        val locator = ui.locator.value
        val progress = (locator?.locations?.totalProgression ?: 0.0).toFloat().coerceIn(0f, 1f)
        // Waehrend des Ziehens den Regler lokal fuehren, erst beim Loslassen springen.
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(progress) }
        val shown = if (dragging) dragValue else progress

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                locator?.title?.takeIf { it.isNotBlank() }?.let { chapter ->
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = shown,
                        onValueChange = {
                            dragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            onSeek(dragValue.toDouble())
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.reader_progress_percent, (shown * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.widthIn(min = 44.dp),
                    )
                }
            }
        }
    }
}

private data class TocEntry(val link: Link, val depth: Int, val label: String)

@Composable
private fun TableOfContents(
    tableOfContents: List<Link>,
    readingOrder: List<Link>,
    onLinkSelected: (Link) -> Unit,
) {
    // Ohne Argumente liefert stringResource den Rohtext "Kapitel %1$d".
    val chapterFormat = stringResource(R.string.reader_chapter_fallback)
    val entries = remember(tableOfContents, readingOrder, chapterFormat) {
        fun flatten(links: List<Link>, depth: Int): List<TocEntry> = links.flatMap { link ->
            listOf(TocEntry(link, depth, link.title.orEmpty())) + flatten(link.children, depth + 1)
        }
        val fromToc = flatten(tableOfContents, 0).filter { it.label.isNotBlank() }
        // Manche EPUBs haben kein Inhaltsverzeichnis → Kapitel der Lesereihenfolge anzeigen.
        fromToc.ifEmpty {
            readingOrder.mapIndexed { index, link ->
                TocEntry(link, 0, link.title?.takeIf { it.isNotBlank() } ?: String.format(chapterFormat, index + 1))
            }
        }
    }

    Column(Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.reader_toc),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries) { entry ->
                Text(
                    text = entry.label,
                    style = if (entry.depth == 0) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLinkSelected(entry.link) }
                        .padding(start = (24 + entry.depth * 20).dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplaySettings(
    settings: AppSettings,
    onThemeSelected: (AppTheme) -> Unit,
    onFontScaleSelected: (Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.reader_display), style = MaterialTheme.typography.titleLarge)

        Text(stringResource(R.string.reader_theme), style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            val options = listOf(
                AppTheme.SYSTEM to R.string.reader_theme_system,
                AppTheme.LIGHT to R.string.reader_theme_light,
                AppTheme.DARK to R.string.reader_theme_dark,
                AppTheme.SEPIA to R.string.reader_theme_sepia,
            )
            options.forEach { (theme, label) ->
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { onThemeSelected(theme) },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        Text(stringResource(R.string.reader_font_size), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onFontScaleSelected(settings.fontScale - AppSettings.FONT_SCALE_STEP) }) {
                Icon(painterResource(R.drawable.reader_ic_remove), stringResource(R.string.reader_font_smaller))
            }
            Slider(
                value = settings.fontScale.toFloat(),
                onValueChange = { onFontScaleSelected(it.toDouble()) },
                valueRange = AppSettings.FONT_SCALE_MIN.toFloat()..AppSettings.FONT_SCALE_MAX.toFloat(),
                steps = ((AppSettings.FONT_SCALE_MAX - AppSettings.FONT_SCALE_MIN) / AppSettings.FONT_SCALE_STEP).roundToInt() - 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onFontScaleSelected(settings.fontScale + AppSettings.FONT_SCALE_STEP) }) {
                Icon(painterResource(R.drawable.reader_ic_add), stringResource(R.string.reader_font_bigger))
            }
            Text(
                text = stringResource(R.string.reader_progress_percent, (settings.fontScale * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(min = 52.dp),
            )
        }
    }
}
