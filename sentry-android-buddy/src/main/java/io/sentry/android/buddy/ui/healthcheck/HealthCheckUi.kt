package io.sentry.android.buddy.ui.healthcheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.BuddyHealthCheckState
import io.sentry.android.buddy.model.BuddyHealthCheckResponse
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface

@Composable
internal fun HealthCheckStatusCard(state: BuddyHealthCheckState, onRetry: () -> Unit) {
  when (state) {
    BuddyHealthCheckState.Hidden -> Unit
    BuddyHealthCheckState.Running ->
      HealthCheckMessageCard(
        title = "Checking Sentry setup",
        description = "Buddy is running a quick health check in the background.",
      ) {
        HealthCheckStep("Reading SDK config")
        HealthCheckStep("Checking the bridge for findings")
        HealthCheckStep("Ranking the most useful fixes")
      }

    is BuddyHealthCheckState.Error ->
      HealthCheckMessageCard(
        title = "Health check paused",
        description = state.message,
      ) {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
          onClick = onRetry,
        ) {
          BuddyButtonText("Try Again")
        }
      }

    is BuddyHealthCheckState.Results ->
      if (state.response.recommendations.isEmpty()) {
        HealthCheckMessageCard(
          title = "Health check complete",
          description = healthCheckSummary(state.response),
        )
      }
  }
}

@Composable
private fun HealthCheckMessageCard(
  title: String,
  description: String,
  content: @Composable (() -> Unit)? = null,
) {
  Surface(color = BuddyPurple.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp)) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(title, color = BuddyInk, fontWeight = FontWeight.Bold)
        Text(description, color = BuddyMuted)
      }
      content?.invoke()
    }
  }
}

@Composable
internal fun HealthCheckStep(text: String) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(10.dp).background(BuddyPurple, CircleShape))
    Text(text, color = BuddyInk)
  }
}

private fun healthCheckSummary(response: BuddyHealthCheckResponse): String =
  if (response.recommendations.isEmpty()) {
    "Buddy did not find any obvious Sentry config changes to recommend."
  } else if (response.recommendations.size == 1) {
    "Buddy found 1 recommendation worth checking."
  } else {
    "Buddy found ${response.recommendations.size} recommendations worth checking."
  }

@Preview(name = "Health check · running", showBackground = true, widthDp = 380)
@Composable
private fun HealthCheckStatusCardRunningPreview() {
  BuddyPreviewSurface { HealthCheckStatusCard(state = BuddyHealthCheckState.Running, onRetry = {}) }
}

@Preview(name = "Health check · all clear", showBackground = true, widthDp = 380)
@Composable
private fun HealthCheckStatusCardAllClearPreview() {
  BuddyPreviewSurface {
    HealthCheckStatusCard(
      state = BuddyHealthCheckState.Results(BuddyHealthCheckResponse()),
      onRetry = {},
    )
  }
}

@Preview(name = "Health check · error", showBackground = true, widthDp = 380)
@Composable
private fun HealthCheckStatusCardErrorPreview() {
  BuddyPreviewSurface {
    HealthCheckStatusCard(
      state = BuddyHealthCheckState.Error("Health check bridge request failed with HTTP 503."),
      onRetry = {},
    )
  }
}
