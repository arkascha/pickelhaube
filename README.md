# Pickelhaube

Android dialer that places calls through a FRITZ!Box using the TR-064 **dial help**
("Wählhilfe", click-to-dial). It behaves exactly like dialling from the router's web UI:
the box first rings the selected phone (e.g. a DECT handset); once you pick it up,
the box dials the target number from that phone.

## Open in Android Studio

*File › Open…* → select this folder. Studio downloads Gradle 8.14.3 and the
dependencies on first sync. If it offers an AGP / dependency upgrade, accepting is fine.

- Kotlin + Jetpack Compose (Material 3), minSdk 26, target/compileSdk 36
- Only external library: OkHttp (TR-064 digest auth is implemented in-app)

## FRITZ!Box preparation

1. **Home Network › Network › Network Settings** → enable *Allow access for applications* (TR-064).
2. **Telephony › Calls › Dial Help** → enable *Use dial help*.
3. **System › FRITZ!Box Users** → use (or create) a user with telephony permission.
   If you log in with password only, the box still has an auto-generated user name
   (e.g. `fritz1234`) shown there – TR-064 needs it.

## Using the app

1. On first start, enter host (`fritz.box` or `192.168.178.1`), user and password. Optionally switch on
   **Use HTTPS**: the port changes from `49000` to the box' HTTPS port – the one the box reports
   (TR-064 `DeviceInfo` `GetSecurityPort`, asked over plain HTTP without login), else the usual `49443`.
   A port entered by hand is kept.
   The **user name** field offers the users of the box once host, port and HTTPS are entered
   (TR-064 `LANConfigSecurity` `X_AVM-DE_GetUserList`), the one who logged in last marked – handy for the
   auto-generated name. The box is asked without credentials, so no failed login is counted; with HTTPS
   whose certificate is not trusted yet, over plain HTTP. If the box does not tell (older FRITZ!OS), the
   field is simply typed in.
2. The app reads the phones the box knows (FON / DECT / IP) and the one currently configured
   for dial help. Choose your DECT handset in **Ring phone**.
3. Enter a number (long-press **0** for **+**) and tap the green button → the handset rings → pick it up → the call is placed.
   The red button cancels a dial-help call that is still being set up.

**Save & connect** tests the settings first: they are only saved once the router accepts them.
Otherwise the settings stay open and an error dialog explains the problem (wrong user name or password,
missing telephony permission, router not found or not reachable, wrong port / HTTPS setting, router
temporarily blocking logins). Errors during normal use appear in the same dialog on top of the dialer,
with **Retry** and, when the cause is likely in the settings, **Open settings**. When a request to
the box failed, **Technical details** unfolds which one it was – TR-064 service and action, its
arguments (long ones shortened), the HTTP status and the box' error code, and the box' address –
selectable and with **Copy**, for a message to whoever looks into it. Credentials are no arguments,
so they never appear there; numbers can.

### Several FRITZ!Boxes

**Settings › FRITZ!Box connections** holds any number of boxes (e.g. home and office), each with its
own name, host, port, HTTPS, user, password, use of its phone books and call list, and trusted
certificate. **Add FRITZ!Box** adds one; tap an entry to edit (or delete) it. Each entry says whether the
box is *reachable* (it answered the last detection: in this network, or at its configured host),
*not reachable* or *not recognised yet* (its device id is not known yet, see below). The box in use is
chosen automatically and marked with a check mark, and shown under the app name on the dialer. The mark
turns into a red warning triangle when that box was not found by the last detection, or when the last
request to it failed; a network change clears the latter until the next request. Only when none of the
boxes is reachable (e.g. on mobile data without VPN) does an empty circle appear in front of the boxes,
to choose one by hand. If not even the box in use answers at its configured host, none is marked as in
use (they all look alike), and the dialer says that none of your boxes is reachable, loads nothing on
its own and disables the dial button (a number can still be entered); the picker then shows only the
phone's own contacts and calls, while its reload button still tries the box.

