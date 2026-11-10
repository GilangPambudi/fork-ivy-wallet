package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const (
	maxPushBodyBytes  = 16 << 20
	maxPushRecords    = 1000
	maxRecordBytes    = 256 << 10
	maxIDLength       = 100
	defaultPullLimit  = 500
	maxPullLimit      = 1000
	maxAuthBodyBytes  = 64 << 10
	maxDeviceNameSize = 100
)

// recordTypes lists what devices may sync. Anything else is rejected so a
// buggy client cannot fill the database with junk.
var recordTypes = map[string]bool{
	"account":              true,
	"category":             true,
	"transaction":          true,
	"planned_payment_rule": true,
	"budget":               true,
	"loan":                 true,
	"loan_record":          true,
	"tag":                  true,
	"tag_association":      true,
	"settings":             true,
}

// API is the HTTP layer of the sync server.
type API struct {
	store        *Store
	verifier     TokenVerifier
	allowedEmail string
	now          func() time.Time
	log          *slog.Logger
}

func NewAPI(store *Store, verifier TokenVerifier, allowedEmail string, log *slog.Logger) *API {
	return &API{
		store:        store,
		verifier:     verifier,
		allowedEmail: allowedEmail,
		now:          time.Now,
		log:          log,
	}
}

func (a *API) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", a.handleHealth)
	mux.HandleFunc("POST /v1/auth/google", a.handleAuthGoogle)
	mux.Handle("POST /v1/auth/logout", a.requireSession(a.handleLogout))
	mux.Handle("POST /v1/sync/push", a.requireSession(a.handlePush))
	mux.Handle("GET /v1/sync/pull", a.requireSession(a.handlePull))
	return mux
}

type session struct {
	email     string
	tokenHash []byte
}

// requireSession lets a request through only with a valid session token.
func (a *API) requireSession(next func(http.ResponseWriter, *http.Request, session)) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if !ok || token == "" {
			writeError(w, http.StatusUnauthorized, "missing session token")
			return
		}
		hash := hashSessionToken(token)
		email, err := a.store.SessionEmail(r.Context(), hash)
		if errors.Is(err, ErrNoSession) {
			writeError(w, http.StatusUnauthorized, "session is not valid, sign in again")
			return
		}
		if err != nil {
			a.internalError(w, r, err)
			return
		}
		// The allowed account can change in the configuration; old sessions of
		// another account must stop working when it does.
		if !strings.EqualFold(email, a.allowedEmail) {
			writeError(w, http.StatusForbidden, "this account may not use this server")
			return
		}
		next(w, r, session{email: email, tokenHash: hash})
	})
}

