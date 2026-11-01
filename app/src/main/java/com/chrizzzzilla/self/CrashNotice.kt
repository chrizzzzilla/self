package com.chrizzzzilla.self

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrizzzzilla.self.core.CrashLog
import com.chrizzzzilla.self.core.design.AmbientColors
import com.chrizzzzilla.self.core.design.Manrope

/** Nach einem Absturz: Protokoll zeigen, kopieren (z. B. an Claude schicken), dann verwerfen. */
@Composable
fun CrashNotice() {
    val context = LocalContext.current
    var text by remember { mutableStateOf(CrashLog.read(context)) }
    var copied by remember { mutableStateOf(false) }
    val t = text ?: return
    val close = { CrashLog.clear(context); text = null }
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(32.dp)
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF00E0F14))
                .border(1.dp, AmbientColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .padding(26.dp),
        ) {
            Text("SELF IST ABGESTÜRZT", style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 5.sp, color = AmbientColors.Gold))
            Spacer(Modifier.height(10.dp))
            Text(
                "Das Protokoll bleibt nur auf dem Gerät. Kopier es und schick es Claude – dann lässt sich der Fehler beheben.",
                style = TextStyle(fontFamily = Manrope, fontSize = 14.sp, color = AmbientColors.Light),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                t,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = AmbientColors.Muted),
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NoticeButton(if (copied) "KOPIERT" else "KOPIEREN", true) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Self Absturz", t))
                    copied = true
                }
                NoticeButton("SCHLIESSEN", false) { close() }
            }
        }
    }
}

@Composable
private fun NoticeButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) Color(0xFF1E2A55) else Color.Transparent)
            .border(1.dp, if (primary) AmbientColors.Arty else Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(label, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 3.sp, color = AmbientColors.Light))
    }
}
