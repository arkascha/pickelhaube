package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.min
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

/** Letters of the quick-jump bar: "#" (digits, symbols) first, as they also sort first. */
val INDEX_LETTERS: List<Char> = listOf('#') + ('A'..'Z')

/** The bar letter a name belongs to: first letter without accents (Ä → A), else '#'. */
fun indexLetter(name: String): Char {
    val first = name.trimStart().firstOrNull() ?: return '#'
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
        .firstOrNull()?.uppercaseChar() ?: return '#'
    return if (base in 'A'..'Z') base else '#'
}

/**
 * Vertical A–Z bar. Tapping a letter – or sliding the finger along the bar – calls
 * [onLetter]. Letters without entries are dimmed but still jump to the next one.
 */
@Composable
fun AlphabetBar(
    available: Set<Char>,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var lastIndex by remember { mutableIntStateOf(-1) }
    // The letters grow with the font size only as far as the bar's height leaves room for all of them.
    val letterSize = with(LocalDensity.current) { min(LETTER_SIZE.toPx(), height / INDEX_LETTERS.size * 0.85f).toSp() }

    fun select(y: Float) {
        val index = (y / height * INDEX_LETTERS.size).toInt().coerceIn(0, INDEX_LETTERS.lastIndex)
        if (index != lastIndex) {
            lastIndex = index
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(INDEX_LETTERS[index])
        }
    }

    Column(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            // Only a finger can slide along it; screen readers reach the entries by the list and its search.
            .clearAndSetSemantics {}
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    lastIndex = -1
                    select(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        if (change.positionChange().y != 0f) select(change.position.y)
                        change.consume()
                    }
                }
            },
    ) {
        INDEX_LETTERS.forEach { letter ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter.toString(),
                    fontSize = letterSize,
                    lineHeight = letterSize,
                    fontWeight = FontWeight.SemiBold,
                    color = if (letter in available) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
            }
        }
    }
}

/** The letters' size at the normal font size. */
private val LETTER_SIZE = 11.sp
