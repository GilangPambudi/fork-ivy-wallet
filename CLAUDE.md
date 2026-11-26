# Rivy

Rivy ("re-ivy") is the owner's personal rebrand of Ivy Wallet. This repository
is a fork of `andhikadk/ivy-wallet`, which is itself a fork of
`Ivy-Apps/ivy-wallet`.

## Current focus

Small, incremental changes the owner asks for one at a time. Every UI change
starts with a mockup the owner approves before any code. A full UI redesign
is off the table.

## Branches

| Branch | State |
| --- | --- |
| `main` | Same as upstream. Never commit here directly. |
| `develop` | Integration branch. All work lands here. |
| `rivy/branding` | Branding work, fully contained in `develop`. |

The sync server, Gmail forwarder and the rejected navbar redesign branches
(`rivy/dev`, `rivy/sync-server`, `rivy/navbar-nama-warna`, `rivy/icon`) were
deleted from GitHub on 2026-11-26 at the owner's request.

## Decisions already made

- App name: `Rivy`. Debug build: `Rivy Debug`.
- Application ID: `dev.pambudi.ivy.wallet`, so it installs next to Ivy Wallet.
- The code package stays `com.ivy`. Do not rename it; small diffs keep
  upstream mergeable.
- Icon: the "orbit" mark. Ring `#9C86FF`, dot `#F1EEFA`, background `#15111F`.
  On a 108 x 108 grid the ring is
  `M80.888 46.19 A28 28 0 1 1 61.81 27.112 A13.5 13.5 0 0 0 57.762 34.356 A20 20 0 1 0 73.644 50.238 A13.5 13.5 0 0 0 80.888 46.19 Z`
  and the dot is a circle at (71, 37) with radius 7.5.
- Colors: keep purple, no green. Preferred font: Figtree, not applied yet.
- Firebase is removed, because its config file belonged to the old
  application ID. Crash reporting calls are no-ops and polls are switched off.
- Ivy's Play Store, Telegram and review buttons are removed; the
  open-source card credits Ivy Wallet. The Home more-menu has "Share Rivy",
  which shares this repository's GitHub link.
- Settings has navbar styles (classic and pill variants, tab order by
  long-press drag, auto-hide), a color palette option, and a version label
  linking to this repository. Settings "Product" is removed.
- In pill styles the active indicator slides with the swipe between tabs. The
  + button stays on Reports and Settings but is grayed out and disabled.
- Amount keypad bottom row for IDR: `000`, `0`, backspace.

## Drive export

Settings has "Auto export to Drive". The owner picks two files once through
the system file picker: `rivy.csv` (all transactions) and `rivy-terbaru.csv`
(the last three periods). Rivy rewrites both about a minute after any change
to transactions or accounts, once a day, and when the owner taps the Home
strip or pulls the transaction list down. Files are UTF-8 with plain numbers
and start with one `BALANCE` row per account. The owner's Claude project reads
them through the Google Drive connector. Code:
`temp/legacy-code/.../driveexport/DriveExport.kt`, Home strip in
`feature/home/.../DriveStrip.kt`.

## Building

CI (`.github/workflows/apk.yml`) builds on every push to `main`, `develop` or
`rivy/**`, about 10 minutes. Changes to Markdown files alone do not.

Builds happen only on GitHub: commit, push to `develop`, then download the
APK from the run. No local builds; on the owner's laptop they were slower than
CI. The owner installs APKs on the phone. Never run builds or `adb install`.

A push whose head commit message contains `[release]` also publishes the APK
as a GitHub Release (tag `v<date>-<count>`). Other pushes only build. A
manual run with the `release` input does the same.

The version label is set from git: name = date of the last commit, code =
number of commits. CI checks out the full history (`fetch-depth: 0`) for
that.

```sh
gh run list --branch develop --limit 3
gh run watch <run id>
gh run download <run id>
```

The workflow produces one of two files:

- `Rivy.apk`: release build signed with the owner's key. Built when the
  repository secrets below exist (they do).
- `Rivy-test-key.apk`: the `demo` build type signed with `debug.jks`, only
  when the secrets are missing.

Secrets for the release build: `SIGNING_KEYSTORE` (keystore, base64 on one
line), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`rivy`),
`SIGNING_KEY_PASSWORD`. The keystore is `~/rivy-release.jks` and its password
`~/rivy-release-password.txt` on the owner's Linux laptop. Never commit either
or a link to them; `sign.jks` is not gitignored. Android only installs an
update signed with the same key, so the owner must keep a backup of both.

## Open items

- The import screen lists "Ivy Wallet" with Ivy's logo on purpose, as another
  app to import from.
- `docs/` is still upstream's.
