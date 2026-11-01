package com.chrizzzzilla.self

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chrizzzzilla.self.core.design.WidthClass
import com.chrizzzzilla.self.core.design.currentWidthClass
import com.chrizzzzilla.self.focus.FocusScreen
import com.chrizzzzilla.self.game.GameScreen
import com.chrizzzzilla.self.reader.LibraryScreen

/** Die drei Bereiche der App. Neue Bereiche = neuer Eintrag + neues Feature-Modul. */
enum class Section(@StringRes val label: Int, @DrawableRes val icon: Int) {
    READ(R.string.app_section_read, R.drawable.app_ic_read),
    FOCUS(R.string.app_section_focus, R.drawable.app_ic_focus),
    GAME(R.string.app_section_game, R.drawable.app_ic_game),
}

/**
 * Rahmen der App: Handy → Leiste unten, Tablet/Querformat → Leiste links (Navigation Rail).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelfAppShell(incomingBook: Uri?, onIncomingBookHandled: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.READ) }
    val widthClass = currentWidthClass()

    // Kommt ein Buch über „Öffnen mit“, immer in den Lesebereich wechseln.
    LaunchedEffect(incomingBook) {
        if (incomingBook != null) section = Section.READ
    }

    if (widthClass == WidthClass.COMPACT) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Section.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = section == entry,
                            onClick = { section = entry },
                            icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                            label = { Text(stringResource(entry.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            SectionContent(
                section = section,
                widthClass = widthClass,
                incomingBook = incomingBook,
                onIncomingBookHandled = onIncomingBookHandled,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        }
    } else {
        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavigationRail {
                Spacer(Modifier.height(12.dp))
                Section.entries.forEach { entry ->
                    NavigationRailItem(
                        selected = section == entry,
                        onClick = { section = entry },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
            SectionContent(
                section = section,
                widthClass = widthClass,
                incomingBook = incomingBook,
                onIncomingBookHandled = onIncomingBookHandled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SectionContent(
    section: Section,
    widthClass: WidthClass,
    incomingBook: Uri?,
    onIncomingBookHandled: () -> Unit,
    modifier: Modifier,
) {
    when (section) {
        Section.READ -> LibraryScreen(
            widthClass = widthClass,
            incomingBook = incomingBook,
            onIncomingBookHandled = onIncomingBookHandled,
            modifier = modifier,
        )
        Section.FOCUS -> FocusScreen(modifier = modifier.background(MaterialTheme.colorScheme.background))
        Section.GAME -> GameScreen(modifier = modifier.background(MaterialTheme.colorScheme.background))
    }
}