The connection page also shows the box' **device details** – model, FRITZ!OS version and serial number
(TR-064 `DeviceInfo` `GetInfo`) – right under its address, to tell boxes apart (hidden while another
address is entered). They are read when the connection is saved and
again whenever the phones are loaded, so they stay current after a FRITZ!OS update. Reading them is
optional: if the box' user lacks the rights for it, they are taken from the box' public device description
(`tr64desc.xml`) instead, which tells them as well. What AVM calls the serial
number is in fact the box' MAC address, not the number on its label.

Further down, the connection page lists the **permissions** of the configured user as the box reports
them (TR-064 `LANConfigSecurity` `X_AVM-DE_GetCurrentUser`), area by area, read alongside the device
details. They are used in three ways: when a connection is saved for a user without *Voice messages,
fax messages, FRITZ!App Fon and call list*, its **Call list** switch is turned off (it could only fail),
with a note under the switch; when the box refuses an action (TR-064 error 606), the error dialog asks
the box for the user's permissions and names that one if it is missing; otherwise it says that the user
may not use the function and where to check the permissions.

The box of the current network is selected automatically – at start, whenever the phone's network
changes and when the app comes back to the foreground; if none of the boxes is found, it tries once more
a few seconds later (right after a network change the box often does not answer yet). Every FRITZ!Box
publishes a public device description (`http://<box>:49000/tr64desc.xml`) with a unique device id
(UDN); Pickelhaube records it when a connection is saved, or – for a connection saved while away – on the
first successful login to it, and asks the network's gateway, `fritz.box` and the configured hosts for
it. The box of the network itself (gateway, `fritz.box`) wins; a box answering only at its configured
host (through a VPN, or at its MyFRITZ address) is used only when the network has none of the boxes.
If a FRITZ!Box answers that is not configured, the dialer says so.

The device description needs no login and can be imitated. So after switching, Pickelhaube also compares
the box' serial number (`DeviceInfo` `GetInfo`, see above) with the stored one, and refuses a box whose
number differs. With HTTPS, the pinned certificate protects in addition.

### Choosing a number: recent calls and phone books

The address-book button at the left of the number field opens **Choose number** with two tabs,
**Recent** and **Phone books**; tapping a number in either puts it into the dialer. The reload button
at the top refreshes both.

### Phone books

The **Phone books** tab shows the phone books stored on the
FRITZ!Box (TR-064 `X_AVM-DE_OnTel`: `GetPhonebookList`, `GetPhonebook`, then the XML export).
Online phone books configured on the box (e.g. CardDAV) are synced into it and should appear as well.
Search by name or number, choose above the list whether all phone books are shown or just one of
them, and tap a number to put it into the dialer.
The books are loaded once per app start; the reload button at the top fetches them again.
Whether a box' phone books are used is set per FRITZ!Box, on its connection page
(**Settings › FRITZ!Box connections › <box> › Phone books**, on by default).

Optionally, the contacts stored on the phone itself appear as an additional book **This phone**
(all Android accounts, e.g. Google or CardDAV via DAVx⁵). This is off by default. When the **Phone books**
tab is shown, Pickelhaube asks whether to include them: **Allow** (Android then asks for the
contacts permission, `READ_CONTACTS`, used for nothing else), **Not now** (asks again the next time
the phone books are opened) or **Don't ask again**. It can always be switched in
**Settings › Phone books › Include this phone's contacts**; switching it off there also stops the
question. The permission itself can be revoked in the Android app settings.

### Taking over and handing over contacts

A long press on a number – in **Phone books** or in **Recent** – opens a menu:

- **Add to this phone's contacts** (for the box' contacts and recent calls): the phone's contacts app
  opens with a new contact filled in – name and numbers with their kind (home, mobile, work, fax,
  other) – to be saved there, in the account chosen there. Pickelhaube needs no permission for this.
- **Hand over to the FRITZ!Box** (for the phone's own contacts and recent calls, while the box in use
  is reachable and its phone books are loaded): asks into which phone book of the box, says if one of
  the numbers is already in it, and adds the contact with all its numbers as a new entry (TR-064
  `X_AVM-DE_OnTel` `SetPhonebookEntryUID`). Kinds the box does not know become *other*.
- **Make speed dial** and **Make FRITZ!Box speed dial** (see below).

