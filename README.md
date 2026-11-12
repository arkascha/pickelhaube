# Pickelhaube

Android dialer that places calls through a FRITZ!Box using the TR-064 **dial help**
("Wählhilfe", click-to-dial). It behaves exactly like dialling from the router's web UI:
the box first rings the selected phone (e.g. a DECT handset); once you pick it up,
the box dials the target number from that phone.

## Open in Android Studio

*File › Open…* → select this folder. Studio downloads Gradle (the wrapper's version, 9.9.1) and the
dependencies on first sync. If it offers an AGP / dependency upgrade, accepting is fine.

- Kotlin + Jetpack Compose (Material 3), minSdk 26, targetSdk 36, compileSdk 37
- Only external library: OkHttp (TR-064 digest auth is implemented in-app)

### Build flavours

- **full** – everything; for an APK or F-Droid.
- **play** – for Google Play: without the phone's own calls in **Recent** (no `READ_CALL_LOG`, no switch
  for it in the settings), since Google Play allows the call log only for the default phone app. The
  FRITZ!Box call list is there as in **full**. The flavours set `BuildConfig.CALL_LOG`
  (`DeviceCallLog.available`); the permission is declared in `src/full/AndroidManifest.xml` only.

Both have the same application id. Build the Play bundle with `./gradlew bundlePlayRelease`
(`app/build/outputs/bundle/playRelease/`), or in Studio via *Build Variants* (*playRelease*).

### Release signing

Release builds are signed with the upload key when these Gradle properties are set – outside the
repository, e.g. in `~/.gradle/gradle.properties` (or as environment variables `ORG_GRADLE_PROJECT_<name>`):

```properties
pickelhaubeStoreFile=/path/to/upload-key.jks
pickelhaubeStorePassword=…
pickelhaubeKeyAlias=…
pickelhaubeKeyPassword=…
```

Without them, release builds are not signed; with only some of them, Gradle warns which are missing.

## FRITZ!Box preparation

1. **Home Network › Network › Network Settings** → enable *Allow access for applications* (TR-064).
2. **Telephony › Calls › Dial Help** → enable *Use dial help*.
3. **System › FRITZ!Box Users** → use (or create) a user with telephony permission.
   If you log in with password only, the box still has an auto-generated user name
   (e.g. `fritz1234`) shown there – TR-064 needs it.

## Using the app

### Welcome tour

On first start – while no FRITZ!Box is set up – a short tour leads to the first connection, with a
mascot: the app icon's helmet with eyes, its handset worn as a grin (drawn in `art/mascot/generate.py`,
which writes the poses as SVGs and as vector drawables). Six pages: hello (with small links to the
demo box and to restoring a backup), what the FRITZ!Box needs (with a link to the guide on the web
page), where the box is (`fritz.box` is asked right away, without logging in; the display name is
left to you – empty, the box' own name is taken), user and password (the only user of a box is
filled in; choosing one from the list moves on to the password), connect (the same save
path as the connection page below: the "already set up" question, the error message, and then
**All settings** – the full connection page with the entries; after a rejected login, **Change password**
next to **Connect** leads back to the password) and ready (which phone the box rings
for dial help – its own setting, read and not changed, as changing it may need a confirmation on the
box – and three tips). **Skip** leads to the connection page. **Settings › Welcome tour** starts it
again; from there, its connect step adds another box. The mascot bobs and sways, blinks, pops up
with each new pose, waves now and then on the first page, hops when something worked and tilts its
head when it did not; pages slide – all of it only while Android's animations are on.

### What's new

After an update, the app shows once what is new – a few points under the cheering mascot, over the
dialer, when nothing else is open; not when it was opened for something particular (a shortcut, the
widget, a link, a shared text), then at the next ordinary start. A new install shows nothing (the
welcome tour explains everything); an update from a version before this note existed counts as an
update when a box is set up. **What's new** at the bottom of the settings shows the note again. The
notes are string arrays `whats_new_<version code>` in all languages, listed in `data/WhatsNew.kt` with
their version name; a version without one shows nothing, skipped versions are shown together. The title
names the newest note shown – not the version installed, which may have no note of its own. The version last seen is kept
on the device and not exported.

