# Rivy

Rivy ("re-ivy") is the owner's personal rebrand of Ivy Wallet. This repository
is a fork of `andhikadk/ivy-wallet`, which is itself a fork of
`Ivy-Apps/ivy-wallet`.

## Current focus

Branding only: name, application ID, icon and logo. Work happens on
`rivy/branding`. Do not add features until the owner asks.

## Branches

| Branch | State |
| --- | --- |
| `main` | Same as upstream. Never commit here directly. |
| `rivy/branding` | Active. Name, application ID, icon, in-app logos, APK workflow. |
| `rivy/dev` | Parked. Branding of that time plus a sync server (`server/`), a Gmail forwarder and the `feature/rivy` module (Google sign-in, sync, bank email inbox). Builds and its tests pass, never tried on a phone. Kept for later, not part of the current focus. |
| `rivy/icon`, `rivy/sync-server` | Fully contained in the branches above. Can be deleted. |
| `rivy/navbar-nama-warna` | A UI redesign the owner rejected. Leave it alone. |

## Decisions already made

- App name: `Rivy`. Debug build: `Rivy Debug`.
- Application ID: `dev.pambudi.ivy.wallet`, so it installs next to Ivy Wallet.
- The code package stays `com.ivy`. Do not rename it; small diffs keep
  upstream mergeable.
- Icon: the "orbit" mark. Ring `#9C86FF`, dot `#F1EEFA`, background `#15111F`.
  On a 108 x 108 grid the ring is
  `M80.888 46.19 A28 28 0 1 1 61.81 27.112 A13.5 13.5 0 0 0 57.762 34.356 A20 20 0 1 0 73.644 50.238 A13.5 13.5 0 0 0 80.888 46.19 Z`
  and the dot is a circle at (71, 37) with radius 7.5.
- Colors: keep purple, no green. Preferred font: Figtree. Neither is applied yet.
- A full UI redesign is off the table.
- Firebase is removed, because its config file belonged to the old
  application ID. Crash reporting calls are no-ops and polls are switched off.

## Where the branding lives

- App name and ID: `app/build.gradle.kts`
- Launcher icon: `app/src/main/res/mipmap-*`, `app/src/release/res/mipmap-*`,
  `ic_launcher_background` in both `values` folders,
  `app/src/main/ic_launcher-playstore.png`
- In-app logo (onboarding): `shared/ui/core/src/main/res/drawable/ivy_wallet_logo.xml`.
  The file name is upstream's, the content is the Rivy mark.
- Notification and quick-settings icons: `ic_notification.xml`,
  `ic_notification_m.xml`, `ic_launcher_foreground.xml` in the same folder

## Building

The APK is built by GitHub Actions (`.github/workflows/apk.yml`), not locally.
Every push to a `rivy/**` branch starts a build of about 10 minutes.
Changes to Markdown files alone do not.

```sh
gh run list --branch rivy/branding --limit 3
gh run watch <run id>
gh run download <run id>
```

The workflow produces one of two files:

- `Rivy.apk`: release build signed with the owner's key. Built when the
  repository secrets below exist.
- `Rivy-test-key.apk`: the `demo` build type, the same app signed with
  `debug.jks`. That key is public in this repository, so this build is for
  testing only.

Secrets for the release build:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE` | The keystore file, base64 encoded on one line |
| `SIGNING_STORE_PASSWORD` | Password of the keystore |
| `SIGNING_KEY_ALIAS` | Alias of the key inside it |
| `SIGNING_KEY_PASSWORD` | Password of the key |

Creating the key, once, on the owner's machine and outside the repository:

```sh
keytool -genkeypair -v -keystore ~/rivy-release.jks -alias rivy \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 ~/rivy-release.jks | gh secret set SIGNING_KEYSTORE
gh secret set SIGNING_STORE_PASSWORD
gh secret set SIGNING_KEY_ALIAS --body rivy
gh secret set SIGNING_KEY_PASSWORD
```

Never commit the keystore or its passwords. The owner must keep a backup of
the keystore and password: Android only installs an update over an existing
app when both are signed with the same key. For the same reason a
`Rivy-test-key.apk` already on a phone has to be uninstalled before
`Rivy.apk` can be installed, which removes its data. Export a backup first.

## Open items, waiting for the owner's decision

- `Ivy Wallet` still appears in about ten texts in
  `shared/ui/core/src/main/res/values/strings.xml` and its translations, and
  in the poll texts in `feature/poll` and `feature/home`. They sit on cards
  about sharing the app, Play Store reviews, the Ivy Telegram group and the
  open-source notice. Renaming them is not enough, because Rivy has no Play
  Store page or community; decide per card whether to reword or hide it.
- The accent color is still Ivy's green, for example the `#opensource` tag
  and the links on the onboarding screen.
- The import screen lists "Ivy Wallet" with Ivy's logo
  (`drawable-nodpi/ivywallet_logo.png`) as a source to import from. It was
  left on purpose: it names the other app, like the Spendee entry does.
- `README.md` and `docs/` are still upstream's.
- The paparazzi snapshot images under `*/src/test/snapshots` were not
  re-recorded after the logo change.
