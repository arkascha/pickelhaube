package org.rustygnome.pickelhaube.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.draw.clip
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

/** What the mascot does on a page – one picture each. */
enum class MascotPose { WAVING, CLIPBOARD, SEARCHING, TYPING, PUZZLED, CHEERING }

/** The part of the web page that goes with a page of the tour – opened by the (?) in its top bar. */
private val WelcomeStep.webPage: String
    get() = when (this) {
        WelcomeStep.HELLO -> WebPages.HOME
        WelcomeStep.PREPARE -> WebPages.GUIDE_PREPARE
        WelcomeStep.FIND, WelcomeStep.LOGIN, WelcomeStep.CONNECT -> WebPages.GUIDE_FIRST_START
        WelcomeStep.READY -> WebPages.GUIDE_DIALLING
    }

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
    /** The last attempt failed because the box rejected user name or password. */
    loginRejected: Boolean,
    /** The error dialog asked to go back to the password (see [changePassword] below); then [onChangePasswordShown]. */
    changePasswordRequest: Boolean,
    onChangePasswordShown: () -> Unit,
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
    // Left to the user; empty, the box' own name (or its address) is taken.
    var name by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var userTakenOver by rememberSaveable { mutableStateOf<String?>(null) }
    var password by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    // What the last connect attempt was made with (address, user, password): failed, it is not offered as the next step again.
    var attemptedLogin by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var focusPassword by rememberSaveable { mutableStateOf(false) }

    // The box at the address: asked right away (so it is known by the time its page shows), and
    // again a moment after the address was changed.
    LaunchedEffect(host) {
        if (box?.host == host.trim()) return@LaunchedEffect
        if (box != null) delay(600)
        onFindBox(host)
    }
    // A connect attempt that ended without connecting – and without a question pending – failed;
    // shown as such until the entries are changed.
    val entries = listOf(host.trim(), user.trim(), password)
    val failed = attempted && !busy && !connected && saveCheck == null && attemptedLogin == entries
    // The box said no to these very entries: correcting them comes first (see ConnectPage).
    val rejected = failed && loginRejected
    val changePassword = {
        password = ""
        focusPassword = true
        stepIndex = WelcomeStep.LOGIN.ordinal
    }
    LaunchedEffect(changePasswordRequest) {
        if (!changePasswordRequest) return@LaunchedEffect
        changePassword()
        onChangePasswordShown()
    }
    val fallbackName = (box as? WelcomeBox.Found)?.takeIf { it.host == host.trim() }?.let { it.name ?: it.model } ?: host.trim()
    val displayName = name.trim().ifEmpty { fallbackName }
    val connect = {
        attempted = true
        attemptedLogin = entries
        onConnect(displayName, host, user, password)
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
                    // Help for this page: the part of the guide on the web that goes with it.
                    val uriHandler = LocalUriHandler.current
                    IconButton(onClick = { uriHandler.openUri(step.webPage) }) {
                        Icon(
                            HelpOutlineIcon,
                            contentDescription = stringResource(R.string.web_guide),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            // Pages slide in from the side they lie on – unless Android's animations are off.
            val animate = rememberAnimationsOn()
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (!animate) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it / 2 else it / 2 } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "page",
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (page) {
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
                            fallbackName = fallbackName,
                            box = box,
                        )
                        WelcomeStep.LOGIN -> LoginPage(
                            user = user,
                            onUserChange = { user = it },
                            users = boxUsers,
                            userTakenOver = user == userTakenOver && user == boxUsers.singleOrNull()?.name,
                            password = password,
                            onPasswordChange = { password = it },
                            focusPassword = focusPassword,
                            onPasswordFocused = { focusPassword = false },
                        )
                        WelcomeStep.CONNECT -> ConnectPage(
                            name = displayName,
                            host = host.trim(),
                            user = user.trim(),
                            busy = busy,
                            failed = attempted && failed,
                            rejectedUser = user.trim().takeIf { rejected },
                        )
                        WelcomeStep.READY -> ReadyPage(ringPhone, ringPhoneOnBox)
                    }
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
                    WelcomeStep.CONNECT -> {
                        // Rejected: changing the password is offered as plainly as trying again.
                        if (rejected) {
                            OutlinedButton(onClick = changePassword, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.welcome_change_password))
                            }
                        }
                        Button(
                            onClick = connect,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(if (busy) R.string.welcome_connecting else R.string.welcome_connect_button)) }
                    }
                    WelcomeStep.READY -> Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.welcome_start_dialling))
                    }
                }
                // After a failure, the full connection page as well – right below the button, not scrolled away.
                if (step == WelcomeStep.CONNECT && failed) {
                    TextButton(onClick = { onLeave(true) }) { Text(stringResource(R.string.welcome_all_settings)) }
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
 * The mascot in [pose] (drawn by art/mascot/generate.py), saying [text] in a bubble. It bobs and
 * sways, blinks now and then and pops up with each new pose; waving, it waves every few seconds;
 * cheering, it hops and wiggles; puzzled, it tilts its head. All of that stays still while
 * Android's animations are switched off (accessibility, developer options).
 */
@Composable
private fun Mascot(pose: MascotPose, text: String?, size: Dp = 120.dp) {
    val animate = rememberAnimationsOn()
    // Always on: a bob up and down, and a slower sway from side to side – out of step, so it looks alive.
    val (bob, sway) = if (animate) {
        val transition = rememberInfiniteTransition(label = "idle")
        val bob by transition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bob",
        )
        val sway by transition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sway",
        )
        bob to sway
    } else 0f to 0f
    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(pose, animate) {
        blinking = false
        if (!animate || pose.blinkDrawable == null) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2000, 4500))
            blinking = true
            delay(160)
            blinking = false
        }
    }
    // On each new pose: a little pop; cheering, a hop and a wiggle; waving, a wave now and then.
    val pop = remember { Animatable(1f) }
    val hop = remember { Animatable(0f) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(pose, animate) {
        if (!animate) return@LaunchedEffect
        pop.snapTo(0.8f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(pose, animate) {
        wiggle.snapTo(0f)
        hop.snapTo(0f)
        if (!animate) return@LaunchedEffect
        when (pose) {
            MascotPose.CHEERING -> repeat(2) {
                coroutineScope {
                    launch {
                        hop.animateTo(-18f, tween(180, easing = FastOutSlowInEasing))
                        hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    for (angle in listOf(-12f, 10f, -7f, 4f, 0f)) wiggle.animateTo(angle, tween(90))
                }
                delay(250)
            }
            MascotPose.WAVING -> while (true) {
                delay(600)
                for (angle in listOf(8f, -6f, 8f, -6f, 0f)) wiggle.animateTo(angle, tween(140))
                delay(2400)
            }
            MascotPose.PUZZLED -> for (angle in listOf(-10f, -10f, 0f)) wiggle.animateTo(angle, tween(260))
            else -> Unit
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painterResource(if (blinking) pose.blinkDrawable ?: pose.drawable else pose.drawable),
            contentDescription = null,
            // The drawings leave room around the head for hands and things: the head itself is about [size].
            modifier = Modifier
                .size(size * 1.5f)
                .graphicsLayer {
                    translationY = (bob + hop.value).dp.toPx()
                    rotationZ = sway + wiggle.value
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
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

/** Whether Android plays animations (its animator duration scale is not off). */
@Composable
private fun rememberAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

private val MascotPose.drawable: Int
    get() = when (this) {
        MascotPose.WAVING -> R.drawable.mascot_waving
        MascotPose.CLIPBOARD -> R.drawable.mascot_clipboard
        MascotPose.SEARCHING -> R.drawable.mascot_searching
        MascotPose.TYPING -> R.drawable.mascot_typing
        MascotPose.PUZZLED -> R.drawable.mascot_puzzled
        MascotPose.CHEERING -> R.drawable.mascot_cheering
    }

/** The pose with its eyes closed, for a blink; null: the pose does not blink (its eyes are closed or squinted already). */
private val MascotPose.blinkDrawable: Int?
    get() = when (this) {
        MascotPose.WAVING -> R.drawable.mascot_waving_blink
        MascotPose.CLIPBOARD -> R.drawable.mascot_clipboard_blink
        MascotPose.SEARCHING -> R.drawable.mascot_searching_blink
        MascotPose.TYPING -> R.drawable.mascot_typing_blink
        MascotPose.PUZZLED, MascotPose.CHEERING -> null
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
    Mascot(MascotPose.WAVING, stringResource(R.string.welcome_hello), size = 112.dp)
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
            .clickable(role = Role.Button) { uriHandler.openUri(WebPages.GUIDE_PREPARE) }
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun FindPage(
    host: String,
    onHostChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    /** The name taken when [name] is left empty: the box' own, or its address. */
    fallbackName: String,
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
        placeholder = { Text(fallbackName, color = TextRoles.placeholder) },
        supportingText = { Text(stringResource(R.string.welcome_name_hint, fallbackName)) },
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
    /** Coming back to correct a rejected password: the cursor goes into the (emptied) password field. */
    focusPassword: Boolean,
    onPasswordFocused: () -> Unit,
) {
    PageTitle(stringResource(R.string.welcome_login_title))
    Mascot(MascotPose.TYPING, stringResource(R.string.welcome_login), size = 96.dp)
    // A user chosen from the list: the keyboard (if it was up) goes away, and the password field
    // is scrolled into view – typing it is the next step, but tapping into it is left to the user.
    val passwordInView = remember { BringIntoViewRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(focusPassword) {
        if (!focusPassword) return@LaunchedEffect
        delay(350) // until the page has slid in
        passwordFocus.requestFocus()
        onPasswordFocused()
    }
    UserField(
        value = user,
        onValueChange = onUserChange,
        users = users,
        takenOver = userTakenOver,
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
        onValueChange = onPasswordChange,
        label = stringResource(R.string.connection_password),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(passwordInView)
            .focusRequester(passwordFocus),
    )
    Text(stringResource(R.string.welcome_login_tip), style = TextRoles.hint)
}

@Composable
private fun ConnectPage(
    name: String,
    host: String,
    user: String,
    busy: Boolean,
    failed: Boolean,
    /** The box rejected this user with the password entered (null: it did not, or the entries changed since). */
    rejectedUser: String?,
) {
    PageTitle(stringResource(R.string.welcome_connect_title))
    Mascot(
        if (failed && !busy) MascotPose.PUZZLED else MascotPose.TYPING,
        when {
            rejectedUser != null && !busy -> stringResource(R.string.welcome_login_rejected, rejectedUser)
            failed && !busy -> stringResource(R.string.welcome_connect_failed)
            else -> stringResource(R.string.welcome_connect)
        },
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
        size = 104.dp,
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
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(R.string.welcome_ready_more),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { uriHandler.openUri(WebPages.GUIDE) }
            .padding(vertical = 8.dp),
    )
}
