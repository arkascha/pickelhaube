package org.rustygnome.pickelhaube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How far the finger travels to the point where letting go refreshes, in dp (it moves the pull at half its speed). */
const val PULL_DISTANCE_DP = 72f

/** The pull follows the finger at this fraction of its way – it resists a little. */
private const val PULL_RESISTANCE = 0.5f

/** Beyond the refresh point the pull goes only a little further. */
private const val PULL_MAX = 1.25f

/**
 * How far the finger has to move down before the dialer is being pulled, in dp: well beyond the touch
 * slop, as a thumb typing in a hurry slides a little on a key – that is a tap on the key, not a pull.
 */
const val PULL_START_DP = 32f

/**
 * Pull to refresh on a screen that does not scroll (the dialer): a downward drag anywhere on it
 * moves [pull] from 0 towards 1, the point where letting go calls [onRefresh] (with a tick of
 * vibration on reaching it); then it springs back. [distancePx]: [PULL_DISTANCE_DP] in pixels.
 *
 * Only a finger that has moved [PULL_START_DP] down starts a pull; until then nothing is consumed, so
 * a key tapped with a slide – down or up, beyond the touch slop – stays a tap. (A drag detector would
 * take any movement beyond the slop for itself, in either direction, and so cancel the key's tap.)
 */
fun Modifier.pullToRefresh(
    pull: Animatable<Float, *>,
    distancePx: Float,
    scope: CoroutineScope,
    haptics: HapticFeedback,
    onRefresh: () -> Unit,
): Modifier = pointerInput(onRefresh) {
    val startPx = PULL_START_DP.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var pulling = false
        var progress = 0f
        var released = false
        fun release(refresh: Boolean) {
            if (released) return
            released = true
            if (refresh && progress >= 1f) onRefresh()
            scope.launch { pull.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        }
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (pulling) release(refresh = true)
                    break
                }
                if (!pulling) {
                    // Taken by something below (a scrolling list, a menu): not a pull.
                    if (change.isConsumed) break
                    if (change.position.y - down.position.y < startPx) continue
                    pulling = true
                    progress = pull.value
                }
                val dy = change.positionChange().y
                change.consume()
                val before = progress
                progress = (progress + dy * PULL_RESISTANCE / distancePx).coerceIn(0f, PULL_MAX)
                if (before < 1f && progress >= 1f) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                val now = progress
                scope.launch { pull.snapTo(now) }
            }
        } finally {
            // Cancelled on the way (another window took the touch): back without refreshing.
            if (pulling) release(refresh = false)
        }
    }
}
