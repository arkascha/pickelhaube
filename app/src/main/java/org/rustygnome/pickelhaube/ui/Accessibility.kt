package org.rustygnome.pickelhaube.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * For a switch or radio button beside its title, which it doesn't contain: screen readers then say what
 * it switches ("Phone books, off") instead of just "off".
 */
internal fun Modifier.labelled(label: String): Modifier = semantics { contentDescription = label }

/** From this font size on (Android's font size setting, 1 = normal), lines that would be cut off may wrap instead. */
internal const val LARGE_FONT_SCALE = 1.3f