Each is a one-off copy of a single contact, not a synchronisation.

### Recent calls

The **Recent** tab lists the numbers recently dialled, received or missed, newest first, each number
once at its latest call: name and number, the time, then an arrow for how the call went (dialled,
received, missed) and an icon for where it was recorded. Rejected calls and calls with a
withheld number are left out. The list is read anew every time the picker is opened (or with the
reload button), from two sources:

- **FRITZ!Box** (router icon with the box' name from the settings) – the dialled, received and missed calls in the box' call list (TR-064 `X_AVM-DE_OnTel` `GetCallList`),
  i.e. Pickelhaube's dial-help calls as well as the calls of the handsets themselves. The FRITZ!Box user
  needs the permission *Voice messages, fax messages, FRITZ!App Fon and call list* for this.
  Whether it is used is set per FRITZ!Box, on its connection page (**Settings › FRITZ!Box connections ›
  <box> › Call list**, on by default) – e.g. for a box whose user lacks that permission.
- **This phone** (phone icon; optional, off by default) – the dialled, received and missed calls of the phone's own telephony app,
  read from the Android call log (`READ_CALL_LOG`, used for nothing else). Pickelhaube's calls run on
  the FRITZ!Box and never appear there. Switch it on in **Settings › Recent numbers › Include this
  phone's calls**. **This only works if Pickelhaube was installed from outside Google Play** (APK or
  F-Droid): Google Play allows the call log permission only for the default phone app.
  On switching it on, Pickelhaube asks Android for the permission and test-reads the call log; if
  either fails, the switch stays off and the settings show why. If reading fails later (e.g. the
  permission was revoked), the **Recent** tab shows a note with **Turn off** and **App settings**.

### Numbers written differently

The same number is often written in different ways: `+49 30 123456`, `0049 30 123456`, `030 123456`
or – dialled locally – just `123456`. Pickelhaube reads the country and area code set on the box in use
(TR-064 `X_VoIP` `X_AVM-DE_GetVoIPCommonCountryCode` and `X_AVM-DE_GetVoIPCommonAreaCode` – on a box
without them the standard `GetVoIPCommonCountryCode` and `GetVoIPCommonAreaCode` –, shown with the device details on
its connection page) and compares numbers in their international form by the box' rules, so a local
number counts as one in the box' area. This is used to list each number only once (in **Recent** and
across the phone books), to give calls without a name the name from the phone books in use, and when
searching the phone books by number (`030 12` also finds `+49 30 123456`). The number dialled is always
exactly the one entered. Until the codes are known (or when the box' user may not read them), the
phone's country is used instead, without area code.

### Speed dial

Speed dials come from two sources that never collide, each of which can be switched off:

- **This phone** – speed dials **1 to 9**, stored on the phone and valid with every FRITZ!Box (name
  and number are kept as a copy). Long-press the digit in the dialer to use one; digits with a speed
  dial have a small dot. Set them up in the picker's **Speed dial** tab (tap a free slot, then the
  number in the phone books; long-press a set one to change or remove it), or long-press a number in
  the **Phone books** tab and choose **Make speed dial**. Switch: **Settings › Speed dial › This phone's speed dials** (on by default).
- **FRITZ!Box** – the two-digit speed dials (**00 to 99**) set up in the phone books of the box in use
  (the `quickdial` attribute of its phone book export), the same ones its handsets dial with `**7`.
  So they depend on where you are. Tap the first digit and long-press the second, or enter `**7` and
  the number – Pickelhaube dials the number it stands for. Switch: on each box' connection page,
  **Speed dials** (on by default, independent of **Phone books**). The box' phone books are read in
  the background after connecting, so these work right away.
  While the box is reachable they can be changed as well: **New FRITZ!Box speed dial** at the end of the
  list (then tap the number in the phone books and choose a free slot), a long press on one to change or
  remove it, or **Make FRITZ!Box speed dial** in the menu of a long-pressed number (phone books, recent
  calls). The speed dial goes to the box' entry that has the number (TR-064 `GetPhonebookEntryUID`, the
  `quickdial` attribute set, `SetPhonebookEntryUID`, the rest of the entry unchanged); a number in none
  of its phone books gets a new entry, in the phone book chosen then. A slot used before is freed there.
  Online phone books the box syncs from a provider (Google, CardDAV, …; `owner` 240 and up in the
  export) cannot be changed through the box: a number only there gets an entry with the speed dial in
  one of the box' own phone books – the dialog says so. They are not offered for handing over either.
  Such a copy can be deleted again (**Delete copy on the FRITZ!Box** in the menu of its speed dial or of its number
  in **Phone books**, TR-064 `DeletePhonebookEntryUID`). Pickelhaube does not remember which entries it
  made: an entry counts as a copy when one of the box' online phone books has a contact of the same name
  with all of its numbers. Other entries of the box cannot be deleted from the app.