### The connection page

1. On the connection page (after **Skip** in the welcome tour, or **Settings › Add FRITZ!Box**), enter host (`fritz.box` or `192.168.178.1`), user and password. Optionally switch on
   **Use HTTPS**: the port changes from `49000` to the box' HTTPS port – the one the box reports
   (TR-064 `DeviceInfo` `GetSecurityPort`, asked over plain HTTP without login), else the usual `49443`.
   A port entered by hand is kept.
   The **user name** field offers the users of the box once host, port and HTTPS are entered
   (TR-064 `LANConfigSecurity` `X_AVM-DE_GetUserList`) – the list opens from the field's **▾**. The user in
   the field is marked, the one who logged in last is named so, and the auto-generated one (`fritz` and
   four digits, made by the box for logging in with a password only) is explained. A box with a single
   user – the usual case – gets it entered right away, unless another name has been typed in. The
   **(?)** in the field says what the user is, and that one can be created just for Pickelhaube. The box
   is asked without credentials, so no failed login is counted; with HTTPS whose certificate is not
   trusted yet, over plain HTTP. If the box does not tell (older FRITZ!OS), the field is simply typed in.
2. The app reads the phones the box knows (FON / DECT / IP) and the one currently configured
   for dial help. Choose your phone in **Ring phone**.
