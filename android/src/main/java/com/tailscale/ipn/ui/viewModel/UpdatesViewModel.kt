// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.ui.util.UpdateChecker
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// benavex fork: drives the "Check for updates" screen. Pure state
// machine around UpdateChecker — the Compose screen just observes
// `state` and calls check()/download()/install().
class UpdatesViewModel : ViewModel() {
  sealed class State {
    object Idle : State()
    object Checking : State()
    data class UpToDate(val installed: Long) : State()
    data class Available(val result: UpdateChecker.CheckResult.Available) : State()
    data class Downloading(val progress: Float, val result: UpdateChecker.CheckResult.Available) :
        State()
    data class ReadyToInstall(val apk: java.io.File, val result: UpdateChecker.CheckResult.Available) :
        State()
    data class Error(val message: String) : State()
  }

  val state: StateFlow<State> = MutableStateFlow(State.Idle)

  fun check(context: Context) {
    state.set(State.Checking)
    viewModelScope.launch {
      when (val r = UpdateChecker.check(context)) {
        is UpdateChecker.CheckResult.UpToDate -> state.set(State.UpToDate(r.installed))
        is UpdateChecker.CheckResult.Available -> state.set(State.Available(r))
        is UpdateChecker.CheckResult.Error -> state.set(State.Error(r.message))
      }
    }
  }

  fun download(context: Context) {
    val avail =
        when (val s = state.value) {
          is State.Available -> s.result
          else -> return
        }
    state.set(State.Downloading(0f, avail))
    viewModelScope.launch {
      val r =
          UpdateChecker.download(context, avail) { p ->
            state.set(State.Downloading(p, avail))
          }
      r.fold(
          onSuccess = { file -> state.set(State.ReadyToInstall(file, avail)) },
          onFailure = { e -> state.set(State.Error(e.message ?: "download failed")) })
    }
  }

  fun install(context: Context) {
    val apk =
        when (val s = state.value) {
          is State.ReadyToInstall -> s.apk
          else -> return
        }
    UpdateChecker.launchInstall(context, apk)
  }

  fun reset() {
    state.set(State.Idle)
  }
}
