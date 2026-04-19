// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

// Kotlin mirror of tailscale/ipn/mesh/invite/invite.go. Parses the
// single-string vpn:// invite the operator hands the user — tolerates
// whitespace, line wrapping, CRLF — and exposes the three fields the
// existing WelcomeViewModel.submit() already knows how to consume.

package com.tailscale.ipn.ui.model

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object MeshInvite {
  const val SCHEME = "vpn://"
  const val VERSION = 1

  @Serializable
  data class Payload(
      val z: Int = 0, // version
      val u: String = "", // bootstrap url
      val v: String = "", // 8-char verifier
      val k: String = "", // pre-auth key
      val n: String? = null, // optional note
  )

  data class Invite(val url: String, val verifier: String, val authKey: String, val note: String?)

  // parse returns Result.success(Invite) for any well-formed vpn://
  // string, Result.failure(IllegalArgumentException) otherwise. Every
  // whitespace rune is stripped before decoding so a terminal-wrapped
  // blob round-trips. Case-insensitive on the scheme prefix.
  fun parse(raw: String): Result<Invite> =
      runCatching {
        val clean = raw.filterNot { it.isWhitespace() }
        require(clean.isNotEmpty()) { "empty invite string" }
        require(clean.length > SCHEME.length && clean.substring(0, SCHEME.length).equals(SCHEME, ignoreCase = true)) {
          "invite must start with \"$SCHEME\""
        }
        val b64 = clean.substring(SCHEME.length)
        // Base64.URL_SAFE expects no padding in the input and handles - / _.
        val jsonBytes =
            try {
              Base64.decode(b64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
              throw IllegalArgumentException("decode base64: ${e.message}")
            }
        val payload =
            Json { ignoreUnknownKeys = true }
                .decodeFromString(Payload.serializer(), String(jsonBytes, Charsets.UTF_8))
        require(payload.z == VERSION) {
          "unsupported invite version z=${payload.z} (this app understands $VERSION)"
        }
        require(payload.u.startsWith("http://", ignoreCase = true) ||
            payload.u.startsWith("https://", ignoreCase = true)) {
          "invite url must start with http:// or https://"
        }
        require(payload.v.length == 8) {
          "invite verifier must be 8 chars (got ${payload.v.length})"
        }
        require(payload.k.isNotEmpty()) { "invite missing auth key" }
        Invite(
            url = payload.u,
            verifier = payload.v.uppercase(),
            authKey = payload.k,
            note = payload.n,
        )
      }
}