3. Enter a number (long-press **0** for **+**) and tap the green button → the phone rings → take the call → the call is placed.
   With **Key tones** switched on in the settings (section *Keypad*, off by default), each key plays its
   tone, as on a phone's dial pad – on this phone only, not into the call, and not while the phone is
   set to silent or vibrate. A key gives way a little under the finger and springs back, with a tiny tap
   of vibration – as far as the system's touch feedback is on, and without the movement when the system's
   animations are switched off.
   The red button cancels a dial-help call that is still being set up.
   A **long press on the green button** offers *Hide my number*: your own number is withheld
   for that call (the box' code `*31#` before the number; not for internal numbers and service codes).
   With several internet numbers on the box, it also offers *From <number>* for each (see
   *Own numbers* below).

**Save & connect** tests the settings first: they are only saved once the router accepts them.
Before logging in, it asks the box at the host who it is (its public device description, no login):
`fritz.box` always leads to the box of the current network, so a box set up for elsewhere is never
logged in to there – its credentials would go to the wrong box, and that box' certificate and device
id would be taken over. If another box answers, or none, the page offers **Save without checking**:
the entries are stored, the device id, details and certificate stay, and the box in use does not
change. A new connection to a box that is set up already asks before adding it once more. While the
entered host leads to another box, the page says so and does not offer that box' users or HTTPS port.
Otherwise the settings stay open and an error dialog explains the problem (wrong user name or password,
missing telephony permission, router not found or not reachable, wrong port / HTTPS setting, router
temporarily blocking logins). Errors during normal use appear in the same dialog on top of the dialer,
with **Retry** and, when the cause is likely in the settings, **Open settings**. When a request to
the box failed, **Technical details** unfolds which one it was – TR-064 service and action, its
arguments (long ones shortened), the HTTP status and the box' error code, and the box' address –
selectable and with **Copy**, for a message to whoever looks into it. Credentials are no arguments,
so they never appear there; numbers can.

### Demo box

Without a FRITZ!Box at hand – and for the review in the app stores – Pickelhaube has a box of its
own: host `demo`, user `demo`, password `demo` (*No FRITZ!Box? Try the demo box*, right at the top
of the page for a new connection, explains it, and its **Fill in the demo box** enters them). It
answers in the app itself instead of over the network (`tr064/DemoBox.kt`,
an OkHttp interceptor in `Tr064Client`, plus its device description and call monitor), with phones,
two phone books with speed dials, a call list and call forwardings. A call through it rings nowhere:
after a few seconds it counts as picked up, then connected, ends after a short talk and appears in
its call list. Changes (speed dials, entries, call forwardings) last until the app is closed. Its
numbers are from the ranges the Bundesnetzagentur keeps free for films.

Like any box reached only at its configured host, it is used when the current network has none of
the configured boxes.

### Several FRITZ!Boxes

**Settings › FRITZ!Box connections** holds any number of boxes (e.g. home and office), each with its
own display name, host, port, HTTPS, user, password, use of its phone books and call list, and trusted
certificate. The display name is only a label in the app (default: the box' own name) – the box is
reached at its host, never looked for by that name; the **(?)** in the field says so. Explanations
on the connection page sit behind such **(?)** icons – also what the box needs set up, at the top of
the page – so the page stays short. Explaining texts are grey and italic, what the app found out about
a box (reachable, its users, its HTTPS port, …) is in the accent colour, values in the normal text
colour. **Add FRITZ!Box** adds one; tap an entry to edit (or delete) it. Each entry says whether the
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

The connection page also shows the box' **device details** (folded, a tap opens them) – model,
FRITZ!OS version and serial number
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

### Suggestions while typing

From the third key on, the row under the number field offers the numbers of the phone books and
recent calls that what is typed may be heading for: numbers starting with it – also written the
other way, `030 1…` finds `+49 30 1…` and the other way round –, then names whose letters are on the
keys typed, from the start of a word (`2766` finds *Arno*, `26626` *Anna M…*, umlauts as their
letter), then numbers containing it elsewhere. Numbers called recently come first. A tap takes the
best one into the field, with its name above; **3 more ▾** opens all of them (up to eight). For this, the
box' phone books are read when the app comes up, as long as they are switched on for the box.

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
The books are loaded when the app starts and again whenever it comes back to the foreground (they may
have been changed on the box meanwhile); the reload button at the top fetches them again. A change of
the box' entries – a speed dial set or removed, a copy deleted, a contact handed over – that the box refuses
because the entry was changed or deleted on the box (a 7530 AX answers 820, others 713 or 714) is no error:
the phone books are read again, a short note says so, and a speed dial is set or removed, or the contact
handed over, on what is there now. If they did not change, the refusal is shown as an error. The box numbers
its phone books by their place in its list, so a phone book added or deleted on the box gives the others
new ids: before a new entry goes in, the app checks that the id still stands for the phone book chosen –
otherwise the contact would land in another one.
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
  i.e. Pickelhaube's dial-help calls as well as the calls of the phones themselves. The FRITZ!Box user
  needs the permission *Voice messages, fax messages, FRITZ!App Fon and call list* for this.
  Whether it is used is set per FRITZ!Box, on its connection page (**Settings › FRITZ!Box connections ›
  <box> › Call list**, on by default) – e.g. for a box whose user lacks that permission.
- **This phone** (phone icon; optional, off by default) – the dialled, received and missed calls of the phone's own telephony app,
  read from the Android call log (`READ_CALL_LOG`, used for nothing else). Pickelhaube's calls run on
  the FRITZ!Box and never appear there. Switch it on in **Settings › Recent numbers › Include this
  phone's calls**. **Not in the Google Play build**: Google Play allows the call log permission only
  for the default phone app, so the `play` flavour leaves out the permission and this switch (see
  *Build flavours* above).
  On switching it on, Pickelhaube asks Android for the permission and test-reads the call log; if
  either fails, the switch stays off and the settings show why. If reading fails later (e.g. the
  permission was revoked), the **Recent** tab shows a note with **Turn off** and **App settings**.

**Own numbers.** With several numbers on the box, each call says which of them it was on – *from
030 123456* for a dialled call, *to Office* for a received or missed one: the own number from the box'
call list, by its name on the box if it has one (**Telephony › Own Numbers**).

**New missed calls.** Whenever the app comes up or back to the foreground, it reads the box' call
list in the background (no service, no polling) and counts its missed calls since **Recent** was
last looked at – those still the latest call with their number: a call back, from the landline or
the mobile, or another call from that number settles one. The dialer then shows the count as a
badge on the picker button and, while no call is being followed, a red line *1 new missed call at
<connection> – show* (its display name) that opens **Recent**; there those calls are marked *New missed call*. Shown
in **Recent**, they count as seen. The first time for a box, its list as it is counts as seen. The
phone's own missed calls are left to Android, which reports them itself (`data/MissedCalls.kt`).

### Internal calls

Switched on per FRITZ!Box on its connection page (**Settings › FRITZ!Box connections › <box> ›
Internal calls**, off by default, as it is only of use with several phones), the picker's tab
**Internal** lists the phones of the box with their internal numbers, for a free call from one phone
of the box to another: analogue phones by their socket (`FON1` → `**1`), DECT
handsets (TR-064 `X_AVM-DE_Dect` `GetGenericDectEntry`), IP phones and FRITZ!App Fon (`X_VoIP`
`X_AVM-DE_GetClient3`), and **All phones** (`**9`). A tap puts the number into the dialer with the
phone's name; the dial help then rings the phone set under **Ring phone**, which calls the other
one. The box' phones are found by their names in the suggestions while typing as well
(`5482` → *Kitchen*). They are read together with the phones for dial help; a part the box does
not tell (user rights, older FRITZ!OS) is left out.

- The box tells the DECT handsets' names and ids, but not their internal numbers: the handset with
  id *n* is taken to be `**61(n-1)`, `**610` to `**615`, as the box numbers them – *not yet verified
  on a real box*.
- A phone cannot call itself: dialling the internal number of the phone under **Ring phone** says so
  instead of placing the call. With a group under **Ring phone** (e.g. all DECT handsets), the phone
  being called rings for the dial help as well – choose a single phone there.
- Internal calls are not followed on the live call status, as it is not known yet whether
  the box' call monitor reports them; the dialer shows *pick up* as without it.

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
  (the `quickdial` attribute of its phone book export), the same ones its phones dial with `**7`.
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
the call is still being set up: the box' hang-up (`X_AVM-DE_DialHangup`) only cancels the dial help and
does not end a connected call – tested on a 7530 AX –, so while connected the dialer says to hang up on
the device itself (handset, headset or speakerphone). The end stays shown for 20 seconds.

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

### Extensions, PINs and phone menus

Digits to be typed once the call is connected – an extension, a conference PIN, a choice in a phone
menu – follow the number after a pause: a **long press on `*`** puts in a `,`. Numbers from
contacts, tel: links and shared text bring theirs along, written `,` `;` `p` `w` or `x`
(`030 1234567 x 42`) or as `;postd=` / `;ext=` in a tel: link; in the dialer they stand as
`0301234567,42#`.

The box' dial help cannot send them: it dials only, sends no tones once connected, and drops the
pause – it would dial all digits as one number (tested on a 7530 AX: `0171…,,42` was dialled as
`0171…42`). So Pickelhaube gives the box only the number and says what to type on the phone
yourself: under the dialer before the call (*Calls 0301234567 – once connected, type 42# on the
phone*), while picking up and dialling, and – with the live call status – in bold once the call is
connected (*Now type 42# on the phone*). Further pauses in them are shown as `…`. Redial brings them
back as well.

### Own numbers

Along with their registration (below), the app reads the box' own numbers (`X_VoIP`
`X_AVM-DE_GetNumbers`): number, type and the name given on the box. They name the own number in
**Recent**, and – with two or more internet numbers – the green button's long press offers *From
<number>* for each one that is not known to be down: the box' code for that number goes before the
one dialled, as listed in the column *Selection* of the box' internet numbers (`*121#` for the first,
`*122#` for the second, …, `*120#` for the tenth). Without it, the box uses the outgoing number set up
for the dial-help phone. Not for internal numbers and service codes, and not together with the hidden
number. With the live call status on, *calling … from …* shows the number the box actually used.

### Numbers not registered

When the app comes up, or the box in use changes, it asks the box in the background whether its
internet numbers are registered with their provider (`X_VoIP` `X_AVM-DE_GetNumbers`, then
`X_AVM-DE_GetVoIPStatus` for each VoIP number). If one that is switched on is not, the dialer says so
in red where the call status goes – e.g. *030 123456 at Home: not registered with the provider, calls
fail* –, before the dial help rings your phone for a call that cannot go out. A tap on it asks the box
again. Numbers switched off on the box, and analogue or ISDN lines, are left out; if the box does not
answer (user rights, older FRITZ!OS), nothing is shown. `X_AVM-DE_GetVoIPAccounts` is not used, as it
also returns the accounts' SIP credentials.

### Language

Pickelhaube is available in English, German, Dutch, French, Italian, Spanish and Polish.
The flag button in the top bar of the **Settings** opens **Language**: **Auto** (the system
language, the default), shown as a globe 🌐, or one of these, each with its national flag (🇬🇧 for
English). Choosing one returns to the settings, already in the new language. The button shows the
flag of the language chosen, or the globe with *Auto* – not the flag of the language that leads to,
which would stand twice in the list and change along with a language chosen by hand. From Android 13 on this is Android's own per-app language, so the same choice also appears under the system's
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

A long press on Pickelhaube's icon in the launcher opens a menu with four entries (Android's app
shortcuts; each can also be dragged onto the home screen on its own):

