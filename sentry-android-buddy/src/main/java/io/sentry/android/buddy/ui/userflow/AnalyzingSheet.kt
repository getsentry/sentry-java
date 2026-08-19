package io.sentry.android.buddy.ui.userflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.formatElapsed
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisRequest
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisSubmission
import io.sentry.android.buddy.ui.preview.previewRecordingResult

@Composable
internal fun AnalyzingSheet(state: SentryBuddySessionState.Analyzing) {
  SheetTitle("Analyzing", "Flow • ${formatElapsed(state.result.recording.summary.durationMs)}")
  Text(
    "Flow Analysis Submitted",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
  )
  listOf(
      "POST /v1/flow-analysis accepted",
      "GET /v1/flow-analysis/${state.submission.flowId}",
      "Building flow recommendations",
      "Waiting for completion",
    )
    .forEachIndexed { index, label ->
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(24.dp)
              .background(if (index < 3) BuddyPurple else Color.White, CircleShape)
              .border(1.dp, if (index < 3) BuddyPurple else BuddyBorder, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Text(if (index < 3) "✓" else "", color = Color.White)
        }
        Text(label, color = if (index < 3) BuddyInk else BuddyMuted)
      }
    }
  HorizontalDivider()
  Text("This runs on device. Nothing leaves the phone until you send it.", color = BuddyMuted)
}

@Preview(name = "Sheet · analyzing", showBackground = true, widthDp = 380, heightDp = 500)
@Composable
private fun AnalyzingSheetPreview() {
  BuddyPreviewSurface {
    AnalyzingSheet(
      SentryBuddySessionState.Analyzing(
        result = previewRecordingResult,
        request = previewFlowAnalysisRequest,
        submission = previewFlowAnalysisSubmission,
      )
    )
  }
}
