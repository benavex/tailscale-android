// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// benavex fork: in-app log viewer backing model.
//
// Replaces the upstream Bug Report flow which uploaded logs to
// Tailscale's infrastructure (we don't have that infrastructure).
// Polls /localapi/v0/logtail on a 2s cadence and republishes as a
// StateFlow so the compose screen can render and auto-scroll.
class LogsViewModel : ViewModel() {
  // All cached log lines, oldest first. Capped to the daemon-side
  // ring size (currently 4096); shrinking is the daemon's job.
  val lines: StateFlow<List<String>> = MutableStateFlow(emptyList())
  // Current search filter; empty means "show everything".
  val filter: StateFlow<String> = MutableStateFlow("")
  // Transient error message from the most recent poll, surfaced in
  // the UI so a broken localapi call doesn't silently hide stale data.
  val error: StateFlow<String?> = MutableStateFlow(null)

  init {
    viewModelScope.launch {
      while (isActive) {
        Client(viewModelScope).logTail(max = MAX_LINES) { result ->
          result
              .onSuccess {
                lines.set(it)
                error.set(null)
              }
              .onFailure { error.set(it.message ?: "log fetch failed") }
        }
        delay(POLL_INTERVAL_MS)
      }
    }
  }

  fun setFilter(text: String) {
    filter.set(text)
  }

  companion object {
    private const val POLL_INTERVAL_MS = 2000L
    private const val MAX_LINES = 4096
  }
}
