package org.rustygnome.pickelhaube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.rustygnome.pickelhaube.R

/**
 * A little surprise behind a long press (the dialer's title, the version in the settings): the
 * helmet pops up, spins once and is gone again. [start] it; [HelmetSpinPopup] shows it.
 */
@Stable
class HelmetSpin {
    /** How often it was started: each start shows it anew. */
    var runs by mutableIntStateOf(0)
        private set

    fun start() {
        runs++
    }
}

@Composable
fun rememberHelmetSpin(): HelmetSpin = remember { HelmetSpin() }

/**
 * The helmet of [spin], over whatever is there, placed at [alignment] of the element it is part of
 * and moved down by [offsetY] (up, if negative):
 * it pops up, turns once around, and fades. With Android's animations off, it just shows for a moment.
 */
@Composable
fun HelmetSpinPopup(spin: HelmetSpin, alignment: Alignment, offsetY: Dp) {
    val run = spin.runs
    if (run == 0) return
    var shown by remember(run) { mutableStateOf(true) }
    if (!shown) return
    val animate = rememberAnimationsOn()
    val turn = remember(run) { Animatable(0f) }
    val grow = remember(run) { Animatable(if (animate) 0.3f else 1f) }
    val fade = remember(run) { Animatable(1f) }
    LaunchedEffect(run) {
        if (animate) {
            coroutineScope {
                launch { grow.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                turn.animateTo(360f, tween(SPIN_MILLIS, easing = FastOutSlowInEasing))
            }
            delay(350)
            fade.animateTo(0f, tween(250))
        } else {
            delay(1_200)
        }
        shown = false
    }
    val offset = with(LocalDensity.current) { IntOffset(0, offsetY.roundToPx()) }
    Popup(alignment = alignment, offset = offset, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Image(
            painterResource(R.drawable.mascot_cheering),
            contentDescription = null,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    rotationZ = turn.value
                    scaleX = grow.value
                    scaleY = grow.value
                    alpha = fade.value
                },
        )
    }
}

private const val SPIN_MILLIS = 900