After a speed dial the number field's label says which one it is and whose number. The **Speed
dial** tab lists both kinds, so you can pick one without knowing its number.

### Call forwarding

The forwarding symbol in the dialer's top bar (next to the settings) opens the call forwardings set up on
the box in use (TR-064
`X_AVM-DE_OnTel` `GetDeflections`): which calls (all, to one of the own numbers, from a number, without
caller ID, …), where to and when (immediately, delayed, when busy, ringing in parallel, …), each with a
switch (`SetDeflectionEnable`). TR-064 only lets them be switched on and off: set them up and change them
on the box (Telephony › Call Handling › Call Forwarding). The symbol sits on a filled circle while one of
them is switched on, and is greyed out while none is. It is there only while the box in use is reachable and has some set up: they are read
in the background after connecting – quietly, so a box whose user may not read them just shows none.

The quick settings tile **Call forwarding** switches one of them on and off – on the box reachable from
the current network: the one marked on that page (a choice offered when there is more than one), else
the first. It shows whether it is on and where it forwards to, and asks for the phone to be unlocked
before switching.

### Live call status

Above the call buttons, the dialer shows how the call goes: *pick up* while the box rings your phone,
then *calling …* (with the own number it goes out from) once you picked up, *connected* with a running
timer, and finally *call ended* with its duration, *not answered, or busy*, or – when nobody picked
up within 45 seconds – *the phone was not picked up*. The red button stays usable exactly as long as
the call is still being set up. The end stays shown for 20 seconds.

TR-064 does not report any of this. It comes from the box' **call monitor**: a plain TCP connection to
port 1012, on which the box reports every call as it happens (`CALL`, `CONNECT`, `DISCONNECT`, `RING`).
Pickelhaube connects to it right before asking the box to dial and closes it once the call is over, so
it is not a permanent listener; of all the calls of the household it follows only the one reporting
the number it dialled. The monitor is **off by default** and cannot be switched through TR-064: dial
`#96*5*` once on a phone connected to the box (`#96*4*` switches it off again). If it is off, the
dialer says so on the first call; without it, the dialer just shows *pick up* and the red button stays
usable for 30 seconds, as before.

**Settings › Live call status** switches the feature (on by default) and lists, per box, whether its
call monitor is on, off or not checked yet – asked when the settings open, for the boxes reachable
from there, and with **Check again**. Mind that the call monitor needs no login and is not encrypted:
once it is on, any device in the home network can follow the box' calls, numbers included. It is
reachable in the home network and usually through a VPN, not through MyFRITZ.

### Language

Pickelhaube is available in English, German, Dutch, French, Italian, Spanish and Polish.
The flag button in the top bar of the **Settings** opens **Language**: **Auto** (the system
language, the default) or one of these, each with its national flag. Choosing one returns to the
settings, already in the new language. The button shows the flag of the language in use – the
region of the system language with *Auto* (e.g. 🇦🇹 for German in Austria), else the language's usual
country; English (🇬🇧) when the system language is not one of the above. From Android 13 on this is Android's own per-app language, so the same choice also appears under the system's
*Settings › Apps › Pickelhaube › Language*; before Android 13 the app applies it itself.
Texts from the FRITZ!Box (e.g. its error descriptions) and the phone's own number labels stay as
they come. The translations still need a review by native speakers; menu names of the FRITZ!Box
are taken from the German FRITZ!OS and translated descriptively for the other languages.

