package main

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"
)

const (
	maxEmailBodyBytes = 2 << 20
	// A transfer between two of the owner's banks produces two emails a few
	// seconds apart. Emails this close with the same amount are one transfer.
	pairWindow = 10 * time.Minute
	// How much of an unreadable email is kept so the owner can see what it was.
	unparsedNoteLength = 280
)

// InboxItem is the `data` of an inbox_item record: a transaction read from a
// bank email that waits for the owner's approval on a device.
type InboxItem struct {
	EmailTransaction
	// Status is "pending" until a device approves or dismisses the item.
	Status string `json:"status"`
	// Parsed is false when the email came from a bank but could not be read.
	Parsed  bool   `json:"parsed"`
	Subject string `json:"subject"`
	// PairID points at the other email of the same transfer, if there is one.
	PairID string `json:"pair_id,omitempty"`
	// Note holds the start of an unreadable email.
	Note       string    `json:"note,omitempty"`
	ReceivedAt time.Time `json:"received_at"`
}

type ingestRequest struct {
	MessageID string `json:"message_id"`
	From      string `json:"from"`
	Subject   string `json:"subject"`
	Date      string `json:"date"`
	HTML      string `json:"html"`
	Text      string `json:"text"`
}

// handleIngestEmail receives one forwarded bank email and turns it into a
// pending inbox item.
func (a *API) handleIngestEmail(w http.ResponseWriter, r *http.Request) {
	if a.ingestToken == "" {
		writeError(w, http.StatusNotFound, "the email inbox is not enabled on this server")
		return
	}
	token, _ := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
	if subtle.ConstantTimeCompare([]byte(token), []byte(a.ingestToken)) != 1 {
		writeError(w, http.StatusUnauthorized, "ingest token is not valid")
		return
	}

	var req ingestRequest
	if err := readJSON(w, r, maxEmailBodyBytes, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if strings.TrimSpace(req.MessageID) == "" {
		writeError(w, http.StatusBadRequest, "message_id is required")
		return
	}

	now := a.now().UTC().Truncate(time.Microsecond)
	mail := BankEmail{
		From:    req.From,
		Subject: req.Subject,
		Date:    now,
		Text:    flattenBody(req.HTML, req.Text),
	}
	if sent, err := time.Parse(time.RFC3339, req.Date); err == nil && !sent.After(now) {
		mail.Date = sent.UTC()
	}

	item := InboxItem{Status: "pending", Subject: strings.TrimSpace(req.Subject), ReceivedAt: now}
	tx, err := ParseBankEmail(mail)
	if err != nil && !errors.Is(err, ErrUnknownSender) && req.HTML != "" && strings.TrimSpace(req.Text) != "" {
		// The HTML could not be read. The plain text copy of the same email
		// sometimes can.
		plain := mail
		plain.Text = flattenBody("", req.Text)
		if plainTx, plainErr := ParseBankEmail(plain); plainErr == nil {
			tx, err = plainTx, nil
		}
	}
	switch {
	case errors.Is(err, ErrUnknownSender), errors.Is(err, ErrNotTransaction):
		writeJSON(w, http.StatusOK, map[string]string{"result": "ignored"})
		return
	case err != nil:
		// From a bank, but unreadable. Keep it so nothing is lost silently.
		a.log.Warn("bank email could not be read", "error", err)
		item.EmailTransaction = EmailTransaction{
			Bank:       bankOfSender(req.From),
			Kind:       "unknown",
			Currency:   "IDR",
			OccurredAt: mail.Date,
		}
		item.Note = truncate(mail.Text, unparsedNoteLength)
	default:
		item.EmailTransaction = tx
		item.Parsed = true
	}

	id := inboxItemID(req.MessageID)
	stored, err := a.store.InsertInboxItem(r.Context(), strings.ToLower(a.allowedEmail), id, item, now)
	if err != nil {
		a.internalError(w, r, err)
		return
	}
	result := "stored"
	switch {
	case !stored:
		result = "duplicate"
	case !item.Parsed:
		result = "unparsed"
	}
	writeJSON(w, http.StatusOK, map[string]string{"result": result, "id": id})
}

// inboxItemID derives a stable record id from the email's Message-ID, so the
// same email forwarded twice lands on the same record.
func inboxItemID(messageID string) string {
	sum := sha256.Sum256([]byte(strings.TrimSpace(messageID)))
	return "eml-" + hex.EncodeToString(sum[:16])
}

func truncate(s string, max int) string {
	runes := []rune(s)
	if len(runes) <= max {
		return s
	}
	return string(runes[:max])
}

// pairKinds lists which kinds can be the other half of a transfer.
var pairKinds = map[string][]string{
	KindIncoming: {KindTransfer, KindTopUp},
	KindTransfer: {KindIncoming},
	KindTopUp:    {KindIncoming},
}

// InsertInboxItem stores a new inbox item. It never replaces an existing one,
// because a device may already have approved it. It reports whether the item
// was new.
func (s *Store) InsertInboxItem(ctx context.Context, owner, id string, item InboxItem, now time.Time) (bool, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, fmt.Errorf("begin inbox insert: %w", err)
	}
	defer tx.Rollback()

	// Same lock as Push, so change numbers commit in order.
	if _, err := tx.ExecContext(ctx, `SELECT pg_advisory_xact_lock(hashtext($1))`, owner); err != nil {
		return false, fmt.Errorf("lock owner: %w", err)
	}

	var exists bool
	err = tx.QueryRowContext(ctx,
		`SELECT EXISTS (SELECT 1 FROM records WHERE owner = $1 AND type = 'inbox_item' AND id = $2)`,
		owner, id,
	).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("check inbox item: %w", err)
	}
	if exists {
		return false, nil
	}

	if kinds := pairKinds[item.Kind]; item.Parsed && len(kinds) > 0 {
		var pairID string
		err := tx.QueryRowContext(ctx,
			`SELECT id FROM records
			 WHERE owner = $1 AND type = 'inbox_item' AND NOT deleted
			   AND data->>'status' = 'pending'
			   AND data->>'kind' = ANY(string_to_array($2, ','))
			   AND data->>'bank' <> $3
			   AND (data->>'amount')::numeric = $4::numeric
			   AND (data->>'occurred_at')::timestamptz BETWEEN $5 AND $6
			   AND COALESCE(data->>'pair_id', '') = ''
			   AND NOT EXISTS (
			       SELECT 1 FROM records other
			       WHERE other.owner = records.owner AND other.type = 'inbox_item'
			         AND other.data->>'pair_id' = records.id)
			 ORDER BY seq DESC
			 LIMIT 1`,
			owner, strings.Join(kinds, ","), item.Bank, fmt.Sprintf("%.2f", item.Amount),
			item.OccurredAt.Add(-pairWindow), item.OccurredAt.Add(pairWindow),
		).Scan(&pairID)
		if err != nil && !errors.Is(err, sql.ErrNoRows) {
			return false, fmt.Errorf("find paired email: %w", err)
		}
		item.PairID = pairID
	}

	data, err := json.Marshal(item)
	if err != nil {
		return false, fmt.Errorf("encode inbox item: %w", err)
	}
	_, err = tx.ExecContext(ctx,
		`INSERT INTO records (owner, type, id, data, updated_at, deleted, seq)
		 VALUES ($1, 'inbox_item', $2, $3::jsonb, $4, false, nextval('records_seq'))`,
		owner, id, string(data), now,
	)
	if err != nil {
		return false, fmt.Errorf("store inbox item: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return false, fmt.Errorf("commit inbox insert: %w", err)
	}
	return true, nil
}
