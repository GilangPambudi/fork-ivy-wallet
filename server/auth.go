package main

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"strings"
	"sync"
	"time"
)

// TokenVerifier turns a Google ID token into the verified email it was
// issued for.
type TokenVerifier interface {
	Verify(ctx context.Context, idToken string) (email string, err error)
}

// ErrInvalidToken is returned for any ID token that must not be trusted.
var ErrInvalidToken = errors.New("invalid id token")

const (
	googleCertsURL = "https://www.googleapis.com/oauth2/v3/certs"
	// Google rotates signing keys rarely; an hour of caching is plenty.
	certsCacheTTL = time.Hour
	// A token with an unknown key id triggers a refresh, but not more often
	// than this, so junk tokens cannot make the server hammer Google.
	certsMinRefresh = time.Minute
	// Allowed clock difference between Google and this server.
	clockLeeway = time.Minute
)

// GoogleVerifier checks Google ID tokens locally against Google's published
// signing keys.
type GoogleVerifier struct {
	clientID string
	certsURL string
	client   *http.Client
	now      func() time.Time

	mu        sync.Mutex
	keys      map[string]*rsa.PublicKey
	fetchedAt time.Time
}

// NewGoogleVerifier returns a verifier for tokens issued to clientID.
func NewGoogleVerifier(clientID string) *GoogleVerifier {
	return &GoogleVerifier{
		clientID: clientID,
		certsURL: googleCertsURL,
		client:   &http.Client{Timeout: 10 * time.Second},
		now:      time.Now,
	}
}

type idTokenHeader struct {
	Alg string `json:"alg"`
	Kid string `json:"kid"`
}

type idTokenClaims struct {
	Iss           string          `json:"iss"`
	Aud           string          `json:"aud"`
	Exp           int64           `json:"exp"`
	Email         string          `json:"email"`
	EmailVerified json.RawMessage `json:"email_verified"`
}

// Verify checks the token's signature, issuer, audience and expiry, and
// returns its email only if Google has verified that address.
func (v *GoogleVerifier) Verify(ctx context.Context, idToken string) (string, error) {
	parts := strings.Split(idToken, ".")
	if len(parts) != 3 {
		return "", fmt.Errorf("%w: not a JWT", ErrInvalidToken)
	}

	var header idTokenHeader
	if err := decodeSegment(parts[0], &header); err != nil {
		return "", fmt.Errorf("%w: bad header", ErrInvalidToken)
	}
	// Only RS256 is accepted. This also rejects "none" and HMAC tokens.
	if header.Alg != "RS256" || header.Kid == "" {
		return "", fmt.Errorf("%w: unsupported algorithm", ErrInvalidToken)
	}

	key, err := v.key(ctx, header.Kid)
	if err != nil {
		return "", err
	}

	signature, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return "", fmt.Errorf("%w: bad signature encoding", ErrInvalidToken)
	}
	digest := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if err := rsa.VerifyPKCS1v15(key, crypto.SHA256, digest[:], signature); err != nil {
		return "", fmt.Errorf("%w: signature mismatch", ErrInvalidToken)
	}

	var claims idTokenClaims
	if err := decodeSegment(parts[1], &claims); err != nil {
		return "", fmt.Errorf("%w: bad claims", ErrInvalidToken)
	}
	if claims.Iss != "accounts.google.com" && claims.Iss != "https://accounts.google.com" {
		return "", fmt.Errorf("%w: wrong issuer", ErrInvalidToken)
	}
	if claims.Aud != v.clientID {
		return "", fmt.Errorf("%w: wrong audience", ErrInvalidToken)
	}
	if v.now().After(time.Unix(claims.Exp, 0).Add(clockLeeway)) {
		return "", fmt.Errorf("%w: expired", ErrInvalidToken)
	}
	verified := strings.Trim(string(claims.EmailVerified), `"`)
	if claims.Email == "" || verified != "true" {
		return "", fmt.Errorf("%w: email not verified", ErrInvalidToken)
	}
	return strings.ToLower(claims.Email), nil
}

// key returns the signing key with the given id, refreshing the cached key
// set when it is stale or the id is unknown.
func (v *GoogleVerifier) key(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	v.mu.Lock()
	defer v.mu.Unlock()

	age := v.now().Sub(v.fetchedAt)
	key, known := v.keys[kid]
	if known && age < certsCacheTTL {
		return key, nil
	}
	if !known && v.keys != nil && age < certsMinRefresh {
		return nil, fmt.Errorf("%w: unknown signing key", ErrInvalidToken)
	}

	keys, err := v.fetchKeys(ctx)
	if err != nil {
		if known {
			// Google is unreachable but the cached key is still good enough.
			return key, nil
		}
		return nil, fmt.Errorf("fetch google signing keys: %w", err)
	}
	v.keys = keys
	v.fetchedAt = v.now()

	key, known = v.keys[kid]
	if !known {
		return nil, fmt.Errorf("%w: unknown signing key", ErrInvalidToken)
	}
	return key, nil
}

func (v *GoogleVerifier) fetchKeys(ctx context.Context) (map[string]*rsa.PublicKey, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, v.certsURL, nil)
	if err != nil {
		return nil, err
	}
	resp, err := v.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d", resp.StatusCode)
	}

	var set struct {
		Keys []struct {
			Kty string `json:"kty"`
			Kid string `json:"kid"`
			N   string `json:"n"`
			E   string `json:"e"`
		} `json:"keys"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&set); err != nil {
		return nil, fmt.Errorf("decode key set: %w", err)
	}

	keys := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, k := range set.Keys {
		if k.Kty != "RSA" || k.Kid == "" {
			continue
		}
		n, err := base64.RawURLEncoding.DecodeString(k.N)
		if err != nil {
			continue
		}
		e, err := base64.RawURLEncoding.DecodeString(k.E)
		if err != nil || len(e) == 0 || len(e) > 4 {
			continue
		}
		exponent := 0
		for _, b := range e {
			exponent = exponent<<8 | int(b)
		}
		keys[k.Kid] = &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: exponent}
	}
	if len(keys) == 0 {
		return nil, errors.New("key set has no usable keys")
	}
	return keys, nil
}

func decodeSegment(segment string, into any) error {
	raw, err := base64.RawURLEncoding.DecodeString(segment)
	if err != nil {
		return err
	}
	return json.Unmarshal(raw, into)
}

// newSessionToken returns a random token for a device and the hash that is
// stored for it. The token itself is never written to the database.
func newSessionToken() (token string, hash []byte, err error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", nil, fmt.Errorf("generate session token: %w", err)
	}
	token = base64.RawURLEncoding.EncodeToString(buf)
	return token, hashSessionToken(token), nil
}

func hashSessionToken(token string) []byte {
	sum := sha256.Sum256([]byte(token))
	return sum[:]
}
