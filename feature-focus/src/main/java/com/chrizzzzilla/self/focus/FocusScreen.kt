package com.chrizzzzilla.self.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chrizzzzilla.self.core.design.PlaceholderScreen

/** Platzhalter fuer Phase 2 (generative Musik + animierte Hintergruende). */
@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.focus_title),
        text = stringResource(R.string.focus_placeholder),
        hint = stringResource(R.string.focus_soon),
        modifier = modifier,
    )
}