1. **Redial** – the number dialled last, with its name from the phone books (taken when it is dialled,
   or once the phone books are read). It **dials right away**, once the app knows which FRITZ!Box is
   reachable from here; with none reachable, the number just stands in the dialer.
2. **Call forwarding** – switches the call forwarding the quick settings tile switches (see *Call
   forwarding* above), on the box reachable from the current network, without opening the app; a short
   message says how it went, or why it could not switch (no box reachable, none set up, not readable).
   The entry is always there; it says whether the forwarding is on and where it forwards to, as last
   read by the app or the tile – the box itself is asked before switching.
3. **Dial number** – the number picker.
4. **The FRITZ!Box reachable from here** (e.g. *FRITZ!Box 7530 AX reachable*, or *No FRITZ!Box
   reachable*), as the app, a tile or a shortcut last found. A tap looks again, without opening the app,
   and says what it found.

Launchers show an entry's long text (e.g. *Erika Muster · 030 1234567*) only when it fits in one line,
otherwise its short one – the name rather than the number (*Erika Muster*). The entries are published when the app starts (so their texts
follow the language) and updated with each call and each reading of the forwardings. Entries 1 and 3
open the dialer through an activity alias that other apps cannot start – only an intent sent there may
dial without the green button being pressed. An entry reopened from the recent apps is not carried out
again.

