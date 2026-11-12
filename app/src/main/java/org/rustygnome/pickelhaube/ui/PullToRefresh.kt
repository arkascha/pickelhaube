package org.rustygnome.pickelhaube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
 * Pull to refresh on a screen that does not scroll (the dialer): a downward drag anywhere on it
 * moves [pull] from 0 towards 1, the point where letting go calls [onRefresh] (with a tick of
 * vibration on reaching it); then it springs back. A tap stays a tap – only a drag beyond the touch
 * slop counts – and upward drags are left alone. [distancePx]: [PULL_DISTANCE_DP] in pixels.
 */
fun Modifier.pullToRefresh(
    pull: Animatable<Float, *>,
    distancePx: Float,
    scope: CoroutineScope,
    haptics: HapticFeedback,
    onRefresh: () -> Unit,
): Modifier = pointerInput(onRefresh) {
    var progress = 0f
    fun release(refresh: Boolean) {
        if (refresh && progress >= 1f) onRefresh()
        progress = 0f
        scope.launch { pull.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }
    detectVerticalDragGestures(
        onDragStart = { progress = pull.value },
        onVerticalDrag = { change, dy ->
            if (dy > 0f || progress > 0f) {
                change.consume()
                val before = progress
                progress = (progress + dy * PULL_RESISTANCE / distancePx).coerceIn(0f, PULL_MAX)
                if (before < 1f && progress >= 1f) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                val now = progress
                scope.launch { pull.snapTo(now) }
            }
        },
        onDragEnd = { release(refresh = true) },
        onDragCancel = { release(refresh = false) },
    )
}