### Confirmation on the FRITZ!Box (two-factor)

Newer FRITZ!OS versions protect settings changes with an *additional confirmation* on the box
(TR-064 error 866). Changing the dial-help phone is such a change; dialling itself is not. Pickelhaube
therefore only changes the dial-help phone when it differs from the selected one. When a confirmation
is needed, a dialog shows how to give it – press a button on the box, or dial the shown code
(e.g. `*12622`) on a connected phone – and the step continues automatically once confirmed.

### HTTPS and the router certificate

The FRITZ!Box uses a self-signed certificate, so it cannot be verified automatically. Pickelhaube
uses *trust on first use* with certificate pinning:

- On the first HTTPS connection a dialog shows the certificate (SHA-256/SHA-1 fingerprints, subject,
  issuer, alternative names, validity, key) and, on request, the full PEM text (selectable for copying).
  Compare the fingerprint with what your browser shows for `https://fritz.box`, then tap **Trust**.
- From then on, only that exact certificate is accepted. Host name checks are skipped because the box'
  certificate rarely matches the name or IP you use; the pin is the stronger check.
- If the box ever presents a different certificate (factory reset, renamed box – or an attacker),
  a **Certificate has changed!** warning appears instead of connecting silently.
- **Settings › Trusted HTTPS certificate** says whether a certificate is pinned, with **Show** (full
  details, fingerprints included) and **Forget**.

### Quick settings tiles

Three tiles can be added to the quick settings, all with the Pickelhaube logo – their names say what
each does. From Android 13 on, **Settings › Quick settings tiles**
has a button for each, and Android asks whether to add it. A tile already there is listed as such
instead – Android cannot be asked, so each tile notes when it is added or removed (and when it is shown); otherwise (or if that fails) pull down the
shade twice, tap the pencil and drag them into your tiles – apps' tiles are usually at the very end:

- **Pickelhaube** opens the dialer. It is highlighted while a FRITZ!Box is reachable from the current
  network – looked for each time the shade is pulled down, as the app does –, and from Android 10 on
  its subtitle names it, or says *No FRITZ!Box reachable*.
- **Redial** opens the dialer with the number dialled last already entered, shown as its subtitle.
  It does not dial by itself – a tap in the shade is easily made by accident – so the green button
  still has to be pressed. The tile is greyed out until something was dialled; like **Pickelhaube**, it
  is highlighted while a FRITZ!Box is reachable.
- **Call forwarding** switches a call forwarding of the FRITZ!Box reachable from the current network
  (see *Call forwarding* above).

On a locked phone, they do what they do only once the phone is unlocked.

### Long press on the app icon

A long press on Pickelhaube's icon in the launcher opens a menu with three entries (Android's app
shortcuts; each can also be dragged onto the home screen on its own):

1. **Redial** – the number dialled last, with its name from the phone books (taken when it is dialled,
   or once the phone books are read). It **dials right away**, once the app knows which FRITZ!Box is
   reachable from here; with none reachable, the number just stands in the dialer.
2. **Call forwarding** – switches the call forwarding the quick settings tile switches (see *Call
   forwarding* above), on the box reachable from the current network, without opening the app; a short
   message says how it went, or why it could not switch (no box reachable, none set up, not readable).
   The entry is always there; it says whether the forwarding is on and where it forwards to, as last
   read by the app or the tile – the box itself is asked before switching.
3. **Choose number** – the number picker.

Launchers show an entry's long text (e.g. *Erika Muster · 030 1234567*) only when it fits in one line,
otherwise its short one (*Erika Muster*). The entries are published when the app starts (so their texts
follow the language) and updated with each call and each reading of the forwardings. Entries 1 and 3
open the dialer through an activity alias that other apps cannot start – only an intent sent there may
dial without the green button being pressed. An entry reopened from the recent apps is not carried out
again.

### Export and import of the settings

