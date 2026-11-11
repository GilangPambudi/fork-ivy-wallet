package main

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"
	"time"
)

const testIngestToken = "test-ingest-token-0123456789"

type ingestResponse struct {
	Result string `json:"result"`
	ID     string `json:"id"`
}

// newInboxServer is a test server with the email inbox switched on and the
// clock fixed shortly after the fixture emails.
func newInboxServer(t *testing.T) *testServer {
	s := newTestServer(t)
	s.api.ingestToken = testIngestToken
	s.api.now = func() time.Time { return time.Date(2026, 11, 11, 9, 0, 0, 0, time.UTC) }
	return s
}

func (s *testServer) ingest(req ingestRequest) ingestResponse {
	s.t.Helper()
	var resp ingestResponse
	if status := s.do("POST", "/v1/inbox/email", testIngestToken, req, &resp); status != http.StatusOK {
		s.t.Fatalf("ingest: status %d", status)
	}
	return resp
}

func inboxItemOf(t *testing.T, rec Record) InboxItem {
	t.Helper()
	var item InboxItem
	if err := json.Unmarshal(rec.Data, &item); err != nil {
		t.Fatalf("decode inbox item %q: %v", rec.Data, err)
	}
	return item
}

func TestInboxIsOffWithoutAnIngestToken(t *testing.T) {
	s := newTestServer(t)
	if status := s.do("POST", "/v1/inbox/email", testIngestToken, ingestRequest{MessageID: "<a@x>"}, nil); status != http.StatusNotFound {
		t.Fatalf("status = %d, want 404", status)
	}
}

func TestInboxNeedsTheIngestToken(t *testing.T) {
	s := newInboxServer(t)
	session := s.signIn("phone")
	req := ingestRequest{MessageID: "<a@x>", From: "bca@bca.co.id", Text: bcaQRISText}

	for name, token := range map[string]string{
		"no token":      "",
		"wrong token":   "not-the-ingest-token-0123456",
		"session token": session,
	} {
		if status := s.do("POST", "/v1/inbox/email", token, req, nil); status != http.StatusUnauthorized {
			t.Errorf("%s: status = %d, want 401", name, status)
		}
	}
	if got := s.pull(session, 0, 100); len(got.Records) != 0 {
		t.Fatalf("rejected emails left %d records", len(got.Records))
	}
}

func TestInboxRejectsRequestsWithoutMessageID(t *testing.T) {
	s := newInboxServer(t)
	if status := s.do("POST", "/v1/inbox/email", testIngestToken, ingestRequest{From: "bca@bca.co.id"}, nil); status != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", status)
	}
}

func TestBankEmailBecomesAPendingItemOnDevices(t *testing.T) {
	s := newInboxServer(t)
	session := s.signIn("phone")

	resp := s.ingest(ingestRequest{
		MessageID: "<qris-1@bca.co.id>", From: "BCA <bca@bca.co.id>",
		Subject: "Internet Transaction Journal", Date: "2026-11-09T11:23:50Z", Text: bcaQRISText,
	})
	if resp.Result != "stored" || !strings.HasPrefix(resp.ID, "eml-") {
		t.Fatalf("ingest = %+v, want stored with an eml- id", resp)
	}

	got := s.pull(session, 0, 100)
	if len(got.Records) != 1 {
		t.Fatalf("pulled %d records, want 1", len(got.Records))
	}
	rec := got.Records[0]
	if rec.Type != "inbox_item" || rec.ID != resp.ID || rec.Deleted {
		t.Fatalf("record = %s/%s deleted=%v", rec.Type, rec.ID, rec.Deleted)
	}
	item := inboxItemOf(t, rec)
	if item.Status != "pending" || !item.Parsed {
		t.Errorf("status = %q parsed = %v, want pending and parsed", item.Status, item.Parsed)
	}
	if item.Bank != "bca" || item.Kind != KindPayment || item.Amount != 18000 || item.Counterparty != "Toko Sumber Rejeki" {
		t.Errorf("item = %+v", item.EmailTransaction)
	}
	if want := jakarta(2026, 11, 9, 18, 23, 47); !item.OccurredAt.Equal(want) {
		t.Errorf("occurred_at = %v, want %v", item.OccurredAt, want)
	}
	if item.Subject != "Internet Transaction Journal" {
		t.Errorf("subject = %q", item.Subject)
	}
}

