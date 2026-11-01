package org.rustygnome.pickelhaube.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle

/**
 * How the kinds of small text look, the same on every page. Values – entered, or reported by the
 * box – keep the normal text colour, labels their usual style, actions the primary and warnings the
 * error colour; these are the two kinds in between.
 */
object TextRoles {

    /** A fixed text that explains something: grey and italic, so it reads as a comment, not as content. */
    val hint: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )

    /** What the app found out about a box (e.g. its users, its HTTPS port): the accent colour. */
    val finding: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.tertiary)
}
