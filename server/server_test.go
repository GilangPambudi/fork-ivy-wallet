package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

const allowedEmail = "owner@example.com"

// fakeVerifier maps ID tokens to emails without talking to Google.
type fakeVerifier map[string]string

func (f fakeVerifier) Verify(_ context.Context, idToken string) (string, error) {
	email, ok := f[idToken]
	if !ok {
		return "", ErrInvalidToken
	}
	return email, nil
}

type testServer struct {
	t   *testing.T
	api *API
	url string
}

// newTestServer starts the API against a real PostgreSQL database. Set
// RIVY_TEST_DATABASE_URL to run these tests; they wipe that database.
func newTestServer(t *testing.T) *testServer {
	t.Helper()
	dsn := os.Getenv("RIVY_TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("RIVY_TEST_DATABASE_URL is not set")
	}
	store, err := OpenStore(context.Background(), dsn)
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	t.Cleanup(func() { store.Close() })
	if _, err := store.db.Exec(`TRUNCATE records, sessions; ALTER SEQUENCE records_seq RESTART`); err != nil {
		t.Fatalf("reset database: %v", err)
	}

	verifier := fakeVerifier{
		"owner-token":    allowedEmail,
		"stranger-token": "stranger@example.com",
	}
	api := NewAPI(store, verifier, allowedEmail, slog.New(slog.NewTextHandler(io.Discard, nil)))
	server := httptest.NewServer(api.Handler())
	t.Cleanup(server.Close)
	return &testServer{t: t, api: api, url: server.URL}
}

// do sends a request and decodes the JSON response into out (if not nil).
func (s *testServer) do(method, path, token string, body, out any) int {
	s.t.Helper()
	var reader io.Reader
	if body != nil {
		switch b := body.(type) {
		case string:
			reader = strings.NewReader(b)
		default:
			raw, err := json.Marshal(b)
			if err != nil {
				s.t.Fatalf("marshal request: %v", err)
			}
			reader = bytes.NewReader(raw)
		}
	}
	req, err := http.NewRequest(method, s.url+path, reader)
	if err != nil {
		s.t.Fatalf("new request: %v", err)
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		s.t.Fatalf("%s %s: %v", method, path, err)
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if out != nil && len(raw) > 0 {
		if err := json.Unmarshal(raw, out); err != nil {
			s.t.Fatalf("decode %s %s response %q: %v", method, path, raw, err)
		}
	}
	return resp.StatusCode
}

// signIn returns a session token for the allowed account.
func (s *testServer) signIn(device string) string {
	s.t.Helper()
	var resp struct {
		Token string `json:"token"`
		Email string `json:"email"`
	}
	status := s.do("POST", "/v1/auth/google", "", map[string]string{"id_token": "owner-token", "device_name": device}, &resp)
	if status != http.StatusOK || resp.Token == "" {
		s.t.Fatalf("sign in: status %d, token %q", status, resp.Token)
	}
	if resp.Email != allowedEmail {
		s.t.Fatalf("sign in email = %q, want %q", resp.Email, allowedEmail)
	}
	return resp.Token
}

func (s *testServer) push(token string, records ...Record) pushResponse {
	s.t.Helper()
	var resp pushResponse
	if status := s.do("POST", "/v1/sync/push", token, map[string]any{"records": records}, &resp); status != http.StatusOK {
		s.t.Fatalf("push: status %d", status)
	}
	return resp
}

func (s *testServer) pull(token string, since int64, limit int) pullResponse {
	s.t.Helper()
	var resp pullResponse
	path := fmt.Sprintf("/v1/sync/pull?since=%d&limit=%d", since, limit)
	if status := s.do("GET", path, token, nil, &resp); status != http.StatusOK {
		s.t.Fatalf("pull: status %d", status)
	}
	return resp
}

func record(recordType, id, title string, updatedAt time.Time) Record {
	return Record{
		Type:      recordType,
		ID:        id,
		Data:      json.RawMessage(fmt.Sprintf(`{"title":%q}`, title)),
		UpdatedAt: updatedAt,
	}
}

func titleOf(t *testing.T, rec Record) string {
	t.Helper()
	var data struct {
		Title string `json:"title"`
	}
	if err := json.Unmarshal(rec.Data, &data); err != nil {
		t.Fatalf("decode data %q: %v", rec.Data, err)
	}
	return data.Title
}

var baseTime = time.Date(2026, 11, 10, 12, 0, 0, 0, time.UTC)

func TestHealth(t *testing.T) {
	s := newTestServer(t)
	if status := s.do("GET", "/healthz", "", nil, nil); status != http.StatusOK {
		t.Fatalf("healthz status = %d, want 200", status)
	}
}

func TestSignInOnlyForAllowedAccount(t *testing.T) {
	s := newTestServer(t)

	cases := []struct {
		name    string
		idToken string
		want    int
	}{
		{"token google did not issue", "made-up", http.StatusUnauthorized},
		{"valid token of another account", "stranger-token", http.StatusForbidden},
		{"missing token", "", http.StatusBadRequest},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			status := s.do("POST", "/v1/auth/google", "", map[string]string{"id_token": c.idToken}, nil)
			if status != c.want {
				t.Fatalf("status = %d, want %d", status, c.want)
			}
		})
	}
}

