// Copyright (c) Tailscale Inc & contributors
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.util.set
import com.tailscale.ipn.ui.view.ErrorDialogType
import com.tailscale.ipn.util.TSLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Drives the one-screen welcome flow used by the benavex fork.
 *
 * The user pastes three pieces of information from the cluster operator: bootstrap URL, 8-character
 * verifier (cluster identity hash), and a preauth key. submit() chains them through the local
 * daemon: cluster-pin first, then editPrefs(ControlURL=...) + start(AuthKey=...) — so the user
 * never has to bounce through the gear → accounts → 3-dots menus, and never has the chance to fall
 * through to the default tailscale.com login.
 */
class WelcomeViewModel : IpnViewModel() {
  val errorDialog: StateFlow<ErrorDialogType?> = MutableStateFlow(null)

  // True while the chain (pin → editPrefs → start → loginInteractive) is in flight.
  val busy: StateFlow<Boolean> = MutableStateFlow(false)

  fun submit(
      url: String,
      verifier: String,
      authKey: String,
      followCrown: Boolean,
      onSuccess: () -> Unit,
  ) {
    val u = url.trim()
    val v = verifier.trim().uppercase()
    val k = authKey.trim()

    if (!(u.startsWith("http://", ignoreCase = true) ||
        u.startsWith("https://", ignoreCase = true)) || u.length <= 8) {
      errorDialog.set(ErrorDialogType.INVALID_CUSTOM_URL)
      return
    }
    if (v.length != 8) {
      errorDialog.set(ErrorDialogType.INVALID_VERIFIER)
      return
    }
    if (k.isEmpty()) {
      errorDialog.set(ErrorDialogType.INVALID_AUTH_KEY)
      return
    }

    busy.set(true)
    Client(viewModelScope).clusterPin(u, v) { pinResult ->
      pinResult
          .onFailure {
            busy.set(false)
            TSLog.e("WelcomeViewModel", "clusterPin failed: ${it.message}")
            errorDialog.set(ErrorDialogType.PIN_FAILED)
          }
          .onSuccess {
            // Pin persisted. Now set ControlURL + AutoExitNode + register
            // with auth key in one shot. Follow-crown is the default for
            // the mesh use case so the user's exit node tracks whichever
            // sibling is currently elected crown.
            val prefs = Ipn.MaskedPrefs()
            prefs.ControlURL = u
            if (followCrown) {
              prefs.AutoExitNode = "follow-crown"
            }
            login(prefs, authKey = k) { loginResult ->
              busy.set(false)
              loginResult
                  .onFailure {
                    TSLog.e("WelcomeViewModel", "login failed: ${it.message}")
                    errorDialog.set(ErrorDialogType.ADD_PROFILE_FAILED)
                  }
                  .onSuccess { onSuccess() }
            }
          }
    }
  }
}
