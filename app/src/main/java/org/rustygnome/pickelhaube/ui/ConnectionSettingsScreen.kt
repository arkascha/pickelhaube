package org.rustygnome.pickelhaube.ui

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import org.rustygnome.pickelhaube.ui.theme.TextRoles
import org.rustygnome.pickelhaube.data.HostCheck
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.BoxInfo
import org.rustygnome.pickelhaube.tr064.DialingCodes
import org.rustygnome.pickelhaube.tr064.BoxUser
import org.rustygnome.pickelhaube.tr064.DemoBox
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.UserRights
import kotlinx.coroutines.delay

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
