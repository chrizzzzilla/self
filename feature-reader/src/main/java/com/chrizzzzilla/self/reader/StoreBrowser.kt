package com.chrizzzzilla.self.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.RenderProcessGoneDetail
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Quellen fuer DRM-freie EPUBs (alle oeffnen sich im eingebauten Browser). */
internal enum class BookSource(
    @StringRes val title: Int,
    @StringRes val hint: Int,
    @DrawableRes val icon: Int,
    val url: String,
) {
    /** Gekaufte Tolino-/Orell-Füssli-Bücher (auch mit Kopierschutz) direkt im Webreader lesen. */
    TOLINO(R.string.reader_source_tolino, R.string.reader_source_tolino_hint, R.drawable.reader_ic_book, "https://webreader.mytolino.com/"),
    BEAM(R.string.reader_source_beam, R.string.reader_source_beam_hint, R.drawable.reader_ic_globe, "https://www.beam-shop.de/"),
    LIBRETURE(R.string.reader_source_libreture, R.string.reader_source_libreture_hint, R.drawable.reader_ic_globe, "https://libreture.com/"),
    GUTENBERG(R.string.reader_source_gutenberg, R.string.reader_source_gutenberg_hint, R.drawable.reader_ic_book, "https://www.gutenberg.org/browse/languages/de"),
    STANDARD_EBOOKS(R.string.reader_source_standard, R.string.reader_source_standard_hint, R.drawable.reader_ic_book, "https://standardebooks.org/ebooks"),
    JNOVEL(R.string.reader_source_jnovel, R.string.reader_source_jnovel_hint, R.drawable.reader_ic_globe, "https://j-novel.club/"),
    HUMBLE(R.string.reader_source_humble, R.string.reader_source_humble_hint, R.drawable.reader_ic_globe, "https://www.humblebundle.com/books"),
}

/** Was eine Download-Anfrage aus dem eingebauten Browser enthaelt. */
internal class StoreDownload(
    val url: String,
    val userAgent: String,
    val contentDisposition: String?,
    val mimeType: String?,
    /** schon im Browser geladene Datei (blob:/data:-Downloads der Webreader) */
    val localFile: File? = null,
    givenName: String? = null,
) {
    val fileName: String = givenName?.takeIf { it.isNotBlank() } ?: URLUtil.guessFileName(url, contentDisposition, mimeType)
    val isAdobeDrm: Boolean =
        fileName.endsWith(".acsm", ignoreCase = true) || mimeType?.contains("adept", ignoreCase = true) == true
    /** EPUB oder Comic-Archiv (CBZ) */
    val isBook: Boolean =
        fileName.contains(".epub", ignoreCase = true) || mimeType?.contains("epub", ignoreCase = true) == true ||
            fileName.endsWith(".cbz", ignoreCase = true) || mimeType?.contains("comicbook", ignoreCase = true) == true ||
            mimeType?.contains("cbz", ignoreCase = true) == true
}

/**
 * Eingebauter Browser fuer die Buch-Quellen – mit der Firefox-Technik (GeckoView), damit Webreader
 * wie der von Tolino laufen, auch wenn die System-WebView des Geraets veraltet ist.
 * Anmeldungen bleiben im Browser gespeichert (Self speichert keine Passwoerter). Startet die Seite
 * einen Download, nimmt Self die Datei direkt entgegen und ueber [onDownload] in die Bibliothek auf.
 */
