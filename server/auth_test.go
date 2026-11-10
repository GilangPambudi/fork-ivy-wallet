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
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

const testClientID = "rivy-test-client.apps.googleusercontent.com"

// fakeGoogle serves a key set like Google's and signs ID tokens with it.
type fakeGoogle struct {
	key    *rsa.PrivateKey
	kid    string
	server *httptest.Server
}

func newFakeGoogle(t *testing.T) *fakeGoogle {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	g := &fakeGoogle{key: key, kid: "test-key-1"}
	g.server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]any{
			"keys": []map[string]string{{
				"kty": "RSA",
				"kid": g.kid,
				"n":   base64.RawURLEncoding.EncodeToString(key.N.Bytes()),
				"e":   base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes()),
			}},
		})
	}))
	t.Cleanup(g.server.Close)
	return g
}

func (g *fakeGoogle) verifier() *GoogleVerifier {
	v := NewGoogleVerifier(testClientID)
	v.certsURL = g.server.URL
	return v
}

func (g *fakeGoogle) token(t *testing.T, header, claims map[string]any) string {
	t.Helper()
	return signToken(t, g.key, header, claims)
}

func signToken(t *testing.T, key *rsa.PrivateKey, header, claims map[string]any) string {
	t.Helper()
	encode := func(v any) string {
		raw, err := json.Marshal(v)
		if err != nil {
			t.Fatalf("marshal: %v", err)
		}
		return base64.RawURLEncoding.EncodeToString(raw)
	}
	signingInput := encode(header) + "." + encode(claims)
	digest := sha256.Sum256([]byte(signingInput))
	signature, err := rsa.SignPKCS1v15(rand.Reader, key, crypto.SHA256, digest[:])
	if err != nil {
		t.Fatalf("sign: %v", err)
	}
	return signingInput + "." + base64.RawURLEncoding.EncodeToString(signature)
}

func validClaims() map[string]any {
	return map[string]any{
		"iss":            "https://accounts.google.com",
		"aud":            testClientID,
		"exp":            time.Now().Add(time.Hour).Unix(),
		"email":          "Owner@Example.com",
		"email_verified": true,
	}
}

func TestGoogleVerifierAcceptsValidToken(t *testing.T) {
	g := newFakeGoogle(t)
	token := g.token(t, map[string]any{"alg": "RS256", "kid": g.kid}, validClaims())

	email, err := g.verifier().Verify(context.Background(), token)
	if err != nil {
		t.Fatalf("Verify: %v", err)
	}
	if email != "owner@example.com" {
		t.Fatalf("email = %q, want lowercased owner@example.com", email)
	}
}

func TestGoogleVerifierRejectsBadTokens(t *testing.T) {
	g := newFakeGoogle(t)
	otherKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	goodHeader := map[string]any{"alg": "RS256", "kid": g.kid}

	with := func(change func(map[string]any)) map[string]any {
		claims := validClaims()
		change(claims)
		return claims
	}

	cases := map[string]string{
		"not a jwt":      "definitely-not-a-token",
		"wrong audience": g.token(t, goodHeader, with(func(c map[string]any) { c["aud"] = "someone-else" })),
		"wrong issuer":   g.token(t, goodHeader, with(func(c map[string]any) { c["iss"] = "https://evil.example" })),
		"expired": g.token(t, goodHeader, with(func(c map[string]any) {
			c["exp"] = time.Now().Add(-time.Hour).Unix()
		})),
		"email not verified": g.token(t, goodHeader, with(func(c map[string]any) { c["email_verified"] = false })),
		"no email":           g.token(t, goodHeader, with(func(c map[string]any) { delete(c, "email") })),
		"alg none":           g.token(t, map[string]any{"alg": "none", "kid": g.kid}, validClaims()),
		"alg hs256":          g.token(t, map[string]any{"alg": "HS256", "kid": g.kid}, validClaims()),
		"unknown key id":     g.token(t, map[string]any{"alg": "RS256", "kid": "other"}, validClaims()),
		"signed by another":  signToken(t, otherKey, goodHeader, validClaims()),
	}

	for name, token := range cases {
		t.Run(name, func(t *testing.T) {
			email, err := g.verifier().Verify(context.Background(), token)
			if !errors.Is(err, ErrInvalidToken) {
				t.Fatalf("Verify = (%q, %v), want ErrInvalidToken", email, err)
			}
		})
	}
}

func TestGoogleVerifierRejectsTamperedClaims(t *testing.T) {
	g := newFakeGoogle(t)
	token := g.token(t, map[string]any{"alg": "RS256", "kid": g.kid}, validClaims())

	// Swap the claims for ones naming a different account, keeping the
	// original signature.
	forged, err := json.Marshal(map[string]any{
		"iss": "https://accounts.google.com", "aud": testClientID,
		"exp": time.Now().Add(time.Hour).Unix(), "email": "attacker@example.com", "email_verified": true,
	})
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	parts := splitToken(token)
	tampered := parts[0] + "." + base64.RawURLEncoding.EncodeToString(forged) + "." + parts[2]

	if _, err := g.verifier().Verify(context.Background(), tampered); !errors.Is(err, ErrInvalidToken) {
		t.Fatalf("Verify error = %v, want ErrInvalidToken", err)
	}
}

func splitToken(token string) [3]string {
	var parts [3]string
	i := 0
	for _, r := range token {
		if r == '.' {
			i++
			continue
		}
		parts[i] += string(r)
	}
	return parts
}
