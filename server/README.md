# Rivy sync server

A small server that keeps Rivy in sync across devices. Devices sign in with
Google, push the items they changed and pull the changes made elsewhere.

It also reads bank emails: a forwarder sends them in, the server turns each
one into a draft transaction, and the devices show it for approval. Nothing is
recorded in the app without the owner approving it.

Go, PostgreSQL, one dependency (`lib/pq`). Built for a single owner: only one
Google account may sign in.

## How sync works

- Every synced item (account, category, transaction, ...) is one **record**:
  a `type`, an `id`, the item itself as JSON in `data`, the time it was last
  edited (`updated_at`) and a `deleted` flag.
- The server does not interpret `data`. It keeps the latest version of each
  record and numbers every change with a growing `seq`.
- **Push**: a device sends the records it changed.
- **Pull**: a device asks for everything after the last `seq` it saw.
- **Conflicts**: the last edit wins. If two devices changed the same record,
  the one with the newer `updated_at` is kept. The loser gets the server's
  version back in `stale` and should adopt it.
- **Deletes** are records with `deleted: true`, so every device learns
  about them.

## API

All bodies are JSON. Everything under `/v1/sync` needs
`Authorization: Bearer <session token>`.

| Method and path | What it does |
| --- | --- |
| `GET /healthz` | 200 when the server and its database are up. |
| `GET /v1/config` | No sign-in needed. Returns `{"google_client_id": "...", "inbox_enabled": bool}` so the app only has to know the server address. |
| `POST /v1/auth/google` | Body `{"id_token": "...", "device_name": "..."}`. Returns `{"token": "...", "email": "..."}`. 401 if Google did not issue the token, 403 if it is another account. |
| `POST /v1/auth/logout` | Ends the session of the calling device. |
| `POST /v1/sync/push` | Body `{"records": [...]}`, at most 1000 per call. Returns `{"applied": n, "unchanged": n, "stale": [...]}`. |
| `GET /v1/sync/pull?since=0&limit=500` | Returns `{"records": [...], "next_since": n, "has_more": bool}`. Call again with `since=next_since` while `has_more` is true. |
| `POST /v1/inbox/email` | For the email forwarder, see below. Needs `Authorization: Bearer <ingest token>`. |

A record:

```json
{
  "type": "transaction",
  "id": "3f2b1c9e-5d4a-4e8b-9a7c-1b2d3e4f5a6b",
  "updated_at": "2026-01-15T12:00:00Z",
  "deleted": false,
  "data": { "title": "Lunch", "amount": 15000.0 }
}
```

Allowed types: `account`, `category`, `transaction`, `planned_payment_rule`,
`budget`, `loan`, `loan_record`, `tag`, `tag_association`, `settings`,
`inbox_item`, `merchant_rule`, `account_link`.

An `updated_at` in the future is treated as "now", so a phone with a wrong
clock cannot win every conflict.

## Email inbox

`POST /v1/inbox/email` takes one bank email:

```json
{
  "message_id": "<abc@bank.example>",
  "from": "BCA <bca@bca.co.id>",
  "subject": "Internet Transaction Journal",
  "date": "2026-11-09T11:23:50Z",
  "html": "<html>...</html>",
  "text": "plain text body, used when html is empty"
}
```

It answers `{"result": "...", "id": "eml-..."}`:

| Result | Meaning |
| --- | --- |
| `stored` | A transaction was read and saved as a pending `inbox_item`. |
| `duplicate` | This email was already stored. Sending an email twice is safe. |
| `ignored` | Not from a known bank address, or not a transaction (a notice, a failed payment). |
| `unparsed` | From a bank and looks like a transaction, but could not be read. It is kept with `parsed: false` and the start of the email in `note`, so it is not lost. |

Known senders: `bca@bca.co.id` (myBCA), `noreply@jago.com` (Jago) and
`receipts@blubybcadigital.id` (blu). Amounts, dates and names are read with
fixed rules, never guessed.

An `inbox_item` record reaches devices through the normal pull:

```json
{
  "bank": "bca",
  "kind": "payment",
  "amount": 18000,
  "currency": "IDR",
  "counterparty": "Toko Sumber Rejeki",
  "source": "0123****45",
  "method": "QRIS",
  "reference": "...",
  "occurred_at": "2026-11-09T11:23:47Z",
  "status": "pending",
  "parsed": true,
  "subject": "Internet Transaction Journal",
  "received_at": "2026-11-09T11:24:02Z"
}
```

`kind` is `payment`, `transfer`, `topup`, `incoming` or `pocket_move` (money
moved between Jago pockets). A device changes `status` when the owner approves
or dismisses the item and pushes the record back.

A transfer between two of the owner's banks arrives as two emails. When an
outgoing and an incoming email from different banks have the same amount
within 10 minutes, the later one gets `pair_id` pointing at the earlier one,
so the app can show them as one transfer.

The forwarder lives in [`forwarder/`](forwarder/README.md).

## Configuration

| Variable | Meaning |
| --- | --- |
| `RIVY_DATABASE_URL` | PostgreSQL connection string. |
| `RIVY_ALLOWED_EMAIL` | The only Google account that may sign in. |
| `RIVY_GOOGLE_CLIENT_ID` | OAuth client ID (type "Web application") the app requests ID tokens for. |
| `RIVY_LISTEN_ADDR` | Optional, defaults to `:8080`. |
| `RIVY_INGEST_TOKEN` | Optional. The secret the email forwarder sends, at least 24 characters. Leave empty to turn the email inbox off. |

## Run with Docker

The full walkthrough, including Google sign-in, the email forwarder and the
app, is in [`SETUP.md`](SETUP.md).

```sh
cp .env.example .env   # then fill it in
docker compose up -d --build
curl http://127.0.0.1:8080/healthz
```

The API only listens on the VPS itself. Put a reverse proxy with HTTPS in
front of it before pointing the app at it.

## Tests

The API tests need a PostgreSQL database they are allowed to wipe:

```sh
RIVY_TEST_DATABASE_URL="postgres://user:pass@localhost:5432/rivy_test?sslmode=disable" go test ./...
```

Without that variable only the Google token and bank email parser tests run.

## Known limits

- The bank email parsers are tested against sample emails written by hand
  from real ones. A layout they do not know shows up as `unparsed`.
