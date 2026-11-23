package org.rustygnome.pickelhaube.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** "Back" on a page that closes to the one underneath: the state it leads to, and [go] to do it. */
class BackStep(val after: UiState, val go: () -> Unit)

/**
 * The page of [state] (see [UiState.page]), each change of page animated (see [pageTransition]). On a
 * page for which [back] says where "back" leads, the back gesture is followed as it is made: the page
 * moves with the finger and the one it leads to shows underneath; let go, it closes, and swiped back,
 * it stays. On other pages, "back" is left to their own handling.
 *
 * Each page is drawn ([content]) with its own state: the one shown with the current state, one sliding
 * away with the state it had last, and the one coming up under a back gesture with the state "back"
 * leads to – so neither shows something it never showed.
 */
@Composable
fun AnimatedPages(state: UiState, back: (UiState) -> BackStep?, content: @Composable (UiState) -> Unit) {
    val pages = remember { SeekableTransitionState(state.page) }
    val transition = rememberTransition(pages, label = "page")
    // The last state each page was shown with – for a page while it slides away.
    val lastShown = remember { mutableMapOf<Page, UiState>() }
    lastShown[state.page] = state
    // While a back gesture is being made: the state "back" leads to, for the page coming up underneath.
    var underneath by remember { mutableStateOf<UiState?>(null) }
    val scope = rememberCoroutineScope()

    // A change of page – by a tap, or a back gesture let go – animated to its end.
    LaunchedEffect(state.page) { pages.animateTo(state.page) }

    val step = back(state)
    PredictiveBackHandler(enabled = step != null) { progress ->
        val toward = step ?: return@PredictiveBackHandler
        val from = state.page
        underneath = toward.after
        try {
            progress.collect { event -> pages.seekTo(event.progress, targetState = toward.after.page) }
            toward.go()
            underneath = null
        } catch (e: CancellationException) {
            // Swiped back: the page returns to its place.
            scope.launch {
                pages.animateTo(from)
                underneath = null
            }
            throw e
        }
    }

    transition.AnimatedContent(transitionSpec = { pageTransition(initialState, targetState) }) { page ->
        content(
            when (page) {
                state.page -> state
                underneath?.page -> underneath ?: state
                else -> lastShown[page] ?: state
            },
        )
    }
}
