package com.chrizzzzilla.self

import android.app.Application
import com.chrizzzzilla.self.core.SelfCore
import com.chrizzzzilla.self.focus.FocusPlayer

class SelfApp : Application() {
    private fun isMainProcess(): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName()
        else runCatching { java.io.File("/proc/self/cmdline").readText().trim('\u0000', ' ') }.getOrDefault(packageName)
        return name == packageName
    }

    override fun onCreate() {
        super.onCreate()
        // GeckoView startet eigene Prozesse (Seiten) – dort nichts von Self initialisieren
        if (!isMainProcess()) return
        com.chrizzzzilla.self.core.CrashLog.install(this)
        SelfCore.init(this)
        // Fokus-Musik anmelden → der Musik-Knopf im Reader steuert sie mit.
        FocusPlayer.init(this)
        SelfCore.focusAudio = FocusPlayer
    }
}
