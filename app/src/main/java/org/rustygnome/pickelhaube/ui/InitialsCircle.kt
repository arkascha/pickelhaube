package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rustygnome.pickelhaube.shortcut.LauncherShortcuts
import java.util.Locale

/** The circle's size in the lists. */
internal val INITIALS_CIRCLE_SIZE = 40.dp

/**
 * Whose a row is, at a glance: the initials of [name] (as on a home screen shortcut, see
 * [LauncherShortcuts.initials]) on a colour of its own – the same name always the same colour – or,
 * for a number without a name, a person in grey. Only to look at: screen readers read the name in the row.
 */
@Composable
internal fun InitialsCircle(name: String?, modifier: Modifier = Modifier, size: Dp = INITIALS_CIRCLE_SIZE) {
    val initials = name?.let(LauncherShortcuts::initials)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val background = if (name != null && initials != null) nameColor(name, dark) else MaterialTheme.colorScheme.surfaceVariant
    val onBackground = when {
        initials == null -> MaterialTheme.colorScheme.onSurfaceVariant
        dark -> Color.White.copy(alpha = 0.92f)
        else -> Color.Black.copy(alpha = 0.8f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clearAndSetSemantics {},
    ) {
        if (initials != null) {
            // The circle keeps its size with large text, so its letters do too.
            val fontSize = (size.value * if (initials.length > 1) 0.38f else 0.45f).sp / LocalDensity.current.fontScale
            Text(initials, color = onBackground, fontSize = fontSize, fontWeight = FontWeight.Medium, maxLines = 1)
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = onBackground, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** How many hues the names are spread over: few enough to tell apart. */
private const val HUES = 12

/**
 * The hue of [name], one of [HUES] around the colour wheel: the same name (in any case) always the same
 * one. The name's hash is mixed first – on its own, similar names land on similar hues (most were green).
 */
internal fun nameHue(name: String): Float {
    var h = name.trim().lowercase(Locale.ROOT).hashCode()
    h = (h xor (h ushr 16)) * 0x45d9f3b
    h = (h xor (h ushr 16)) * 0x45d9f3b
    h = h xor (h ushr 16)
    return Math.floorMod(h, HUES) * (360f / HUES)
}

/** A soft colour of [name]'s hue – light behind dark letters, or dark behind light ones in the [dark] theme. */
private fun nameColor(name: String, dark: Boolean): Color =
    if (dark) Color.hsl(nameHue(name), saturation = 0.35f, lightness = 0.32f)
    else Color.hsl(nameHue(name), saturation = 0.55f, lightness = 0.85f)
