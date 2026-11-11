package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.BuildConfig
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.WhatsNew

/**
 * What is new in the [versions] (version codes, newest first – see [WhatsNew]): their points,
 * under the cheering mascot, at most [WhatsNew.MAX_POINTS] of them; the title names the newest of them.
 */
@Composable
fun WhatsNewDialog(versions: List<Int>, onDismiss: () -> Unit) {
    val points = versions.flatMap { version ->
        WhatsNew.NOTES[version]?.let { stringArrayResource(it.points).toList() }.orEmpty()
    }.take(WhatsNew.MAX_POINTS)
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Image(painterResource(R.drawable.mascot_cheering), contentDescription = null, modifier = Modifier.size(96.dp)) },
        title = { Text(stringResource(R.string.whats_new_title, WhatsNew.titleVersion(versions) ?: BuildConfig.VERSION_NAME)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                points.forEach { point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        Text(point, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    stringResource(R.string.welcome_ready_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { uriHandler.openUri(WebPages.GUIDE) }
                        .padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.whats_new_ok)) } },
    )
}
