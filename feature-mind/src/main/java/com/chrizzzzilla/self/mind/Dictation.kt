package com.chrizzzzilla.self.mind

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chrizzzzilla.self.core.design.AmbientColors
import kotlin.math.sin

/**
 * Erzaehlen statt tippen: Mikrofon-Knopf fuer die Antworten in Mind.
 * Bevorzugt die Spracherkennung direkt auf dem Geraet (Android 12+), sonst die des Systems.
 * Der erkannte Text wird an das bisher Geschriebene angehaengt.
 */
@Composable
internal fun DictationButton(
    current: () -> String,
    onText: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var onDevice by remember { mutableStateOf(false) }
    val text by rememberUpdatedState(onText)
    val msg by rememberUpdatedState(onMessage)
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    fun create(preferDevice: Boolean): SpeechRecognizer? {
        recognizer?.destroy()
        val r = when {
            preferDevice && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> {
                onDevice = true
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }
            SpeechRecognizer.isRecognitionAvailable(context) -> {
                onDevice = false
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            else -> null
        }
        recognizer = r
        return r
    }

    // Rueckfall: der Sprach-Dialog des Systems (kann Deutsch fast immer)
    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        listening = false
        val said = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (said != null) {
            val base = current().let { if (it.isBlank()) "" else it.trimEnd() + " " }
            text(base + said)
        }
    }
    fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "de-DE")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Erzähl einfach …")
    }
    fun openDialog() {
        runCatching { dialog.launch(speechIntent()) }.onFailure { msg("Auf diesem Gerät gibt es keine deutsche Spracherkennung.") }
    }

    fun start(preferDevice: Boolean = true) {
        val r = (if (recognizer == null || (preferDevice != onDevice && !preferDevice)) create(preferDevice) else recognizer)
            ?: run { openDialog(); return }
        val base = current().let { if (it.isBlank()) "" else it.trimEnd() + " " }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text(base + it) }
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text(base + it) }
                listening = false
            }
            override fun onError(error: Int) {
                listening = false
                when (error) {
                    // Sprachpaket fehlt auf dem Geraet → einmal mit der System-Erkennung versuchen
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        // Deutsches Sprachpaket fuer die Erkennung auf dem Geraet nachladen (Android 13+) …
                        if (onDevice && Build.VERSION.SDK_INT >= 33) {
                            runCatching { recognizer?.triggerModelDownload(speechIntent()) }
                            msg("Das deutsche Sprachpaket wird geladen – bis dahin hilft der System-Dialog.")
                        }
                        // … und jetzt gleich mit dem System-Dialog weitermachen
                        openDialog()
                    }
                    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> openDialog()
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> msg("Ich habe nichts gehört – tipp noch einmal aufs Mikrofon.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> msg("Das Mikrofon ist nicht erlaubt.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> msg("Die Spracherkennung braucht gerade Internet.")
                    else -> msg("Spracherkennung hat gerade nicht geklappt.")
                }
            }
        })
        val intent = speechIntent().apply { putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) }
        runCatching { r.startListening(intent); listening = true }.onFailure { openDialog() }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else msg("Ohne Mikrofon-Erlaubnis kann ich nicht zuhören.")
    }

    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        val s = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (it - s) / 1e9f }
    }

    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (listening) Color(0x556888EC) else Color(0x22FFFFFF))
            .border(1.dp, if (listening) AmbientColors.Arty else Color(0x33FFFFFF), CircleShape)
            .clickable {
                when {
                    listening -> { recognizer?.stopListening(); listening = false }
                    hasMic(context) -> start()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val col = if (listening) AmbientColors.Light else AmbientColors.ArtyText
            if (listening) {
                val p = 0.6f + 0.4f * sin(t.floatValue * 6f)
                drawCircle(
                    Brush.radialGradient(listOf(AmbientColors.Arty.copy(alpha = 0.6f * p), Color.Transparent), center = center, radius = w),
                    radius = w, center = center, blendMode = BlendMode.Plus,
                )
            }
            drawRoundRect(col, topLeft = Offset(w * 0.34f, h * 0.06f), size = Size(w * 0.32f, h * 0.56f), cornerRadius = CornerRadius(w * 0.16f), style = Stroke(1.6f * density))
            drawArc(col, 0f, 180f, false, topLeft = Offset(w * 0.2f, h * 0.22f), size = Size(w * 0.6f, h * 0.5f), style = Stroke(1.6f * density, cap = StrokeCap.Round))
            drawLine(col, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.92f), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
        }
    }
}

private fun hasMic(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
