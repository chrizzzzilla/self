package com.chrizzzzilla.self.reader

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Die Browser-Technik von Firefox (GeckoView) fuer die Shop-Ansicht. Sie bringt ihren eigenen,
 * aktuellen Browser mit und haengt nicht von der (oft veralteten) System-WebView ab.
 * Eine Laufzeit pro App – Anmeldungen und Webreader-Daten bleiben darin gespeichert.
 *
 * Kein Tracking-Schutz mit Speicher-Trennung: Self oeffnet nur die festen Buch-Quellen, und der
 * Tolino-Webreader legt seine Daten teils ueber eingebettete Seiten ab – mit getrenntem Speicher
 * („Total Cookie Protection“) laeuft die Datenbank dort in eine Zeitueberschreitung.
 */
internal object Gecko {
    @Volatile private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .contentBlocking(
                        ContentBlocking.Settings.Builder()
                            .antiTracking(ContentBlocking.AntiTracking.NONE)
                            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
                            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                            .build(),
                    )
                    .build(),
            ).also { runtime = it }
        }
}
