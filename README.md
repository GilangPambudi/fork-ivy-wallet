# Rivy

> [!IMPORTANT]
> **This is not Ivy Wallet.** Rivy is a personal fork with its own name, icon
> and application ID. It is not made, reviewed or supported by the Ivy Wallet
> team. Looking for Ivy Wallet itself? Go to the original repository:
> **[Ivy-Apps/ivy-wallet](https://github.com/Ivy-Apps/ivy-wallet)**.

Rivy ("re-ivy") is a money manager for Android, built on
[Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) after its original
developers stopped maintaining it in November 2024.

Rivy keeps Ivy Wallet's features: accounts, transactions, budgets,
categories, loans, planned payments, reports and CSV import. Data stays on the
phone. There is no account and no tracking.

What Rivy adds or changes:

- its own name, purple orbit icon and logo
- application ID `dev.pambudi.ivy.wallet`, so it installs next to Ivy Wallet
- optional automatic export of two CSV files to Google Drive, updated after
  every change, for reading with an AI assistant
- an `000` key on the amount keypad for IDR
- no Firebase, and no Play Store, Telegram or review links

## Getting the app

Rivy is not on the Play Store. Download `Rivy.apk` from the
[Releases page](https://github.com/GilangPambudi/fork-ivy-wallet/releases)
and open it on your phone. Android may ask you to allow installing apps from
your browser or file manager.

## Moving from Ivy Wallet

In Ivy Wallet, open Settings and use **Backup data**. In Rivy, choose
**Import** and pick **Ivy Wallet** as the source. Rivy and Ivy Wallet can stay
installed side by side.

## Credits

Rivy exists because of the people who built Ivy Wallet. All of the original
work, design and most of the code is theirs.

### Contributors Wall

<a href="https://github.com/Ivy-Apps/ivy-wallet/graphs/contributors">
  <img alt="contributors graph" src="https://contrib.rocks/image?repo=Ivy-Apps/ivy-wallet" />
</a>

### Creative Contributors

People who helped Ivy Wallet in non-code, creative ways.

<div style="text-align: center">
    <img src="https://avatars.githubusercontent.com/u/62771583?v=4" width="100px;" alt="Stefan Ilijev - Desinger"/><br>
    <strong>Stefan Ilijev</strong><br>
    <small>Co-founder and designer of Ivy Wallet. Created the <a href="https://www.figma.com/file/kSwIa07jcHEHZXo6rzx7dn/Design-System?node-id=0%3A1&mode=dev">Ivy design system</a>.</small>
    <br/>
    <br/>
</div>

<div style="text-align: center">
    <img src="https://avatars.githubusercontent.com/u/86833171?v=4" width="100px;" alt="Aditya [ADX]"/><br>
    <strong><a href="https://github.com/adx69" >Aditya</a> </strong><br>
    <br/>
</div>

<div style="text-align: center">
    <img src="https://avatars.githubusercontent.com/u/130169485?v=4" width="100px;" alt="Shymom [SSI]"/><br>
    <strong><a href="https://github.com/SHYMOM" >Shymom</a> </strong><br>
    <br/>
</div>

Rivy is forked from [andhikadk/ivy-wallet](https://github.com/andhikadk/ivy-wallet),
which added the `000` keypad shortcut.

## License

Like Ivy Wallet, Rivy is licensed under the [GPL-3.0](LICENSE) and comes with
no warranty. The developer guidelines in [docs/](docs/) are Ivy Wallet's and
still apply to this codebase.
