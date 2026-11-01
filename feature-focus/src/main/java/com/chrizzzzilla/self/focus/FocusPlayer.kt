package com.chrizzzzilla.self.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.chrizzzzilla.self.core.playback.FocusAudio
import com.chrizzzzilla.self.focus.audio.AmbientEngine
import com.chrizzzzilla.self.focus.audio.AmbientMix
import com.chrizzzzilla.self.focus.audio.SampleDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Spielt die selbst zusammengestellte Klangmischung (Ambiente + Natur + Percussion).
 * Lebt fuer die ganze App und laeuft beim Lesen im Hintergrund weiter.
 *
 * TODO: Foreground-Service + Benachrichtigung, damit es auch bei ausgeschaltetem Bildschirm
 * zuverlaessig weiterlaeuft.
 */
object FocusPlayer : FocusAudio {

    private const val SAMPLE_RATE = 44100
    private const val BLOCK_FRAMES = 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var audioManager: AudioManager
    private lateinit var store: AmbientStore
    private var focusRequest: AudioFocusRequest? = null

    private val engine = AmbientEngine(SAMPLE_RATE)
    private var thread: Thread? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val isAvailable: Boolean = true

    private val _mix = MutableStateFlow(AmbientMix())
    val mix: StateFlow<AmbientMix> = _mix.asStateFlow()

    private val _activePreset = MutableStateFlow<Int?>(null)
    /** Zuletzt geladenes/gespeichertes Preset (1–5) oder null. */
    val activePreset: StateFlow<Int?> = _activePreset.asStateFlow()

    lateinit var presets: StateFlow<List<AmbientMix?>>
        private set

    /** Fuer die Animation (0..1). */
    val level: Float get() = engine.level
    val pulse: Float get() = engine.pulse
    val levelAmbience: Float get() = engine.levelAmbience
    val levelNature: Float get() = engine.levelNature
    val levelPercussion: Float get() = engine.levelPercussion

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        if (::store.isInitialized) return
        audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
        store = AmbientStore(context)
        val app = context.applicationContext
        engine.setNatureLoader { name -> SampleDecoder.decode(app, "nature/$name.ogg") }
        presets = store.presets.stateIn(scope, SharingStarted.Eagerly, List(AmbientStore.PRESET_COUNT) { null })
        engine.setMix(_mix.value)
        scope.launch {
            store.loadCurrent()?.let { saved ->
                _mix.value = saved
                engine.setMix(saved)
            }
            // Beim Oeffnen der App direkt mit der zuletzt verwendeten Mischung beginnen
            autoStart()
            // Aenderungen gesammelt speichern
            _mix.drop(1).debounce(800).onEach { store.saveCurrent(it) }.launchIn(scope)
        }
    }

    fun update(mix: AmbientMix) {
        _mix.value = mix
        engine.setMix(mix)
        // Kein Play-Knopf: ist etwas ausgewaehlt und hoerbar, laeuft es – sonst ist Ruhe.
        if (isAudible(mix)) play() else pause()
    }

    /** Beim Oeffnen des Readers: gespeicherte Mischung automatisch spielen, wenn sie hoerbar ist. */
    fun autoStart() {
        if (isAudible(_mix.value)) play()
    }

    private fun isAudible(m: AmbientMix): Boolean =
        (m.ambience != null && m.ambienceVolume > 0.01f) ||
            (m.nature.isNotEmpty() && m.natureVolume > 0.01f) ||
            (m.percussion != null && m.percussionVolume > 0.01f)

    fun loadPreset(slot: Int) {
        val preset = presets.value.getOrNull(slot - 1) ?: return
        update(preset)
        _activePreset.value = slot
    }

    fun savePreset(slot: Int) {
        val current = _mix.value
        _activePreset.value = slot
        scope.launch { store.savePreset(slot, current) }
    }

    override fun toggle() {
        if (_isPlaying.value) pause() else play()
    }

    @Synchronized
    fun play() {
        if (_isPlaying.value) return
        requestFocus()
        engine.setMix(_mix.value)
        engine.fadeTarget = 1f
        _isPlaying.value = true
        if (thread?.isAlive != true) {
            thread = Thread(::audioLoop, "self-ambient-audio").apply { start() }
        }
    }

    @Synchronized
    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        engine.fadeTarget = 0f // Audio-Thread blendet aus und beendet sich dann selbst
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun requestFocus() {
        if (!::audioManager.isInitialized) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { change ->
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
                engine.render(buffer, BLOCK_FRAMES)
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                if (engine.isSilent && !_isPlaying.value) break
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            synchronized(this) { if (thread == Thread.currentThread()) thread = null }
            if (_isPlaying.value) synchronized(this) {
                if (thread == null) thread = Thread(::audioLoop, "self-ambient-audio").apply { start() }
            }
        }
    }
}
