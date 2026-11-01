package com.chrizzzzilla.self.reader

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

/**
 * Die Browser-Technik von Firefox (GeckoView) fuer die Shop-Ansicht. Sie bringt ihren eigenen,
 * aktuellen Browser mit und haengt nicht von der (oft veralteten) System-WebView ab.
 * Eine Laufzeit pro App – Anmeldungen und Webreader-Daten bleiben darin gespeichert.
 */
internal object Gecko {
    @Volatile private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.getDefault(context.applicationContext).also { runtime = it }
        }
}
