# Pickelhaube – documents outside the app

- `web/` – the static web page: home, guide, help testing, privacy policy. Plain HTML and CSS, no build step.
- `store/app-access.txt` – what Google's review needs to get into the app (the built-in demo box)

The store listing itself is in `fastlane/metadata/android/` at the top of the repository, where F-Droid
reads it; the Google Play listing is the same:
- `<locale>/title.txt`, `short_description.txt`, `full_description.txt` – en-US and de-DE
- `<locale>/images/phoneScreenshots/` – taken on the emulator with the demo box, 1080×1920
- `en-US/images/icon.png` (512×512) and `featureGraphic.png` (1024×500) – for both languages
- `<locale>/changelogs/<version code>.txt` – "What's new" per version, max. 500 characters each (Google Play's
  and F-Droid's limit); for the Play Console, copy each language's file into its field

- `fdroid/org.rustygnome.pickelhaube.yml` – the app's build recipe for F-Droid, as submitted to
  `metadata/` of gitlab.com/fdroid/fdroiddata (the copy there is the one F-Droid uses). F-Droid builds each
  new `vX.Y.Z` tag on its own and ships the GitHub APK if its own build matches it byte for byte – so the
  APK attached to a release must be built from exactly the tagged commit, with a clean working tree.

The privacy policy (`web/privacy-policy.html`) has to match what the app does: when a release changes what the app stores,
reads or sends, update it along with the code.

The app links to these pages (see `WebPages` in the app): the home page, `guide.html` with its anchors
`#prepare`, `#first-start` and `#dialling`, and `privacy-policy.html`. Installed versions keep opening
them, so these file names and anchors must not change.