func (a *API) handleHealth(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()
	if err := a.store.Ping(ctx); err != nil {
		a.log.Error("health check failed", "error", err)
		writeError(w, http.StatusServiceUnavailable, "database is not reachable")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (a *API) handleAuthGoogle(w http.ResponseWriter, r *http.Request) {
	var req struct {
		IDToken    string `json:"id_token"`
		DeviceName string `json:"device_name"`
	}
	if err := readJSON(w, r, maxAuthBodyBytes, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if req.IDToken == "" {
		writeError(w, http.StatusBadRequest, "id_token is required")
		return
	}
	if len(req.DeviceName) > maxDeviceNameSize {
		req.DeviceName = req.DeviceName[:maxDeviceNameSize]
	}
	// Cutting may split a character, and the database only accepts valid text.
	req.DeviceName = strings.ToValidUTF8(req.DeviceName, "")

	email, err := a.verifier.Verify(r.Context(), req.IDToken)
	if errors.Is(err, ErrInvalidToken) {
		writeError(w, http.StatusUnauthorized, "google sign-in could not be verified")
		return
	}
	if err != nil {
		a.internalError(w, r, err)
		return
	}
	if !strings.EqualFold(email, a.allowedEmail) {
		a.log.Warn("sign-in rejected for an account that is not allowed")
		writeError(w, http.StatusForbidden, "this account may not use this server")
		return
	}

	token, hash, err := newSessionToken()
	if err != nil {
		a.internalError(w, r, err)
		return
	}
	if err := a.store.CreateSession(r.Context(), hash, strings.ToLower(email), req.DeviceName); err != nil {
		a.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"token": token, "email": strings.ToLower(email)})
}

func (a *API) handleLogout(w http.ResponseWriter, r *http.Request, s session) {
	if err := a.store.DeleteSession(r.Context(), s.tokenHash); err != nil {
		a.internalError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

type pushResponse struct {
	Applied   int      `json:"applied"`
	Unchanged int      `json:"unchanged"`
	Stale     []Record `json:"stale"`
}

func (a *API) handlePush(w http.ResponseWriter, r *http.Request, s session) {
	var req struct {
		Records []Record `json:"records"`
	}
	if err := readJSON(w, r, maxPushBodyBytes, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if len(req.Records) > maxPushRecords {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("too many records in one push, the limit is %d", maxPushRecords))
		return
	}

	now := a.now()
	for i := range req.Records {
		if err := prepareRecord(&req.Records[i], now); err != nil {
			writeError(w, http.StatusBadRequest, fmt.Sprintf("record %d: %s", i, err))
			return
		}
	}

	result, err := a.store.Push(r.Context(), s.email, req.Records)
	if err != nil {
		a.internalError(w, r, err)
		return
	}
	stale := result.Stale
	if stale == nil {
		stale = []Record{}
	}
	writeJSON(w, http.StatusOK, pushResponse{Applied: result.Applied, Unchanged: result.Unchanged, Stale: stale})
}

type pullResponse struct {
	Records   []Record `json:"records"`
	NextSince int64    `json:"next_since"`
	HasMore   bool     `json:"has_more"`
}

func (a *API) handlePull(w http.ResponseWriter, r *http.Request, s session) {
	query := r.URL.Query()

	since := int64(0)
	if raw := query.Get("since"); raw != "" {
		parsed, err := strconv.ParseInt(raw, 10, 64)
		if err != nil || parsed < 0 {
			writeError(w, http.StatusBadRequest, "since must be a non-negative number")
			return
		}
		since = parsed
	}

	limit := defaultPullLimit
	if raw := query.Get("limit"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil || parsed < 1 || parsed > maxPullLimit {
			writeError(w, http.StatusBadRequest, fmt.Sprintf("limit must be between 1 and %d", maxPullLimit))
			return
		}
		limit = parsed
	}

	records, hasMore, err := a.store.Pull(r.Context(), s.email, since, limit)
	if err != nil {
		a.internalError(w, r, err)
		return
	}
	next := since
	if len(records) > 0 {
		next = records[len(records)-1].Seq
	}
	writeJSON(w, http.StatusOK, pullResponse{Records: records, NextSince: next, HasMore: hasMore})
}

// prepareRecord validates one pushed record and normalizes its timestamp.
func prepareRecord(rec *Record, now time.Time) error {
	if !recordTypes[rec.Type] {
		return fmt.Errorf("unknown type %q", rec.Type)
	}
	if rec.ID == "" || len(rec.ID) > maxIDLength {
		return fmt.Errorf("id must be 1 to %d characters", maxIDLength)
	}
	if rec.UpdatedAt.IsZero() {
		return errors.New("updated_at is required")
	}
	data := bytes.TrimSpace(rec.Data)
	if len(data) == 0 || data[0] != '{' {
		return errors.New("data must be a JSON object")
	}
	if len(data) > maxRecordBytes {
		return fmt.Errorf("data is larger than %d bytes", maxRecordBytes)
	}
	rec.Data = data

	// PostgreSQL keeps microseconds; drop anything finer so comparisons in Go
	// and in SQL agree.
	rec.UpdatedAt = rec.UpdatedAt.UTC().Truncate(time.Microsecond)
	// A device with a clock set to the future must not win every conflict
	// from now on, so its edits count as made right now.
	if serverNow := now.UTC().Truncate(time.Microsecond); rec.UpdatedAt.After(serverNow) {
		rec.UpdatedAt = serverNow
	}
	rec.Seq = 0
	return nil
}

func readJSON(w http.ResponseWriter, r *http.Request, limit int64, into any) error {
	r.Body = http.MaxBytesReader(w, r.Body, limit)
	decoder := json.NewDecoder(r.Body)
	if err := decoder.Decode(into); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			return fmt.Errorf("request body is larger than %d bytes", limit)
		}
		return errors.New("request body is not valid JSON")
	}
	return nil
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func (a *API) internalError(w http.ResponseWriter, r *http.Request, err error) {
	a.log.Error("request failed", "method", r.Method, "path", r.URL.Path, "error", err)
	writeError(w, http.StatusInternalServerError, "something went wrong on the server")
}
