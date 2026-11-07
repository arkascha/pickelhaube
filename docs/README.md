# Pickelhaube – documents outside the app

- `web/` – the static web page: home, guide, help testing, privacy policy. Plain HTML and CSS, no build step.
- `store/` – the Google Play listing:
  - `en-US/`, `de-DE/` – short and full description; `en-US/screenshots/` – the phone screenshots
  - `feature-graphic.png` – the listing's feature graphic (1024×500)
  - `app-access.txt` – what Google's review needs to get into the app (the built-in demo box)
  - `release-notes/` – "What's new" per version, in the Play Console's `<en-US>…</en-US>` format (max. 500 characters per language)

The privacy policy (`web/privacy-policy.html`) has to match what the app does: when a release changes what the app stores,
reads or sends, update it along with the code.
