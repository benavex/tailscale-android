// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.model.Mesh
import com.tailscale.ipn.ui.theme.listItem
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.viewModel.PeerMeshDetailViewModel
import kotlin.math.roundToInt

// benavex fork: per-peer detail view. Shows all of the snapshot
// fields for one peer (current probe state + the ReliabilityStats
// rollup headscale computed) so the user can drill into a specific
// control server's health.
//
// Historic hourly timeline (§11 endpoint) is not wired — see the
// comment on PeerMeshDetailViewModel. A placeholder row points at
// the relevant CapMap rollup fields for now.
@Composable
fun PeerMeshDetailView(
    peerName: String,
    backToMeshStatus: BackNavigation,
    model: PeerMeshDetailViewModel =
        viewModel(factory = PeerMeshDetailViewModelFactory(peerName))
) {
  val peer by model.peer.collectAsState()
  val isCrown by model.isCrown.collectAsState()

  Scaffold(topBar = { Header(title = { Text(peerName) }, onBack = backToMeshStatus) }) { inner ->
    LazyColumn(Modifier.padding(inner)) {
      val p = peer
      if (p == null) {
        item("gone") {
          Text(
              text = stringResource(R.string.mesh_peer_gone),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp))
        }
      } else {
        item("summary") { SummarySection(peer = p, isCrown = isCrown) }

        val rel = p.reliability
        if (rel != null) {
          item("historyHeader") {
            Lists.SectionDivider(stringResource(R.string.mesh_peer_reliability))
          }
          item("relContent") { ReliabilitySection(rel) }
        }

        item("historyNote") {
          Lists.SectionDivider(stringResource(R.string.mesh_peer_history))
          Text(
              text = stringResource(R.string.mesh_peer_history_pending),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp))
        }
      }
    }
  }
}

@Composable
private fun SummarySection(peer: Mesh.Peer, isCrown: Boolean) {
  Column {
  InfoRow(stringResource(R.string.mesh_peer_url), peer.url)
  InfoRow(
      stringResource(R.string.mesh_peer_online),
      if (peer.online) stringResource(R.string.on) else stringResource(R.string.off))
  if (isCrown) {
    InfoRow(
        stringResource(R.string.mesh_crown),
        stringResource(R.string.mesh_crown_is_this_peer))
  }
  if (peer.latency_ms > 0) {
    InfoRow(stringResource(R.string.mesh_peer_latency), "${peer.latency_ms.roundToInt()} ms")
  }
  if (peer.score > 0) {
    InfoRow(stringResource(R.string.mesh_peer_score), "%.3f".format(peer.score))
  }
  if (peer.derp_region_name.isNotBlank() || peer.derp_region_id != 0) {
    InfoRow(
        stringResource(R.string.mesh_peer_derp),
        listOf(
                peer.derp_region_code.takeIf { it.isNotBlank() },
                peer.derp_region_name.takeIf { it.isNotBlank() },
                "region ${peer.derp_region_id}".takeIf { peer.derp_region_id != 0 })
            .filterNotNull()
            .joinToString(" / "))
  }
  if (peer.exit_node_name.isNotBlank()) {
    InfoRow(stringResource(R.string.mesh_peer_exit_node), peer.exit_node_name)
  }
  if (peer.last_seen != null) {
    InfoRow(stringResource(R.string.mesh_peer_last_seen), peer.last_seen)
  }
  }
}

@Composable
private fun ReliabilitySection(rel: Mesh.ReliabilityStats) {
  Column {
  InfoRow(stringResource(R.string.mesh_rel_uptime_1h), "${rel.uptime_1h_pct.roundToInt()}%")
  InfoRow(stringResource(R.string.mesh_rel_uptime_24h), "${rel.uptime_24h_pct.roundToInt()}%")
  InfoRow(stringResource(R.string.mesh_rel_uptime_7d), "${rel.uptime_7d_pct.roundToInt()}%")
  InfoRow(stringResource(R.string.mesh_rel_uptime_30d), "${rel.uptime_30d_pct.roundToInt()}%")
  InfoRow(
      stringResource(R.string.mesh_rel_uptime_life), "${rel.uptime_lifetime_pct.roundToInt()}%")
  InfoRow(
      stringResource(R.string.mesh_rel_disc_24h), rel.disconnect_count_24h.toString())
  InfoRow(
      stringResource(R.string.mesh_rel_disc_life), rel.disconnect_count_lifetime.toString())
  if (rel.latency_min_ms_1h > 0 || rel.latency_max_ms_1h > 0) {
    InfoRow(
        stringResource(R.string.mesh_rel_latency_window),
        "${rel.latency_min_ms_1h.roundToInt()} – ${rel.latency_max_ms_1h.roundToInt()} ms")
  }
  if (rel.throughput_p50_1h_mbps > 0) {
    InfoRow(
        stringResource(R.string.mesh_rel_thr_1h),
        "p50 %.1f Mbps, p01 %.1f Mbps".format(
            rel.throughput_p50_1h_mbps, rel.throughput_p01_1h_mbps))
  }
  if (rel.throughput_p50_24h_mbps > 0) {
    InfoRow(
        stringResource(R.string.mesh_rel_thr_24h),
        "p50 %.1f Mbps".format(rel.throughput_p50_24h_mbps))
  }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  ListItem(
      colors = MaterialTheme.colorScheme.listItem,
      headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
      supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall) })
}

// Factory so the peer name can be injected via constructor. Follows
// the pattern used by ExitNodePickerViewModelFactory in this package.
class PeerMeshDetailViewModelFactory(private val peerName: String) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    return PeerMeshDetailViewModel(peerName) as T
  }
}
