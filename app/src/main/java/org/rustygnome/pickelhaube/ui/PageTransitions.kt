package org.rustygnome.pickelhaube.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/** How long a change of page takes. */
private const val PAGE_MILLIS = 300

/** How far the page underneath moves along, as a share of the width: it follows a little, not all the way. */
private const val UNDERNEATH_SHARE = 4

/**
 * How deep a page lies: the dialer at the bottom, the pages opened from it over it, their sub-pages
 * (a box' page, the language) over those.
 */
private val Page.depth: Int
    get() = when (this) {
        Page.DIALER -> 0
        Page.PICKER, Page.DEFLECTIONS, Page.WIDGET_SETUP, Page.SETTINGS -> 1
        Page.CONNECTION, Page.LANGUAGE -> 2
        Page.WELCOME -> 3
    }

/**
 * From page [from] to page [to]: a page opened over another slides in from the side (from the right, or
 * the left in a right-to-left language) over it, and slides out the same way when closed; the page
 * underneath follows a little and fades. The welcome tour, a world of its own, fades in and out.
 * Animations switched off in the system leave the change at once.
 */
internal fun <S> AnimatedContentTransitionScope<S>.pageTransition(from: Page, to: Page): ContentTransform {
    if (from == Page.WELCOME || to == Page.WELCOME) {
        return fadeIn(tween(PAGE_MILLIS)) togetherWith fadeOut(tween(PAGE_MILLIS))
    }
    val opening = to.depth >= from.depth
    return if (opening) {
        (slideIntoContainer(SlideDirection.Start, tween(PAGE_MILLIS)) togetherWith
            (slideOutOfContainer(SlideDirection.Start, tween(PAGE_MILLIS)) { it / UNDERNEATH_SHARE } + fadeOut(tween(PAGE_MILLIS))))
            .apply { targetContentZIndex = 1f }
    } else {
        ((slideIntoContainer(SlideDirection.End, tween(PAGE_MILLIS)) { it / UNDERNEATH_SHARE } + fadeIn(tween(PAGE_MILLIS))) togetherWith
            slideOutOfContainer(SlideDirection.End, tween(PAGE_MILLIS)))
            // The page closed slides away on top of the one coming back.
            .apply { targetContentZIndex = -1f }
    }
}