func TestSameEmailTwiceIsStoredOnce(t *testing.T) {
	s := newInboxServer(t)
	session := s.signIn("phone")
	req := ingestRequest{MessageID: "<qris-1@bca.co.id>", From: "bca@bca.co.id", Text: bcaQRISText}

	first := s.ingest(req)
	second := s.ingest(req)
	if first.Result != "stored" || second.Result != "duplicate" || first.ID != second.ID {
		t.Fatalf("first = %+v, second = %+v", first, second)
	}
	if got := s.pull(session, 0, 100); len(got.Records) != 1 {
		t.Fatalf("pulled %d records, want 1", len(got.Records))
	}
}

func TestForwardingAgainDoesNotUndoAnApproval(t *testing.T) {
	s := newInboxServer(t)
	session := s.signIn("phone")
	req := ingestRequest{MessageID: "<qris-1@bca.co.id>", From: "bca@bca.co.id", Text: bcaQRISText}
	id := s.ingest(req).ID

	// The device approves the item.
	item := inboxItemOf(t, s.pull(session, 0, 100).Records[0])
	item.Status = "approved"
	data, _ := json.Marshal(item)
	s.push(session, Record{Type: "inbox_item", ID: id, Data: data, UpdatedAt: s.api.now()})

	if resp := s.ingest(req); resp.Result != "duplicate" {
		t.Fatalf("second ingest = %+v, want duplicate", resp)
	}
	records := s.pull(session, 0, 100).Records
	if len(records) != 1 || inboxItemOf(t, records[0]).Status != "approved" {
		t.Fatalf("approval was lost: %d records, status %q", len(records), inboxItemOf(t, records[0]).Status)
	}
}

func TestEmailsThatAreNotTransactionsAreIgnored(t *testing.T) {
	s := newInboxServer(t)
	session := s.signIn("phone")

	for name, req := range map[string]ingestRequest{
		"other sender": {MessageID: "<1@x>", From: "promo@shop.example", Subject: "Diskon", Text: "Total Rp10.000"},
		"jago notice": {
			MessageID: "<2@x>", From: "noreply@jago.com", Subject: "Kontak baru ditambahkan ke daftar kontak",
			Text: "Kamu telah menambahkan SITI AMINAH ke daftar kontak.",
		},
	} {
		if resp := s.ingest(req); resp.Result != "ignored" {
			t.Errorf("%s: result = %q, want ignored", name, resp.Result)
		}
	}
	if got := s.pull(session, 0, 100); len(got.Records) != 0 {
		t.Fatalf("ignored emails left %d records", len(got.Records))
	}
}

func TestUnreadableBankEmailIsKeptForTheOwner(t *testing.T) {
	s := newInboxServer(t)
	session := s.signIn("phone")

	broken := strings.Replace(bcaQRISText, "IDR 18,000.00", "see attachment", 1)
	resp := s.ingest(ingestRequest{MessageID: "<broken@bca.co.id>", From: "bca@bca.co.id", Subject: "Internet Transaction Journal", Text: broken})
	if resp.Result != "unparsed" {
		t.Fatalf("result = %q, want unparsed", resp.Result)
	}
	records := s.pull(session, 0, 100).Records
	if len(records) != 1 {
		t.Fatalf("pulled %d records, want 1", len(records))
	}
	item := inboxItemOf(t, records[0])
	if item.Parsed || item.Status != "pending" || item.Bank != "bca" || item.Amount != 0 {
		t.Errorf("item = %+v", item)
	}
	if !strings.Contains(item.Note, "Toko Sumber Rejeki") {
		t.Errorf("note = %q, want the start of the email", item.Note)
	}
}

