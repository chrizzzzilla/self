@file:OptIn(ExperimentalReadiumApi::class)

package com.chrizzzzilla.self.reader

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
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
import com.chrizzzzilla.self.core.design.enterImmersive
import com.chrizzzzilla.self.core.settings.AppSettings
import com.chrizzzzilla.self.core.settings.AppTheme
import kotlinx.coroutines.launch
import com.chrizzzzilla.self.focus.FocusPlayer
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.navigator.epub.css.Color as CssColor
import org.readium.r2.navigator.preferences.Color as ReadiumColor

/** Zustand der Bedienleisten, geteilt zwischen den Compose-Ebenen. */
internal class ReaderUiState(settings: AppSettings, locator: Locator?) {
    val chromeVisible = mutableStateOf(true)
    val settings = mutableStateOf(settings)
    val locator = mutableStateOf(locator)
    val showToc = mutableStateOf(false)
    val showDisplaySettings = mutableStateOf(false)
    val showAmbient = mutableStateOf(false)
    /** Fokusmodus: Buch ausgeblendet, nur Szene + Klang. */
    val focusMode = mutableStateOf(false)
    val focusControlsVisible = mutableStateOf(true)
}

/** CSS-Wert "transparent" fuer Readium – damit die Szene hinter dem Text durchscheint. */
private object TransparentCss : CssColor {
    override fun toCss(): String = "transparent"
}

/**
 * Lese-Ansicht. Ebenen von hinten nach vorne:
 *  1. animierte Nachtstadt + Lesefilter (Compose)
 *  2. Readium-`EpubNavigatorFragment` mit durchsichtigem Hintergrund (das Buch)
 *  3. Bedienleisten oben/unten (Compose)
 * Tippen links/rechts blaettert, Tippen in die Mitte blendet die Leisten ein/aus.
 * Der Fokusmodus blendet das Buch aus – dann bleiben nur Szene und Klang.
 */
class ReaderActivity : AppCompatActivity() {

    private var session: ReaderSession? = null
    private lateinit var navigator: OverflowableNavigator
    /** nur bei EPUBs gesetzt (Einstellungen, Durchsichtigkeit) */
    private var epub: EpubNavigatorFragment? = null
    private lateinit var ui: ReaderUiState
    private lateinit var navigatorContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        val itemId = intent.getStringExtra(Reader.EXTRA_ITEM_ID)
        val current = ReaderSessions.current?.takeIf { it.item.id == itemId }

