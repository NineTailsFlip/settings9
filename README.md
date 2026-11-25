# Settings

The phone's settings for [NineTailsFlip](https://github.com/NineTailsFlip)'s LineageOS build for
keypad flip phones (ALT AT-M140, thor2), in the look of the Nine apps: every page a list, a hint
bar, Up/Down to move, OK to open or choose, Left/Right to change a value, `*` for the menu, Back
for the previous page.

Network and Wi-Fi, mobile data, airplane mode, Bluetooth, apps (info, notifications, permissions,
force stop, uninstall, clear data, default apps), notifications and Do not disturb, sound and
volumes, display, battery, storage, the screen lock PIN, location, accounts, accessibility,
languages, the keyboard and K9's layouts, date and time, developer options, erasing the phone,
and about phone. [`docs/COVERAGE.md`](docs/COVERAGE.md) is the full list of the stock Settings
app, page by page, with what is here and what isn't yet.

## How it fits in

- **In the image, not on its own.** The ROM build ([android_vendor_thor2](https://github.com/kloploid/android_vendor_thor2))
  takes the latest release, re-signs it with the platform key and installs it as a privileged
  app, which is what lets it change system settings. It isn't updated on its own.
- **It answers `android.settings.*`.** The launcher icon, the gear in the notification shade, and
  any app asking for a settings page (Wi-Fi, Bluetooth, an app's details, …) open here: its
  intent filters outrank the stock app's.
- **The stock Settings app stays, out of sight,** for the system's own dialogs (Bluetooth pairing,
  the home screen before the first unlock, confirming the PIN for another app) and for the pages
  not here yet; its icon is hidden. `*` → "Android's own settings" opens it.

## Building

```sh
git clone --recursive https://github.com/NineTailsFlip/settings9
./gradlew assembleRelease
```

The shared [nine-core](https://github.com/NineTailsFlip/nine-core) comes as a submodule. Releases
are built and signed by CI on a `v<versionName>` tag.

## License

GPL-3.0, like the rest of the Nine suite.