### Home screen widget

The widget **Numbers to dial** offers numbers chosen by hand, each widget its own list (e.g. "Family"
and "Work"). Placing it opens its setup in the app (with no box set up yet, the welcome tour or the page for the
first box comes first, and the setup follows once a box is there): an optional title, and **Add number** opens the
number picker (recent calls, speed dials, phone books) to add an entry with its name; entries can be
moved up and down and removed. In the widget a tap on an entry puts its number into the dialer, with
the name as the field's label – it is dialled with the green button only, as a tap on the home
screen is easily made by mistake. A tap on the Pickelhaube icon in its top right corner (or on the title) opens the setup again
(from Android 12 also *Reconfigure* in the launcher). The setup also switches the widget to
**transparent**: the wallpaper shows through, a thin light outline holds it together, and the rows
have a dark tint of their own. Wide enough (from about 250 dp), a row has the number right-aligned
beside the name rather than below it. The lists belong to this phone's home screen: they are kept apart
from the settings and not exported. The widget's row only says which entry was tapped; the number is
looked up in its stored list, so no other app can put another number into the dialer through it.
The rows go to the launcher together with the widget itself (`RemoteCollectionItems` through AndroidX
`RemoteViewsCompat`, which brings its own service before Android 12), so a change or a resize simply
redraws the whole widget.

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

