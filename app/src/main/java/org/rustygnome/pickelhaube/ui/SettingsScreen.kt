package org.rustygnome.pickelhaube.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.ClipboardManager
import android.content.ClipData
import org.rustygnome.pickelhaube.BuildConfig
import org.rustygnome.pickelhaube.ui.theme.TextRoles
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.WhatsNew
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.SettingsFileInfo
import org.rustygnome.pickelhaube.data.DeviceCallLog

/**
 * Settings overview: the configured FritzBoxes (the one in use marked, each with whether it is in
 * this network; tap to edit), "Add FritzBox", the phone book and the recent numbers options. The
 * box in use follows the network; only when none of them is found here can one be chosen by hand
 * (tap its circle). The flag in the top bar opens the language screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profiles: List<BoxProfile>,
    activeProfileId: String?,
    /** Shown on the box in use: it was not found in this network, or the last request failed. */
    activeIssue: ActiveBoxIssue?,
    /** The boxes (ids) that answered the last detection; null before the first one. */
    boxesSeen: Set<String>?,
    /** Not even the box in use is reachable: then none counts as in use, all can be chosen. */
    noBoxReachable: Boolean,
    /** A request to the box is running – e.g. trying the one just chosen by hand. */
    busy: Boolean,
    networkNotice: UiText?,
    onEditProfile: (String) -> Unit,
    onActivateProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    /** Starts the welcome tour again: a guided way to add another box. */
    onStartWelcomeTour: () -> Unit,
    /** Shows the "What's new" note of this version again. */
    onShowWhatsNew: () -> Unit,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    deviceContactsEnabled: Boolean,
    onDeviceContactsChange: (Boolean) -> Unit,
    callLogEnabled: Boolean,
    callLogProblem: UiText?,
    /** Android's answer to the call log permission: try reading it, enable the feature if that works. */
    onEnableCallLog: (granted: Boolean) -> Unit,
    onDisableCallLog: () -> Unit,
    phoneSpeedDialsEnabled: Boolean,
    onPhoneSpeedDialsChange: (Boolean) -> Unit,
    keyTonesEnabled: Boolean,
    onKeyTonesChange: (Boolean) -> Unit,
    callStatusEnabled: Boolean,
    onCallStatusChange: (Boolean) -> Unit,
    checkingCallMonitors: Boolean,
    /** Asks the boxes reachable now whether their call monitor is on. */
    onCheckCallMonitors: () -> Unit,
    transferBusy: Boolean,
    pendingImport: SettingsFileInfo?,
    importError: UiText?,
    onExportSettings: (Uri, CharArray?) -> Unit,
    onOpenSettingsFile: (Uri) -> Unit,
    onImportSettings: (CharArray?) -> Unit,
    onCancelImport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // The national flag of the language chosen, as an emoji; a globe for "Auto".
                    val context = LocalContext.current
                    val flag = if (AppLanguage.current(context) == null) AppLanguage.AUTO_SYMBOL
                    else AppLanguage.flag(LocalConfiguration.current.locales[0])
                    val languageLabel = stringResource(R.string.settings_language)
                    IconButton(
                        onClick = onOpenLanguage,
                        modifier = Modifier.semantics { contentDescription = languageLabel },
                    ) {
                        Text(flag, fontSize = 22.sp)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.settings_connections),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Text(
                stringResource(R.string.settings_connections_hint),
                style = TextRoles.hint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            networkNotice?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Chosen automatically; by hand only when none of the boxes is in this network.
            val canChoose = boxesSeen?.isEmpty() == true
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeProfileId && !noBoxReachable,
                    // Chosen by hand while none is reachable: being tried right now.
                    trying = busy && noBoxReachable && profile.id == activeProfileId,
                    seen = boxesSeen?.contains(profile.id),
                    canChoose = canChoose,
                    busy = busy,
                    issue = activeIssue,
                    onEdit = { onEditProfile(profile.id) },
                    onActivate = { onActivateProfile(profile.id) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_add_box)) },
                leadingContent = { MarkSlot { Icon(Icons.Default.Add, contentDescription = null) } },
                modifier = Modifier.clickable(onClick = onAddProfile),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_welcome_tour)) },
                supportingContent = { Text(stringResource(R.string.settings_welcome_tour_hint), style = TextRoles.hint) },
                leadingContent = { MarkSlot { Icon(Icons.Default.Star, contentDescription = null) } },
                modifier = Modifier.clickable(onClick = onStartWelcomeTour),
            )

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.phone_books), style = MaterialTheme.typography.titleSmall)
                DeviceContactsSetting(enabled = deviceContactsEnabled, onChange = onDeviceContactsChange)
            }

            // The section holds only the phone's calls, which the Google Play build leaves out.
            if (DeviceCallLog.available) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.settings_recents), style = MaterialTheme.typography.titleSmall)
                    CallLogSetting(
                        enabled = callLogEnabled,
                        problem = callLogProblem,
                        onEnable = onEnableCallLog,
                        onDisable = onDisableCallLog,
                    )
                }
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.speed_dial), style = MaterialTheme.typography.titleSmall)
                BoxFeatureSetting(
                    title = stringResource(R.string.settings_phone_speed_dials),
                    hint = stringResource(R.string.settings_phone_speed_dials_hint),
                    enabled = phoneSpeedDialsEnabled,
                    onChange = onPhoneSpeedDialsChange,
                )
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_keypad), style = MaterialTheme.typography.titleSmall)
                BoxFeatureSetting(
                    title = stringResource(R.string.settings_key_tones),
                    hint = stringResource(R.string.settings_key_tones_hint),
                    enabled = keyTonesEnabled,
                    onChange = onKeyTonesChange,
                )
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_call_status), style = MaterialTheme.typography.titleSmall)
                CallStatusSetting(
                    enabled = callStatusEnabled,
                    onChange = onCallStatusChange,
                    profiles = profiles,
                    checking = checkingCallMonitors,
                    canCheck = !boxesSeen.isNullOrEmpty(),
                    onCheck = onCheckCallMonitors,
                )
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_tiles), style = MaterialTheme.typography.titleSmall)
                TilesSetting()
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_transfer), style = MaterialTheme.typography.titleSmall)
                SettingsTransferSetting(
                    busy = transferBusy,
                    pendingImport = pendingImport,
                    importError = importError,
                    onExport = onExportSettings,
                    onOpenImport = onOpenSettingsFile,
                    onImport = onImportSettings,
                    onCancelImport = onCancelImport,
                )
            }

            HorizontalDivider()
            VersionInfo(onShowWhatsNew)
            Text(
                stringResource(R.string.trademark_notice),
                style = TextRoles.hint,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * The column in front of the boxes: as wide as a touch target, so the check mark, the circle to tap
 * and the "Add" icon line up.
 */
@Composable
private fun MarkSlot(content: @Composable () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { content() }
}

/**
 * One configured box: check mark when in use, else – only when [canChoose] – a circle to tap to
 * switch to it, and a progress indicator while it is being [trying]; then its name and whether it
 * is reachable ([seen]; null = not detected yet). Tapping the line opens its connection page (also
 * to delete it).
 */
@Composable
private fun ProfileRow(
    profile: BoxProfile,
    active: Boolean,
    trying: Boolean,
    seen: Boolean?,
    canChoose: Boolean,
    busy: Boolean,
    issue: ActiveBoxIssue?,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
) {
    val presence = when {
        trying -> stringResource(R.string.status_connecting, profile.config.host)
        // Recognised only by its device id, learned once logged in to it.
        profile.deviceId == null -> stringResource(R.string.settings_box_never_recognised)
        seen == true -> stringResource(R.string.settings_box_here)
        seen == false -> stringResource(R.string.settings_box_not_here)
        else -> null
    }
    ListItem(
        // The name and where the box is; the details are on the connection page.
        headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // What was found out about the box; red when it is not there.
        supportingContent = presence?.let {
            {
                Text(
                    it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (seen == false && !trying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkSlot {
                    if (trying) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (active && issue != null) {
                        // In use, but not as expected: a warning instead of the check mark.
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(
                                when (issue) {
                                    ActiveBoxIssue.NOT_FOUND -> R.string.settings_in_use_not_found
                                    ActiveBoxIssue.NOT_WORKING -> R.string.settings_in_use_not_working
                                }
                            ),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    } else if (active) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.settings_in_use),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else if (canChoose) {
                        // Only one attempt at a time (a second tap would otherwise be ignored silently).
                        IconButton(onClick = onActivate, enabled = !busy) {
                            Icon(
                                CircleOutlineIcon,
                                contentDescription = stringResource(R.string.settings_use_box_now, profile.name),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Icon(
                    RouterIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The glyph's weight (the box) sits low, under thin antenna arcs: lift it to look centred.
                    modifier = Modifier.offset(y = (-2).dp),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onEdit),
    )
}

/**
 * Which version this is, as the build says – a long press copies it, for reporting a problem – and
 * where its source code is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionInfo(onShowWhatsNew: () -> Unit) {
    val context = LocalContext.current
    val version = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    // A long press copies the version (for a bug report) – and the helmet spins once, just for fun.
    val spin = rememberHelmetSpin()
    // Above the version.
    HelmetSpinPopup(spin, Alignment.TopCenter, offsetY = (-120).dp)
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = {
                spin.start()
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                    ClipData.newPlainText(version, "$version, ${BuildConfig.FLAVOR}")
                )
            })
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Text(version, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (BuildConfig.CALL_LOG) R.string.settings_build_full else R.string.settings_build_play),
                style = TextRoles.hint,
            )
            Text(" · ", style = TextRoles.hint)
            WebLink(stringResource(R.string.settings_source_code), WebPages.SOURCE_CODE)
        }
        // The note shown after updating, again – if this version has one.
        if (WhatsNew.latest(BuildConfig.VERSION_CODE) != null) {
            Text(
                stringResource(R.string.settings_whats_new),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onShowWhatsNew)
                    .padding(vertical = 6.dp),
            )
        }
        // The web page: help beyond the app's own explanations, and the privacy policy.
        Row(verticalAlignment = Alignment.CenterVertically) {
            WebLink(stringResource(R.string.web_guide), WebPages.GUIDE)
            Text(" · ", style = TextRoles.hint)
            WebLink(stringResource(R.string.web_privacy_policy), WebPages.PRIVACY_POLICY)
        }
    }
}

/** A link to [url], opened in the browser. */
@Composable
private fun WebLink(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(role = Role.Button) { uriHandler.openUri(url) }
            .padding(vertical = 6.dp),
    )
}
