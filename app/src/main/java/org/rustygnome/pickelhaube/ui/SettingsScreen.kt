package org.rustygnome.pickelhaube.ui

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.ClipboardManager
import android.content.ClipData
import org.rustygnome.pickelhaube.BuildConfig
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import org.rustygnome.pickelhaube.ui.theme.TextRoles
import org.rustygnome.pickelhaube.data.HostCheck
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.data.BoxProfile
import org.rustygnome.pickelhaube.data.SettingsFileInfo
import org.rustygnome.pickelhaube.data.DeviceCallLog
import org.rustygnome.pickelhaube.data.DeviceContacts
import org.rustygnome.pickelhaube.tile.PickelhaubeTile
import org.rustygnome.pickelhaube.tile.TileAddResult
import org.rustygnome.pickelhaube.tile.canRequestAddTile
import org.rustygnome.pickelhaube.tile.isTileAdded
import org.rustygnome.pickelhaube.tile.requestAddTile
import org.rustygnome.pickelhaube.tr064.BoxInfo
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.BoxUser
import org.rustygnome.pickelhaube.tr064.DemoBox
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.UserRights
import kotlinx.coroutines.delay

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
            VersionInfo()
            Text(
                stringResource(R.string.trademark_notice),
                style = TextRoles.hint,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * Switch for following the calls on the box' call monitor and, while on, whether each box has its
 * monitor on – asked when the settings open (the boxes reachable from here), and again on request,
 * e.g. right after switching it on at the box.
 */
@Composable
private fun CallStatusSetting(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    profiles: List<BoxProfile>,
    checking: Boolean,
    canCheck: Boolean,
    onCheck: () -> Unit,
) {
    LaunchedEffect(Unit) { if (enabled) onCheck() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_call_status_switch),
            listOf(stringResource(R.string.settings_call_status_hint), stringResource(R.string.settings_call_status_privacy)),
            Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = onChange)
    }
    if (!enabled) return
    profiles.forEach { profile ->
        Text(
            when (profile.callMonitor) {
                true -> stringResource(R.string.settings_call_monitor_on, profile.name)
                false -> stringResource(R.string.settings_call_monitor_off, profile.name)
                null -> stringResource(R.string.settings_call_monitor_unknown, profile.name)
            },
            style = TextRoles.finding,
            color = if (profile.callMonitor == false) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
    if (canCheck) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCheck, enabled = !checking) {
                Text(stringResource(R.string.settings_call_monitor_check))
            }
            if (checking) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * The quick settings tiles: each one already there is shown as such; for the others, from Android 13
 * on, a button which lets Android ask whether to add it; before that (and if adding fails) how to add
 * them by hand.
 */
@Composable
private fun TilesSetting() {
    val context = LocalContext.current
    // The outcome of the last request, shown under the buttons.
    var result by remember { mutableStateOf<Pair<PickelhaubeTile, TileAddResult>?>(null) }
    // The tiles in the quick settings; checked again on returning (one may have been removed meanwhile).
    var added by remember { mutableStateOf(PickelhaubeTile.entries.filter { isTileAdded(context, it) }.toSet()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        added = PickelhaubeTile.entries.filter { isTileAdded(context, it) }.toSet()
    }
    PickelhaubeTile.entries.filter { it in added }.forEach { tile ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "  " + stringResource(R.string.settings_tile_already_added, stringResource(tile.label)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (canRequestAddTile) {
        PickelhaubeTile.entries.filter { it !in added }.forEach { tile ->
            OutlinedButton(
                onClick = {
                    result = null
                    requestAddTile(context, tile) { outcome ->
                        result = tile to outcome
                        added = PickelhaubeTile.entries.filter { isTileAdded(context, it) }.toSet()
                    }
                },
            ) {
                Icon(painterResource(tile.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + stringResource(R.string.settings_tile_add, stringResource(tile.label)))
            }
        }
    }
    // An "already there" is shown by the list above.
    result?.takeIf { it.first !in added }?.let { (tile, outcome) ->
        val name = stringResource(tile.label)
        Text(
            when (outcome) {
                TileAddResult.ADDED -> stringResource(R.string.settings_tile_added, name)
                TileAddResult.ALREADY_ADDED -> stringResource(R.string.settings_tile_already_added, name)
                TileAddResult.NOT_ADDED -> stringResource(R.string.settings_tile_not_added, name)
                TileAddResult.FAILED -> stringResource(R.string.settings_tile_failed, name)
            },
            style = TextRoles.finding,
            color = if (outcome == TileAddResult.FAILED) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
    if (added.size < PickelhaubeTile.entries.size && (!canRequestAddTile || result?.second == TileAddResult.FAILED)) {
        Text(
            stringResource(R.string.settings_tiles_by_hand),
            style = TextRoles.hint,
        )
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

@Composable
private fun ConfirmDeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_title, name)) },
        text = { Text(stringResource(R.string.settings_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Saving waits: another box (or none) answers at the host of the one edited – it can be saved without
 * checking –, or a new one is set up already – it can be added anyway.
 */
@Composable
internal fun SaveCheckDialog(check: SaveCheck, onSaveUnchecked: () -> Unit, onSaveAnyway: () -> Unit, onDismiss: () -> Unit) {
    val (title, text) = when (val found = check.check) {
        is HostCheck.OtherBox -> R.string.save_check_other_title to stringResource(R.string.save_check_other_text, check.host, found.name, check.name)
        is HostCheck.AlreadySetUp -> R.string.save_check_known_title to stringResource(R.string.save_check_known_text, check.host, found.name)
        else -> R.string.save_check_none_title to stringResource(R.string.save_check_none_text, check.host, check.name)
    }
    val alreadySetUp = check.check is HostCheck.AlreadySetUp
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = if (alreadySetUp) onSaveAnyway else onSaveUnchecked) {
                Text(stringResource(if (alreadySetUp) R.string.save_anyway else R.string.save_unchecked))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Which version this is, as the build says – a long press copies it, for reporting a problem – and
 * where its source code is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionInfo() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = {
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
            Text(
                stringResource(R.string.settings_source_code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri(SOURCE_CODE_URL) },
            )
        }
    }
}

private const val SOURCE_CODE_URL = "https://github.com/arkascha/pickelhaube"

/** What the demo box is, and filling it in on the page for a new connection. */
@Composable
private fun DemoBoxDialog(onFill: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_demo_title)) },
        text = { Text(stringResource(R.string.connection_demo_hint), fontStyle = FontStyle.Italic) },
        confirmButton = { TextButton(onClick = onFill) { Text(stringResource(R.string.connection_demo_fill)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Router connection: host, port, HTTPS, user, password, whether the box' phone books, call list and
 * speed dials are used, what the box said about itself, and the trusted certificate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    initial: FritzConfig,
    canGoBack: Boolean,
    /** Whether a certificate of the box has been confirmed (for HTTPS). */
    hasTrustedCert: Boolean,
    busy: Boolean,
    status: UiText?,
    /** Name of the connection being edited (empty for a new one). */
    initialName: String,
    /** Adding a new FritzBox rather than editing an existing one. */
    isNew: Boolean,
    /** Whether this box' phone books, call list and speed dials are used (all on for a new box). */
    initialPhonebooksEnabled: Boolean,
    initialCallListEnabled: Boolean,
    initialSpeedDialsEnabled: Boolean,
    initialInternalCallsEnabled: Boolean,
    /** Model, FRITZ!OS version and serial number as last read from the box; null for a new one. */
    boxInfo: BoxInfo?,
    /** The box' country and area code as last read; null when not known. */
    dialingCodes: DialingCodes?,
    /** What the box last said the configured user may do; null when not known. */
    userRights: UserRights?,
    /** The users of the box at the entered address, offered for the user name (empty = none known). */
    boxUsers: List<BoxUser>,
    /** Asks the box at the entered host, port and HTTPS setting for its users. */
    onLookUpUsers: (FritzConfig) -> Unit,
    /** The entered host leads to another box than this one, or to one set up already. */
    hostNote: HostNote?,
    /** Saving waits for a decision, as the box at the host is not (or not only) this one. */
    saveCheck: SaveCheck?,
    onSaveUnchecked: () -> Unit,
    onSaveAnyway: () -> Unit,
    onDismissSaveCheck: () -> Unit,
    /** The HTTPS port the box at the entered host reported, if it did. */
    securityPort: SecurityPort?,
    /** Asks the box at the entered host for its HTTPS port. */
    onLookUpSecurityPort: (String) -> Unit,
    onSave: (
        config: FritzConfig,
        name: String,
        phonebooksEnabled: Boolean,
        callListEnabled: Boolean,
        speedDialsEnabled: Boolean,
        internalCallsEnabled: Boolean,
    ) -> Unit,
    onBack: () -> Unit,
    /** Deletes the edited connection; null for a new one. */
    onDelete: (() -> Unit)?,
    onShowCertificate: () -> Unit,
    onForgetCertificate: () -> Unit,
    /** Offered in the top bar instead of entering a connection (no box set up yet); null: not offered. */
    importOffer: (@Composable () -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var confirmDelete by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var useHttps by remember { mutableStateOf(initial.useHttps) }
    var phonebooksEnabled by remember { mutableStateOf(initialPhonebooksEnabled) }
    var callListEnabled by remember { mutableStateOf(initialCallListEnabled) }
    var speedDialsEnabled by remember { mutableStateOf(initialSpeedDialsEnabled) }
    var internalCallsEnabled by remember { mutableStateOf(initialInternalCallsEnabled) }
    val portValue = port.toIntOrNull()
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = host.isNotBlank() && portValue != null && portValue in 1..65535

    // Once the address has stopped changing for a moment, ask the box there for its users.
    LaunchedEffect(host, portValue, useHttps) {
        if (!valid) return@LaunchedEffect
        delay(USER_LOOKUP_DELAY_MILLIS)
        onLookUpUsers(initial.copy(host = host.trim(), port = portValue, useHttps = useHttps))
    }

    // A box with a single user – the usual case, often the one it made for logging in with a password
    // only – gets it entered, unless another name has been typed in. When the address changes, a name
    // entered that way gives way to the single user of the box there, or is taken out again.
    var userTakenOver by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(boxUsers) {
        if (boxUsers.isEmpty() || (user.isNotBlank() && user != userTakenOver)) return@LaunchedEffect
        val single = boxUsers.singleOrNull()?.name
        if (single == null && user == userTakenOver) user = ""
        if (single != null) user = single
        userTakenOver = single
    }

    // With HTTPS, ask the box on which port it talks it, rather than assume the usual one.
    LaunchedEffect(host, useHttps) {
        if (!useHttps || host.isBlank()) return@LaunchedEffect
        delay(USER_LOOKUP_DELAY_MILLIS)
        onLookUpSecurityPort(host.trim())
    }
    // The port the box reported for the entered host, if any.
    val boxHttpsPort = securityPort?.takeIf { it.host == host.trim() }?.port
    // Taken over unless the user chose a port of their own (anything but none or the usual ones).
    var suggestedPort by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(boxHttpsPort, useHttps) {
        val reported = boxHttpsPort?.toString() ?: return@LaunchedEffect
        if (useHttps && port in setOf("", FritzConfig.HTTP_PORT.toString(), FritzConfig.HTTPS_PORT.toString(), suggestedPort)) {
            port = reported
            suggestedPort = reported
        }
    }

    var showDemoOffer by remember { mutableStateOf(false) }

    // Edge to edge, the window no longer shrinks for the keyboard: the content makes room for it
    // itself, so fields further down can be scrolled to (and are, when tapped).
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.settings_add_box else R.string.connection_title)) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    importOffer?.invoke()
                    // What the box needs before it can be used – asked about only when it fails.
                    HelpButton(stringResource(R.string.connection_help_title), listOf(stringResource(R.string.connection_help)))
                },
            )
        },
    ) { padding ->
        if (showDemoOffer) {
            DemoBoxDialog(
                onFill = {
                    showDemoOffer = false
                    name = "Demo"
                    host = DemoBox.HOST
                    port = FritzConfig.HTTP_PORT.toString()
                    useHttps = false
                    user = DemoBox.USERNAME
                    password = DemoBox.PASSWORD
                },
                onDismiss = { showDemoOffer = false },
            )
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Without a box at hand (and for the app store's review): the box that answers in the app
            // itself – a single line at the top, so it is seen at once without being in the way.
            if (isNew && !DemoBox.isDemo(host)) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(role = Role.Button) { showDemoOffer = true }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.connection_demo_question),
                        style = MaterialTheme.typography.bodyMedium.merge(TextRoles.hint.copy(fontSize = TextUnit.Unspecified, lineHeight = TextUnit.Unspecified)),
                    )
                    Text(
                        stringResource(R.string.connection_demo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.connection_name)) },
                placeholder = { Text(stringResource(R.string.connection_name_placeholder), maxLines = 1, color = TextRoles.placeholder) },
                // Only a label – the box is reached by its host, not looked for by this name.
                trailingIcon = { HelpButton(stringResource(R.string.connection_name), listOf(stringResource(R.string.connection_name_hint))) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host, onValueChange = { host = it },
                label = { Text(stringResource(R.string.connection_host)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            hostNote?.takeIf { it.host == host.trim() }?.let { note ->
                when (val check = note.check) {
                    is HostCheck.OtherBox -> Text(
                        stringResource(R.string.connection_host_other, note.host, check.name),
                        style = TextRoles.finding,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is HostCheck.AlreadySetUp -> Text(
                        stringResource(R.string.connection_host_known, note.host, check.name),
                        style = TextRoles.finding,
                    )
                    else -> {}
                }
            }
            // What the box at the stored address said about itself – not about one just typed in.
            if (boxInfo != null && host.trim() == initial.host) {
                Column(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BoxInfoSection(boxInfo, dialingCodes)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OptionTitle(
                    stringResource(R.string.connection_https),
                    listOf(stringResource(R.string.connection_https_hint, boxHttpsPort ?: FritzConfig.HTTPS_PORT)),
                    Modifier.weight(1f),
                )
                Switch(
                    checked = useHttps,
                    onCheckedChange = { checked ->
                        useHttps = checked
                        // Swap the port along with the protocol unless the user chose a custom one.
                        if (checked && port in setOf("", FritzConfig.HTTP_PORT.toString())) {
                            port = (boxHttpsPort ?: FritzConfig.HTTPS_PORT).toString()
                        } else if (!checked && port in setOf("", FritzConfig.HTTPS_PORT.toString(), suggestedPort)) {
                            port = FritzConfig.HTTP_PORT.toString()
                        }
                    },
                )
            }
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.connection_port)) },
                supportingText = if (useHttps && boxHttpsPort != null && port == boxHttpsPort.toString()) {
                    { Text(stringResource(R.string.connection_port_from_box), style = TextRoles.finding) }
                } else null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            // A user chosen from the list: the keyboard (if it was up) goes away, and the password field
            // is scrolled into view – typing it is the next step, but tapping into it is left to the user.
            val passwordInView = remember { BringIntoViewRequester() }
            val focusManager = LocalFocusManager.current
            val scope = rememberCoroutineScope()
            UserField(
                value = user,
                onValueChange = { user = it },
                users = boxUsers,
                takenOver = user == userTakenOver && user == boxUsers.singleOrNull()?.name,
                onChosen = {
                    focusManager.clearFocus()
                    scope.launch {
                        delay(300) // until the keyboard is gone and the page has its full height again
                        passwordInView.bringIntoView()
                    }
                },
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.connection_password),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(passwordInView),
            )
            BoxFeatureSetting(
                title = stringResource(R.string.connection_phonebooks),
                hint = stringResource(R.string.connection_phonebooks_hint),
                enabled = phonebooksEnabled,
                onChange = { phonebooksEnabled = it },
            )
            BoxFeatureSetting(
                title = stringResource(R.string.connection_call_list),
                hint = stringResource(R.string.connection_call_list_hint),
                enabled = callListEnabled,
                onChange = { callListEnabled = it },
            )
            // Known rights belong to the stored user, not to one just typed in.
            if (userRights?.phone == false && user.trim() == initial.username) {
                Text(
                    stringResource(R.string.connection_call_list_no_right, initial.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            BoxFeatureSetting(
                title = stringResource(R.string.connection_speed_dials),
                hint = stringResource(R.string.connection_speed_dials_hint),
                enabled = speedDialsEnabled,
                onChange = { speedDialsEnabled = it },
            )
            BoxFeatureSetting(
                title = stringResource(R.string.connection_internal_calls),
                hint = stringResource(R.string.connection_internal_calls_hint),
                enabled = internalCallsEnabled,
                onChange = { internalCallsEnabled = it },
            )
            Button(
                onClick = {
                    keyboard?.hide()
                    onSave(
                        initial.copy(
                            host = host.trim(),
                            port = portValue ?: FritzConfig.HTTP_PORT,
                            username = user.trim(),
                            password = password,
                            useHttps = useHttps,
                        ),
                        name,
                        phonebooksEnabled,
                        callListEnabled,
                        speedDialsEnabled,
                        internalCallsEnabled,
                    )
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (busy) R.string.connection_connecting else R.string.connection_save)) }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (busy && status != null) {
                Text(status.asString(), style = TextRoles.finding)
            }

            if (userRights != null && userRights.access.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                UserRightsSection(userRights.username ?: initial.username, userRights)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.connection_trusted_cert), style = MaterialTheme.typography.titleSmall)
            if (!hasTrustedCert) {
                Text(
                    stringResource(R.string.connection_no_cert),
                    style = TextRoles.finding,
                )
            } else {
                Text(stringResource(R.string.connection_cert_trusted), style = TextRoles.finding)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onShowCertificate) { Text(stringResource(R.string.show)) }
                    OutlinedButton(onClick = onForgetCertificate) {
                        Text(stringResource(R.string.forget), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (onDelete != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  " + stringResource(R.string.connection_delete_box), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    saveCheck?.let { SaveCheckDialog(it, onSaveUnchecked, onSaveAnyway, onDismissSaveCheck) }

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            name = name.ifBlank { initial.host },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private const val USER_LOOKUP_DELAY_MILLIS = 800L

/**
 * The user name: typed, or chosen from the [users] of the box (when it told them), the one who
 * logged in last marked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserField(
    value: String,
    onValueChange: (String) -> Unit,
    users: List<BoxUser>,
    /** [value] is the box' only user, entered by the app. */
    takenOver: Boolean,
    /** A user was chosen from the list (not typed): e.g. on to the password. */
    onChosen: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = users.isNotEmpty()
    // Choosing from the list needs no keyboard: the field – which the tap or the menu would focus –
    // gives up the focus while the list is open, and the keyboard goes away.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        repeat(3) {
            focusManager.clearFocus()
            keyboard?.hide()
            delay(120)
        }
    }
    ExposedDropdownMenuBox(expanded = expanded && canOpen, onExpandedChange = { if (canOpen) expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(stringResource(R.string.connection_user)) },
            supportingText = when {
                takenOver -> {
                    { Text(stringResource(R.string.connection_user_taken_over), style = TextRoles.finding) }
                }
                canOpen -> {
                    { Text(pluralStringResource(R.plurals.connection_users_found, users.size, users.size), style = TextRoles.finding) }
                }
                else -> null
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HelpButton(stringResource(R.string.connection_user), listOf(stringResource(R.string.connection_user_hint)))
                    if (canOpen) {
                        // Only the arrow opens the list, so that tapping the help next to it does not.
                        // A plain button, not a menu anchor: an anchor of an editable field focuses it,
                        // and the keyboard would come up just for choosing from the list.
                        val chooseLabel = stringResource(R.string.connection_user_choose)
                        IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.semantics { contentDescription = chooseLabel },
                        ) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                // Only where the list hangs: as "not editable", opening the list does not focus the field
                // (and bring up the keyboard just for choosing); tapping the field still lets you type.
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = false),
        )
        ExposedDropdownMenu(expanded = expanded && canOpen, onDismissRequest = { expanded = false }) {
            users.forEach { candidate ->
                // The one in the field is marked, and why a user is there is said below its name.
                val current = candidate.name == value.trim()
                val notes = listOfNotNull(
                    stringResource(R.string.connection_user_made_by_box).takeIf { candidate.madeByBox },
                    stringResource(R.string.connection_user_last).takeIf { candidate.lastUsed },
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                candidate.name,
                                fontWeight = if (current) FontWeight.Bold else null,
                            )
                            notes.forEach {
                                Text(it, style = TextRoles.finding)
                            }
                        }
                    },
                    trailingIcon = if (current) {
                        { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.connection_user_current)) }
                    } else null,
                    modifier = if (current) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                    onClick = {
                        expanded = false
                        onValueChange(candidate.name)
                        onChosen()
                    },
                )
            }
        }
    }
}