func TestSyncNeedsASession(t *testing.T) {
	s := newTestServer(t)

	if status := s.do("GET", "/v1/sync/pull", "", nil, nil); status != http.StatusUnauthorized {
		t.Fatalf("pull without token: status = %d, want 401", status)
	}
	if status := s.do("GET", "/v1/sync/pull", "not-a-session", nil, nil); status != http.StatusUnauthorized {
		t.Fatalf("pull with unknown token: status = %d, want 401", status)
	}
	if status := s.do("POST", "/v1/sync/push", "", map[string]any{"records": []Record{}}, nil); status != http.StatusUnauthorized {
		t.Fatalf("push without token: status = %d, want 401", status)
	}
}

func TestLogoutEndsTheSession(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")

	if status := s.do("POST", "/v1/auth/logout", token, nil, nil); status != http.StatusNoContent {
		t.Fatalf("logout status = %d, want 204", status)
	}
	if status := s.do("GET", "/v1/sync/pull", token, nil, nil); status != http.StatusUnauthorized {
		t.Fatalf("pull after logout: status = %d, want 401", status)
	}
}

func TestSessionStopsWorkingWhenAccountIsNoLongerAllowed(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")

	s.api.allowedEmail = "new-owner@example.com"
	if status := s.do("GET", "/v1/sync/pull", token, nil, nil); status != http.StatusForbidden {
		t.Fatalf("status = %d, want 403", status)
	}
}

func TestPushThenPullOnAnotherDevice(t *testing.T) {
	s := newTestServer(t)
	phoneA := s.signIn("phone A")
	phoneB := s.signIn("phone B")

	pushed := s.push(phoneA,
		record("account", "acc-1", "Bank", baseTime),
		record("transaction", "trn-1", "Lunch", baseTime),
	)
	if pushed.Applied != 2 || pushed.Unchanged != 0 || len(pushed.Stale) != 0 {
		t.Fatalf("push = %+v, want 2 applied", pushed)
	}

	pulled := s.pull(phoneB, 0, 100)
	if len(pulled.Records) != 2 || pulled.HasMore {
		t.Fatalf("pull returned %d records (has_more %v), want 2", len(pulled.Records), pulled.HasMore)
	}
	if got := titleOf(t, pulled.Records[0]); got != "Bank" {
		t.Fatalf("first record title = %q, want Bank", got)
	}
	if got := titleOf(t, pulled.Records[1]); got != "Lunch" {
		t.Fatalf("second record title = %q, want Lunch", got)
	}
	if !pulled.Records[1].UpdatedAt.Equal(baseTime) {
		t.Fatalf("updated_at = %v, want %v", pulled.Records[1].UpdatedAt, baseTime)
	}
	if pulled.NextSince != pulled.Records[1].Seq {
		t.Fatalf("next_since = %d, want seq of last record %d", pulled.NextSince, pulled.Records[1].Seq)
	}

	// Phone B edits, phone A pulls from where it left off and sees only that.
	s.push(phoneB, record("transaction", "trn-1", "Lunch at the corner stall", baseTime.Add(time.Minute)))
	later := s.pull(phoneA, pulled.NextSince, 100)
	if len(later.Records) != 1 || titleOf(t, later.Records[0]) != "Lunch at the corner stall" {
		t.Fatalf("pull after edit = %+v, want only the edited transaction", later.Records)
	}

	// Nothing new: the cursor stays where it is.
	idle := s.pull(phoneA, later.NextSince, 100)
	if len(idle.Records) != 0 || idle.NextSince != later.NextSince {
		t.Fatalf("idle pull = %+v, want no records and unchanged cursor", idle)
	}
}

func TestLastEditWins(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")

	s.push(token, record("transaction", "trn-1", "edited at noon", baseTime))

	// An older edit arrives late (say, from a phone that was offline).
	older := s.push(token, record("transaction", "trn-1", "edited in the morning", baseTime.Add(-time.Hour)))
	if older.Applied != 0 || len(older.Stale) != 1 {
		t.Fatalf("older push = %+v, want 0 applied and 1 stale", older)
	}
	if got := titleOf(t, older.Stale[0]); got != "edited at noon" {
		t.Fatalf("stale carries %q, want the server's newer version", got)
	}

	// A newer edit replaces it.
	newer := s.push(token, record("transaction", "trn-1", "edited in the evening", baseTime.Add(time.Hour)))
	if newer.Applied != 1 || len(newer.Stale) != 0 {
		t.Fatalf("newer push = %+v, want 1 applied", newer)
	}

	all := s.pull(token, 0, 100)
	if len(all.Records) != 1 || titleOf(t, all.Records[0]) != "edited in the evening" {
		t.Fatalf("stored = %+v, want only the evening edit", all.Records)
	}
}

