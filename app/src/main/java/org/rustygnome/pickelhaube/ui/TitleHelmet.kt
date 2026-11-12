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
 * [spin] is started (a long press on the title), it turns once around; pulled down for a refresh,
 * it nods forward as far as pulled. Still while Android's
 * animations are switched off – the progress bar below the title says that the app is busy all the same.
 */
@Composable
fun TitleHelmet(
    busy: Boolean,
    spin: HelmetSpin,
    modifier: Modifier = Modifier,
    /** How far the dialer is pulled down for a refresh (1: letting go refreshes): it tips its spike forward that far. */
    pull: Float = 0f,
) {
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
            painterResource(
                when {
                    // Pulled down a little already: it looks down, nodding along.
                    pull > NOD_LOOK_DOWN -> MascotPose.NODDING.drawable
                    blinking -> MascotPose.IDLE.blinkDrawable!!
                    else -> MascotPose.IDLE.drawable
                },
            ),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The drawing leaves room around the head: grown, the head itself is about the box' size.
                    scaleX = HEAD_SCALE
                    scaleY = HEAD_SCALE
                    // Around the chin for the rocking; the nod stays in the title row (see below).
                    transformOrigin = TransformOrigin(0.5f, if (pull > 0f) NOD_PIVOT else 0.8f)
                    rotationZ = rock.value
                    // A nod: the spike tipped forward, towards the one looking, in perspective – nearly flat at
                    // the refresh point, and never beyond: pulled further, it only creeps closer to flat.
                    rotationX = -nodDegrees(pull)
                    cameraDistance = 12 * density
                    translationY = (pull.coerceAtMost(1f) * PULL_DROP).dp.toPx()
                },
        )
    }
}

private const val HEAD_SCALE = 1.4f

/** At the refresh point of a pull: how far the head nods forward, and is lowered (dp). */
private const val PULL_DEGREES = 80f
private const val PULL_DROP = 4f

/** The furthest it nods, pulled beyond the refresh point: short of flat (90°), so it never tips over. */
private const val PULL_DEGREES_MAX = 86f

/** Where the nod turns the head, from its top: around the middle, so it stays in the title row. */
private const val NOD_PIVOT = 0.55f

/** How far the head nods at [pull]: [PULL_DEGREES] at the refresh point, then slowly on towards [PULL_DEGREES_MAX]. */
private fun nodDegrees(pull: Float): Float =
    if (pull <= 1f) pull * PULL_DEGREES
    else (PULL_DEGREES + (pull - 1f) * 4 * (PULL_DEGREES_MAX - PULL_DEGREES)).coerceAtMost(PULL_DEGREES_MAX)

/** From this much of a pull on, the head looks down. */
private const val NOD_LOOK_DOWN = 0.15f
private const val ROCK_DEGREES = 12f
private const val ROCK_MILLIS = 420
private const val SPIN_MILLIS = 900
