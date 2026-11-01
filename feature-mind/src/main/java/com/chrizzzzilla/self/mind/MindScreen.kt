package com.chrizzzzilla.self.mind

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.ArtySun
import com.chrizzzzilla.self.core.design.Manrope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

internal val mHead = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 6.sp, color = AmbientColors.Light)
internal val mText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 16.sp, color = AmbientColors.Light)

private val THANKS = listOf("Wofür bist du heute dankbar?", "Was hat dich heute gefreut?", "Worauf bist du heute stolz?", "Wer hat dir heute gutgetan?")
private val HARD = listOf("Was war heute schwer?", "Was möchtest du heute loslassen?", "Was würdest du morgen anders machen?", "Was hat dich heute Kraft gekostet?")
private val KEEP = listOf("Was hast du heute gelernt?", "Was nimmst du mit in den Schlaf?", "Welcher Moment war heute ganz still?", "Was wünschst du dir für morgen?")

/** Drei Fragen fuer heute – jeden Tag etwas anders. Hatte man morgens ein Wort gewaehlt, geht es zuerst darum. */
internal fun questionsFor(date: LocalDate, word: String?): List<String> {
    val r = Random(date.toEpochDay())
    val q = mutableListOf(THANKS.random(r), HARD.random(r), KEEP.random(r))
    if (word != null) q[0] = "Dein Wort heute war „$word“. Wie war es damit?"
    return q
}

/**
 * Mind: am Abend fragt Arty drei ruhige Dinge. Jeder Abend wird ein Stern am Himmel;
 * Sterne antippen zeigt, was man an dem Tag erzaehlt hat.
 */
@Composable
fun MindScreen(modifier: Modifier = Modifier, onOverlayChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { MindStore(context) }
    val entries by store.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val morning by store.morning.collectAsStateWithLifecycle(initialValue = MorningState("", 0, "", ""))
    val scope = rememberCoroutineScope()
    var writing by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<MindEntry?>(null) }
    LaunchedEffect(writing, shown) { onOverlayChanged(writing || shown != null) }
    val today = LocalDate.now()
    val doneToday = entries.any { it.date == today.toString() }
    val evening = LocalTime.now().hour >= 17

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        StarSky(entries, Modifier.fillMaxSize()) { shown = it }
        ArtySun(
            onClick = { if (!doneToday) writing = true },
            diameter = if (compact) 380.dp else 560.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (!writing) {
            Column(
                Modifier.align(Alignment.Center).padding(bottom = 60.dp).widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("MIND", style = mHead.copy(color = AmbientColors.ArtyText))
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        doneToday -> "Heute hast du schon erzählt.\nDein Stern leuchtet."
                        evening -> "Der Tag geht zu Ende.\nMagst du mir drei Dinge erzählen?"
                        else -> "Am Abend frage ich dich drei Dinge.\nDu kannst aber auch jetzt schon erzählen."
                    },
                    textAlign = TextAlign.Center,
                    style = mText.copy(fontSize = 20.sp, lineHeight = 30.sp),
                )
                Spacer(Modifier.height(24.dp))
                if (!doneToday) MindButton("ERZÄHLEN") { writing = true }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (entries.isEmpty()) "Jeder Abend wird ein Stern." else "${entries.size} Sterne · antippen zum Erinnern",
                    style = mText.copy(fontSize = 12.sp, color = AmbientColors.Muted),
                )
            }
        } else {
            QuestionFlow(
                questions = remember(today) { questionsFor(today, morning.todayWord) },
                modifier = Modifier.align(Alignment.Center),
                onCancel = { writing = false },
                onDone = { qa ->
                    scope.launch { store.addEntry(MindEntry(today.toString(), qa)) }
                    writing = false
                },
            )
        }
        shown?.let { e -> EntryCard(e, Modifier.align(Alignment.Center)) { shown = null } }
    }
}

@Composable
internal fun MindButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF1E2A55))
            .border(1.dp, AmbientColors.Arty, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 13.dp),
    ) {
        Text(label, style = mHead.copy(fontSize = 12.sp, letterSpacing = 4.sp))
    }
}

@Composable
internal fun MindGhostButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
    ) {
        Text(label, style = mHead.copy(fontSize = 11.sp, letterSpacing = 3.sp, color = AmbientColors.Muted))
    }
}

