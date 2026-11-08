package org.rustygnome.pickelhaube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.BoxUser
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.ui.theme.TextRoles

/** The pages of the welcome tour, in their order. */
enum class WelcomeStep { HELLO, PREPARE, FIND, LOGIN, CONNECT, READY }

/** What the mascot does on a page – one picture each (placeholders until the mascot is drawn). */
enum class MascotPose { WAVING, CLIPBOARD, SEARCHING, TYPING, PUZZLED, CHEERING }

/** Where the guide explains how to prepare the FRITZ!Box. */
private const val GUIDE_PREPARE_URL = "https://www.rustygnome.org/pickelhaube/guide.html#prepare"

/**
 * The welcome tour: on first start (nothing set up yet) or started again from the settings. It
 * explains what the app does, what the box needs, asks for the box' address, name, user and
 * password, connects – and says which phone will ring. Skipping leads to the full connection page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    /** Nothing set up yet: the tour offers restoring a backup, and "back" on its first page leaves the app. */
    firstStart: Boolean,
    box: WelcomeBox?,
    boxUsers: List<BoxUser>,
    busy: Boolean,
    /** The tour's box is connected: it shows its last page. */
    connected: Boolean,
    /** The phone the box rings for dial help, as the box' phone name; null: none known. */
    ringPhone: String?,
    /** The box has [ringPhone] set itself (false: the app's choice, written on the first call). */
    ringPhoneOnBox: Boolean,
    saveCheck: SaveCheck?,
    onSaveUnchecked: () -> Unit,
    onSaveAnyway: () -> Unit,
    onDismissSaveCheck: () -> Unit,
    onFindBox: (host: String) -> Unit,
    onConnect: (name: String, host: String, user: String, password: String) -> Unit,
    onDemo: () -> Unit,
    /** Leaves the tour: [toForm] with the entries to the full connection page, else as skipped. */
    onLeave: (toForm: Boolean) -> Unit,
    onFinish: () -> Unit,
    /** First start only: restoring a backup instead (see [SettingsImportOffer]). */
    restoreOffer: @Composable () -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = if (connected) WelcomeStep.READY else WelcomeStep.entries[stepIndex.coerceAtMost(WelcomeStep.CONNECT.ordinal)]
    var host by rememberSaveable { mutableStateOf("fritz.box") }
    var name by rememberSaveable { mutableStateOf("") }
    // The name as taken from the box: replaced when another box is found, unless typed over.
    var nameTakenOver by rememberSaveable { mutableStateOf<String?>(null) }
    var user by rememberSaveable { mutableStateOf("") }
    var userTakenOver by rememberSaveable { mutableStateOf<String?>(null) }
    var password by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    // The box at the address: asked right away (so it is known by the time its page shows), and
    // again a moment after the address was changed.
    LaunchedEffect(host) {
        if (box?.host == host.trim()) return@LaunchedEffect
        if (box != null) delay(600)
        onFindBox(host)
    }
    // A connect attempt that ended without connecting – and without a question pending – failed.
    val failed = attempted && !busy && !connected && saveCheck == null
    // Its name for the display name, unless one was typed.
    LaunchedEffect(box) {
        val found = (box as? WelcomeBox.Found)?.let { it.name ?: it.model } ?: return@LaunchedEffect
        if (name.isBlank() || name == nameTakenOver) {
            name = found
            nameTakenOver = found
        }
    }
    // A box with a single user gets it entered (as on the connection page).
    LaunchedEffect(boxUsers) {
        val single = boxUsers.singleOrNull()?.name ?: return@LaunchedEffect
        if (user.isBlank() || user == userTakenOver) {
            user = single
            userTakenOver = single
        }
    }

    val back: (() -> Unit)? = when {
        step == WelcomeStep.READY -> null
        stepIndex > 0 -> { { stepIndex-- } }
        !firstStart -> { { onLeave(false) } }
        else -> null
    }
    BackHandler(enabled = back != null) { back?.invoke() }

    saveCheck?.let { SaveCheckDialog(it, onSaveUnchecked, onSaveAnyway, onDismissSaveCheck) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (back != null) {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (step != WelcomeStep.READY) {
                        TextButton(onClick = { onLeave(false) }, enabled = !busy) { Text(stringResource(R.string.welcome_skip)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    WelcomeStep.HELLO -> HelloPage(
                        firstStart = firstStart,
                        onStart = { stepIndex = WelcomeStep.PREPARE.ordinal },
                        onDemo = { attempted = true; onDemo() },
                        busy = busy,
                        restoreOffer = restoreOffer,
                    )
                    WelcomeStep.PREPARE -> PreparePage()
                    WelcomeStep.FIND -> FindPage(
                        host = host,
                        onHostChange = { host = it },
                        name = name,
                        onNameChange = { name = it },
                        box = box,
                    )
                    WelcomeStep.LOGIN -> LoginPage(
                        user = user,
                        onUserChange = { user = it },
                        users = boxUsers,
                        userTakenOver = user == userTakenOver && user == boxUsers.singleOrNull()?.name,
                        password = password,
                        onPasswordChange = { password = it },
                    )
                    WelcomeStep.CONNECT -> ConnectPage(
                        name = name.ifBlank { host.trim() },
                        host = host.trim(),
                        user = user.trim(),
                        busy = busy,
                        failed = attempted && failed,
                        onAllSettings = { onLeave(true) },
                    )
                    WelcomeStep.READY -> ReadyPage(ringPhone, ringPhoneOnBox)
                }
            }

            // The page's way on, and where the tour stands.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    WelcomeStep.HELLO -> Unit // its buttons are part of the page
                    WelcomeStep.PREPARE -> NextButton(enabled = true) { stepIndex = WelcomeStep.FIND.ordinal }
                    WelcomeStep.FIND -> NextButton(enabled = host.isNotBlank()) { stepIndex = WelcomeStep.LOGIN.ordinal }
                    WelcomeStep.LOGIN -> NextButton(enabled = user.isNotBlank() && password.isNotEmpty()) {
                        stepIndex = WelcomeStep.CONNECT.ordinal
                    }
                    WelcomeStep.CONNECT -> Button(
                        onClick = {
                            attempted = true
                            onConnect(name.ifBlank { host.trim() }, host, user, password)
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (busy) R.string.welcome_connecting else R.string.welcome_connect_button)) }
                    WelcomeStep.READY -> Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.welcome_start_dialling))
                    }
                }
                StepDots(step)
            }
        }
    }
}

