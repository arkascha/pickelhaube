package org.rustygnome.pickelhaube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * The helmet in the dialer's title, beside the app's name: at rest it just looks out and blinks now
 * and then; while the app talks to the box ([busy]) it rocks its spike from side to side; and when
 * [spin] is started (a long press on the title), it turns once around. Still while Android's
 * animations are switched off – the progress bar below the title says that the app is busy all the same.
 */
@Composable
fun TitleHelmet(busy: Boolean, spin: HelmetSpin, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsOn()
    val blinking = rememberBlinking(MascotPose.IDLE, animate)
    // Degrees of the rocking (around the chin) and of the spin (around the middle).
    val rock = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(busy, animate) {
        if (!busy || !animate) {
            // Back upright – with a little wobble, once it is done.
            if (animate) rock.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) else rock.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            rock.animateTo(ROCK_DEGREES, tween(ROCK_MILLIS, easing = FastOutSlowInEasing))
            rock.animateTo(-ROCK_DEGREES, tween(ROCK_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(spin.runs) {
        if (spin.runs == 0 || !animate) return@LaunchedEffect
        turn.snapTo(0f)
        turn.animateTo(360f, tween(SPIN_MILLIS, easing = FastOutSlowInEasing))
        turn.snapTo(0f)
    }
    Box(modifier.size(40.dp).graphicsLayer { rotationZ = turn.value }) {
        Image(
            painterResource(if (blinking) MascotPose.IDLE.blinkDrawable!! else MascotPose.IDLE.drawable),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The drawing leaves room around the head: grown, the head itself is about the box' size.
                    scaleX = HEAD_SCALE
                    scaleY = HEAD_SCALE
                    transformOrigin = TransformOrigin(0.5f, 0.8f)
                    rotationZ = rock.value
                },
        )
    }
}

private const val HEAD_SCALE = 1.4f
private const val ROCK_DEGREES = 12f
private const val ROCK_MILLIS = 420
private const val SPIN_MILLIS = 900
