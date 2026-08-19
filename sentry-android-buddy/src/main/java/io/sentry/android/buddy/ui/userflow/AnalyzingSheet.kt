package io.sentry.android.buddy.ui.userflow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.ui.common.BuddyWavyProgress
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.formatElapsed
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisRequest
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisSubmission
import io.sentry.android.buddy.ui.preview.previewRecordingResult

@Composable
internal fun AnalyzingSheet(state: SentryBuddySessionState.Analyzing) {
  SheetTitle(
    "Analyzing Flow",
    "Duration: ${formatElapsed(state.result.recording.summary.durationMs)}",
  )
  Text(
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center,
    fontStyle = FontStyle.Italic,
    text = "Hold tight. Seer is having a look at your flow.",
  )
  BuddyWavyProgress(modifier = Modifier.padding(16.dp))
  Text(
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center,
    color = BuddyMuted,
    style = MaterialTheme.typography.bodyMedium,
    text = "Zap some bugs while you wait.",
  )
  BuddyZapABug()
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
