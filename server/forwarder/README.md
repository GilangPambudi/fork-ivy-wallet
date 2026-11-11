# Rivy email forwarder

A Google Apps Script that sends new bank emails from Gmail to the Rivy server
every 5 minutes. It runs in Google's cloud, so no computer or phone has to
stay on.

It only touches emails from these addresses: `bca@bca.co.id`,
`noreply@jago.com`, `receipts@blubybcadigital.id`. It does not change, label
or delete anything in Gmail.

## Setup

Use the Google account whose Gmail receives the bank emails.

1. Open <https://script.google.com> and create a new project. Name it
   `Rivy forwarder`.
2. Replace the content of `Code.gs` with the `Code.gs` in this folder and save.
3. Open **Project Settings** (the gear), scroll to **Script Properties** and
   add two properties:
   - `RIVY_URL`: the https address of the server, for example
     `https://rivy.example.com`
   - `RIVY_INGEST_TOKEN`: the same value as `RIVY_INGEST_TOKEN` in the
     server's `.env`
4. Back in the editor, choose the function `install` in the toolbar and press
   **Run**. Google asks for permission to read Gmail and to contact an
   external server; allow it.

`install` forwards the bank emails of the last 24 hours once and then starts
the 5 minute schedule. Older emails are left alone.

## Checking and stopping

- **Executions** (left menu) lists every run with a line like
  `Rivy forwarder: {"stored":2,"duplicate":1}, failed: 0`.
- `duplicate` is normal: every run looks one hour back again in case Gmail
  filed an email late, and the server skips what it already has.
- When the server cannot be reached, nothing is lost. The same emails are
  tried again on the next run.
- To stop, run the function `uninstall`.
