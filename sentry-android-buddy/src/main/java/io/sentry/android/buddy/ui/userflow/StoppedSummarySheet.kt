package io.sentry.android.buddy.ui.userflow

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.model.BuddyFlowRecording
import io.sentry.android.buddy.model.BuddySentryUiLinks
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.MetricGrid
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.formatElapsed
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.common.timeline.BuddyTimeline
import io.sentry.android.buddy.ui.common.timeline.toTimelineRow
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewRecordingResult
import io.sentry.android.buddy.ui.preview.previewSentryUiLinks

@Composable
internal fun StoppedSummarySheet(
  state: SentryBuddySessionState.StoppedSummary,
  sentryUiLinks: BuddySentryUiLinks,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val context = LocalContext.current
  val recording = state.result.recording
  SheetTitle("Recording Flow", "Everything stays on device")
  RecordingCard(recording)
  MetricGrid(recording)
  Text("Live Trace", style = MaterialTheme.typography.labelLarge, color = BuddyMuted)
  BuddyTimeline(
    rows =
      recording.timeline.takeLast(TIMELINE_SUMMARY_ROW_LIMIT).mapIndexed { index, item ->
        item.toTimelineRow(id = index.toLong())
      }
  )
  sentryUiLinks.linkFor(recording)?.let { traceLink ->
    OutlinedButton(
      modifier = Modifier.fillMaxWidth().height(52.dp),
      onClick = { onOpenUrl(context, traceLink) },
    ) {
      BuddyButtonText("Open in Sentry")
    }
  }
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddyRed),
    onClick = { onDispatch { briefRecording() } },
  ) {
    BuddyButtonText("Stop and Analyze")
  }
}

@Composable
internal fun RecordingCard(recording: BuddyFlowRecording) {
  Card(
    colors = CardDefaults.cardColors(containerColor = BuddyRed.copy(alpha = 0.10f)),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("●  Recording", color = BuddyRed, fontWeight = FontWeight.Bold)
        Text(
          formatElapsed(recording.summary.durationMs),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
        )
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        listOf(
            18,
            26,
            16,
            34,
            12,
            24,
            28,
            14,
            20,
            38,
            12,
            24,
            22,
            14,
            32,
            18,
            26,
            12,
            22,
            36,
            14,
            20,
            28,
            16,
            24,
          )
          .forEachIndexed { index, height ->
            Box(
              modifier =
                Modifier.size(width = 10.dp, height = height.dp)
                  .background(
                    if (index == 24) BuddyRed else BuddyRed.copy(alpha = 0.30f),
                    RoundedCornerShape(3.dp),
                  )
            )
          }
      }
    }
  }
}

private const val TIMELINE_SUMMARY_ROW_LIMIT = 5

@Preview(name = "Sheet · stopped summary", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun StoppedSummarySheetPreview() {
  BuddyPreviewSurface {
    StoppedSummarySheet(
      state = SentryBuddySessionState.StoppedSummary(previewRecordingResult),
      sentryUiLinks = previewSentryUiLinks,
      onDispatch = {},
      onOpenUrl = { _, _ -> },
    )
  }
}
