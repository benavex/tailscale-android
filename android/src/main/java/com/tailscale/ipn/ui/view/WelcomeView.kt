// Copyright (c) Tailscale Inc & contributors
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.model.MeshInvite
import com.tailscale.ipn.ui.util.set
import com.tailscale.ipn.ui.viewModel.WelcomeViewModel

/**
 * One-screen first-launch flow. Single paste field + button: the operator mints one vpn:// invite
 * string on the server (`headscale mesh user-invite …`), the user pastes it here, and the field
 * tolerates whatever whitespace/line-wrapping the terminal mangled the blob with on the way over.
 */
@Composable
fun WelcomeView(
    onNavigateHome: () -> Unit,
    viewModel: WelcomeViewModel = WelcomeViewModel(),
) {
  val error by viewModel.errorDialog.collectAsState()
  val busy by viewModel.busy.collectAsState()

  var invite by remember { mutableStateOf("") }
  var followCrown by remember { mutableStateOf(true) }

  error?.let { ErrorDialog(type = it, action = { viewModel.errorDialog.set(null) }) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
      Spacer(modifier = Modifier.height(60.dp))
      TailscaleLogoView(modifier = Modifier.width(60.dp).height(60.dp))
      Spacer(modifier = Modifier.height(24.dp))
      Text(
          text = stringResource(R.string.welcome_setup_title),
          style = MaterialTheme.typography.titleLarge,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
          modifier = Modifier.padding(horizontal = 32.dp),
          text = stringResource(R.string.welcome_setup_subtitle),
          style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(32.dp))

      Column(
          modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
            // Multi-line so the pasted blob doesn't get visually
            // truncated, and so soft-keyboards with paste buttons don't
            // trim trailing content. The parser strips whitespace at
            // submit time regardless.
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            value = invite,
            onValueChange = { invite = it },
            singleLine = false,
            maxLines = 8,
            enabled = !busy,
            label = { Text(stringResource(R.string.welcome_field_invite)) },
            placeholder = { Text(stringResource(R.string.welcome_placeholder_invite)) },
            supportingText = { Text(stringResource(R.string.welcome_help_invite)) },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Default),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
      Column(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = stringResource(R.string.welcome_follow_crown_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.welcome_follow_crown_help),
                style = MaterialTheme.typography.bodySmall,
            )
          }
          Switch(
              checked = followCrown,
              onCheckedChange = { followCrown = it },
              enabled = !busy,
          )
        }
      }
      Spacer(modifier = Modifier.height(24.dp))
      Column(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
        PrimaryActionButton(
            onClick = {
              MeshInvite.parse(invite).fold(
                  onSuccess = { inv ->
                    viewModel.submit(
                        url = inv.url,
                        verifier = inv.verifier,
                        authKey = inv.authKey,
                        followCrown = followCrown,
                        onSuccess = onNavigateHome,
                    )
                  },
                  onFailure = { viewModel.errorDialog.set(ErrorDialogType.INVALID_INVITE) },
              )
            },
        ) {
          Text(
              text =
                  stringResource(
                      if (busy) R.string.welcome_submit_busy else R.string.welcome_submit),
              fontSize = MaterialTheme.typography.titleMedium.fontSize,
          )
        }
      }
      Spacer(modifier = Modifier.height(40.dp))
    }

    if (busy) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
          contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(16.dp))
          Text(
              text = stringResource(R.string.welcome_connecting),
              color = MaterialTheme.colorScheme.onPrimary,
              style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
    }
  }
}