Numbers that are plain text – in an email, a signature, a messenger – get in two more ways: **Share**
the text to Pickelhaube, or copy it and tap the **paste** icon in the empty number field. The number is
picked out and cleaned for dialling (`+49 (0)30 / 123 45-6` becomes `+4930123456`); dates, times and
version numbers are left out. With several numbers in the text (phone, fax, mobile), a dialog asks
which one (`data/NumberText.kt`).

The bottom of the settings shows the **version** – name and version code, and whether it is the full
or the Google Play version – with a link to the source code; a long press copies it, e.g. for a bug
report.

## Code map

| File | Purpose |
|---|---|
| `tr064/DigestAuthenticator.kt` | HTTP Digest (MD5, qop=auth) for OkHttp |
| `tr064/Tr064Client.kt` | Generic SOAP call over HTTP/HTTPS + response / UPnP fault parsing |
| `tr064/CertificateTrust.kt` | Pinning trust manager, certificate details, "untrusted certificate" exception |
| `tr064/FritzDialer.kt` | `X_VoIP` actions: `X_AVM-DE_GetPhonePort`, `X_AVM-DE_DialGetConfig`, `X_AVM-DE_DialSetConfig`, `X_AVM-DE_DialNumber`, `X_AVM-DE_DialHangup`; device details (`DeviceInfo` `GetInfo`) |
| `tr064/BoxUsers.kt` | The box' users, offered for the user name (`LANConfigSecurity` `X_AVM-DE_GetUserList`) |
| `tr064/BoxDiscovery.kt` | Recognises the FRITZ!Box of the current network (gateway, `tr64desc.xml` device id) |
| `tr064/DemoBox.kt` | The demo box (host `demo`): answers TR-064 requests, its device description and its call monitor in the app itself |
| `tr064/Phonebooks.kt` | Phone book list / download / XML parsing (`X_AVM-DE_OnTel`) |
| `tr064/LineStatus.kt` | The box' own numbers, their codes for a single call, and whether its internet numbers are registered with their provider (`X_VoIP` `X_AVM-DE_GetNumbers`, `X_AVM-DE_GetVoIPStatus`) |
| `tr064/CallMonitor.kt` | The box' call monitor (TCP port 1012): connection and line parsing |
| `ui/CallProgress.kt` | How the call placed last is going, and how the monitor's report of it is recognised |
| `shortcut/LauncherShortcuts.kt` | The entries of the app icon's long-press menu |
| `shortcut/ShortcutActivities.kt` | Invisible activities behind the "Call forwarding" and FRITZ!Box entries |
| `tile/Tiles.kt` | Quick settings tiles "Pickelhaube", "Redial" and "Call forwarding" |
| `widget/` | Home screen widget "Numbers to dial": the widget, its rows, its lists and the activity the launcher starts to set it up |
| `data/DeviceContacts.kt` | The phone's own contacts as an extra phone book (`READ_CONTACTS`) |
| `tr064/CallList.kt` | Dialled, received and missed calls from the box' call list (`X_AVM-DE_OnTel` `GetCallList`, XML export) |
| `data/DeviceCallLog.kt` | The phone's own dialled, received and missed calls from the Android call log (`READ_CALL_LOG`) |
| `data/MissedCalls.kt` | Which of the box' missed calls are new: after the last look at "Recent", and not settled by a later call |
| `data/NumberText.kt` | Phone numbers picked out of shared or pasted text, cleaned for dialling |
| `data/AppLanguage.kt` | UI language: system or chosen (per-app language on Android 13+, own handling before) |
| `data/BoxRecognition.kt` | Which configured FRITZ!Box is reachable from the current network (app and tile) |
| `data/HostCheck.kt` | Which box answers at the host of a connection being saved, compared with the boxes set up |
| `data/SettingsRepository.kt` | FRITZ!Box connections (passwords encrypted via Android Keystore) and options |
| `data/SettingsFile.kt` | Settings file for export and import (passwords optionally under a passphrase) |
| `ui/` | ViewModel and Compose screens (dialer, settings, certificate dialogs); `UiText` carries ViewModel texts as resources. `DialerViewModel` keeps the core (number input, dialling, `runOnBox`, which talks to the box one request at a time – an action tapped while it is busy waits its turn rather than being dropped, and runs after a failure only once its error was read); each other area is a part of its own (`WidgetPart`, `TransferPart`, `WelcomePart`, `WhatsNewPart`, `CallStatusPart`, `NetworkPart`, `ConnectionsPart`, `DeflectionsPart`, `SpeedDialsPart`, `CallsPart`), reached from the screens as `viewModel.widget`, `viewModel.connections`, …, and sharing state and helpers through `DialerHost` |
| `ui/theme/TextRoles.kt` | How hints (grey italic) and findings about a box (accent colour) look, on every page |
| `res/values*/strings.xml` | All UI texts: English (`values/`) and the translations (`values-de/`, …) |

