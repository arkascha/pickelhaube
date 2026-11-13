package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.rustygnome.pickelhaube.R

/**
 * Calls [number] right away, from a row of the number picker – green, like the dialer's button; a tap
 * on the row itself only puts the number into the dialer. Greyed out while no box is there to call
 * through. [name]: whose it is, for accessibility services ("Call Anna").
 */
@Composable
internal fun CallButton(
    number: String,
    name: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onCall: () -> Unit,
) {
    IconButton(
        onClick = onCall,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(contentColor = CallGreen),
    ) {
        Icon(
            Icons.Default.Call,
            contentDescription = stringResource(R.string.picker_call, name?.takeIf { it.isNotBlank() } ?: number),
            tint = if (enabled) CallGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * Moves a [CallButton] at the end of a list item by its own margin, so its symbol stands where the
 * row's other symbols at the end do (the "+" of a free speed dial slot).
 */
internal val AtRowEnd = Modifier.offset(x = 12.dp)
