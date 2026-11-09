package org.rustygnome.pickelhaube.ui

/**
 * Pickelhaube's web pages (docs/web in the repository), linked from the app. The app keeps these
 * addresses for good – old versions go on opening them –, so the pages and the guide's anchors
 * (#prepare, #first-start, #dialling) must not be renamed.
 */
object WebPages {
    private const val BASE = "https://www.rustygnome.org/pickelhaube/"

    const val HOME = BASE
    const val GUIDE = "${BASE}guide.html"
    const val GUIDE_PREPARE = "$GUIDE#prepare"
    const val GUIDE_FIRST_START = "$GUIDE#first-start"
    const val GUIDE_DIALLING = "$GUIDE#dialling"
    const val PRIVACY_POLICY = "${BASE}privacy-policy.html"
    const val SOURCE_CODE = "https://github.com/arkascha/pickelhaube"
}
