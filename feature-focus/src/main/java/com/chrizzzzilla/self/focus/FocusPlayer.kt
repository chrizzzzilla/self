package com.chrizzzzilla.self.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.chrizzzzilla.self.core.playback.FocusAudio
import com.chrizzzzilla.self.focus.audio.LofiParams
import com.chrizzzzilla.self.focus.audio.LofiSynth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spielt die generative Lofi-Musik. Lebt fuer die ganze App (laeuft also auch im Reader weiter).
 * Wird in `SelfApp` als `SelfCore.focusAudio` angemeldet.
 *
 * TODO Phase 2b: Foreground-Service + Benachrichtigung, damit es auch bei ausgeschaltetem
 * Bildschirm zuverlaessig weiterlaeuft.
 */
object FocusPlayer : FocusAudio {

    private const val SAMPLE_RATE = 44100
    private const val BLOCK_FRAMES = 1024

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val synth = LofiSynth(SAMPLE_RATE)
    private var thread: Thread? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _params = MutableStateFlow(LofiParams())
    val params: StateFlow<LofiParams> = _params.asStateFlow()

    /** Fuer die Animation (0..1). */
    val level: Float get() = synth.level
    val pulse: Float get() = synth.pulse

    override val isAvailable: Boolean = true

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun init(context: Context) {
        audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    }

    fun update(params: LofiParams) {
        _params.value = params
        synth.params = params
    }

    override fun toggle() {
        if (_isPlaying.value) pause() else play()
    }

    @Synchronized
    fun play() {
        if (_isPlaying.value) return
        requestFocus()
        synth.params = _params.value
        synth.fadeTarget = 1f
        _isPlaying.value = true
        if (thread?.isAlive != true) {
            thread = Thread(::audioLoop, "self-focus-audio").apply { start() }
        }
    }

    @Synchronized
    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        synth.fadeTarget = 0f // der Audio-Thread blendet aus und beendet sich dann selbst
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun requestFocus() {
        if (!::audioManager.isInitialized) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { change ->
                // Anruf, Navi-Ansage oder andere Musik → leise stellen
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
            }
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, BLOCK_FRAMES * 2 * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val buffer = FloatArray(BLOCK_FRAMES * 2)
        try {
            track.play()
            while (true) {
                synth.render(buffer, BLOCK_FRAMES)
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                // Ausgeblendet und keiner will mehr Musik → Thread beenden
                if (synth.isSilent && !_isPlaying.value) break
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            synchronized(this) { if (thread == Thread.currentThread()) thread = null }
            // Falls waehrend des Ausblendens wieder "Play" gedrueckt wurde
            if (_isPlaying.value) synchronized(this) {
                if (thread == null) thread = Thread(::audioLoop, "self-focus-audio").apply { start() }
            }
        }
    }
}
