package io.sentry.android.buddy.ui.userflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface

@Composable
internal fun ErrorSheet(
  state: SentryBuddySessionState.Error,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
) {
  SheetTitle("Buddy paused", "Something needs attention")
  Text(state.message, color = BuddyRed, style = MaterialTheme.typography.bodyLarge)
  TextButton(onClick = { onDispatch { recordAgain() } }) { Text("Start over") }
}

@Preview(name = "Sheet · error", showBackground = true, widthDp = 380)
@Composable
private fun ErrorSheetPreview() {
  BuddyPreviewSurface {
    ErrorSheet(
      state =
        SentryBuddySessionState.Error(
          message = "Flow analysis bridge request failed with HTTP 503.",
          previousState = SentryBuddySessionState.Closed,
        ),
      onDispatch = {},
    )
  }
}
