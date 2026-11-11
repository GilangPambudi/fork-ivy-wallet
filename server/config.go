package main

import (
	"fmt"
	"strings"
)

// Config holds everything the server needs to start. All values come from
// environment variables so the same image runs anywhere.
type Config struct {
	// DatabaseURL is a PostgreSQL connection string.
	DatabaseURL string
	// AllowedEmail is the only Google account that may sign in.
	AllowedEmail string
	// GoogleClientID is the OAuth client ID the app requests ID tokens for.
	GoogleClientID string
	// ListenAddr is the address the HTTP server binds to.
	ListenAddr string
}

func loadConfig(getenv func(string) string) (Config, error) {
	cfg := Config{
		DatabaseURL:    strings.TrimSpace(getenv("RIVY_DATABASE_URL")),
		AllowedEmail:   strings.TrimSpace(getenv("RIVY_ALLOWED_EMAIL")),
		GoogleClientID: strings.TrimSpace(getenv("RIVY_GOOGLE_CLIENT_ID")),
		ListenAddr:     strings.TrimSpace(getenv("RIVY_LISTEN_ADDR")),
	}
	if cfg.ListenAddr == "" {
		cfg.ListenAddr = ":8080"
	}

	var missing []string
	if cfg.DatabaseURL == "" {
		missing = append(missing, "RIVY_DATABASE_URL")
	}
	if cfg.AllowedEmail == "" {
		missing = append(missing, "RIVY_ALLOWED_EMAIL")
	}
	if cfg.GoogleClientID == "" {
		missing = append(missing, "RIVY_GOOGLE_CLIENT_ID")
	}
	if len(missing) > 0 {
		return Config{}, fmt.Errorf("missing required environment variables: %s", strings.Join(missing, ", "))
	}
	return cfg, nil
}
