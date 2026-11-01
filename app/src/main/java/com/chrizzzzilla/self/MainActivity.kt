package com.chrizzzzilla.self

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.chrizzzzilla.self.core.design.AmbientTheme
import com.chrizzzzilla.self.core.design.enterImmersive

class MainActivity : ComponentActivity() {

    /** Buch aus „Öffnen mit“, das noch importiert werden muss. */
    private val incomingBook = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            AmbientTheme {
                SelfAppShell(
                    incomingBook = incomingBook.value,
                    onIncomingBookHandled = { incomingBook.value = null },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // nach Dialogen/Dateiauswahl wieder Vollbild
        if (hasFocus) enterImmersive()
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
