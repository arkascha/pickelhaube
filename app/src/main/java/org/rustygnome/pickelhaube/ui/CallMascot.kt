package org.rustygnome.pickelhaube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The mascot's pose for how the call is going. */
val CallProgress.mascotPose: MascotPose
    get() = when (this) {
        is CallProgress.PickUp -> MascotPose.RINGING
        is CallProgress.Dialling -> MascotPose.LISTENING
        is CallProgress.Connected -> MascotPose.TALKING
        is CallProgress.Ended -> MascotPose.WAVING
        CallProgress.NotAnswered, CallProgress.NotPickedUp -> MascotPose.PUZZLED
    }

/** How far the head grows beyond its place: the drawings leave room around it for hands and marks. */
private const val HEAD_SCALE = 1.5f

/**
 * The mascot beside the call's status in the dialer, acting it out all along: while the box rings
 * the user's phone, rings spread from it and it shakes like a bell; while the other side is called,
 * its spike rocks from side to side; connected, it nods along; ended, it bows and keeps waving;
 * not answered, it shakes its head and tilts it. It bobs and blinks. Still while Android's
 * animations are switched off.
 */
@Composable
fun CallMascot(progress: CallProgress, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsOn()
    val pose = progress.mascotPose
    val blinking = rememberBlinking(pose, animate)
    // Degrees of tilt (around the chin), and a nod downwards in dp.
    val tilt = remember { Animatable(0f) }
    val nod = remember { Animatable(0f) }
    LaunchedEffect(pose, animate) {
        tilt.snapTo(0f)
        nod.snapTo(0f)
        if (!animate) {
            if (pose == MascotPose.PUZZLED) tilt.snapTo(PUZZLED_TILT)
            return@LaunchedEffect
        }
        when (pose) {
            MascotPose.RINGING -> while (true) {
                for (angle in listOf(-10f, 10f, -10f, 10f, -10f, 10f, -5f, 0f)) tilt.animateTo(angle, tween(55))
                delay(700)
            }
            MascotPose.LISTENING -> while (true) {
                tilt.animateTo(-9f, tween(900, easing = FastOutSlowInEasing))
                tilt.animateTo(9f, tween(900, easing = FastOutSlowInEasing))
            }
            MascotPose.TALKING -> while (true) {
                // Nodding along: twice at once when connected, then every few seconds.
                repeat(2) {
                    nod.animateTo(5f, tween(140))
                    nod.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                delay(2200)
            }
            MascotPose.WAVING -> {
                // A bow: the spike tipped forward, then up again – and waves goodbye.
                tilt.animateTo(-20f, tween(240, easing = FastOutSlowInEasing))
                delay(300)
                tilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                while (true) {
                    for (angle in listOf(9f, -7f, 9f, -7f, 0f)) tilt.animateTo(angle, tween(130))
                    delay(1800)
                }
            }
            MascotPose.PUZZLED -> {
                // A shake of the head, then it tilts it and stays so.
                for (angle in listOf(-8f, 8f, -8f, 8f, 0f)) tilt.animateTo(angle, tween(110))
                tilt.animateTo(PUZZLED_TILT, tween(260))
            }
            else -> Unit
        }
    }
    // Always, while animations are on: a gentle bob up and down, so it looks alive.
    val bob = if (animate) {
        val transition = rememberInfiniteTransition(label = "bob")
        transition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bob",
        ).value
    } else 0f
    // While the box rings the phone: two rings spreading from the head, one after the other.
    val ringing = pose == MascotPose.RINGING && animate
    val rings = remember { Animatable(0f) }
    LaunchedEffect(ringing) {
        rings.snapTo(0f)
        if (!ringing) return@LaunchedEffect
        while (true) {
            rings.animateTo(1f, tween(RING_MILLIS, easing = LinearEasing))
            rings.snapTo(0f)
        }
    }
    val ringColor = MaterialTheme.colorScheme.primary
    Box(
        modifier.drawBehind {
            if (!ringing) return@drawBehind
            for (k in 0..1) {
                val t = (rings.value + k * 0.5f) % 1f
                drawCircle(
                    ringColor.copy(alpha = 0.5f * (1f - t)),
                    radius = size.minDimension * (0.5f + 0.5f * t),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        },
    ) {
        Image(
            painterResource(if (blinking) pose.blinkDrawable ?: pose.drawable else pose.drawable),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = HEAD_SCALE
                    scaleY = HEAD_SCALE
                    // Around the chin, so a tilt moves the spike most.
                    transformOrigin = TransformOrigin(0.5f, 0.8f)
                    rotationZ = tilt.value
                    translationY = (nod.value + bob).dp.toPx()
                },
        )
    }
}

private const val PUZZLED_TILT = -10f
private const val RING_MILLIS = 1400
