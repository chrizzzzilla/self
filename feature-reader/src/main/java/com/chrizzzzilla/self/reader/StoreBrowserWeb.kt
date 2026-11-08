package com.chrizzzzilla.self.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Desktop-Kennung, falls eine Quelle nur in ihrer PC-Ansicht laeuft. */
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"

/**
 * Eingebauter Browser auf Basis der Android-System-WebView (Chromium). Fuer Webreader wie Tolino,
 * deren Datenbank unter Firefox/Gecko in eine Zeitueberschreitung laeuft („DB ACTION TIMED OUT“).
 * Auf aktuellen Geraeten ist die System-WebView ein aktuelles Chromium. Gleiche Schnittstelle wie
 * [StoreBrowser]: Anmeldungen bleiben per Cookies erhalten, Downloads laufen ueber [onDownload].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun StoreBrowserWeb(
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
    var notice by remember { mutableStateOf<String?>(null) }
    val download by rememberUpdatedState(onDownload)
    val prefs = remember { context.getSharedPreferences("store_browser", Context.MODE_PRIVATE) }
    var desktop by remember { mutableStateOf(prefs.getBoolean("desktop_${source.name}", false)) }

    val webView = remember {
        WebView(context).apply {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                override fun onReceivedTitle(view: WebView?, title: String?) { pageTitle = title.orEmpty() }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    if (url != null) currentUrl = url
                    canGoBack = view.canGoBack()
                }
            }
            // Downloads: http(s) direkt, blob:/data: ueber JS einlesen
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onBlob(dataUrl: String, mime: String?, name: String?) {
                    val comma = dataUrl.indexOf(',')
                    if (comma < 0) return
                    val bytes = runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull() ?: return
                    Handler(Looper.getMainLooper()).post { notice = "Lade Datei …" }
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            runCatching {
                                File(context.cacheDir, "web-${System.currentTimeMillis()}").also { it.writeBytes(bytes) }
                            }.getOrNull()
                        }
                        notice = null
                        if (file == null) notice = "Der Download hat nicht geklappt."
                        else download(StoreDownload("blob", "", null, mime, file, name))
                    }
                }
            }, "SelfDl")
            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (url.startsWith("blob:") || url.startsWith("data:")) {
                    val js = "(function(){var x=new XMLHttpRequest();x.open('GET','$url',true);x.responseType='blob';" +
                        "x.onload=function(){if(this.status==200){var r=new FileReader();r.onloadend=function(){" +
                        "SelfDl.onBlob(r.result,x.response.type,null);};r.readAsDataURL(x.response);}};x.send();})()"
                    this@apply.evaluateJavascript(js, null)
                } else {
                    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    download(StoreDownload(url, userAgent, contentDisposition, mimeType, null, name))
                }
            })
            applyUserAgent(this, desktop)
            loadUrl(source.url)
        }
    }

    // UA-Umschaltung Handy/PC
    DisposableEffect(desktop) {
        applyUserAgent(webView, desktop)
        webView.reload()
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { runCatching { webView.destroy() } } }

    fun resetSite() {
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        notice = "Webreader zurückgesetzt – das Buch noch einmal öffnen."
        webView.reload()
    }

    BackHandler { if (canGoBack) webView.goBack() else onClose() }

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
            IconButton(onClick = {
                desktop = !desktop
                prefs.edit().putBoolean("desktop_${source.name}", desktop).apply()
                notice = if (desktop) "PC-Ansicht an – die Seite lädt neu." else "Tablet-Ansicht an – die Seite lädt neu."
            }) {
                androidx.compose.foundation.Canvas(Modifier.padding(12.dp).fillMaxSize()) {
                    val col = if (desktop) AmbientColors.ArtyText else AmbientColors.Light
                    val w = size.width
                    val h = size.height
                    drawRoundRect(
                        col, topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.55f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f * density),
                    )
                    drawLine(col, androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.7f), androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.85f), strokeWidth = 1.8f * density)
                    drawLine(col, androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.87f), androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.87f), strokeWidth = 1.8f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            IconButton(onClick = { openExternalDefault(context, currentUrl) }) {
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
                    WebNoticeAction("WEBREADER ZURÜCKSETZEN") { resetSite() }
                    WebNoticeAction("IM BROWSER ÖFFNEN") { openExternalDefault(context, currentUrl) }
                    WebNoticeAction("OK") { notice = null }
                }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webView },
        )
    }
}

private fun applyUserAgent(view: WebView, desktop: Boolean) {
    view.settings.apply {
        userAgentString = if (desktop) DESKTOP_UA else null
        useWideViewPort = desktop
        loadWithOverviewMode = desktop
    }
}

/** Im Standardbrowser oeffnen (System waehlt; Chromium ist hier gewuenscht, nicht Firefox). */
private fun openExternalDefault(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun WebNoticeAction(label: String, onClick: () -> Unit) {
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
