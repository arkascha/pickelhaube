package org.rustygnome.pickelhaube.ui

import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The helmet in the dialer's title, in place of the app's name (beside the box in use): at rest it
 * just looks out and blinks now and then; while the app talks to the box ([busy]) it rocks its spike
 * from side to side; when [spin] is started (a long press on the title), it turns once around; and
 * pulled down for a refresh, it nods forward as far as pulled. Still while Android's animations are
 * switched off – the progress bar below the title says that the app is busy all the same.
 *
 * It takes part in dialling, too: each change of the [number] (a key pressed) makes it hop, and it
 * looks down at the keypad for a moment; when the other side answers ([connectedSince]), it cheers
 * with a double hop. Without animations it only changes its look.
 */
@Composable
fun TitleHelmet(
    busy: Boolean,
    spin: HelmetSpin,
    modifier: Modifier = Modifier,
    /** How far the dialer is pulled down for a refresh (1: letting go refreshes): it tips its spike forward that far. */
    pull: Float = 0f,
    /** What accessibility services say for it; null: nothing (decoration). */
    description: String? = null,
    /** The number in the dialer: as it changes, it hops and looks at the keypad. */
    number: String = "",
    /** When the call in progress was answered ([android.os.SystemClock.elapsedRealtime]); null: none is. */
    connectedSince: Long? = null,
) {
    val animate = rememberAnimationsOn()
    val blinking = rememberBlinking(MascotPose.IDLE, animate)
    // Upwards in dp, for the hops.
    val hop = remember { Animatable(0f) }
    var lookingDown by remember { mutableStateOf(false) }
    // The number it saw last: a hop for a change only, not for the number already there when shown.
    val seen = remember { arrayOf(number) }
    LaunchedEffect(number) {
        if (number == seen[0]) return@LaunchedEffect
        seen[0] = number
        // A key pressed again restarts this: it keeps looking down while the keys are pressed.
        lookingDown = true
        if (animate) {
            hop.animateTo(HOP_DP, tween(HOP_UP_MILLIS, easing = FastOutSlowInEasing))
            hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        delay(LOOK_DOWN_MILLIS)
        lookingDown = false
    }
    // Its own, so a cheer and a key pressed do not cut each other's hops short.
    val cheerHop = remember { Animatable(0f) }
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(connectedSince) {
        // Only just answered – not again when the screen is built anew during the call (turned, say).
        if (connectedSince == null || SystemClock.elapsedRealtime() - connectedSince > CHEER_MILLIS) {
            cheering = false
            return@LaunchedEffect
        }
        cheering = true
        if (animate) {
            repeat(2) {
                cheerHop.animateTo(CHEER_HOP_DP, tween(HOP_UP_MILLIS + 40, easing = FastOutSlowInEasing))
                cheerHop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
        delay(CHEER_MILLIS)
        cheering = false
    }
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
                    cheering -> MascotPose.CHEERING.drawable
                    // At the keypad below, as a key was pressed.
                    lookingDown -> MascotPose.NODDING.drawable
                    blinking -> MascotPose.IDLE.blinkDrawable!!
                    else -> MascotPose.IDLE.drawable
                },
            ),
            contentDescription = description,
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
                    translationY = (pull.coerceAtMost(1f) * PULL_DROP - hop.value - cheerHop.value).dp.toPx()
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

/** How high it hops for a key pressed, and for joy when a call is answered (dp), and how quickly up. */
private const val HOP_DP = 4f
private const val CHEER_HOP_DP = 7f
private const val HOP_UP_MILLIS = 70

/** How long it looks at the keypad after a key, and cheers after a call was answered. */
private const val LOOK_DOWN_MILLIS = 1200L
private const val CHEER_MILLIS = 2000L
