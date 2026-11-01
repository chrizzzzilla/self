package com.chrizzzzilla.self.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
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
 * Eingebauter Browser fuer die Buch-Quellen. Anmeldungen bleiben in den Cookies des Browsers
 * gespeichert (Self speichert keine Passwoerter). Startet die Seite einen Download, meldet er ihn
 * ueber [onDownload] – Self laedt die Datei dann selbst und nimmt sie in die Bibliothek auf.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun StoreBrowser(
    source: BookSource,
    onClose: () -> Unit,
    onDownload: (StoreDownload) -> Unit,
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("") }
    var webKey by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

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
            IconButton(onClick = { web?.url?.let { openExternal(context, it) } }) {
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
            Text(
                it,
                style = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = AmbientColors.Gold),
                modifier = Modifier.fillMaxWidth().background(Color(0xFF15161C)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        androidx.compose.runtime.key(webKey) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    @Suppress("DEPRECATION")
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = SecureUpgradeClient {
                        notice = "Die Seite ist abgestürzt und wurde neu geladen. Bücher mit Kopierschutz kann Self nicht übernehmen – lies sie hier im Webreader."
                        web = null
                        webKey++
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        override fun onReceivedTitle(view: WebView?, title: String?) { pageTitle = title.orEmpty() }
                    }
                    val main = Handler(Looper.getMainLooper())
                    // blob:-Downloads (Webreader) holt eine kleine Bruecke als Base64 aus der Seite
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onBlob(base64: String, mime: String?, name: String?) {
                            val file = runCatching {
                                File(ctx.cacheDir, "blob-${System.currentTimeMillis()}").apply {
                                    writeBytes(Base64.decode(base64, Base64.DEFAULT))
                                }
                            }.getOrNull() ?: return
                            main.post { onDownload(StoreDownload("blob:", settings.userAgentString, null, mime, file, name)) }
                        }

                        @JavascriptInterface
                        fun onTooBig() {
                            main.post { notice = "Diese Datei ist zu groß, um sie aus dem Webreader zu übernehmen." }
                        }
                    }, "SelfBridge")
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        when {
                            url.startsWith("blob:") -> evaluateJavascript(blobFetchJs(url, mimeType, URLUtil.guessFileName(url, contentDisposition, mimeType)), null)
                            url.startsWith("data:") -> {
                                val file = runCatching {
                                    File(ctx.cacheDir, "data-${System.currentTimeMillis()}").apply {
                                        writeBytes(Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT))
                                    }
                                }.getOrNull()
                                if (file != null) main.post { onDownload(StoreDownload(url.take(40), userAgent, contentDisposition, mimeType, file)) }
                            }
                            // nicht im Rueckruf des Browsers selbst weitermachen (er wird danach geschlossen)
                            else -> main.post { onDownload(StoreDownload(url, userAgent, contentDisposition, mimeType)) }
                        }
                    }
                    loadUrl(source.url)
                    web = this
                }
            },
            onRelease = { w ->
                // erst nach dem aktuellen Rueckruf zerstoeren – sonst kann der Browser abstuerzen
                w.setDownloadListener(null)
                w.stopLoading()
                Handler(Looper.getMainLooper()).post { runCatching { w.destroy() } }
            },
        )
        }
    }
}

/**
 * Manche Shops (z. B. der Tolino-Webreader) leiten zwischendurch auf http:// um. Android blockiert
 * unverschluesselte Verbindungen (ERR_CLEARTEXT_NOT_PERMITTED) – statt das zu erlauben, wird der
 * Aufruf auf https:// umgeschrieben.
 */
private class SecureUpgradeClient(private val onGone: () -> Unit) : WebViewClient() {
    /** Der Seiten-Prozess ist abgestuerzt (z. B. zu wenig Speicher) → nicht die App schliessen lassen, sondern neu laden. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onGone()
        return true
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        // fremde Schemata (intent:, mailto: …) nicht im Browser laden
        if (url.scheme !in setOf("http", "https", "about", "blob", "data", "javascript")) {
            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return true
        }
        if (url.scheme == "http") {
            view.loadUrl(url.buildUpon().scheme("https").build().toString())
            return true
        }
        return false
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        // Hauptseite per http aufgerufen (z. B. durch Weiterleitung) → einmal mit https versuchen
        if (request.isForMainFrame && request.url.scheme == "http") {
            view.loadUrl(request.url.buildUpon().scheme("https").build().toString())
        }
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** JavaScript: blob-URL in der Seite lesen und als Base64 an Self geben. */
private fun blobFetchJs(url: String, mime: String?, name: String?): String {
    fun q(s: String?) = "'" + (s ?: "").replace("\\", "\\\\").replace("'", "\\'") + "'"
    return """
        (function(){
          fetch(${q(url)}).then(function(r){ return r.blob(); }).then(function(b){
            if (b.size > 60 * 1024 * 1024) { SelfBridge.onTooBig(); return; }
            var fr = new FileReader();
            fr.onloadend = function(){
              var s = String(fr.result); var i = s.indexOf(',');
              SelfBridge.onBlob(s.substring(i + 1), b.type || ${q(mime)}, ${q(name)});
            };
            fr.readAsDataURL(b);
          });
        })();
    """.trimIndent()
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
