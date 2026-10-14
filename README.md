<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="112" alt="Contacts for Nextcloud">
</p>

<h1 align="center">Contacts for Nextcloud</h1>

<p align="center">
A contacts app for a Nextcloud server. It syncs an address book over CardDAV.<br>
It is an unofficial Nextcloud Contacts app.
</p>

<p align="center">
  <a href="https://github.com/LukasSor/Contacts-for-Nextcloud/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0--or--later-blue?style=flat-square" alt="License: AGPL-3.0-or-later"></a>
  <a href="https://github.com/LukasSor/Contacts-for-Nextcloud"><img src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android"></a>
  <a href="https://github.com/LukasSor/Contacts-for-Nextcloud"><img src="https://img.shields.io/badge/built%20with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://github.com/LukasSor/Contacts-for-Nextcloud"><img src="https://img.shields.io/badge/sync-Nextcloud%20%2F%20CardDAV-0082C9?style=flat-square&logo=nextcloud&logoColor=white" alt="Nextcloud / CardDAV"></a>
  <a href="https://github.com/LukasSor/Contacts-for-Nextcloud/commits/main"><img src="https://img.shields.io/github/last-commit/LukasSor/Contacts-for-Nextcloud?style=flat-square" alt="Last commit"></a>
</p>

<p align="center">
  <a href="https://github.com/LukasSor/Contacts-for-Nextcloud/releases"><img src="fastlane/metadata/android/en-US/images/badge_github.png" height="54" alt="Get it on GitHub"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.nextcloud.contacts%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FLukasSor%2FContacts-for-Nextcloud%22%2C%22author%22%3A%22LukasSor%22%2C%22name%22%3A%22Contacts%20for%20Nextcloud%22%7D"><img src="fastlane/metadata/android/en-US/images/badge_obtainium.png" height="54" alt="Get it on Obtainium"></a>
</p>

## Features

- Syncs an address book with Nextcloud over CardDAV
- Works with the Android contacts system
- Material 3
- Edit contacts, groups, and favorites
- Open Nextcloud Talk, and use other apps already on the phone
- Put birthdays, anniversaries, and dates of death on a calendar
- Import and export contacts

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01_contact_list.png" width="250" alt="Contact list">
  &nbsp;
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02_contact_detail.png" width="250" alt="Contact detail">
  &nbsp;
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03_add_contact.png" width="250" alt="Add contact">
</p>

## Privacy

Contacts go to the Nextcloud server you sign in to. A copy of the address book stays on the phone, and so does the login.

Website logos are on unless you turn them off. The app then loads the icon from that website. Address suggestions are also on unless you turn them off. The text you type is sent to a Photon server. The default is `https://photon.komoot.io`. You can set another server in settings.

The app asks for contacts permission to use the phone address book and linked apps, phone permission to place a call, and calendar permission to write dates to the phone calendar.

The app has no analytics library.

## Build

Android 8.0 (API 26) or newer. Open the project in Android Studio, or run:

```bash
./gradlew assembleDebug
```

Build output is written outside the project folder, under `nextcloud-contacts-build/` in `%LOCALAPPDATA%`, `$XDG_CACHE_HOME`, or `~/.cache`.

## Contributing

Issues and pull requests are welcome.

Translations are the `values-*` string files under `app/src/main/res`.

## License

The app is under the GNU Affero General Public License v3.0 or later.

See the [LICENSE](LICENSE) file.
