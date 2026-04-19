// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.util.UpdateChecker
import com.tailscale.ipn.ui.viewModel.UpdatesViewModel

// benavex fork: "Check for updates" screen. Reaches out to the
// GitHub "awg-latest" rolling release, parses the versionCode from
// the release body, and if newer than BuildConfig.VERSION_CODE lets
// the user download + install. The APK is staged under cacheDir/apk
// and wiped on subsequent app starts (see App.onCreate).
@Composable
fun UpdatesView(backToSettings: BackNavigation, model: UpdatesViewModel = viewModel()) {
  val ctx = LocalContext.current
  val state by model.state.collectAsState()
  val installed = remember { UpdateChecker.installedVersionCode(ctx) }

  Scaffold(topBar = { Header(R.string.updates_title, onBack = backToSettings) }) { inner ->
    Column(
        modifier = Modifier.padding(inner).padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          stringResource(R.string.updates_current, installed),
          style = MaterialTheme.typography.bodyMedium)

      when (val s = state) {
        is UpdatesViewModel.State.Idle -> {
          Button(onClick = { model.check(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_check))
          }
        }
        is UpdatesViewModel.State.Checking -> {
          Text(
              stringResource(R.string.updates_checking),
              style = MaterialTheme.typography.bodyMedium)
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is UpdatesViewModel.State.UpToDate -> {
          Text(
              stringResource(R.string.updates_up_to_date),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary)
          Button(onClick = { model.reset() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_check_again))
          }
        }
        is UpdatesViewModel.State.Available -> {
          Text(
              stringResource(R.string.updates_available, s.result.remote),
              style = MaterialTheme.typography.bodyMedium)
          val mb = s.result.sizeBytes.toDouble() / (1024 * 1024)
          Text(
              stringResource(R.string.updates_size_mb, "%.1f".format(mb)),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          Button(onClick = { model.download(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_download))
          }
        }
        is UpdatesViewModel.State.Downloading -> {
          val pct = (s.progress * 100).toInt()
          Text(
              stringResource(R.string.updates_downloading, pct),
              style = MaterialTheme.typography.bodyMedium)
          LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        }
        is UpdatesViewModel.State.ReadyToInstall -> {
          Text(
              stringResource(R.string.updates_ready),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary)
          Button(onClick = { model.install(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_install))
          }
        }
        is UpdatesViewModel.State.Error -> {
          Text(
              stringResource(R.string.updates_error, s.message),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error)
          Button(onClick = { model.check(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_retry))
          }
        }
      }
    }
  }
}