func TestPushingTheSameVersionAgainChangesNothing(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")
	rec := record("category", "cat-1", "Food", baseTime)

	s.push(token, rec)
	before := s.pull(token, 0, 100)

	again := s.push(token, rec)
	if again.Applied != 0 || again.Unchanged != 1 || len(again.Stale) != 0 {
		t.Fatalf("second push = %+v, want 1 unchanged", again)
	}
	after := s.pull(token, before.NextSince, 100)
	if len(after.Records) != 0 {
		t.Fatalf("re-pushing produced %d new changes, want none", len(after.Records))
	}
}

func TestDeletionReachesOtherDevices(t *testing.T) {
	s := newTestServer(t)
	phoneA := s.signIn("phone A")
	phoneB := s.signIn("phone B")

	s.push(phoneA, record("transaction", "trn-1", "Laundry", baseTime))
	cursor := s.pull(phoneB, 0, 100).NextSince

	deleted := record("transaction", "trn-1", "Laundry", baseTime.Add(time.Minute))
	deleted.Deleted = true
	s.push(phoneA, deleted)

	pulled := s.pull(phoneB, cursor, 100)
	if len(pulled.Records) != 1 || !pulled.Records[0].Deleted {
		t.Fatalf("pull = %+v, want the deleted transaction", pulled.Records)
	}
}

func TestPullInPages(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")

	var records []Record
	for i := 0; i < 5; i++ {
		records = append(records, record("transaction", fmt.Sprintf("trn-%d", i), fmt.Sprintf("item %d", i), baseTime))
	}
	s.push(token, records...)

	var titles []string
	since := int64(0)
	for page := 0; page < 10; page++ {
		resp := s.pull(token, since, 2)
		for _, rec := range resp.Records {
			titles = append(titles, titleOf(t, rec))
		}
		since = resp.NextSince
		if !resp.HasMore {
			break
		}
	}
	want := "item 0,item 1,item 2,item 3,item 4"
	if got := strings.Join(titles, ","); got != want {
		t.Fatalf("paged pull = %q, want %q", got, want)
	}
}

func TestFutureTimestampCountsAsNow(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")
	s.api.now = func() time.Time { return baseTime }

	// A phone with its clock a year ahead edits a record...
	s.push(token, record("transaction", "trn-1", "from the future", baseTime.AddDate(1, 0, 0)))
	// ...and a normal edit a minute later must still be able to win.
	s.api.now = func() time.Time { return baseTime.Add(time.Minute) }
	resp := s.push(token, record("transaction", "trn-1", "a minute later", baseTime.Add(time.Minute)))
	if resp.Applied != 1 {
		t.Fatalf("push = %+v, want the later edit to be applied", resp)
	}
}

func TestPushRejectsInvalidRecords(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")

	cases := map[string]string{
		"unknown type":    `{"records":[{"type":"secret","id":"1","updated_at":"2026-11-10T12:00:00Z","data":{}}]}`,
		"missing id":      `{"records":[{"type":"account","id":"","updated_at":"2026-11-10T12:00:00Z","data":{}}]}`,
		"missing time":    `{"records":[{"type":"account","id":"1","data":{}}]}`,
		"data not object": `{"records":[{"type":"account","id":"1","updated_at":"2026-11-10T12:00:00Z","data":[1,2]}]}`,
		"missing data":    `{"records":[{"type":"account","id":"1","updated_at":"2026-11-10T12:00:00Z"}]}`,
		"not json":        `this is not json`,
	}
	for name, body := range cases {
		t.Run(name, func(t *testing.T) {
			var resp map[string]string
			status := s.do("POST", "/v1/sync/push", token, body, &resp)
			if status != http.StatusBadRequest {
				t.Fatalf("status = %d, want 400", status)
			}
			if resp["error"] == "" {
				t.Fatalf("response has no error message")
			}
		})
	}

	// A rejected batch must not have stored anything.
	if got := s.pull(token, 0, 100); len(got.Records) != 0 {
		t.Fatalf("rejected pushes stored %d records, want none", len(got.Records))
	}
}

func TestPullRejectsBadParameters(t *testing.T) {
	s := newTestServer(t)
	token := s.signIn("phone")

	for _, path := range []string{
		"/v1/sync/pull?since=-1",
		"/v1/sync/pull?since=abc",
		"/v1/sync/pull?limit=0",
		"/v1/sync/pull?limit=100000",
	} {
		if status := s.do("GET", path, token, nil, nil); status != http.StatusBadRequest {
			t.Fatalf("%s: status = %d, want 400", path, status)
		}
	}
}
