package org.rustygnome.pickelhaube

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings exported with the passwords under a passphrase, the box deleted, and the file imported
 * again: the box is back and connects. The system file picker is answered by the test (Espresso
 * Intents), with a file in the app's cache.
 */
@RunWith(AndroidJUnit4::class)
class TransferFlowTest : FlowTest() {

    private val file = File(context.cacheDir, "pickelhaube-settings-test.json")

    @Before
    fun answerFilePicker() {
        Intents.init()
        val picked = ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(picked)
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(picked)
    }

    @After
    fun releaseFilePicker() {
        Intents.release()
    }

    @Test
    fun exportDeleteAndImportAgain() {
        setUpDemoBox()

        // Export, the passwords included.
        compose.onNode(labelled(text(R.string.settings))).performClick()
        compose.onNodeWithText(text(R.string.settings_export)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.settings_export_passwords)).performClick()
        compose.onNodeWithText(text(R.string.settings_passphrase)).performTextInput(PASSPHRASE)
        compose.onNodeWithText(text(R.string.settings_passphrase_repeat)).performTextInput(PASSPHRASE)
        compose.onNodeWithText(text(R.string.settings_export_save)).performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { file.length() > 0 }
        // The passwords went along – encrypted (that part is covered by SettingsFileTest).
        assertTrue("the file holds the passwords", org.json.JSONObject(file.readText()).has("passwords"))

        // The only box deleted: the page for a new one, which offers restoring a backup.
        compose.onNodeWithText(text(R.string.settings_export)).performScrollTo()
        compose.onNodeWithText(DEMO_BOX).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.connection_delete_box), substring = true).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.delete)).performClick()
        awaitText(R.string.settings_restore_backup)

        // Imported again, with the passphrase: the box is back – and connects, its password restored.
        compose.onNodeWithText(text(R.string.settings_restore_backup)).performClick()
        awaitText(R.string.settings_import_confirm)
        compose.onNodeWithText(text(R.string.settings_passphrase)).performTextInput(PASSPHRASE)
        compose.onNodeWithText(text(R.string.settings_import_confirm)).performClick()
        awaitText(DEMO_BOX)
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitText(R.string.dialer_number_label)
        awaitText(DEMO_PHONE)
    }

    private companion object {
        const val PASSPHRASE = "lang genug"
    }
}
