package org.rustygnome.pickelhaube.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.NetworkKind

/** The note in the dialer's title while none of the boxes is reachable, by the kind of network. */
@Composable
internal fun noBoxNotice(kind: NetworkKind?): String = stringResource(
    when (kind) {
        NetworkKind.NONE -> R.string.notice_no_network
        NetworkKind.MOBILE -> R.string.notice_mobile_data
        NetworkKind.LOCAL -> R.string.notice_other_network
        NetworkKind.VPN -> R.string.notice_vpn_no_box
        null -> R.string.notice_no_known_box
    },
)

/**
 * Tapped on that note: why no box answers – the phone is not in the home network of any of them –,
 * and what helps: Wi-Fi at home, or a VPN into it from elsewhere. Leads to Android's VPN settings (or,
 * without any network, to the internet settings), or looks for the boxes again ([onSearchAgain]).
 */
@Composable
internal fun NotAtHomeDialog(kind: NetworkKind?, onSearchAgain: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val noNetwork = kind == NetworkKind.NONE
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogMascot(MascotPose.SEARCHING) },
        title = { DialogTitle(stringResource(if (noNetwork) R.string.not_at_home_title_offline else R.string.not_at_home_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.not_at_home_how))
                Text(
                    stringResource(
                        when (kind) {
                            NetworkKind.NONE -> R.string.not_at_home_offline
                            NetworkKind.MOBILE -> R.string.not_at_home_mobile
                            NetworkKind.VPN -> R.string.not_at_home_vpn
                            NetworkKind.LOCAL, null -> R.string.not_at_home_other_network
                        },
                    ),
                )
                // In a network where none of the boxes answers – maybe at home, with another cause.
                if (kind == NetworkKind.LOCAL || kind == NetworkKind.VPN || kind == null) {
                    WebHelpLink(stringResource(R.string.web_help_connection), WebPages.FAQ_NO_CONNECTION)
                }
                OutlinedButton(onClick = {
                    val intent = when {
                        !noNetwork -> Intent(Settings.ACTION_VPN_SETTINGS)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                        else -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    }
                    // Not every device has that page: then at least its settings.
                    runCatching { context.startActivity(intent) }
                        .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    onDismiss()
                }) {
                    Text(stringResource(if (noNetwork) R.string.not_at_home_network_settings else R.string.not_at_home_vpn_settings))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onSearchAgain() }) { Text(stringResource(R.string.not_at_home_search_again)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
