package io.sentry.android.buddy.ui.healthcheck

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.BuddyHealthCheckState
import io.sentry.android.buddy.R
import io.sentry.android.buddy.model.BuddyHealthCheckResponse
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.BuddyRecommendationCard
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.toCardModel
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewRecommendation

@Composable
internal fun HealthCheckActionButton(enabled: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(12.dp)
  Surface(
    modifier =
      Modifier.size(40.dp)
        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
        .clip(shape)
        .clickable(enabled = enabled, onClick = onClick),
    color = Color.Transparent,
    shape = shape,
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Image(
        painter = painterResource(id = R.drawable.health_check_smiley),
        contentDescription = null,
        modifier = Modifier.size(36.dp),
        alpha = if (enabled) 1f else 0.45f,
        contentScale = ContentScale.Fit,
      )
    }
  }
}

@Composable
internal fun HealthCheckDialog(
  state: BuddyHealthCheckState,
  onDismiss: () -> Unit,
  onRetry: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val healthCheckContext = LocalContext.current
  when (state) {
    BuddyHealthCheckState.Hidden -> Unit
    BuddyHealthCheckState.Running ->
      AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Checking Sentry setup", fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              "Buddy is checking your Sentry setup for recommended changes.",
              color = BuddyInk,
            )
            HealthCheckStep("Reading SDK config")
            HealthCheckStep("Checking the bridge for findings")
            HealthCheckStep("Ranking the most useful fixes")
          }
        },
      )

    is BuddyHealthCheckState.Error ->
      AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { BuddyButtonText("Close") } },
        dismissButton = { TextButton(onClick = onRetry) { BuddyButtonText("Try Again") } },
        title = { Text("Health check paused", fontWeight = FontWeight.Bold) },
        text = { Text(state.message, color = BuddyInk) },
      )

    is BuddyHealthCheckState.Results ->
      AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { BuddyButtonText("Close") } },
        dismissButton = { TextButton(onClick = onRetry) { BuddyButtonText("Run Again") } },
        title = { Text("Health check", fontWeight = FontWeight.Bold) },
        text = {
          Column(
            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(healthCheckSummary(state.response.recommendations.size), color = BuddyMuted)
            if (state.response.recommendations.isEmpty()) {
              Surface(
                color = BuddyPurple.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  "Setup looks healthy. Buddy did not find any obvious changes to recommend right now.",
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  color = BuddyInk,
                )
              }
            } else {
              state.response.recommendations.forEach { recommendation ->
                BuddyRecommendationCard(
                  model =
                    recommendation.toCardModel(
                      pillLabel = recommendation.severity.value,
                      showStatus = false,
                    ),
                  onOpenLink =
                    recommendation.link?.let { link -> { onOpenUrl(healthCheckContext, link) } },
                )
              }
            }
          }
        },
      )
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

internal fun healthCheckSummary(count: Int): String =
  if (count == 0) {
    "Buddy did not find any obvious Sentry config changes to recommend."
  } else if (count == 1) {
    "Buddy found 1 recommendation worth checking."
  } else {
    "Buddy found $count recommendations worth checking."
  }

@Preview(name = "Health check · running", showBackground = true, widthDp = 380, heightDp = 320)
@Composable
private fun HealthCheckDialogRunningPreview() {
  BuddyPreviewSurface {
    HealthCheckDialog(
      state = BuddyHealthCheckState.Running,
      onDismiss = {},
      onRetry = {},
      onOpenUrl = { _, _ -> },
    )
  }
}

@Preview(name = "Health check · results", showBackground = true, widthDp = 380, heightDp = 520)
@Composable
private fun HealthCheckDialogResultsPreview() {
  BuddyPreviewSurface {
    HealthCheckDialog(
      state =
        BuddyHealthCheckState.Results(
          BuddyHealthCheckResponse(recommendations = listOf(previewRecommendation))
        ),
      onDismiss = {},
      onRetry = {},
      onOpenUrl = { _, _ -> },
    )
  }
}

/** An empty response is the good outcome, so the dialog congratulates instead of listing. */
@Preview(name = "Health check · all clear", showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun HealthCheckDialogAllClearPreview() {
  BuddyPreviewSurface {
    HealthCheckDialog(
      state = BuddyHealthCheckState.Results(BuddyHealthCheckResponse()),
      onDismiss = {},
      onRetry = {},
      onOpenUrl = { _, _ -> },
    )
  }
}

@Preview(name = "Health check · error", showBackground = true, widthDp = 380, heightDp = 320)
@Composable
private fun HealthCheckDialogErrorPreview() {
  BuddyPreviewSurface {
    HealthCheckDialog(
      state = BuddyHealthCheckState.Error("Health check bridge request failed with HTTP 503."),
      onDismiss = {},
      onRetry = {},
      onOpenUrl = { _, _ -> },
    )
  }
}
