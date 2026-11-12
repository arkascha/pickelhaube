package org.rustygnome.pickelhaube.ui

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.rustygnome.pickelhaube.R

/**
 * The poses of the mascot, the app icon's helmet as a little character (drawn by
 * art/mascot/generate.py): the welcome tour's, and those of a call in the dialer.
 */
enum class MascotPose {
    WAVING, CLIPBOARD, SEARCHING, TYPING, PUZZLED, CHEERING,

    /** The box rings the user's phone. */
    RINGING,

    /** The other side is being called. */
    LISTENING,

    /** Connected. */
    TALKING,
}

@get:DrawableRes
val MascotPose.drawable: Int
    get() = when (this) {
        MascotPose.WAVING -> R.drawable.mascot_waving
        MascotPose.CLIPBOARD -> R.drawable.mascot_clipboard
        MascotPose.SEARCHING -> R.drawable.mascot_searching
        MascotPose.TYPING -> R.drawable.mascot_typing
        MascotPose.PUZZLED -> R.drawable.mascot_puzzled
        MascotPose.CHEERING -> R.drawable.mascot_cheering
        MascotPose.RINGING -> R.drawable.mascot_ringing
        MascotPose.LISTENING -> R.drawable.mascot_listening
        MascotPose.TALKING -> R.drawable.mascot_talking
    }

/** The pose with its eyes closed, for a blink; null: the pose does not blink (its eyes are closed or squinted already). */
@get:DrawableRes
val MascotPose.blinkDrawable: Int?
    get() = when (this) {
        MascotPose.WAVING -> R.drawable.mascot_waving_blink
        MascotPose.CLIPBOARD -> R.drawable.mascot_clipboard_blink
        MascotPose.SEARCHING -> R.drawable.mascot_searching_blink
        MascotPose.TYPING -> R.drawable.mascot_typing_blink
        MascotPose.RINGING -> R.drawable.mascot_ringing_blink
        MascotPose.LISTENING -> R.drawable.mascot_listening_blink
        MascotPose.PUZZLED, MascotPose.CHEERING, MascotPose.TALKING -> null
    }

/** Whether Android plays animations (its animator duration scale is not off). */
@Composable
fun rememberAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Whether the mascot in [pose] has its eyes closed just now: a blink every few seconds, while [animate]. */
@Composable
fun rememberBlinking(pose: MascotPose, animate: Boolean): Boolean {
    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(pose, animate) {
        blinking = false
        if (!animate || pose.blinkDrawable == null) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2000, 4500))
            blinking = true
            delay(160)
            blinking = false
        }
    }
    return blinking
}
