package org.rustygnome.pickelhaube.ui

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rustygnome.pickelhaube.R

/**
 * Picks a number for the dialer: tab "Recent" (calls from the box' call list and the phone's call
 * log), tab "Speed dial", tab "Phone books" and tab "Internal" (the box' phones). Reload refreshes them all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPickerScreen(
    /** The tab shown; kept in the view model, as setting up a speed dial switches tabs. */
    tab: PickerTab,
    onTabChange: (PickerTab) -> Unit,
    busy: Boolean,
    onReload: () -> Unit,
    onBack: () -> Unit,
    recents: @Composable () -> Unit,
    phonebooks: @Composable () -> Unit,
    speedDials: @Composable () -> Unit,
    internal: @Composable () -> Unit,
    /** The tab "Internal" is there: switched on for the box in use. */
    showInternal: Boolean,
    /** Something just removed, offered to be put back; null: nothing. */
    undoOffer: UndoOffer?,
    onUndo: () -> Unit,
    /** The offer (its serial) went by without "Undo". */
    onUndoGone: (Long) -> Unit,
) {
    // A removal says so at the bottom, with "Undo" – in place of asking beforehand.
    val snackbars = remember { SnackbarHostState() }
    val undoMessage = undoOffer?.message?.asString()
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(undoOffer?.serial) {
        val offer = undoOffer ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(
            message = undoMessage.orEmpty(),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo() else onUndoGone(offer.serial)
    }
    val shown = if (tab == PickerTab.INTERNAL && !showInternal) PickerTab.RECENT else tab
    // Edge to edge, the window no longer shrinks for the keyboard: the content makes room for it
    // itself, so fields further down can be scrolled to (and are, when tapped).
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onReload, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.picker_reload))
                    }
                },
            )
        },
    ) { padding ->
        // On a wide screen no wider than this, in the middle: a name and its time stay within one glance.
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = PICKER_MAX_WIDTH)
                    .fillMaxSize()
            ) {
                PrimaryTabRow(selectedTabIndex = shown.ordinal) {
                    PickerTabItem(shown, PickerTab.RECENT, R.string.picker_tab_recent, HistoryIcon, onTabChange)
                    PickerTabItem(shown, PickerTab.SPEED_DIALS, R.string.speed_dial, Icons.Default.Star, onTabChange)
                    PickerTabItem(shown, PickerTab.PHONEBOOKS, R.string.phone_books, ContactsIcon, onTabChange)
                    if (showInternal) {
                        PickerTabItem(shown, PickerTab.INTERNAL, R.string.picker_tab_internal, Icons.Default.Home, onTabChange)
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                when (shown) {
                    PickerTab.RECENT -> recents()
                    PickerTab.SPEED_DIALS -> speedDials()
                    PickerTab.PHONEBOOKS -> phonebooks()
                    PickerTab.INTERNAL -> internal()
                }
            }
        }
    }
}

/** The picker's tabs and lists are at most this wide – on a tablet across, too. */
private val PICKER_MAX_WIDTH = 720.dp

/**
 * One tab: icon and title. The title is never cut off: it wraps onto a second line and, if a word
 * still does not fit (long translation, large font), gets smaller.
 */
@Composable
private fun PickerTabItem(
    selected: PickerTab,
    tab: PickerTab,
    @StringRes title: Int,
    icon: ImageVector,
    onSelect: (PickerTab) -> Unit,
) {
    Tab(selected = selected == tab, onClick = { onSelect(tab) }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null)
            val style = MaterialTheme.typography.titleSmall
            BasicText(
                stringResource(title),
                style = style.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