@Composable
private fun NextButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.welcome_next)) }
}

/** One dot per page, the current one filled. */
@Composable
private fun StepDots(step: WelcomeStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WelcomeStep.entries.forEach {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (it == step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    ),
            )
        }
    }
}

/**
 * The mascot in [pose], saying [text] in a bubble. Until the mascot is drawn, every pose shows the
 * app icon's helmet.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun Mascot(pose: MascotPose, text: String?, size: Dp = 120.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF005EA2)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(size * 1.5f),
            )
        }
        if (text != null) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
}

@Composable
private fun HelloPage(
    firstStart: Boolean,
    onStart: () -> Unit,
    onDemo: () -> Unit,
    busy: Boolean,
    restoreOffer: @Composable () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Mascot(MascotPose.WAVING, stringResource(R.string.welcome_hello), size = 140.dp)
    Text(stringResource(R.string.welcome_hello_next), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Button(onClick = onStart, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.welcome_start)) }
    // Small on purpose: whoever installs Pickelhaube usually has a FRITZ!Box.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.connection_demo_question), style = TextRoles.hint)
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.connection_demo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(enabled = !busy, role = Role.Button, onClick = onDemo)
                .padding(vertical = 8.dp),
        )
    }
    if (firstStart) restoreOffer()
}

@Composable
private fun PreparePage() {
    val uriHandler = LocalUriHandler.current
    PageTitle(stringResource(R.string.welcome_prepare_title))
    Mascot(MascotPose.CLIPBOARD, stringResource(R.string.welcome_prepare), size = 96.dp)
    listOf(
        R.string.welcome_prepare_access_title to R.string.welcome_prepare_access,
        R.string.welcome_prepare_dial_help_title to R.string.welcome_prepare_dial_help,
        R.string.welcome_prepare_user_title to R.string.welcome_prepare_user,
    ).forEachIndexed { i, (title, text) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text(
        stringResource(R.string.welcome_prepare_how),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(role = Role.Button) { uriHandler.openUri(GUIDE_PREPARE_URL) }
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun FindPage(
    host: String,
    onHostChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    box: WelcomeBox?,
) {
    PageTitle(stringResource(R.string.welcome_find_title))
    val (pose, text) = when (box) {
        is WelcomeBox.Found -> {
            val what = box.model ?: box.name ?: box.host
            MascotPose.CHEERING to if (box.setUpAs != null) stringResource(R.string.welcome_find_set_up, what, box.setUpAs)
            else stringResource(R.string.welcome_find_found, what)
        }
        is WelcomeBox.NotFound -> MascotPose.PUZZLED to stringResource(R.string.welcome_find_none, box.host)
        else -> MascotPose.SEARCHING to stringResource(R.string.welcome_find)
    }
    Mascot(pose, text, size = 96.dp)
    OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        label = { Text(stringResource(R.string.connection_host)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        trailingIcon = if (box is WelcomeBox.Searching) {
            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.connection_name)) },
        supportingText = { Text(stringResource(R.string.welcome_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LoginPage(
    user: String,
    onUserChange: (String) -> Unit,
    users: List<BoxUser>,
    userTakenOver: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
) {
    PageTitle(stringResource(R.string.welcome_login_title))
    Mascot(MascotPose.TYPING, stringResource(R.string.welcome_login), size = 96.dp)
    UserField(value = user, onValueChange = onUserChange, users = users, takenOver = userTakenOver)
    PasswordField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.connection_password),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.welcome_login_tip), style = TextRoles.hint)
}

@Composable
private fun ConnectPage(name: String, host: String, user: String, busy: Boolean, failed: Boolean, onAllSettings: () -> Unit) {
    PageTitle(stringResource(R.string.welcome_connect_title))
    Mascot(
        if (failed && !busy) MascotPose.PUZZLED else MascotPose.TYPING,
        stringResource(if (failed && !busy) R.string.welcome_connect_failed else R.string.welcome_connect),
        size = 96.dp,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow(stringResource(R.string.connection_name), name)
            SummaryRow(stringResource(R.string.welcome_address), host)
            SummaryRow(stringResource(R.string.connection_user), user)
        }
    }
    if (busy) CircularProgressIndicator()
    if (failed && !busy) {
        TextButton(onClick = onAllSettings) { Text(stringResource(R.string.welcome_all_settings)) }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column {
        Text(label, style = TextRoles.hint)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReadyPage(ringPhone: String?, ringPhoneOnBox: Boolean) {
    PageTitle(stringResource(R.string.welcome_ready_title))
    val phone = ringPhone?.let(FritzDialer::displayName)
    Mascot(
        MascotPose.CHEERING,
        when {
            phone == null -> stringResource(R.string.welcome_ready_no_phone)
            ringPhoneOnBox -> stringResource(R.string.welcome_ready_ring, phone)
            else -> stringResource(R.string.welcome_ready_ring_unset, phone)
        },
        size = 120.dp,
    )
    if (phone != null) {
        Text(stringResource(R.string.welcome_ready_ring_change), style = TextRoles.hint, textAlign = TextAlign.Center)
    }
    Text(stringResource(R.string.welcome_ready_tips), style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
    listOf(R.string.welcome_tip_picker, R.string.welcome_tip_long_press, R.string.welcome_tip_tiles).forEach {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
