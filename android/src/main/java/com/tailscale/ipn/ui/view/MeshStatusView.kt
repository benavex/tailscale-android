// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.model.Mesh
import com.tailscale.ipn.ui.theme.listItem
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.util.itemsWithDividers
import com.tailscale.ipn.ui.viewModel.MeshStatusViewModel
import kotlin.math.roundToInt

// benavex fork: mesh status screen. Surfaces the CapMap snapshot
// that headscale already ships every netmap — crown + peers + health
// stats — so the user can see which control server they are bound
// to and how the cluster is behaving. Tapping a peer opens the
// detail screen for that peer.
@Composable
fun MeshStatusView(
    backToSettings: BackNavigation,
    onNavigateToPeerDetail: (peerName: String) -> Unit,
    model: MeshStatusViewModel = viewModel()
) {
  val snap by model.snapshot.collectAsState()
  val controlURL by model.controlURL.collectAsState()

  Scaffold(topBar = { Header(R.string.mesh_status_title, onBack = backToSettings) }) { inner ->
    LazyColumn(Modifier.padding(inner)) {
      item("header") {
        ListItem(
            colors = MaterialTheme.colorScheme.listItem,
            headlineContent = {
              Text(
                  stringResource(R.string.mesh_control_url),
                  style = MaterialTheme.typography.titleSmall)
            },
            supportingContent = {
              Text(
                  controlURL.ifBlank { stringResource(R.string.mesh_not_bound) },
                  style = MaterialTheme.typography.bodyMedium)
            })
      }

      val s = snap
      if (s == null) {
        item("empty") {
          Lists.SectionDivider()
          Text(
              text = stringResource(R.string.mesh_no_snapshot),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp))
        }
      } else {
        item("crown") {
          Lists.SectionDivider(stringResource(R.string.mesh_crown))
          ListItem(
              colors = MaterialTheme.colorScheme.listItem,
              headlineContent = {
                Text(
                    s.crown.ifBlank { stringResource(R.string.mesh_crown_unknown) },
                    style = MaterialTheme.typography.bodyMedium)
              })
        }

        val self = s.self
        if (self != null) {
          item("selfHeader") { Lists.SectionDivider(stringResource(R.string.mesh_self)) }
          item("self") {
            PeerRow(peer = self, isCrown = s.crown == self.name, onClick = null)
          }
        }

        if (s.peers.isNotEmpty()) {
          item("peersHeader") { Lists.SectionDivider(stringResource(R.string.mesh_peers)) }
          itemsWithDividers(s.peers, key = { it.name }) { p ->
            PeerRow(peer = p, isCrown = s.crown == p.name) { onNavigateToPeerDetail(p.name) }
          }
        }
      }
    }
  }
}

@Composable
private fun PeerRow(peer: Mesh.Peer, isCrown: Boolean, onClick: (() -> Unit)?) {
  val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
  ListItem(
      modifier = modifier,
      colors = MaterialTheme.colorScheme.listItem,
      leadingContent = { OnlineDot(peer.online) },
      headlineContent = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(peer.name, style = MaterialTheme.typography.bodyMedium)
          if (isCrown) {
            Text(
                stringResource(R.string.mesh_crown_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
          }
        }
      },
      supportingContent = {
        val parts = mutableListOf<String>()
        parts.add(peer.url)
        if (peer.latency_ms > 0) parts.add("${peer.latency_ms.roundToInt()} ms")
        if (peer.score > 0) parts.add("score ${"%.2f".format(peer.score)}")
        if (peer.exit_node_name.isNotBlank()) parts.add("exit=${peer.exit_node_name}")
        Text(parts.joinToString("  "), style = MaterialTheme.typography.bodySmall)
      })
}

@Composable
private fun OnlineDot(online: Boolean) {
  // Small 10dp filled circle. Green when the peer is marked online in
  // the mesh snapshot, muted grey otherwise. Matches the at-a-glance
  // dots used in the upstream status screens.
  val color = if (online) Color(0xFF2E7D32) else Color(0xFF9E9E9E)
  Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}
