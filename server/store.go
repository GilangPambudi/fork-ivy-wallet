package main

import (
	"context"
	"database/sql"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	_ "github.com/lib/pq"
)

//go:embed schema.sql
var schemaSQL string

// ErrNoSession is returned when a session token is unknown.
var ErrNoSession = errors.New("session not found")

// Record is one synced item in the shape devices send and receive.
type Record struct {
	Type      string          `json:"type"`
	ID        string          `json:"id"`
	Data      json.RawMessage `json:"data"`
	UpdatedAt time.Time       `json:"updated_at"`
	Deleted   bool            `json:"deleted"`
	// Seq is set by the server. It is ignored on push.
	Seq int64 `json:"seq,omitempty"`
}

// PushResult says what happened to a batch of pushed records.
type PushResult struct {
	// Applied counts records the server stored.
	Applied int
	// Unchanged counts records the server already had in the same version.
	Unchanged int
	// Stale holds the server's version of every record that lost because the
	// server already had a newer edit. The device should adopt these.
	Stale []Record
}

// Store is the PostgreSQL-backed storage of the sync server.
type Store struct {
	db *sql.DB
}

// OpenStore connects to PostgreSQL and makes sure the schema exists.
func OpenStore(ctx context.Context, databaseURL string) (*Store, error) {
	db, err := sql.Open("postgres", databaseURL)
	if err != nil {
		return nil, fmt.Errorf("open database: %w", err)
	}
	db.SetMaxOpenConns(10)
	db.SetConnMaxIdleTime(5 * time.Minute)

	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, fmt.Errorf("connect to database: %w", err)
	}
	if _, err := db.ExecContext(ctx, schemaSQL); err != nil {
		db.Close()
		return nil, fmt.Errorf("apply schema: %w", err)
	}
	return &Store{db: db}, nil
}

func (s *Store) Close() error { return s.db.Close() }

func (s *Store) Ping(ctx context.Context) error { return s.db.PingContext(ctx) }

const upsertRecordSQL = `
INSERT INTO records (owner, type, id, data, updated_at, deleted, seq)
VALUES ($1, $2, $3, $4::jsonb, $5, $6, nextval('records_seq'))
ON CONFLICT (owner, type, id) DO UPDATE
SET data = EXCLUDED.data,
    updated_at = EXCLUDED.updated_at,
    deleted = EXCLUDED.deleted,
    seq = EXCLUDED.seq
WHERE records.updated_at < EXCLUDED.updated_at
   OR (records.updated_at = EXCLUDED.updated_at
       AND (records.data IS DISTINCT FROM EXCLUDED.data
            OR records.deleted IS DISTINCT FROM EXCLUDED.deleted))
RETURNING seq`

// Push stores a batch of records for one owner. The last edit wins: a record
// replaces the stored one only if its updated_at is the same or newer.
func (s *Store) Push(ctx context.Context, owner string, records []Record) (PushResult, error) {
	var result PushResult

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return result, fmt.Errorf("begin push: %w", err)
	}
	defer tx.Rollback()

	// Pushes for the same owner run one at a time. That keeps seq values
	// committing in order, so a device pulling "after seq N" never misses a
	// change that was assigned a lower seq but committed later.
	if _, err := tx.ExecContext(ctx, `SELECT pg_advisory_xact_lock(hashtext($1))`, owner); err != nil {
		return result, fmt.Errorf("lock owner: %w", err)
	}

	for _, rec := range records {
		var seq int64
		err := tx.QueryRowContext(ctx, upsertRecordSQL,
			owner, rec.Type, rec.ID, string(rec.Data), rec.UpdatedAt, rec.Deleted,
		).Scan(&seq)
		if err == nil {
			result.Applied++
			continue
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return result, fmt.Errorf("store %s %s: %w", rec.Type, rec.ID, err)
		}

		// Nothing was written: the server has this exact version or a newer one.
		existing := Record{Type: rec.Type, ID: rec.ID}
		var data []byte
		err = tx.QueryRowContext(ctx,
			`SELECT data, updated_at, deleted, seq FROM records WHERE owner = $1 AND type = $2 AND id = $3`,
			owner, rec.Type, rec.ID,
		).Scan(&data, &existing.UpdatedAt, &existing.Deleted, &existing.Seq)
		if err != nil {
			return result, fmt.Errorf("load %s %s: %w", rec.Type, rec.ID, err)
		}
		existing.Data = data
		existing.UpdatedAt = existing.UpdatedAt.UTC()

		if existing.UpdatedAt.After(rec.UpdatedAt) {
			result.Stale = append(result.Stale, existing)
		} else {
			result.Unchanged++
		}
	}

	if err := tx.Commit(); err != nil {
		return result, fmt.Errorf("commit push: %w", err)
	}
	return result, nil
}

// Pull returns up to limit records changed after the given seq, oldest first,
// and whether more are waiting.
func (s *Store) Pull(ctx context.Context, owner string, since int64, limit int) ([]Record, bool, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT type, id, data, updated_at, deleted, seq
		 FROM records
		 WHERE owner = $1 AND seq > $2
		 ORDER BY seq
		 LIMIT $3`,
		owner, since, limit+1,
	)
	if err != nil {
		return nil, false, fmt.Errorf("query changes: %w", err)
	}
	defer rows.Close()

	records := make([]Record, 0, limit)
	for rows.Next() {
		var rec Record
		var data []byte
		if err := rows.Scan(&rec.Type, &rec.ID, &data, &rec.UpdatedAt, &rec.Deleted, &rec.Seq); err != nil {
			return nil, false, fmt.Errorf("read change: %w", err)
		}
		rec.Data = data
		rec.UpdatedAt = rec.UpdatedAt.UTC()
		records = append(records, rec)
	}
	if err := rows.Err(); err != nil {
		return nil, false, fmt.Errorf("read changes: %w", err)
	}

	hasMore := len(records) > limit
	if hasMore {
		records = records[:limit]
	}
	return records, hasMore, nil
}

// CreateSession records a signed-in device.
func (s *Store) CreateSession(ctx context.Context, tokenHash []byte, email, deviceName string) error {
	_, err := s.db.ExecContext(ctx,
		`INSERT INTO sessions (token_hash, email, device_name) VALUES ($1, $2, $3)`,
		tokenHash, email, deviceName,
	)
	if err != nil {
		return fmt.Errorf("create session: %w", err)
	}
	return nil
}

// SessionEmail returns the account a session token belongs to and marks the
// session as used. It returns ErrNoSession for an unknown token.
func (s *Store) SessionEmail(ctx context.Context, tokenHash []byte) (string, error) {
	var email string
	err := s.db.QueryRowContext(ctx,
		`UPDATE sessions SET last_used_at = now() WHERE token_hash = $1 RETURNING email`,
		tokenHash,
	).Scan(&email)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrNoSession
	}
	if err != nil {
		return "", fmt.Errorf("load session: %w", err)
	}
	return email, nil
}

// DeleteSession signs a device out.
func (s *Store) DeleteSession(ctx context.Context, tokenHash []byte) error {
	if _, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE token_hash = $1`, tokenHash); err != nil {
		return fmt.Errorf("delete session: %w", err)
	}
	return nil
}