@Composable
private fun QuestionFlow(questions: List<String>, modifier: Modifier, onCancel: () -> Unit, onDone: (List<Pair<String, String>>) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf("", "", "") }
    val focus = remember { FocusRequester() }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) { if (hint != null) { kotlinx.coroutines.delay(3500); hint = null } }
    LaunchedEffect(index) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier
            .imePadding()
            .padding(24.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xEE0E0F14))
            .border(1.dp, AmbientColors.Arty.copy(alpha = 0.4f), shape)
            .padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FRAGE ${index + 1} VON 3", style = mHead.copy(fontSize = 10.sp, color = AmbientColors.ArtyText))
            Spacer(Modifier.weight(1f))
            Text("schließen", style = mText.copy(fontSize = 12.sp, color = AmbientColors.Muted), modifier = Modifier.clickable(onClick = onCancel))
        }
        Spacer(Modifier.height(16.dp))
        Text(questions[index], style = mText.copy(fontSize = 22.sp, lineHeight = 30.sp))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = answers[index],
            onValueChange = { answers[index] = it },
            textStyle = mText.copy(fontSize = 17.sp, lineHeight = 26.sp),
            cursorBrush = SolidColor(AmbientColors.ArtyText),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x14FFFFFF))
                .padding(start = 16.dp, top = 16.dp, end = 64.dp, bottom = 16.dp)
                .focusRequester(focus),
            decorationBox = { inner ->
                if (answers[index].isEmpty()) Text("Schreib oder erzähl einfach (Mikrofon) …", style = mText.copy(fontSize = 15.sp, color = AmbientColors.Muted))
                inner()
            },
        )
        DictationButton(
            current = { answers[index] },
            onText = { answers[index] = it },
            onMessage = { hint = it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        )
        }
        hint?.let { Text(it, style = mText.copy(fontSize = 12.sp, color = AmbientColors.Gold), modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (k in 0 until 3) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(if (k == index) 18.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (k <= index) AmbientColors.ArtyText else Color(0x33FFFFFF)),
                )
            }
            Spacer(Modifier.weight(1f))
            if (index > 0) { MindGhostButton("ZURÜCK") { index-- }; Spacer(Modifier.size(10.dp)) }
            MindButton(if (index < 2) "WEITER" else "FERTIG") {
                if (index < 2) index++ else onDone(questions.zip(answers.toList()).filter { it.second.isNotBlank() })
            }
        }
    }
}

@Composable
private fun EntryCard(e: MindEntry, modifier: Modifier, onClose: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    val date = runCatching {
        LocalDate.parse(e.date).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
    }.getOrDefault(e.date)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier
                .padding(24.dp)
                .widthIn(max = 640.dp)
                .clip(shape)
                .background(Color(0xF00E0F14))
                .border(1.dp, AmbientColors.Gold.copy(alpha = 0.4f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(date.uppercase(Locale.GERMAN), style = mHead.copy(fontSize = 10.sp, color = AmbientColors.Gold))
            if (e.qa.isEmpty()) Text("Ein stiller Abend.", style = mText)
            e.qa.forEach { (q, a) ->
                Column {
                    Text(q, style = mText.copy(fontSize = 13.sp, color = AmbientColors.Muted))
                    Spacer(Modifier.height(4.dp))
                    Text(a, style = mText.copy(fontSize = 17.sp, lineHeight = 26.sp))
                }
            }
        }
    }
}

/** Jeder Abend ein Stern – verbunden wie ein Sternbild, der neueste pulsiert. */
@Composable
private fun StarSky(entries: List<MindEntry>, modifier: Modifier, onPick: (MindEntry) -> Unit) {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (it - start) / 1e9f }
    }
    val sorted = entries.sortedBy { it.date }
    fun pos(e: MindEntry, w: Float, h: Float): Offset {
        val r = Random(e.date.hashCode())
        return Offset(w * (0.06f + r.nextFloat() * 0.88f), h * (0.06f + r.nextFloat() * 0.5f))
    }
    Canvas(
        modifier.pointerInput(sorted) {
            detectTapGestures { p ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                sorted.minByOrNull { (pos(it, w, h) - p).getDistance() }?.let {
                    if ((pos(it, w, h) - p).getDistance() < 48.dp.toPx()) onPick(it)
                }
            }
        },
    ) {
        val w = size.width
        val h = size.height
        val pts = sorted.map { pos(it, w, h) }
        for (i in 1 until pts.size) {
            drawLine(AmbientColors.Gold.copy(alpha = 0.12f), pts[i - 1], pts[i], strokeWidth = 1f * density, blendMode = BlendMode.Plus)
        }
        pts.forEachIndexed { i, p ->
            val e = sorted[i]
            val words = e.qa.sumOf { it.second.length }
            val bright = (0.45f + words / 400f).coerceAtMost(1f)
            val newest = i == pts.lastIndex
            val tw = 0.8f + 0.2f * sin(t.floatValue * (if (newest) 2.4f else 0.9f) + i)
            val r = (if (newest) 34f else 22f) * density * tw
            drawCircle(
                Brush.radialGradient(listOf(AmbientColors.Gold.copy(alpha = 0.5f * bright), Color.Transparent), center = p, radius = r),
                radius = r, center = p, blendMode = BlendMode.Plus,
            )
            drawCircle(Color(0xFFFFF4DC).copy(alpha = bright), radius = 2.6f * density, center = p)
        }
    }
}

@Suppress("unused")
private fun keepMindImports(a: androidx.compose.ui.unit.Dp) = min(a, a)
