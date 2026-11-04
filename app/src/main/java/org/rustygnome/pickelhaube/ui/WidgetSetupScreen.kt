package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.ui.theme.TextRoles
import org.rustygnome.pickelhaube.widget.WidgetList

/**
 * Setup of a dial widget: its title and the numbers it offers, in their order. Numbers are added
 * from the number picker (recent calls, speed dials, phone books).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSetupScreen(
    list: WidgetList,
    onTitleChange: (String) -> Unit,
    onTransparentChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    /** Moves an entry up (-1) or down (+1). */
    onMove: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = list.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.widget_setup_name)) },
                placeholder = { Text(stringResource(R.string.app_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OptionTitle(
                    stringResource(R.string.widget_setup_transparent),
                    listOf(stringResource(R.string.widget_setup_transparent_help)),
                    Modifier.weight(1f),
                )
                Switch(checked = list.transparent, onCheckedChange = onTransparentChange)
            }
            if (list.entries.isEmpty()) {
                Text(stringResource(R.string.widget_setup_hint), style = TextRoles.hint)
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                itemsIndexed(list.entries) { i, entry ->
                    ListItem(
                        headlineContent = { Text(entry.name.ifBlank { entry.number }) },
                        supportingContent = if (entry.name.isNotBlank()) {
                            { Text(entry.number) }
                        } else null,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onMove(i, -1) }, enabled = i > 0) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.widget_setup_up))
                                }
                                IconButton(onClick = { onMove(i, 1) }, enabled = i < list.entries.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.widget_setup_down))
                                }
                                IconButton(onClick = { onRemove(i) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.widget_setup_remove, entry.name.ifBlank { entry.number }),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  " + stringResource(R.string.widget_setup_add))
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.widget_setup_save))
            }
        }
    }
}
