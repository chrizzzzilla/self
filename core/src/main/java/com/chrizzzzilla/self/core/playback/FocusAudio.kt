package com.chrizzzzilla.self.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Schnittstelle fuer die spaetere Fokus-Musik (Phase 2).
 *
 * Der Reader zeigt dafuer schon jetzt einen Musik-Knopf. Sobald `feature-focus`
 * einen Media3-`MediaSessionService` mit generativer Musik mitbringt, meldet es
 * hier seine Implementierung an – der Reader muss dafuer nicht geaendert werden.
 */
interface FocusAudio {
    /** Gibt es ueberhaupt schon Fokus-Musik? (Phase 1: nein) */
    val isAvailable: Boolean
    val isPlaying: StateFlow<Boolean>
    fun toggle()
}

/** Platzhalter, solange es noch keine Fokus-Musik gibt. */
object NoFocusAudio : FocusAudio {
    override val isAvailable: Boolean = false
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false)
    override fun toggle() = Unit
}
