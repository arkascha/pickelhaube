package org.rustygnome.pickelhaube.ui

/**
 * Pickelhaube's web pages (docs/web in the repository), linked from the app. The app keeps these
 * addresses for good – old versions go on opening them –, so the pages and the guide's anchors
 * (#prepare, #first-start, #dialling, and the answers #faq-end-call, #faq-no-connection) must not be
 * renamed – feedback.html included. The same holds for their German twins (guide.de.html …), which
 * keep the same anchors: an app showing German texts opens those ([localized]).
 */
object WebPages {
    private const val BASE = "https://www.rustygnome.org/pickelhaube/"

    const val HOME = BASE
    const val GUIDE = "${BASE}guide.html"
    const val GUIDE_PREPARE = "$GUIDE#prepare"
    const val GUIDE_FIRST_START = "$GUIDE#first-start"
    const val GUIDE_DIALLING = "$GUIDE#dialling"
    /** The answer why the app cannot end a connected call. */
    const val FAQ_END_CALL = "$GUIDE#faq-end-call"
    /** The answer what to do when the app doesn't connect to the box. */
    const val FAQ_NO_CONNECTION = "$GUIDE#faq-no-connection"
    const val PRIVACY_POLICY = "${BASE}privacy-policy.html"
    const val FEEDBACK = "${BASE}feedback.html"
    const val SOURCE_CODE = "https://github.com/arkascha/pickelhaube"

    /** The languages the web pages are written in besides English, as their file names say (guide.de.html). */
    private val LANGUAGES = setOf("de")

    /**
     * [url] in [language] (the one of the app's texts, R.string.web_language): one of these pages becomes
     * its twin in that language – guide.html#faq-end-call → guide.de.html#faq-end-call, the home page →
     * index.de.html –, if there is one; English, any other address and any other language stay as they are.
     */
    fun localized(url: String, language: String): String {
        if (language !in LANGUAGES || !url.startsWith(BASE)) return url
        val (page, anchor) = url.removePrefix(BASE).split('#', limit = 2).let { it[0] to it.getOrNull(1) }
        val translated = when {
            page.isEmpty() -> "index.$language.html"
            page.endsWith(".html") && !page.removeSuffix(".html").contains('.') -> page.removeSuffix(".html") + ".$language.html"
            else -> return url
        }
        return BASE + translated + (anchor?.let { "#$it" } ?: "")
    }
}
