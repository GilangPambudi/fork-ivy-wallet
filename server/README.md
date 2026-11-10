# Rivy sync server

A small server that keeps Rivy in sync across devices. Devices sign in with
Google, push the items they changed and pull the changes made elsewhere.

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
| `POST /v1/auth/google` | Body `{"id_token": "...", "device_name": "..."}`. Returns `{"token": "...", "email": "..."}`. 401 if Google did not issue the token, 403 if it is another account. |
| `POST /v1/auth/logout` | Ends the session of the calling device. |
| `POST /v1/sync/push` | Body `{"records": [...]}`, at most 1000 per call. Returns `{"applied": n, "unchanged": n, "stale": [...]}`. |
| `GET /v1/sync/pull?since=0&limit=500` | Returns `{"records": [...], "next_since": n, "has_more": bool}`. Call again with `since=next_since` while `has_more` is true. |

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
`budget`, `loan`, `loan_record`, `tag`, `tag_association`, `settings`.

An `updated_at` in the future is treated as "now", so a phone with a wrong
clock cannot win every conflict.

## Configuration

| Variable | Meaning |
| --- | --- |
| `RIVY_DATABASE_URL` | PostgreSQL connection string. |
| `RIVY_ALLOWED_EMAIL` | The only Google account that may sign in. |
| `RIVY_GOOGLE_CLIENT_ID` | OAuth client ID (type "Web application") the app requests ID tokens for. |
| `RIVY_LISTEN_ADDR` | Optional, defaults to `:8080`. |

## Run with Docker

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

Without that variable only the Google token tests run.

## Not here yet

- The Android side: sign-in, tracking what changed, pushing and pulling.
- The inbox for transactions read from bank emails.
