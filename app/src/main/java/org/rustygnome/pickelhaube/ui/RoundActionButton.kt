package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Round call button. With a [menu] (label and action each), a long press opens it – a Button has no
 * long press, so this one is drawn by hand in the Button's colours.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoundActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
    icon: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val container = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val content = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(container)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClickLabel = menu.getOrNull(1)?.first,
                    onLongClick = if (menu.isEmpty()) null else {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            open = true
                        }
                    },
                ),
        ) {
            CompositionLocalProvider(LocalContentColor provides content) { icon() }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}