        if (current == null) {
            // App wurde vom System beendet und wiederhergestellt: kein Buch mehr im Speicher.
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        session = current

        // WICHTIG: FragmentFactory vor super.onCreate() setzen (Readium-Vorgabe).
        supportFragmentManager.fragmentFactory = if (current.isComic) {
            ImageNavigatorFragment.createFactory(current.publication, current.lastLocator)
        } else {
            current.navigatorFactory.createFragmentFactory(
                initialLocator = current.lastLocator,
                initialPreferences = current.initialSettings.toEpubPreferences(isSystemDark()),
                configuration = EpubNavigatorFragment.Configuration(
                    readiumCssRsProperties = RsProperties(backgroundColor = TransparentCss),
                ),
            )
        }
        super.onCreate(savedInstanceState)
        enterImmersive()

        ui = ReaderUiState(current.initialSettings, current.lastLocator)

        val match = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val root = FrameLayout(this)

        // 1) Hintergrund: Szene + Lesefilter (+ Bedienung im Fokusmodus)
        root.addView(
            ComposeView(this).apply {
                setContent {
                    SelfTheme(ui.settings.value.theme) {
                        ReaderBackdrop(
                            ui = ui,
                            onExitFocus = { setFocusMode(false) },
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(match),
        )

        // 2) Das Buch
        navigatorContainer = FragmentContainerView(this).apply { id = R.id.reader_navigator_container }
        root.addView(navigatorContainer, FrameLayout.LayoutParams(match))

        // 3) Leisten
        root.addView(
            ComposeView(this).apply {
                setContent {
                    SelfTheme(ui.settings.value.theme) {
                        ReaderTopChrome(
                            ui = ui,
                            title = current.item.title,
                            tableOfContents = current.publication.tableOfContents,
                            readingOrder = current.publication.readingOrder,
                            onBack = { finish() },
                            onFocusMode = { setFocusMode(true) },
                            onLinkSelected = ::goToLink,
                            onThemeSelected = { theme -> lifecycleScope.launch { SelfCore.settings.setTheme(theme) } },
                            onFontScaleSelected = { scale -> lifecycleScope.launch { SelfCore.settings.setFontScale(scale) } },
                            onReadingFilterSelected = { v -> lifecycleScope.launch { SelfCore.settings.setReadingFilter(v) } },
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
                val cls = if (current.isComic) ImageNavigatorFragment::class.java else EpubNavigatorFragment::class.java
                add(R.id.reader_navigator_container, cls, Bundle(), NAVIGATOR_TAG)
            }
        }
        val fragment = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)!!
        navigator = fragment as OverflowableNavigator
        epub = fragment as? EpubNavigatorFragment

        // Readium-Ansicht durchsichtig machen, sobald sie existiert
        fragment.viewLifecycleOwnerLiveData.observe(this) { owner ->
            if (owner != null) clearNavigatorBackgrounds()
        }
        FocusPlayer.autoStart()

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
                        clearNavigatorBackgrounds()
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
                        epub?.submitPreferences(settings.toEpubPreferences(isSystemDark()))
                        clearNavigatorBackgrounds()
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

    /**
     * Readium faerbt seinen Seiten-Container in der Theme-Farbe ein, WebViews sind standardmaessig weiss.
     * Beides auf durchsichtig setzen (nach jedem Einstellungs-/Seitenwechsel erneut).
     */
    private fun clearNavigatorBackgrounds() {
        val view = epub?.view ?: return
        view.post { makeTransparent(view) }
    }

    private fun makeTransparent(v: View) {
        if (v is WebView) {
            v.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        v.setBackgroundColor(Color.TRANSPARENT)
        if (v is ViewGroup) for (i in 0 until v.childCount) makeTransparent(v.getChildAt(i))
    }

    private fun setFocusMode(on: Boolean) {
        ui.focusMode.value = on
        ui.focusControlsVisible.value = true
        if (on) {
            ui.chromeVisible.value = false
            navigatorContainer.animate().alpha(0f).setDuration(500).withEndAction {
                navigatorContainer.visibility = View.INVISIBLE
            }.start()
            hideSystemBars(true)
        } else {
            navigatorContainer.visibility = View.VISIBLE
            navigatorContainer.animate().alpha(1f).setDuration(400).start()
            setChromeVisible(true)
        }
    }

    private fun setChromeVisible(visible: Boolean) {
        ui.chromeVisible.value = visible
        hideSystemBars(!visible)
    }

    /** Uhr, Akku und Navigationsleiste bleiben immer ausgeblendet (Chris, 01.11.). */
    @Suppress("UNUSED_PARAMETER")
    private fun hideSystemBars(hide: Boolean) {
        enterImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && session != null) enterImmersive()
    }

    /** Statusleisten-Symbole hell oder dunkel, passend zum Farbmodus. */
    private fun applySystemBarStyle(theme: AppTheme) {
        val style = if (isDark(theme)) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun isDark(theme: AppTheme): Boolean = when (theme) {
        AppTheme.SYSTEM -> isSystemDark()
        AppTheme.DARK -> true
        AppTheme.LIGHT, AppTheme.SEPIA -> false
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

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val NAVIGATOR_TAG = "epub_navigator"
    }
}

/**
 * App-Einstellungen → Readium-Einstellungen. Den Seitenhintergrund malt nicht Readium,
 * sondern unser Lesefilter – Readium bekommt nur die passende Textfarbe.
 */
internal fun AppSettings.toEpubPreferences(systemDark: Boolean): EpubPreferences {
    val dark = theme == AppTheme.DARK || (theme == AppTheme.SYSTEM && systemDark)
    val textColor = when {
        dark -> 0xFFE6E4DF.toInt()
        theme == AppTheme.SEPIA -> 0xFF4E3B2C.toInt()
        else -> 0xFF1B1C1C.toInt()
    }
    return EpubPreferences(
        fontSize = fontScale,
        theme = Theme.LIGHT,
        textColor = ReadiumColor(textColor),
        // Seitenweise blaettern; im Querformat waehlt Readium auf grossen Bildschirmen zwei Spalten.
        scroll = false,
    )
}
