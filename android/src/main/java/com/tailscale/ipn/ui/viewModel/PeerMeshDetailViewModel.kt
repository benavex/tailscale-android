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

// benavex fork: per-peer detail view-model.
//
// Lives for the duration of a single PeerMeshDetailView navigation.
// Driven by the same CapMap snapshot as MeshStatusViewModel, scoped
// to one peer name so the view re-renders whenever probing or the
// crown changes.
//
// Historic hour-bucket data (§11 on the headscale side) is not yet
// wired through localapi: the /mesh/history endpoint is HMAC-signed
// with the cluster secret and the client only holds the verifier
// pin. Surfacing it requires either (a) a noise-tunneled proxy in
// tailscaled that holds the secret, or (b) the endpoint accepting a
// pin-derived proof. Neither is implemented yet; the detail view
// shows the current-window stats that already live in the snapshot.
class PeerMeshDetailViewModel(private val peerName: String) : ViewModel() {
  val peer: StateFlow<Mesh.Peer?> = MutableStateFlow(null)
  val isCrown: StateFlow<Boolean> = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      Notifier.netmap.collect { nm ->
        val snap = Mesh.decode(nm?.SelfNode?.CapMap?.get(MESH_CAP_KEY))
        val p =
            when {
              snap == null -> null
              snap.self?.name == peerName -> snap.self
              else -> snap.peers.firstOrNull { it.name == peerName }
            }
        peer.set(p)
        isCrown.set(snap?.crown == peerName)
      }
    }
  }
}
