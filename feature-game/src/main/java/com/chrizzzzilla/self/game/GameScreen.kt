package com.chrizzzzilla.self.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chrizzzzilla.self.core.design.PlaceholderScreen

/** Platzhalter fuer das spaetere Spiel (Compose Canvas). */
@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.game_title),
        text = stringResource(R.string.game_placeholder),
        hint = stringResource(R.string.game_soon),
        modifier = modifier,
    )
}
