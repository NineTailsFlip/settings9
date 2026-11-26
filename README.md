# Settings

The phone's settings for NineTailsFlip's LineageOS on keypad flip phones, in the Nine look.

[![Build](https://github.com/NineTailsFlip/settings9/actions/workflows/build.yml/badge.svg)](https://github.com/NineTailsFlip/settings9/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/NineTailsFlip/settings9)](https://github.com/NineTailsFlip/settings9/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

On the [thor2 image](https://github.com/kloploid/android_vendor_thor2) (ALT AT-M140) these
Settings open instead of the stock app: from the launcher, the gear in the notification shade, and
any app asking for an `android.settings.*` page. Every page is a Nine list. Part of
[NineTailsFlip](https://github.com/NineTailsFlip).

## Features

| Section | What's there |
|---|---|
| Network & internet | Wi-Fi (networks, join with a password, forget), mobile data, roaming, access point names, airplane mode |
| Bluetooth | On/off, the phone's name, paired devices (connect, forget), pairing new ones |
| Apps | Every app: info, open, notifications, permissions, force stop, turn off or uninstall, clear data; default apps |
| Notifications | Do not disturb, the lock screen and its privacy |
| Sound & vibration | Ring/vibrate/silent, five volumes, ringtone and notification and alarm sounds, vibrate when ringing, dial pad tones |
| Display | Brightness, adaptive brightness, screen timeout, dark theme, text size, stay on while charging; the outer screen (pages, clock, status line, what lights it up, night mode) |
| Battery | Charge, battery saver, percentage in the status bar |
| Storage | Used and free, apps by size, files |
| Security | Set, change or remove the PIN; permission manager; show passwords; install unknown apps |
| Location | On/off, app access |
| Accounts | Accounts on the phone, add one, auto-sync |
| Accessibility | High-contrast text, color inversion, remove animations, accessibility services |
| System | Languages, keyboard and K9's layouts (any number), date and time and time zone, buttons and lid (each extra key's short and long press, the red key, long press Home, the lid), developer options, erase everything |
| About phone | Device name, model, Android and LineageOS versions, build number (7 presses: developer options), phone number, IMEI, IP, uptime |

### The status bar

The status bar is Settings' too: one line over SystemUI's, always shown (android_vendor_thor2's
patch `0011` keeps it on and every app's content below it). The time on the left; on the right
what's new and what's on - `✉3`, an alarm, Do not disturb, vibrate or silent, Bluetooth, Wi-Fi,
the signal, the battery - bright when connected, gray when only on; with too little room the
least important go first. A new urgent notification takes the line for five seconds
("Mom: are you coming?") in place of SystemUI's pop-ups, which it turns off; an alarm's stays
until it's gone. A tap (or a swipe down) opens the shade, or the notification on the line.
SystemUI's own panel never opens.

### The shade

The notification shade and quick settings are here too, as one Nine list in place of SystemUI's
panel (which needs a touchscreen). A long press on Home opens it, and so does any app expanding
the status bar (android_vendor_thor2's patch `0010`); the same key closes it.

- **Notifications**, newest first, with the music on top (play/pause, previous, next): OK opens
  one, Left dismisses it, `*` lists its actions - a reply is typed under the list - and "Clear
  all" is at the end.
- **Quick settings:** Wi-Fi, Bluetooth, mobile data, airplane mode, flashlight, Do not disturb,
  ring/vibrate/silent, brightness, location, battery saver. OK switches; `*` opens the setting's
  page.

[`docs/COVERAGE.md`](docs/COVERAGE.md) lists every page of the stock Settings app and where each
stands here.

## Installation

Settings is part of the image and isn't installed on its own: the ROM build takes the latest
release, re-signs it with the platform key and installs it as a privileged app, which is what
lets it change system settings. It updates with the image.

The stock Settings app stays in the image, without its icon, for the system's own dialogs
(Bluetooth pairing, the home screen before the first unlock, confirming the PIN for another app)
and the pages not here yet; `*` → "Android's own settings" opens it.

## Usage

| Key | Does |
|---|---|
| Up / Down | Move |
| OK | Open a page, choose, or flip a value |
| Left / Right | Change a value; jump by letter in long lists |
| Back | The previous page |
| `*` | The page's menu, all settings, Settings' own look |

## How it works

- `ScreenActivity` shows any page; a page (`Screen`) only says what its rows are and what they
  do. `Screens` names every page and maps each `android.settings.*` action to one; the manifest
  answers those actions at a priority above the stock app's.
- System calls that the SDK doesn't show (@SystemApi and hidden ones) are made by reflection
  (`Hidden`); a call that fails logs and the row says so, rather than crashing.

## Building

Requirements: JDK 17+, Android SDK (platform 34).

```bash
git clone --recursive https://github.com/NineTailsFlip/settings9.git
cd settings9
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

A tag `v<versionName>` builds and publishes a release (NineTailsFlip/.github's `android.yml`).

## Project structure

| Path | What's there |
|---|---|
| `app/src/main/java/io/github/ninetailsflip/settings/` | `ScreenActivity`, `Screen`, `Screens`, the pages (`MainScreen`, `NetworkScreens`, `BluetoothScreen`, `AppsScreens`, `DeviceScreens`, `SecurityScreens`, `SystemScreens`, `AboutScreen`), `Hidden`; the shade (`ShadeActivity`, `Shade`: the screen, its notification listener, the flashlight); the status bar (`StatusBar`: `StatusBarService`, its line and its pop-up line) |
| `docs/COVERAGE.md` | The stock Settings pages, and which are done |
| `nine-core/` | Git submodule: [nine-core](https://github.com/NineTailsFlip/nine-core) |

## Contributing

Issues and pull requests are welcome; see
[CONTRIBUTING](https://github.com/NineTailsFlip/.github/blob/main/CONTRIBUTING.md). The pages
still to do are in [`docs/COVERAGE.md`](docs/COVERAGE.md).

## License

GPL-3.0 — see [LICENSE](LICENSE), like the rest of the Nine apps.