@Composable
internal fun StoreBrowser(
    source: BookSource,
    onClose: () -> Unit,
    onDownload: (StoreDownload) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(source.url) }
    var canGoBack by remember { mutableStateOf(false) }
    var sessionKey by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val runtime = remember { Gecko.runtime(context) }
    val download by rememberUpdatedState(onDownload)
    val setProgress: (Int) -> Unit = { progress = it }
    val setCanGoBack: (Boolean) -> Unit = { canGoBack = it }

    val session = remember(sessionKey) {
        GeckoSession().apply {
            setContentDelegate(object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) { pageTitle = title.orEmpty() }

                override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                    val headers = response.headers
                    val cd = headers.entries.firstOrNull { it.key.equals("Content-Disposition", ignoreCase = true) }?.value
                    val mime = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
                    val name = URLUtil.guessFileName(response.uri, cd, mime)
                    val probe = StoreDownload(response.uri, "", cd, mime, null, name)
                    val body = response.body
                    if (probe.isAdobeDrm || body == null) {
                        runCatching { body?.close() }
                        download(probe)
                        return
                    }
                    notice = "Lade „$name“ …"
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            runCatching {
                                File(context.cacheDir, "gecko-${System.currentTimeMillis()}").also { f ->
                                    body.use { input -> f.outputStream().use { input.copyTo(it) } }
                                }
                            }.getOrNull()
                        }
                        notice = null
                        if (file == null) notice = "Der Download hat nicht geklappt." else download(StoreDownload(response.uri, "", cd, mime, file, name))
                    }
                }

                override fun onCrash(session: GeckoSession) {
                    notice = "Die Seite ist abgestürzt und wurde neu geladen."
                    sessionKey++
                }

                override fun onKill(session: GeckoSession) {
                    notice = "Die Seite wurde beendet und neu geladen."
                    sessionKey++
                }
            })
            setProgressDelegate(object : GeckoSession.ProgressDelegate {
                override fun onProgressChange(session: GeckoSession, progress: Int) { setProgress(progress) }
            })
            setNavigationDelegate(object : GeckoSession.NavigationDelegate {
                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean,
                ) { if (url != null) currentUrl = url }

                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { setCanGoBack(canGoBack) }

                // Links mit neuem Fenster (target=_blank) im selben Browser oeffnen
                override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                    session.loadUri(uri)
                    return null
                }
            })
            open(runtime)
            loadUri(currentUrl)
        }
    }
    DisposableEffect(session) { onDispose { runCatching { session.close() } } }

    fun resetSite() {
        val host = Uri.parse(source.url).host ?: return
        runtime.storageController.clearDataFromHost(
            host,
            StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.ALL_CACHES,
        )
        notice = "Webreader zurückgesetzt – das Buch noch einmal öffnen."
        sessionKey++
    }

    BackHandler { if (canGoBack) session.goBack() else onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AmbientColors.Ground),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    stringResource(source.title).uppercase(),
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 2.5.sp, color = AmbientColors.Light),
                )
                if (pageTitle.isNotBlank()) {
                    Text(
                        pageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Manrope, fontSize = 11.sp, color = AmbientColors.Muted),
                    )
                }
            }
            // Speicher der Seite leeren (z. B. bei „Browser Storage“-Fehlern im Webreader) – Anmeldung bleibt
            IconButton(onClick = { resetSite() }) {
                androidx.compose.foundation.Canvas(Modifier.padding(12.dp).fillMaxSize()) {
                    val r = size.minDimension * 0.38f
                    drawArc(
                        AmbientColors.Light, 40f, 290f, false,
                        topLeft = center - androidx.compose.ui.geometry.Offset(r, r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                    val tip = center + androidx.compose.ui.geometry.Offset(r * 0.77f, r * 0.64f)
                    drawLine(AmbientColors.Light, tip, tip + androidx.compose.ui.geometry.Offset(0f, -r * 0.6f), strokeWidth = 1.8f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(AmbientColors.Light, tip, tip + androidx.compose.ui.geometry.Offset(-r * 0.6f, 0f), strokeWidth = 1.8f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            IconButton(onClick = { openExternal(context, currentUrl) }) {
                Icon(painterResource(R.drawable.reader_ic_globe), stringResource(R.string.reader_open_in_browser), tint = AmbientColors.Light)
            }
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.reader_ic_back), stringResource(R.string.reader_back), tint = AmbientColors.Light)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = AmbientColors.Arty,
                    trackColor = Color.Transparent,
                )
            }
        }
        notice?.let {
            Column(Modifier.fillMaxWidth().background(Color(0xFF15161C)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(it, style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = AmbientColors.Gold))
                Row(Modifier.padding(top = 8.dp)) {
                    NoticeAction("WEBREADER ZURÜCKSETZEN") { resetSite() }
                    NoticeAction("IM BROWSER ÖFFNEN") { openExternal(context, currentUrl) }
                    NoticeAction("OK") { notice = null }
                }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> GeckoView(ctx) },
            update = { v ->
                if (v.tag !== session) {
                    v.releaseSession()
                    v.setSession(session)
                    v.tag = session
                }
            },
            onRelease = { v -> runCatching { v.releaseSession() } },
        )
    }
}

@Composable
private fun NoticeAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 2.sp, color = AmbientColors.Light),
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Im externen Browser oeffnen – bevorzugt Firefox (der Tolino-Webreader laeuft dort, in Chrome-Varianten oft nicht). */
private fun openExternal(context: Context, url: String) {
    val uri = Uri.parse(url)
    val firefox = listOf("org.mozilla.firefox", "org.mozilla.fenix", "org.mozilla.firefox_beta", "org.mozilla.fennec_fdroid", "org.mozilla.focus")
    for (pkg in firefox) {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Laedt eine Datei aus dem eingebauten Browser (mit dessen Anmelde-Cookies) in den Cache. */
internal suspend fun downloadToCache(context: Context, d: StoreDownload): File = withContext(Dispatchers.IO) {
    d.localFile?.let { f ->
        val ext = if (d.fileName.endsWith(".cbz", ignoreCase = true) || d.mimeType?.contains("comic") == true) "cbz" else "epub"
        val named = File(f.parentFile, f.name + "." + ext)
        return@withContext if (f.renameTo(named)) named else f
    }
    var url = URL(d.url)
    var conn: HttpURLConnection? = null
    var redirects = 0
    while (true) {
        val c = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", d.userAgent)
            CookieManager.getInstance().getCookie(url.toString())?.let { setRequestProperty("Cookie", it) }
        }
        conn = c
        val code = c.responseCode
        if (code in 300..399 && redirects < 6) {
            val location = c.getHeaderField("Location") ?: break
            url = URL(url, location)
            redirects++
            c.disconnect()
            continue
        }
        if (code !in 200..299) throw ReaderException(R.string.reader_error_download)
        break
    }
    val connection = conn ?: throw ReaderException(R.string.reader_error_download)
    val ext = if (d.fileName.endsWith(".cbz", ignoreCase = true)) "cbz" else "epub"
    val file = File(context.cacheDir, "download-${System.currentTimeMillis()}.$ext")
    try {
        connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
    } catch (e: Exception) {
        file.delete()
        throw ReaderException(R.string.reader_error_download, e)
    } finally {
        connection.disconnect()
    }
    file
}