func TestTransferBetweenOwnBanksIsPaired(t *testing.T) {
	s := newInboxServer(t)
	session := s.signIn("phone")

	out := s.ingest(ingestRequest{
		MessageID: "<out@jago.com>", From: "noreply@jago.com",
		Subject: "Kamu telah melakukan transfer💸", Text: jagoTransferText,
	})
	in := s.ingest(ingestRequest{
		MessageID: "<in@blu>", From: "receipts@blubybcadigital.id",
		Subject: "Info Transaksi Masuk ke blu Kamu 💸", Text: bluIncomingText,
	})
	// A second email with the same amount must not join an existing pair.
	extra := s.ingest(ingestRequest{
		MessageID: "<in-again@blu>", From: "receipts@blubybcadigital.id",
		Subject: "Info Transaksi Masuk ke blu Kamu 💸", Text: bluIncomingText,
	})

	items := map[string]InboxItem{}
	for _, rec := range s.pull(session, 0, 100).Records {
		items[rec.ID] = inboxItemOf(t, rec)
	}
	if len(items) != 3 {
		t.Fatalf("pulled %d items, want 3", len(items))
	}
	if got := items[in.ID].PairID; got != out.ID {
		t.Errorf("incoming pair_id = %q, want %q", got, out.ID)
	}
	if got := items[out.ID].PairID; got != "" {
		t.Errorf("outgoing pair_id = %q, want empty (the later email points back)", got)
	}
	if got := items[extra.ID].PairID; got != "" {
		t.Errorf("third email pair_id = %q, want empty", got)
	}
}

func TestEmailsAreNotPairedWhenTheyDoNotMatch(t *testing.T) {
	cases := map[string]string{
		"different amount": strings.Replace(bluIncomingText, "Rp650.000,00", "Rp600.000,00", 1),
		"too far apart":    strings.Replace(bluIncomingText, "04 Nov 2026 20:26:41", "04 Nov 2026 21:26:41", 1),
	}
	for name, incoming := range cases {
		t.Run(name, func(t *testing.T) {
			s := newInboxServer(t)
			session := s.signIn("phone")
			s.ingest(ingestRequest{MessageID: "<out@jago.com>", From: "noreply@jago.com", Subject: "Kamu telah melakukan transfer💸", Text: jagoTransferText})
			in := s.ingest(ingestRequest{MessageID: "<in@blu>", From: "receipts@blubybcadigital.id", Subject: "Info Transaksi Masuk ke blu Kamu 💸", Text: incoming})

			for _, rec := range s.pull(session, 0, 100).Records {
				if rec.ID == in.ID && inboxItemOf(t, rec).PairID != "" {
					t.Fatalf("pair_id = %q, want empty", inboxItemOf(t, rec).PairID)
				}
			}
		})
	}
}

func TestPaymentsAreNeverPaired(t *testing.T) {
	// Two purchases of the same amount at the same minute are two purchases.
	s := newInboxServer(t)
	session := s.signIn("phone")
	s.ingest(ingestRequest{MessageID: "<p1@jago.com>", From: "noreply@jago.com", Subject: "Kamu telah membayar ke WARUNG BU TINI, LWK💸", Text: jagoPaymentText})
	second := strings.NewReplacer("Rp12.000,00", "Rp14.000,00", "26 Okt 2026 12:11:08", "05 Nov 2026 12:10:30").Replace(bluQRISText)
	s.ingest(ingestRequest{MessageID: "<p2@blu>", From: "receipts@blubybcadigital.id", Subject: "Transaksimu Pakai blu Berhasil", Text: second})

	records := s.pull(session, 0, 100).Records
	if len(records) != 2 {
		t.Fatalf("pulled %d records, want 2", len(records))
	}
	for _, rec := range records {
		if item := inboxItemOf(t, rec); item.PairID != "" || item.Amount != 14000 {
			t.Errorf("item %s: pair_id = %q amount = %v", rec.ID, item.PairID, item.Amount)
		}
	}
}

func TestConfigTellsTheAppHowToSignIn(t *testing.T) {
	s := newTestServer(t)
	s.api.googleClientID = "1234-abc.apps.googleusercontent.com"

	var cfg struct {
		GoogleClientID string `json:"google_client_id"`
		InboxEnabled   bool   `json:"inbox_enabled"`
	}
	if status := s.do("GET", "/v1/config", "", nil, &cfg); status != http.StatusOK {
		t.Fatalf("status = %d, want 200", status)
	}
	if cfg.GoogleClientID != "1234-abc.apps.googleusercontent.com" || cfg.InboxEnabled {
		t.Errorf("config = %+v", cfg)
	}

	s.api.ingestToken = testIngestToken
	s.do("GET", "/v1/config", "", nil, &cfg)
	if !cfg.InboxEnabled {
		t.Error("inbox_enabled = false after setting an ingest token")
	}
}
