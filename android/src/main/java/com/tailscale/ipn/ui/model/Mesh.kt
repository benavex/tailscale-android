// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// benavex fork: Android decoders for the mesh snapshot that headscale
// stuffs into every node's CapMap under MESH_CAP_KEY. Surfaced on the
// phone so the "which server am I bound to / which one is crown"
// question has a UI answer.
//
// Mirrors the MeshSnapshot/PeerStatus/ReliabilityStats structs in
// hscontrol (see headscale). Fields match exactly so kotlinx.serialization
// can decode straight from the JsonElement that netmap already holds.

// MESH_CAP_KEY names the CapMap key published by the fork's headscale.
// Every peer (self included) carries a fresh copy each MapResponse.
const val MESH_CAP_KEY = "benavex.com/cap/mesh"

object Mesh {
  // JSON keys match the headscale side wire format (lowercase_snake).
  // See hscontrol/mesh/mesh.go and reliability.go.

  @Serializable
  data class ReliabilityStats(
      val uptime_1h_pct: Double = 0.0,
      val uptime_24h_pct: Double = 0.0,
      val uptime_7d_pct: Double = 0.0,
      val uptime_30d_pct: Double = 0.0,
      val uptime_lifetime_pct: Double = 0.0,
      val disconnect_count_24h: Long = 0,
      val disconnect_count_lifetime: Long = 0,
      val latency_min_ms_1h: Double = 0.0,
      val latency_max_ms_1h: Double = 0.0,
      val throughput_p50_1h_mbps: Double = 0.0,
      val throughput_p01_1h_mbps: Double = 0.0,
      val throughput_p50_24h_mbps: Double = 0.0,
  )

  @Serializable
  data class Peer(
      val name: String = "",
      val url: String = "",
      val online: Boolean = false,
      val last_seen: String? = null,
      val uptime_seconds: Double = 0.0,
      val score: Double = 0.0,
      val latency_ms: Double = 0.0,
      val noise_pub: String = "",
      val cluster_sig: String = "",
      val exit_node_name: String = "",
      val derp_region_id: Int = 0,
      val derp_host: String = "",
      val derp_port: Int = 0,
      val derp_v4: String = "",
      val derp_v6: String = "",
      val derp_stun_port: Int = 0,
      val derp_region_code: String = "",
      val derp_region_name: String = "",
      val reliability: ReliabilityStats? = null,
  )

  @Serializable
  data class Snapshot(
      val self: Peer? = null,
      val peers: List<Peer> = emptyList(),
      val crown: String = "",
      val updated: String = "",
  )

  // json matches the Notifier decoder settings — unknown keys must not
  // break us if headscale adds a field mid-release.
  private val json = Json { ignoreUnknownKeys = true }

  // Decode the JsonElement held in CapMap[MESH_CAP_KEY]. Headscale
  // wraps the snapshot in a single-element JSON array (CapMap is
  // map[key][]RawMessage server-side), so we unwrap that before
  // handing to the Snapshot deserializer. A bare object also works
  // in case the envelope convention changes. Returns null on any
  // parse failure — a malformed cap must not break the UI.
  fun decode(el: JsonElement?): Snapshot? {
    if (el == null) return null
    val payload: JsonElement =
        when (el) {
          is JsonArray -> el.firstOrNull() ?: return null
          is JsonObject -> el
          else -> return null
        }
    return try {
      json.decodeFromJsonElement(Snapshot.serializer(), payload)
    } catch (t: Throwable) {
      null
    }
  }
}
