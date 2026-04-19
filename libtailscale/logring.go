// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"sync"

	"tailscale.com/logtail"
)

// logRingMax caps the in-memory log buffer. Enough for the most
// recent few minutes of activity on a busy device; bounded so an
// idle app never hoards memory.
const logRingMax = 4096

// logRing is a bounded, goroutine-safe FIFO of the most recent
// tailscale log lines. Populated by subscribing to logtail's
// log-tap broadcast at app startup. Consumed by the android
// in-app log viewer via /localapi/v0/logtail. The ring only lives
// in RAM; nothing is persisted.
type logRing struct {
	mu    sync.Mutex
	lines []string // circular; newest append, drop head on overflow
}

var (
	// package-level singleton. Safe because there is a single
	// libtailscale App per process.
	theLogRing logRing
	// subscribeOnce guards the background goroutine that
	// consumes from logtail.RegisterLogTap.
	logRingSubscribeOnce sync.Once
)

// startLogRing subscribes to logtail's broadcast once. Each
// message comes in as a JSON object of the form {"text":"...line..."}
// (see tailscale.com/logtail.RegisterLogTap); we unwrap and keep
// the plain text.
func startLogRing() {
	logRingSubscribeOnce.Do(func() {
		msgc := make(chan string, 64)
		logtail.RegisterLogTap(msgc)
		go func() {
			for msg := range msgc {
				text := unwrapLogTapLine(msg)
				if text == "" {
					continue
				}
				theLogRing.append(text)
			}
		}()
	})
}

// unwrapLogTapLine strips the {"text":"..."} JSON envelope that
// logtail uses for its tap stream. If the envelope is missing or
// malformed, we fall back to the raw line so diagnostic data is
// never silently dropped.
func unwrapLogTapLine(raw string) string {
	raw = strings.TrimRight(raw, "\r\n")
	if raw == "" {
		return ""
	}
	var env struct {
		Text string `json:"text"`
	}
	if err := json.Unmarshal([]byte(raw), &env); err == nil && env.Text != "" {
		return strings.TrimRight(env.Text, "\r\n")
	}
	return raw
}

func (r *logRing) append(line string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if len(r.lines) >= logRingMax {
		// Drop oldest. Shift instead of reslicing so the
		// backing array doesn't grow without bound on pathological
		// workloads.
		copy(r.lines, r.lines[1:])
		r.lines = r.lines[:len(r.lines)-1]
	}
	r.lines = append(r.lines, line)
}

// snapshot returns the last n lines (or all, if n <= 0 or n > len).
func (r *logRing) snapshot(n int) []string {
	r.mu.Lock()
	defer r.mu.Unlock()
	if n <= 0 || n >= len(r.lines) {
		out := make([]string, len(r.lines))
		copy(out, r.lines)
		return out
	}
	out := make([]string, n)
	copy(out, r.lines[len(r.lines)-n:])
	return out
}

// serveLogRing answers GET /localapi/v0/logtail?max=N with a JSON
// array of the most recent log lines. Deliberately a one-shot
// snapshot, not a streaming endpoint, because the android Client
// helper calls bodyBytes() which waits for the full response.
func serveLogRing(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "GET required", http.StatusMethodNotAllowed)
		return
	}
	n := 0
	if s := r.URL.Query().Get("max"); s != "" {
		if v, err := strconv.Atoi(s); err == nil {
			n = v
		}
	}
	lines := theLogRing.snapshot(n)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(lines)
}

// forkLocalAPIMux wraps the upstream localapi handler so fork-local
// endpoints can be served without touching the tailscale submodule.
// Paths not matched here fall through to upstream unchanged.
func forkLocalAPIMux(upstream http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/localapi/v0/logtail":
			serveLogRing(w, r)
			return
		}
		upstream.ServeHTTP(w, r)
	})
}
