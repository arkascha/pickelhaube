package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R

/**
 * The best of the numbers the one typed may be heading for, between the number field and the keypad:
 * a tap takes it; "2 more ▾" opens all of them. Empty while there are none – its height stays.
 */
@Composable
internal fun SuggestionRow(suggestions: List<Suggestion>, onSuggestion: (Suggestion) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val best = suggestions.firstOrNull() ?: return@Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
        ) {
            SuggestionText(
                best,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.dialer_take_suggestion),
                        onClick = { onSuggestion(best) },
                    )
                    .padding(horizontal = 14.dp),
            )
            if (suggestions.size > 1) {
                var open by remember { mutableStateOf(false) }
                Box {
                    val more = suggestions.size - 1
                    val description = pluralStringResource(R.plurals.dialer_more_suggestions, more, more)
                    // Looks like the drop-down of "Ring phone": a list to open, not a count of calls.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable(role = Role.DropdownList, onClick = { open = true })
                            .semantics { contentDescription = description }
                            .padding(start = 12.dp, end = 8.dp),
                    ) {
                        VerticalDivider(Modifier.height(24.dp).padding(end = 12.dp))
                        Text(
                            pluralStringResource(R.plurals.dialer_more_suggestions_short, more, more),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        suggestion.name?.let { Text(it, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        Text(
                                            LocalNumberDisplay.current(suggestion.number),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = { open = false; onSuggestion(suggestion) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Name and number of a [suggestion] on one line: the number is cut short first, the name only when it is long. */
@Composable
private fun SuggestionText(suggestion: Suggestion, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        suggestion.name?.let {
            Text(it, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            LocalNumberDisplay.current(suggestion.number),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (suggestion.name != null) MaterialTheme.typography.bodyMedium else LocalTextStyle.current,
            color = if (suggestion.name != null) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
