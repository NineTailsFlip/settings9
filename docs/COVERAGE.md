# What these Settings cover

The full list of the stock Settings app (LineageOS 23.2, Android 16: `packages/apps/Settings`,
its `res/xml` pages) and our own additions, each with where it stands. This is the to-do list:
once every row here is done, and the system dialogs at the end too, the stock app can leave the
image.

- ✅ — here, in the Nine look
- ↗ — opens Android's own page for it, from a row here (the permission controller, the ringtone
  picker, an app's own settings): those aren't part of stock Settings and stay
- ⏳ — not yet; until then the stock app's page is reachable from `*` → "Android's own settings"

## Network & internet

| Stock | Here |
|---|---|
| Internet (Wi-Fi on/off, networks, join with a password, saved networks, forget) | ✅ |
| Wi-Fi: enterprise (EAP) networks, network details, IP settings, Wi-Fi preferences | ⏳ |
| Calls & SMS, SIMs | ⏳ |
| Mobile data, roaming | ✅ |
| Access point names | ↗ (stock page) |
| Airplane mode | ✅ |
| Hotspot & tethering | ⏳ |
| Data Saver, data usage | ⏳ |
| VPN | ⏳ |
| Private DNS | ⏳ |
| Adaptive connectivity, mobile network security | ⏳ |

## Connected devices

| Stock | Here |
|---|---|
| Bluetooth on/off, the phone's name | ✅ |
| Saved (paired) devices, connect | ✅ |
| Forget a device | ✅ (`*`) |
| Pair new device (scan, pair) | ✅ — the PIN prompt is still the stock app's dialog |
| Device details (profiles: calls, media) | ⏳ |
| Audio sharing, media devices, "Play media to" | ⏳ |
| Connection preferences: NFC, printing, Android Auto, Nearby Share | ⏳ |
| USB (file transfer, charging only) | ⏳ |

## Apps

| Stock | Here |
|---|---|
| All apps (with system apps from `*`) | ✅ |
| App info: version, size, open | ✅ |
| App info: notifications on/off | ✅ |
| App info: permissions | ↗ |
| App info: force stop, turn off / uninstall, clear data | ✅ |
| App info: clear cache only, storage details, mobile data, battery, open by default, language | ⏳ |
| Default apps (phone, messages, home screen, browser) | ✅ list, ↗ choose |
| Recently opened apps | ⏳ |
| Unused apps, app battery usage, cloned apps, aspect ratio | ⏳ |
| Special app access (all files, display over apps, usage access, battery optimization, alarms, install unknown apps…) | ⏳ — "Install unknown apps" ↗ |

## Notifications

