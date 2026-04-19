// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.ui.model.MESH_CAP_KEY
import com.tailscale.ipn.ui.model.Mesh
import com.tailscale.ipn.ui.notifier.Notifier
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// benavex fork: mesh status view-model. Re-decodes the CapMap
// snapshot on every netmap emission so the UI always reflects the
// latest crown / peer list the daemon has seen.
class MeshStatusViewModel : ViewModel() {
  val snapshot: StateFlow<Mesh.Snapshot?> = MutableStateFlow(null)
  val controlURL: StateFlow<String> = MutableStateFlow("")

  init {
    viewModelScope.launch {
      Notifier.netmap.collect { nm ->
        val snap = Mesh.decode(nm?.SelfNode?.CapMap?.get(MESH_CAP_KEY))
        snapshot.set(snap)
      }
    }
    viewModelScope.launch {
      Notifier.prefs.collect { p -> controlURL.set(p?.ControlURL ?: "") }
    }
  }

  // Lookup used by the peer-detail screen. Falls back to null (peer
  // no longer in snapshot) which the view renders as "peer gone".
  fun peerByName(name: String): Mesh.Peer? {
    val snap = snapshot.value ?: return null
    if (snap.self?.name == name) return snap.self
    return snap.peers.firstOrNull { it.name == name }
  }
}
