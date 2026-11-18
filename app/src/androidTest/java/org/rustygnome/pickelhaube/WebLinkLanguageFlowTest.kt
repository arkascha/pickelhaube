package org.rustygnome.pickelhaube

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.data.AppLanguage
import org.rustygnome.pickelhaube.ui.WebPages

/**
 * The links to the web pages open them in the language of the app's texts: in German, the German twin
 * of the guide. The browser is not started – an activity monitor takes the intent instead.
 */
@RunWith(AndroidJUnit4::class)
class WebLinkLanguageFlowTest : FlowTest() {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Takes every https page the app opens, instead of a browser. */
    private val browser = object : Instrumentation.ActivityMonitor() {
        @Volatile var opened: String? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW || intent.data?.scheme != "https") return null
            opened = intent.dataString
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    /** [id] in German, as the app shows it once switched. */
    private fun german(id: Int): String =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) }).getString(id)

    @Test
    fun theGuideOpensInGerman() {
        setUpDemoBox()
        scenario!!.onActivity { AppLanguage.set(it, "de") }
        awaitText(german(R.string.dialer_number_label))
        compose.onNode(labelled(german(R.string.settings))).performClick()
        instrumentation.addMonitor(browser)
        compose.onNodeWithText(german(R.string.web_guide)).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { browser.opened != null }
        assertEquals(WebPages.localized(WebPages.GUIDE, "de"), browser.opened)
        assertEquals("https://www.rustygnome.org/pickelhaube/guide.de.html", browser.opened)
    }

    @After
    fun backToTheSystemLanguage() {
        instrumentation.removeMonitor(browser)
        // From Android 13 on, the choice is kept by Android itself, outside the app's data.
        scenario?.onActivity { AppLanguage.set(it, null) }
    }
}
