package org.rustygnome.pickelhaube.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.FritzDialer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneSelector(
    phones: List<String>,
    selected: String?,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = !busy && phones.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded && canOpen,
        onExpandedChange = { if (canOpen) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.let(FritzDialer::displayName)
                ?: stringResource(if (phones.isEmpty()) R.string.dialer_no_phones else R.string.dialer_choose_phone),
            onValueChange = {},
            readOnly = true,
            enabled = !busy,
            label = { Text(stringResource(R.string.dialer_ring_phone)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reloads the list from the router. The menu anchor sees the same tap and would
                    // toggle the list open, so it is explicitly kept closed here.
                    IconButton(
                        onClick = {
                            expanded = false
                            onRefresh()
                        },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dialer_reload_phones))
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && canOpen)
                    Spacer(Modifier.size(8.dp))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, canOpen),
        )
        ExposedDropdownMenu(expanded = expanded && canOpen, onDismissRequest = { expanded = false }) {
            phones.forEach { phone ->
                DropdownMenuItem(
                    text = { Text(FritzDialer.displayName(phone)) },
                    onClick = {
                        expanded = false
                        onSelect(phone)
                    },
                )
            }
        }
    }
}
