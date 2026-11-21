package org.rustygnome.pickelhaube.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.SpeedDial

/** ⌫ button: tap deletes the last digit, long press clears the whole number. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BackspaceButton(onBackspace: () -> Unit, onClear: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.dialer_delete_digit),
                onLongClickLabel = stringResource(R.string.dialer_clear_number),
                onClick = onBackspace,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClear()
                },
            ),
    ) {
        Icon(BackspaceIcon, contentDescription = stringResource(R.string.dialer_backspace_description))
    }
}

private data class Key(val main: Char, val sub: String = "")

private val KEYS = listOf(
    listOf(Key('1'), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*'), Key('0', "+"), Key('#')),
)

/**
 * 4×3 keypad whose keys are sized to fit the available width *and* height. Keys with one of this
 * phone's [speedDials] have a small dot.
 */
@Composable
internal fun DialPad(
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
    speedDials: List<SpeedDial>,
    /** The keys grow up to this size, no further. */
    maxKey: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gap = 10.dp
        val byWidth = (maxWidth - gap * 2) / 3
        val byHeight = (maxHeight - gap * 3) / 4
        val keySize: Dp = min(min(byWidth, byHeight), maxKey).coerceAtLeast(36.dp)

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            KEYS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { key ->
                        DialKey(key, keySize, speedDials.firstOrNull { it.slot == key.main.toString() }, onDigit, onLongDigit)
                    }
                }
            }
        }
    }
}

/**
 * Round key. A long press on a digit is for speed dials; without one it acts like a tap, except
 * "0", which inserts "+" like on a regular phone keypad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    key: Key,
    size: Dp,
    speedDial: SpeedDial?,
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val longPress: (() -> Unit)? = if (key.main.isDigit() || key.main == '*') {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongDigit(key.main)
        }
    } else null
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberKeyBounce(interaction)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = {
                    // A tiny tap, as on a phone's dial pad (as far as the system's touch feedback is on).
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onDigit(key.main)
                },
                onLongClickLabel = if (key.main == '*') stringResource(R.string.dialer_insert_pause)
                else speedDial?.let {
                    stringResource(R.string.dialer_speed_dial_label, it.slot, it.name)
                },
                onLongClick = longPress,
            ),
    ) {
        if (speedDial != null) {
            // Marks a key with a speed dial, at the top of the round key.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.1f)
                    .size((size * 0.08f).coerceAtLeast(4.dp))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                key.main.toString(),
                fontSize = (size.value * 0.36f).sp,
                lineHeight = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            // Letters only when the key is large enough to show them legibly.
            if (key.sub.isNotEmpty() && size >= 52.dp) {
                Text(
                    key.sub,
                    fontSize = (size.value * 0.13f).sp,
                    lineHeight = (size.value * 0.15f).sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** How far a key gives way under the finger. */
private const val KEY_PRESSED_SCALE = 0.9f

/**
 * A key's size while pressed: it gives way under the finger and springs back with a little
 * overshoot when let go. A tap is often over before it has given way much – then it dips first, so
 * each key press is seen. Animations switched off in the system (developer options, accessibility)
 * leave it at its size.
 */
@Composable
private fun rememberKeyBounce(interaction: InteractionSource): Animatable<Float, AnimationVector1D> {
    val scale = remember { Animatable(1f) }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(KEY_PRESSED_SCALE, spring(stiffness = Spring.StiffnessHigh))
        } else {
            if (scale.value > (1f + KEY_PRESSED_SCALE) / 2) scale.animateTo(KEY_PRESSED_SCALE, tween(KEY_DIP_MILLIS))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    return scale
}

/** How long a tap's dip takes, when the key had hardly given way. */
private const val KEY_DIP_MILLIS = 50
