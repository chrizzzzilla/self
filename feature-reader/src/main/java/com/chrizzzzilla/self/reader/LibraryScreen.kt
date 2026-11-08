package com.chrizzzzilla.self.reader

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrizzzzilla.self.core.CrashLog
import com.chrizzzzilla.self.core.SelfCore
import com.chrizzzzilla.self.core.design.ArtySun
import com.chrizzzzilla.self.core.design.WidthClass
import com.chrizzzzilla.self.core.library.LibraryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bibliothek: alle bisher geoeffneten Buecher als Raster, zuletzt gelesene zuerst.
 *
 * @param incomingBook Datei aus „Öffnen mit“ (Browser/Dateimanager), wird importiert und geoeffnet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    widthClass: WidthClass,
    incomingBook: Uri?,
    onIncomingBookHandled: () -> Unit,
    modifier: Modifier = Modifier,
    /** true, solange ein Shop/eine Seite bildschirmfüllend offen ist (dann Menü ausblenden). */
    onOverlayChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by remember { SelfCore.library.observeAll() }
        .collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<LibraryItem?>(null) }

    // Fuehrt eine Aktion aus, zeigt dabei einen Ladekreis und Fehler als Snackbar.
    fun runTask(task: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val error = try {
                task()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // auch schwere Fehler (z. B. zu wenig Speicher) nur melden, nicht abstuerzen
                android.util.Log.e("Self", "Aufgabe fehlgeschlagen", e)
                CrashLog.write(context, e)
                e
            }
            busy = false
            if (error != null) {
                val res = (error as? ReaderException)?.messageRes ?: R.string.reader_error_generic
                snackbar.showSnackbar(context.getString(res))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Dauerhafte Leseberechtigung sichern (nicht jeder Anbieter erlaubt das → Fehler ignorieren).
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runTask { Reader.importAndOpen(context, uri) }
        }
    }

    LaunchedEffect(incomingBook) {
        val uri = incomingBook ?: return@LaunchedEffect
        onIncomingBookHandled()
        runTask { Reader.importAndOpen(context, uri) }
    }

    val openFiles = {
        picker.launch(
            arrayOf(
                "application/epub+zip", "application/vnd.comicbook+zip", "application/x-cbz",
                "application/zip", "application/octet-stream",
            ),
        )
    }
    var chooser by remember { mutableStateOf(false) }
    val openPicker = { chooser = true }
    var store by remember { mutableStateOf<BookSource?>(null) }
    var libraryOpen by remember { mutableStateOf(false) }
    LaunchedEffect(store, libraryOpen, chooser) { onOverlayChanged(store != null || libraryOpen || chooser) }

    val sunDiameter = if (widthClass == WidthClass.COMPACT) 380.dp else 560.dp

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            // Arty als aufgehende Sonne am unteren Rand (antippen öffnet ebenfalls die Bibliothek)
            ArtySun(
                onClick = { libraryOpen = true },
                diameter = sunDiameter,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // Zuletzt gelesenes Buch oben mittig: antippen = direkt weiterlesen
            val current = items.orEmpty().firstOrNull()
            if (current != null && !libraryOpen) {
                ContinueReading(
                    item = current,
                    compact = widthClass == WidthClass.COMPACT,
                    onOpen = { runTask { Reader.open(context, current) } },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (widthClass == WidthClass.COMPACT) 36.dp else 56.dp),
                )
            }

            // Bibliotheks-Knopf unten rechts (Gegenstück zum Raster-Menü links)
            if (!libraryOpen && store == null && !chooser) {
                LibraryButton(
                    size = if (widthClass == WidthClass.COMPACT) 68.dp else 84.dp,
                    onClick = { libraryOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(34.dp),
                )
            }

            if (libraryOpen) {
                LibraryPanel(
                    items = items.orEmpty(),
                    widthClass = widthClass,
                    onOpen = { item -> runTask { Reader.open(context, item) } },
                    onRemove = { item -> toRemove = item },
                    onAdd = openPicker,
                    onClose = { libraryOpen = false },
                )
            }

            if (busy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))

        if (chooser) {
            SourceChooser(
                compact = widthClass == WidthClass.COMPACT,
                onSource = {
                    chooser = false
                    store = it
                },
                onFiles = {
                    chooser = false
                    openFiles()
                },
                onDismiss = { chooser = false },
            )
        }

        // Eingebauter Browser: Download eines EPUBs → direkt in die Bibliothek und öffnen.
        // Tolino laeuft ueber die Chromium-WebView (Gecko bricht dort mit „DB ACTION TIMED OUT“ ab),
        // alle anderen Quellen bleiben bei GeckoView.
        store?.let { src ->
            val onDl: (StoreDownload) -> Unit = { d ->
                when {
                    d.isAdobeDrm -> scope.launch { snackbar.showSnackbar(context.getString(R.string.reader_error_adobe_drm)) }
                    !d.isBook && d.localFile == null && d.mimeType?.contains("octet-stream") != true ->
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.reader_error_not_epub_download)) }
                    else -> {
                        store = null
                        runTask {
                            val file = downloadToCache(context, d)
                            try {
                                Reader.importAndOpen(context, Uri.fromFile(file))
                            } finally {
                                file.delete()
                            }
                        }
                    }
                }
            }
            if (src == BookSource.TOLINO) {
                StoreBrowserWeb(source = src, onClose = { store = null }, onDownload = onDl)
            } else {
                StoreBrowser(source = src, onClose = { store = null }, onDownload = onDl)
            }
        }
    }

    toRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.reader_remove_title)) },
            text = { Text(stringResource(R.string.reader_remove_text, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    toRemove = null
                    scope.launch { SelfCore.library.remove(item) }
                }) { Text(stringResource(R.string.reader_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.reader_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookCard(item: LibraryItem, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val cover by produceState<ImageBitmap?>(initialValue = null, item.coverPath) {
        value = item.coverPath?.let { path ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = cover
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        LinearProgressIndicator(
            progress = { item.progress.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        item.creator?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

