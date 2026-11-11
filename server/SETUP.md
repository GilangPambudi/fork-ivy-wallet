# Setting up Rivy sync and the email inbox

Four parts, in this order. Each one is done once.

1. Google sign-in (Google Cloud Console)
2. The server (VPS)
3. The email forwarder (Gmail)
4. The app (phone)

## 1. Google sign-in

The app signs in with Google and the server checks that sign-in. Google needs
to know both of them.

1. Open <https://console.cloud.google.com>, create a project named `Rivy`.
2. Open **APIs & Services > OAuth consent screen** (also shown as
   **Google Auth Platform**). Choose **External**, fill in the app name and
   your email, and add your own Google account under **Audience > Test users**.
3. Open **Clients** (or **Credentials > Create credentials > OAuth client ID**)
   and create a client of type **Web application**. Name it `Rivy server`.
   Copy its **Client ID**. This is `RIVY_GOOGLE_CLIENT_ID` in part 2.
4. Create a second client of type **Android**:
   - Package name: `dev.pambudi.ivy.wallet`
   - SHA-1: `96:27:30:45:9B:10:6B:8A:A8:FB:22:54:30:DE:B6:A9:7C:55:96:99`

   Nothing has to be copied from this one, it only has to exist. The SHA-1
   belongs to `debug.jks` in this repository, which signs the Demo build that
   GitHub Actions produces. A build signed with another key needs its own
   Android client with that key's SHA-1.

## 2. The server

Needs Docker with the compose plugin, and a domain or subdomain that points
at the VPS.

```sh
git clone https://github.com/GilangPambudi/fork-ivy-wallet.git
cd fork-ivy-wallet/server
cp .env.example .env
```

Fill in `.env`:

| Variable | Value |
| --- | --- |
| `RIVY_DB_PASSWORD` | A new password, letters and digits only: `openssl rand -hex 16` |
| `RIVY_ALLOWED_EMAIL` | Your Google account. Nobody else can sign in. |
| `RIVY_GOOGLE_CLIENT_ID` | The Web application client ID from part 1. |
| `RIVY_INGEST_TOKEN` | A new secret for the email forwarder: `openssl rand -hex 24` |

Start it and check it:

```sh
docker compose up -d --build
curl http://127.0.0.1:8080/healthz
```

The server only listens on the VPS itself. Put it behind the reverse proxy
you already use, with HTTPS. With Caddy that is:

```
rivy.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

From any other computer this must now answer with the client ID:

```sh
curl https://rivy.example.com/v1/config
```

To update later: `git pull && docker compose up -d --build`. The data lives
in the Docker volume `rivy-db` and survives updates.

## 3. The email forwarder

Follow [`forwarder/README.md`](forwarder/README.md). It needs the server
address and `RIVY_INGEST_TOKEN` from part 2.

## 4. The app

1. Download `Rivy-Demo.apk` from the latest **APK** run under the Actions tab
   of the repository and install it.
2. Rivy is a separate app from Ivy Wallet and starts empty. To bring your
   data over, make a backup in the old app (**Settings > Backup data**) and
   import it in Rivy (**Settings > Import data**) before connecting.
3. In Rivy open **Settings > Sinkronisasi**, type the server address and tap
   **Sambungkan dengan Google**.
4. On every further phone: install, skip the import, connect, and choose
   **Pakai data server**.

Bank transactions show up under **Settings > Kotak masuk transaksi**. Nothing
is recorded until you approve it there.