| Stock | Here |
|---|---|
| Do not disturb on/off | ✅ |
| Do not disturb schedules and exceptions (priority modes) | ⏳ |
| Notifications on the lock screen, their contents | ✅ |
| Per-app notifications | ✅ (on the app's page) |
| Notification history, conversations, bubbles | ⏳ |
| Notification access (apps that read notifications) | ⏳ |
| Notification cooldown, snoozing, dot on app icon, heads-up, blink light | ⏳ |
| Wireless emergency alerts | ⏳ |

## Sound & vibration

| Stock | Here |
|---|---|
| Ring / vibrate / silent | ✅ |
| Media, call, ring, notification, alarm volume | ✅ |
| Ringtone, notification sound, alarm sound | ✅ list, ↗ pick |
| Vibrate when ringing | ✅ |
| Dial pad tones | ✅ |
| Screen locking, charging, docking, touch sounds | ⏳ |
| Vibration & haptics, sound patterns, spatial audio, "Take call on" | ⏳ |
| Shortcut to prevent ringing, power-on sounds | ⏳ |

## Display

| Stock | Here |
|---|---|
| Brightness level, adaptive brightness | ✅ |
| Screen timeout | ✅ |
| Dark theme | ✅ |
| Text size (font scale) | ✅ |
| Stay on while charging | ✅ (also in developer options) |
| Display size | ⏳ |
| Night Light, colors, color contrast | ⏳ |
| Lock screen: always show time and info, ambient display | ⏳ |
| Auto-rotate, screen resolution, refresh rate | — not on thor2 (fixed screen) |
| Screen saver | ⏳ |
| Wake on plug, Lineage: tap to wake/sleep, LiveDisplay | ⏳ |

## Battery

| Stock | Here |
|---|---|
| Charge level, charging | ✅ |
| Battery Saver on/off | ✅ |
| Battery percentage in the status bar | ✅ |
| Battery usage (per app), battery information, Battery Manager | ⏳ |

## Storage

| Stock | Here |
|---|---|
| Used / free | ✅ |
| Apps by size | ✅ via Apps |
| Files | ↗ (Files app) |
| Images / videos / audio / documents / games / system breakdown | ⏳ |
| Storage manager, free up space, trash | ⏳ |

## Security & privacy

| Stock | Here |
|---|---|
| Screen lock: set, change, remove a PIN | ✅ |
| Screen lock: pattern, password, swipe | ⏳ |
| Fingerprint, face | — not on thor2 |
| Permission manager | ↗ |
| Show passwords | ✅ |
| Install unknown apps | ↗ |
| Device admin apps, trust agents, SIM lock, encryption & credentials, Smart Lock, app pinning | ⏳ |
| Privacy dashboard, camera/microphone access toggles, clipboard access, show media on lock screen | ⏳ |
| Safety center | — (no Safety center in this build) |

## Location

| Stock | Here |
|---|---|
| Use location | ✅ |
| App location permissions | ↗ |
| Recent access, location services (Wi-Fi/Bluetooth scanning), assisted GPS | ⏳ |

## Passwords & accounts

| Stock | Here |
|---|---|
| Accounts on the phone | ✅ (list) |
| Add an account | ✅ — the account's own app takes it from there |
| Remove an account, per-account sync | ⏳ |
| Automatically sync app data | ✅ |
| Passwords, autofill service | ⏳ |

## Accessibility

| Stock | Here |
|---|---|
| Text size | ✅ (Display) |
| High-contrast text | ✅ |
| Color inversion | ✅ |
| Remove animations | ✅ |
| Accessibility services (screen readers and others) on/off | ✅ |
| Magnification, extra dim, color correction, captions, audio, timing, shortcuts, text-to-speech | ⏳ |

## System

| Stock | Here |
|---|---|
| Languages (the phone's) | ✅ |
| Keyboard: which keyboard is in use | ✅ |
| Keyboard: K9's layouts, any number | ✅ (extra: K9's LanguagesProvider) |
| Keyboard: its own settings | ↗ (K9's Settings) |
| Physical keyboard layouts, spell checker, text-to-speech | ⏳ |
| Date & time: automatic, time zone (west to east), by hand, 24-hour | ✅ |
| Gestures (Lineage: buttons, power menu) | ⏳ — "Long press Home" ✅ (extra, thor2) |
| System updates | ⏳ (LineageOS Updater isn't in this image) |
| Users | ⏳ |
| Reset: erase everything | ✅ |
| Reset: Wi-Fi/mobile/Bluetooth, app preferences | ⏳ |
| Developer options: on/off, USB debugging, stay awake | ✅ |
| Developer options: the rest | ↗ (stock page) |
| Backup | ⏳ |

## About phone

| Stock | Here |
|---|---|
| Device name | ✅ |
| Model, Android version (security patch), LineageOS version, kernel | ✅ |
| Build number (7 presses: developer options) | ✅ |
| Phone number, IMEI, IP address, uptime | ✅ |
| SIM status, Wi-Fi and Bluetooth MAC, EID | ⏳ |
| Legal information, regulatory labels | ⏳ |

## Our own additions

| What | Here |
|---|---|
| K9 layouts, more than one at a time (also a step of the first-time setup) | ✅ |
| The notification shade and quick settings, in place of SystemUI's panel (long press Home, apps expanding the status bar; patch `0010`) | ✅ — `ShadeActivity` |
| Shade: notifications newest first, the music, open/dismiss/clear all, actions and typed replies | ✅ |
| Shade: Wi-Fi, Bluetooth, mobile data, airplane, flashlight, Do not disturb, sound mode, brightness, location, battery saver | ✅ |
| Shade: hotspot, screen cast, third-party Quick Settings tiles (`TileService`) | ⏳ |
| The Nine apps' look (font, hint bar) for all of them at once, as in the first-time setup | ⏳ |
| Outer screen (ThorSubScreen): Display → Outer screen, through ThorSubScreen's `PrefsProvider` | ✅ |
| Buttons and lid (ThorSubScreen's keys, the red key, long press Home, the lid): System → Buttons and lid | ✅ |
| Recent apps (L9) | ✅ — in L9, not here |

## The system's own dialogs in the stock app

These aren't pages anyone opens from a list: Android starts them itself. They stay with the stock
app until they move here, and it can't leave the image before then.

| Dialog | Here |
|---|---|
| Bluetooth pairing PIN / confirm (`BluetoothPairingRequest`) | ⏳ |
| Bluetooth "allow access to contacts/messages", turn-on requests from apps | ⏳ |
| The home screen before the first unlock after a restart (`FallbackHome`) | ⏳ |
| Confirm the screen lock for another app (`CONFIRM_DEVICE_CREDENTIAL`), choose a new lock (`SET_NEW_PASSWORD`) | ⏳ |
| Wi-Fi requests from apps (`REQUEST_ENABLE`, network request, Wi-Fi picker) | ⏳ |
| Add a device admin, request to ignore battery optimizations, usage access requests | ⏳ |
| Add an account (`ADD_ACCOUNT_SETTINGS`) | ⏳ |
| App widgets: bind, pick | ⏳ |
| Quick Settings tile long-presses into pages not listed above | ✅ — `*` on a switch in the shade opens its page |
