// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.viewModel.LogsViewModel

// benavex fork: in-app logs viewer. Replaces the Bug Report flow.
// Reads a snapshot of the tailscaled in-memory log ring every 2s
// (see libtailscale.logRing) and renders the filtered view. Top-bar
// actions let the user copy all lines to clipboard or share as
// text/plain so the lines can be pasted into a support chat.
@Composable
fun LogsView(backToSettings: BackNavigation, model: LogsViewModel = viewModel()) {
  val allLines by model.lines.collectAsState()
  val filter by model.filter.collectAsState()
  val error by model.error.collectAsState()
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current

  val filteredLines =
      if (filter.isBlank()) {
        allLines
      } else {
        // Case-insensitive substring match; good-enough grep for
        // a debugging session, no regex support intentionally.
        allLines.filter { it.contains(filter, ignoreCase = true) }
      }

  val listState = rememberLazyListState()
  // Auto-scroll to the latest line each time the ring grows.
  LaunchedEffect(filteredLines.size) {
    if (filteredLines.isNotEmpty()) {
      listState.scrollToItem(filteredLines.size - 1)
    }
  }

  Scaffold(
      topBar = {
        Header(
            titleRes = R.string.logs_title,
            onBack = backToSettings,
            actions = {
              TextButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(allLines.joinToString("\n")))
                  }) {
                    Text(stringResource(R.string.logs_copy_all))
                  }
              TextButton(
                  onClick = {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                          type = "text/plain"
                          putExtra(Intent.EXTRA_TEXT, allLines.joinToString("\n"))
                          putExtra(Intent.EXTRA_SUBJECT, "tailscale logs")
                        }
                    val chooser =
                        Intent.createChooser(send, context.getString(R.string.logs_share))
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(chooser)
                  }) {
                    Text(stringResource(R.string.logs_share))
                  }
            })
      }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
          // Filter text field. BasicTextField keeps the chrome minimal
          // so it reads like a grep prompt rather than a form input.
          Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            BasicTextField(
                value = filter,
                onValueChange = { model.setFilter(it) },
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                  if (filter.isEmpty()) {
                    Text(
                        text = stringResource(R.string.logs_filter_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  inner()
                })
          }

          error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
          }

          LazyColumn(
              state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(filteredLines) { line ->
                  Text(
                      text = line,
                      style =
                          MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                      color = MaterialTheme.colorScheme.onSurface,
                      softWrap = true,
                      overflow = TextOverflow.Visible,
                      modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
                }
              }
        }
      }
}
