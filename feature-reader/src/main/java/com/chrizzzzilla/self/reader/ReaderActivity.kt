@file:OptIn(ExperimentalReadiumApi::class)

package com.chrizzzzilla.self.reader

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chrizzzzilla.self.core.SelfCore
import com.chrizzzzilla.self.core.design.SelfTheme
import com.chrizzzzilla.self.core.settings.AppSettings
import com.chrizzzzilla.self.core.settings.AppTheme
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression

/** Zustand der Bedienleisten, geteilt zwischen den beiden Compose-Leisten. */
internal class ReaderUiState(settings: AppSettings, locator: Locator?) {
    val chromeVisible = mutableStateOf(true)
    val settings = mutableStateOf(settings)
    val locator = mutableStateOf(locator)
    val showToc = mutableStateOf(false)
    val showDisplaySettings = mutableStateOf(false)
}

/**
 * Lese-Ansicht. Aufbau:
 *  - unten: Readium-`EpubNavigatorFragment` (zeigt das Buch, Wischen blaettert)
 *  - oben drueber: zwei schmale Compose-Leisten (oben Titel + Knoepfe, unten Fortschritt)
 * Tippen links/rechts blaettert, Tippen in die Mitte blendet die Leisten ein/aus.
 */
class ReaderActivity : AppCompatActivity() {

    private var session: ReaderSession? = null
    private lateinit var navigator: EpubNavigatorFragment
    private lateinit var ui: ReaderUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        val itemId = intent.getStringExtra(Reader.EXTRA_ITEM_ID)
        val current = ReaderSessions.current?.takeIf { it.item.id == itemId }

        if (current == null) {
            // App wurde vom System beendet und wiederhergestellt: kein Buch mehr im Speicher.
            // Readium verlangt trotzdem eine FragmentFactory → Dummy setzen und zurueck zur Bibliothek.
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        session = current

        // WICHTIG: FragmentFactory vor super.onCreate() setzen (Readium-Vorgabe).
        supportFragmentManager.fragmentFactory = current.navigatorFactory.createFragmentFactory(
            initialLocator = current.lastLocator,
            initialPreferences = current.initialSettings.toEpubPreferences(isSystemDark()),
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ui = ReaderUiState(current.initialSettings, current.lastLocator)

        val root = FrameLayout(this)
        val container = FragmentContainerView(this).apply { id = R.id.reader_navigator_container }
        root.addView(
            container,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        root.addView(
            ComposeView(this).apply {
                setContent {
                    SelfTheme(ui.settings.value.theme) {
                        ReaderTopChrome(
                            ui = ui,
                            title = current.item.title,
                            tableOfContents = current.publication.tableOfContents,
                            readingOrder = current.publication.readingOrder,
                            focusAvailable = SelfCore.focusAudio.isAvailable,
                            onBack = { finish() },
                            onMusic = ::onMusicPressed,
                            onLinkSelected = ::goToLink,
                            onThemeSelected = { theme -> lifecycleScope.launch { SelfCore.settings.setTheme(theme) } },
                            onFontScaleSelected = { scale -> lifecycleScope.launch { SelfCore.settings.setFontScale(scale) } },
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
        root.addView(
            ComposeView(this).apply {
                setContent {
                    SelfTheme(ui.settings.value.theme) {
                        ReaderBottomChrome(ui = ui, onSeek = ::seekTo)
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        setContentView(root)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.reader_navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment

        // Tippen an den Raendern blaettert, Tippen in die Mitte zeigt/versteckt die Leisten.
        navigator.addInputListener(DirectionalNavigationAdapter(navigator, animatedTransition = true))
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                runOnUiThread { setChromeVisible(!ui.chromeVisible.value) }
                return true
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    navigator.currentLocator.collect { locator ->
                        current.lastLocator = locator
                        ui.locator.value = locator
                        SelfCore.library.updatePosition(
                            id = current.item.id,
                            progress = locator.locations.totalProgression ?: 0.0,
                            positionJson = locator.toJSON().toString(),
                        )
                    }
                }
                launch {
                    SelfCore.settings.settings.collect { settings ->
                        ui.settings.value = settings
                        navigator.submitPreferences(settings.toEpubPreferences(isSystemDark()))
                        applySystemBarStyle(settings.theme)
                    }
                }
            }
        }

        setChromeVisible(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) session?.let { ReaderSessions.close(it) }
    }

    private fun setChromeVisible(visible: Boolean) {
        ui.chromeVisible.value = visible
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Statusleisten-Symbole hell oder dunkel, passend zum Farbmodus. */
    private fun applySystemBarStyle(theme: AppTheme) {
        val dark = when (theme) {
            AppTheme.SYSTEM -> isSystemDark()
            AppTheme.DARK -> true
            AppTheme.LIGHT, AppTheme.SEPIA -> false
        }
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun goToLink(link: Link) {
        navigator.go(link)
        ui.showToc.value = false
    }

    private fun seekTo(progression: Double) {
        val publication = session?.publication ?: return
        lifecycleScope.launch {
            publication.locateProgression(progression)?.let { navigator.go(it) }
        }
    }

    private fun onMusicPressed() {
        val focus = SelfCore.focusAudio
        if (focus.isAvailable) {
            focus.toggle()
        } else {
            Toast.makeText(this, R.string.reader_music_later, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val NAVIGATOR_TAG = "epub_navigator"
    }
}

/** App-Einstellungen → Readium-Einstellungen. */
internal fun AppSettings.toEpubPreferences(systemDark: Boolean): EpubPreferences =
    EpubPreferences(
        fontSize = fontScale,
        theme = when (theme) {
            AppTheme.SYSTEM -> if (systemDark) Theme.DARK else Theme.LIGHT
            AppTheme.LIGHT -> Theme.LIGHT
            AppTheme.DARK -> Theme.DARK
            AppTheme.SEPIA -> Theme.SEPIA
        },
        // Seitenweise blaettern statt scrollen. Spalten: Readium waehlt im Querformat
        // auf grossen Bildschirmen automatisch zwei Spalten (ColumnCount.AUTO).
        scroll = false,
    )