/** What the box reported about itself – to tell it apart from others, and its dialling codes; selectable for copying. */
@Composable
private fun BoxInfoSection(info: BoxInfo, codes: DialingCodes?) {
    // Rarely needed, so folded away until asked for.
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(role = Role.Button) { open = !open },
    ) {
        Text(stringResource(R.string.connection_box_info), style = MaterialTheme.typography.titleSmall)
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!open) return
    val lines = listOfNotNull(
        info.model?.let { stringResource(R.string.connection_box_model, it) },
        info.fritzOsVersion?.let { stringResource(R.string.connection_box_fritzos, it) },
        info.serialNumber?.let { stringResource(R.string.connection_box_serial, it) },
        // As dialled: "+49", "030".
        codes?.let { stringResource(R.string.connection_box_country_code, "+" + it.countryCode) },
        codes?.areaCode?.let { stringResource(R.string.connection_box_area_code, codes.nationalPrefix + it) },
    )
    if (lines.isEmpty()) {
        Text(
            stringResource(R.string.connection_box_info_unknown),
            style = TextRoles.hint,
        )
    } else {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** What the box said the user may do, area by area, as the box' user settings name them. */
@Composable
private fun UserRightsSection(username: String, rights: UserRights) {
    // "Permissions of the user 'x'" if that fits in one line, else the shorter "Permissions of 'x'".
    BoxWithConstraints {
        val style = MaterialTheme.typography.titleSmall
        val title = stringResource(R.string.connection_rights_title, username)
        val fits = !rememberTextMeasurer()
            .measure(title, style, maxLines = 1, constraints = Constraints(maxWidth = constraints.maxWidth))
            .hasVisualOverflow
        Text(if (fits) title else stringResource(R.string.connection_rights_title_short, username), style = style)
    }
    Text(
        stringResource(R.string.connection_rights_hint),
        style = TextRoles.hint,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rights.access.forEach { (area, access) ->
            val name = when (area) {
                UserRights.BOX_ADMIN -> stringResource(R.string.right_box_admin)
                UserRights.PHONE -> stringResource(R.string.right_phone)
                UserRights.HOME_AUTO -> stringResource(R.string.right_home_auto)
                UserRights.NAS -> stringResource(R.string.right_nas)
                else -> area
            }
            val level = when (access) {
                UserRights.NONE -> stringResource(R.string.access_none)
                UserRights.READ -> stringResource(R.string.access_read)
                UserRights.READ_WRITE -> stringResource(R.string.access_read_write)
                else -> access
            }
            val color = if (access == UserRights.NONE) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
            // Bullet, area (may wrap) and its access level at the end of the line.
            Row(Modifier.padding(start = 4.dp)) {
                Text("•", style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.width(12.dp))
                Text(name, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(level, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

/** Switch for an optional source (phone books, call list, speed dials); [hint] behind a question mark. */
@Composable
private fun BoxFeatureSetting(title: String, hint: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(title, listOf(hint), Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

/**
 * Switch for the optional "this phone's contacts" feature. Turning it on asks Android for the
 * contacts permission; the feature is only enabled once that is granted.
 */
@Composable
private fun DeviceContactsSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(DeviceContacts.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        denied = !granted
        if (granted) onChange(true)
    }
    // Re-check when coming back from the Android app settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = DeviceContacts.hasPermission(context)
        if (allowed) denied = false
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_device_contacts),
            listOf(stringResource(R.string.settings_device_contacts_hint)),
            Modifier.weight(1f),
        )
        Switch(
            checked = enabled && allowed,
            onCheckedChange = { on ->
                when {
                    !on -> onChange(false)
                    allowed -> onChange(true)
                    else -> launcher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
        )
    }
    if (denied || (enabled && !allowed)) {
        Text(
            stringResource(R.string.settings_device_contacts_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text(stringResource(R.string.app_settings))
        }
    }
}

/**
 * Switch for the optional "this phone's calls" feature. Turning it on asks Android for the call log
 * permission and then tries to read the call log; only if that works the feature is enabled,
 * otherwise it stays off and [problem] says why.
 */
@Composable
private fun CallLogSetting(
    enabled: Boolean,
    problem: UiText?,
    onEnable: (granted: Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onEnable)

    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_call_log),
            listOf(stringResource(R.string.settings_call_log_hint), stringResource(R.string.settings_call_log_play)),
            Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                when {
                    !on -> onDisable()
                    DeviceCallLog.hasPermission(context) -> onEnable(true)
                    else -> launcher.launch(Manifest.permission.READ_CALL_LOG)
                }
            },
        )
    }
    if (problem != null) {
        Text(problem.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text(stringResource(R.string.app_settings))
        }
    }
}
