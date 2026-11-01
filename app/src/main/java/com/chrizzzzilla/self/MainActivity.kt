package com.chrizzzzilla.self

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrizzzzilla.self.core.SelfCore
import com.chrizzzzilla.self.core.design.ResolvedTheme
import com.chrizzzzilla.self.core.design.SelfTheme
import com.chrizzzzilla.self.core.design.resolve
import com.chrizzzzilla.self.core.settings.AppSettings

class MainActivity : ComponentActivity() {

    /** Buch aus „Öffnen mit“, das noch importiert werden muss. */
    private val incomingBook = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by SelfCore.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val resolved = settings.theme.resolve()

            // Statusleisten-Symbole passend zum Farbmodus (hell/dunkel) einfaerben.
            LaunchedEffect(resolved) {
                val style = if (resolved == ResolvedTheme.DARK) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            SelfTheme(settings.theme) {
                SelfAppShell(
                    incomingBook = incomingBook.value,
                    onIncomingBookHandled = { incomingBook.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { incomingBook.value = it }
        }
    }
}
