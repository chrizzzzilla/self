package com.chrizzzzilla.self.core.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Window Size Classes nach den Material-Breakpoints:
 * COMPACT < 600 dp (Handy hochkant), MEDIUM < 840 dp (Handy quer / kleines Tablet),
 * EXPANDED >= 840 dp (Tablet quer).
 */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun currentWidthClass(): WidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> WidthClass.COMPACT
        widthDp < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}
