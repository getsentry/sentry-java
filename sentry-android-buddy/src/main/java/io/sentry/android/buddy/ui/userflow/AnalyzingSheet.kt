package io.sentry.android.buddy.ui.userflow

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisRequest
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisSubmission
import io.sentry.android.buddy.ui.preview.previewRecordingResult

@Composable
internal fun AnalyzingSheet(
  @Suppress("UNUSED_PARAMETER") state: SentryBuddySessionState.Analyzing
) {
  BuddyZapABug(modifier = Modifier.padding(top = 12.dp))
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