Unit tests (`./gradlew test`) cover digest computation, envelope building, number sanitising, withholding the own number, picking numbers out of text, the new missed calls,
the call list's date format, the device details, the user list (and the users made by the box), the user's permissions, the comparison of numbers, merging of the recent numbers, certificate pinning, the settings file, the widget's lists, which box answers at a connection's host, the call monitor's lines, the box' VoIP numbers and their state, recognising the dialled number in them, following a call on the call monitor, how a saved connection merges what the box told, which box counts as the one of the network, editing a widget's list, an import without Android's permissions and the demo box.

Instrumented tests (`app/src/androidTest`) run flows in the real app on an emulator, with the demo box –
no FRITZ!Box needed: the welcome tour on first start – its demo link, and page by page with the demo
box at its address `demo` as a real box would be entered, a wrong password corrected, an address where
nothing answers, going back and skipping –, the widget placed before any box is set up
(the 1.6.1 fix), a call followed on the call monitor to its end, and the settings exported with the
passwords, the box deleted and the file imported again (the system file picker answered by the test),
this phone's speed dials (set from the phone books, used with a long press on the keypad – also after a
new start –, removed, switched off in the settings) and the box' ones (used, a new one set up in its
phone books), the call forwardings switched on and off and the one for the tile chosen, and a widget's
numbers added, moved, removed and saved, and the keypad's tones switched on. Each test starts like a fresh install (Android Test Orchestrator with
`clearPackageData`), and animations are off for them. Run them with `scripts/instrumented-tests.sh`,
on one emulator: a connected test run installs the app, clears its data and uninstalls it at the end –
on a phone in use, that would remove Pickelhaube with its boxes and passwords, so the script refuses
anything but an emulator, and checks the result files (Gradle can report success for a run that broke
off). A second argument runs one test class or test: `scripts/instrumented-tests.sh emulator-5554 SpeedDialFlowTest`.

## Notes / ideas

- Plain HTTP (port 49000) remains the default; even there the password never travels in clear text
  (digest auth). HTTPS additionally encrypts the numbers you dial and protects against spoofed routers.
- Works only while the phone is in the home network (or via VPN to it).
- Letting another phone ring for a single call is not offered: the box knows only one phone for dial
  help, and changing it (`X_AVM-DE_DialSetConfig`) has to be confirmed on the box (tested on a
  7530 AX) – twice per call, to switch and to switch back.

## Trademarks

Pickelhaube is an independent project and is not affiliated with FRITZ! GmbH (formerly AVM).
FRITZ! and FRITZ!Box are trademarks of FRITZ! GmbH; they are used here only to say which
routers the app works with.
