# Rivy

Rivy ("re-ivy") is a personal money manager for Android. It is a rebrand of
[Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet), which its original
developers stopped maintaining in November 2024.

Rivy keeps Ivy Wallet's features: accounts, transactions, budgets,
categories, loans, planned payments, reports and CSV import. Everything is
stored on the phone. There is no account, cloud sync or tracking.

What Rivy changes so far:

- its own name, icon and logo
- application ID `dev.pambudi.ivy.wallet`, so it installs next to Ivy Wallet
- no Firebase, no Play Store or community links
- a purple accent instead of Ivy's green

## Getting the app

Rivy is not on the Play Store. Each push to a `rivy/**` branch builds an APK
with GitHub Actions; download `Rivy.apk` from the
[latest APK run](https://github.com/GilangPambudi/fork-ivy-wallet/actions/workflows/apk.yml).

## Moving from Ivy Wallet

In Ivy Wallet, export a backup from Settings. In Rivy, choose **Import** and
pick **Ivy Wallet** as the source.

## Credits and license

Rivy is built on the work of the [Ivy Wallet contributors](https://github.com/Ivy-Apps/ivy-wallet/graphs/contributors).
Like Ivy Wallet, it is licensed under the [GPL-3.0](LICENSE) and comes with
no warranty.

The developer guidelines in [docs/](docs/) are Ivy Wallet's and still apply
to this codebase.