*Settings › Back up and transfer* writes all connections and options into a JSON file
(*Export…*) and reads them back from one (*Import…*) – e.g. for another phone, or for a version
of the app under a different package name, where the passwords' key in the Android Keystore is not
available. The passwords go into the file only on request: then encrypted with a passphrase
(PBKDF2-HMAC-SHA256, AES-256-GCM), which the import asks for. Left empty there, or exported without
them, a connection keeps the password stored for it already, if any; otherwise it has to be entered
again. The import replaces all connections and options, after confirming what the file holds; the
phone's contacts and call log stay off unless Android already grants reading them.
While no box is set up yet – right after installing – the page for the first connection offers
the import as well, as *Restore backup…* in its top bar, instead of entering one.

Pickelhaube also registers for `tel:` links, so you can pick it when tapping a number in
Contacts or a web page; the number is pre-filled.

## Code map

| File | Purpose |
|---|---|
| `tr064/DigestAuthenticator.kt` | HTTP Digest (MD5, qop=auth) for OkHttp |
| `tr064/Tr064Client.kt` | Generic SOAP call over HTTP/HTTPS + response / UPnP fault parsing |
| `tr064/CertificateTrust.kt` | Pinning trust manager, certificate details, "untrusted certificate" exception |
| `tr064/FritzDialer.kt` | `X_VoIP` actions: `X_AVM-DE_GetPhonePort`, `X_AVM-DE_DialGetConfig`, `X_AVM-DE_DialSetConfig`, `X_AVM-DE_DialNumber`, `X_AVM-DE_DialHangup`; device details (`DeviceInfo` `GetInfo`) |
| `tr064/BoxUsers.kt` | The box' users, offered for the user name (`LANConfigSecurity` `X_AVM-DE_GetUserList`) |
| `tr064/BoxDiscovery.kt` | Recognises the FRITZ!Box of the current network (gateway, `tr64desc.xml` device id) |
| `tr064/Phonebooks.kt` | Phone book list / download / XML parsing (`X_AVM-DE_OnTel`) |
| `tr064/CallMonitor.kt` | The box' call monitor (TCP port 1012): connection and line parsing |
| `ui/CallProgress.kt` | How the call placed last is going, and how the monitor's report of it is recognised |
| `shortcut/LauncherShortcuts.kt` | The entries of the app icon's long-press menu |
| `shortcut/DeflectionShortcutActivity.kt` | Invisible activity behind the "Call forwarding" entry: switches it |
| `tile/Tiles.kt` | Quick settings tiles "Pickelhaube", "Redial" and "Call forwarding" |
| `data/DeviceContacts.kt` | The phone's own contacts as an extra phone book (`READ_CONTACTS`) |
| `tr064/CallList.kt` | Dialled, received and missed calls from the box' call list (`X_AVM-DE_OnTel` `GetCallList`, XML export) |
| `data/DeviceCallLog.kt` | The phone's own dialled, received and missed calls from the Android call log (`READ_CALL_LOG`) |
| `data/AppLanguage.kt` | UI language: system or chosen (per-app language on Android 13+, own handling before) |
| `data/BoxRecognition.kt` | Which configured FRITZ!Box is reachable from the current network (app and tile) |
| `data/SettingsRepository.kt` | FRITZ!Box connections (passwords encrypted via Android Keystore) and options |
| `data/SettingsFile.kt` | Settings file for export and import (passwords optionally under a passphrase) |
| `ui/` | ViewModel and Compose screens (dialer, settings, certificate dialogs); `UiText` carries ViewModel texts as resources |
| `res/values*/strings.xml` | All UI texts: English (`values/`) and the translations (`values-de/`, …) |

Unit tests (`./gradlew test`) cover digest computation, envelope building, number sanitising,
the call list's date format, the device details, the user list, the user's permissions, the comparison of numbers, merging of the recent numbers, certificate pinning, the settings file, the call monitor's lines and recognising the dialled number in them.

## Notes / ideas

- Plain HTTP (port 49000) remains the default; even there the password never travels in clear text
  (digest auth). HTTPS additionally encrypts the numbers you dial and protects against spoofed routers.
- Works only while the phone is in the home network (or via VPN to it).

## Trademarks

Pickelhaube is an independent project and is not affiliated with FRITZ! GmbH (formerly AVM).
FRITZ! and FRITZ!Box are trademarks of FRITZ! GmbH; they are used here only to say which
routers the app works with.
