package org.rustygnome.pickelhaube.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.ui.WebPages

@Composable
fun PickelhaubeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        // Before Android 12, no colours from the wallpaper: the app's own, from its icon (Palette.kt).
        dark -> PickelhaubeDark
        else -> PickelhaubeLight
    }
    // Links to Pickelhaube's web pages open them in the language of the app's texts, where there is one
    // (WebPages.localized) – every openUri through LocalUriHandler, whichever screen it is on.
    val language = stringResource(R.string.web_language)
    val platform = LocalUriHandler.current
    val uriHandler = remember(platform, language) {
        object : UriHandler {
            override fun openUri(uri: String) = platform.openUri(WebPages.localized(uri, language))
        }
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
