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
import com.tailscale.ipn.ui.util.set
import com.tailscale.ipn.ui.viewModel.WelcomeViewModel

/**
 * One-screen first-launch flow for the benavex fork. Three fields, one button: bootstrap URL,
 * verifier, preauth key. Replaces the upstream IntroView so the user never lands on the default
 * tailscale.com sign-in path.
 */
@Composable
fun WelcomeView(
    onNavigateHome: () -> Unit,
    viewModel: WelcomeViewModel = WelcomeViewModel(),
) {
  val error by viewModel.errorDialog.collectAsState()
  val busy by viewModel.busy.collectAsState()

  var url by remember { mutableStateOf("") }
  var verifier by remember { mutableStateOf("") }
  var authKey by remember { mutableStateOf("") }
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
          modifier = Modifier.fillMaxWidth(),
          value = url,
          onValueChange = { url = it },
          singleLine = true,
          enabled = !busy,
          label = { Text(stringResource(R.string.welcome_field_url)) },
          placeholder = { Text(stringResource(R.string.welcome_placeholder_url)) },
          keyboardOptions =
              KeyboardOptions(
                  capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next),
      )
      OutlinedTextField(
          modifier = Modifier.fillMaxWidth(),
          value = verifier,
          onValueChange = { verifier = it.uppercase().take(8) },
          singleLine = true,
          enabled = !busy,
          label = { Text(stringResource(R.string.welcome_field_verifier)) },
          placeholder = { Text(stringResource(R.string.welcome_placeholder_verifier)) },
          supportingText = { Text(stringResource(R.string.welcome_help_verifier)) },
          keyboardOptions =
              KeyboardOptions(
                  capitalization = KeyboardCapitalization.Characters,
                  imeAction = ImeAction.Next),
      )
      OutlinedTextField(
          modifier = Modifier.fillMaxWidth(),
          value = authKey,
          onValueChange = { authKey = it },
          singleLine = true,
          enabled = !busy,
          label = { Text(stringResource(R.string.welcome_field_authkey)) },
          placeholder = { Text(stringResource(R.string.welcome_placeholder_authkey)) },
          keyboardOptions =
              KeyboardOptions(
                  capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
    // Follow-crown toggle (§3c). When on, the daemon's exit-node tracks
    // whichever sibling is currently elected crown — see
    // tailscale `--exit-node=auto:follow-crown`. Off is "no auto exit
    // node"; the user can pick one manually from settings.
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
            viewModel.submit(url, verifier, authKey, followCrown, onSuccess = onNavigateHome)
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

  // Connecting overlay (§3b). The user-visible signal that submit is
  // in flight: a translucent scrim plus a centered spinner with the
  // URL we're trying to reach. Without this, submit looked silent —
  // the only feedback was the button text changing one line, easy to
  // miss when the keyboard hid the button.
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
