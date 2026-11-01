package com.chrizzzzilla.self

import android.app.Application
import com.chrizzzzilla.self.core.SelfCore
import com.chrizzzzilla.self.focus.FocusPlayer

class SelfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.chrizzzzilla.self.core.CrashLog.install(this)
        SelfCore.init(this)
        // Fokus-Musik anmelden → der Musik-Knopf im Reader steuert sie mit.
        FocusPlayer.init(this)
        SelfCore.focusAudio = FocusPlayer
    }
}
