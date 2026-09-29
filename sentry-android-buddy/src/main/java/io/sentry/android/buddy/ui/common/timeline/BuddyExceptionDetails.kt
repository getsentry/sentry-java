package io.sentry.android.buddy.ui.common.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sentry.android.buddy.model.BuddyExceptionFrame
import io.sentry.android.buddy.model.BuddyExceptionReport
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewExceptionReport

private const val COLLAPSED_FRAME_LIMIT = 5

/**
 * The message and stack frames of a captured exception, shown under its live feed row. Tapping a
 * frame asks the host to open its source in Android Studio.
 */
@Composable
internal fun BuddyExceptionDetails(
  report: BuddyExceptionReport,
  onFrameClick: (BuddyExceptionFrame) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by rememberSaveable(report.id) { mutableStateOf(false) }
  val visibleFrames = if (expanded) report.frames else report.frames.take(COLLAPSED_FRAME_LIMIT)
  val hiddenFrameCount = report.frames.size - visibleFrames.size

  Column(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
    report.value
      ?.takeIf { it.isNotBlank() }
      ?.let { Text(text = it, color = BuddyInk, style = MaterialTheme.typography.bodyMedium) }
    if (report.frames.isNotEmpty()) {
      Text(
        text = "Tap a frame to open its source in Android Studio",
        color = BuddyMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
      )
    }
    visibleFrames.forEach { frame -> FrameRow(frame, onFrameClick) }
    if (hiddenFrameCount > 0) {
      Text(
        text = "Show $hiddenFrameCount more frames",
        color = BuddyPurple,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun FrameRow(frame: BuddyExceptionFrame, onFrameClick: (BuddyExceptionFrame) -> Unit) {
  Text(
    text = frame.display,
    color = if (frame.inApp) BuddyPurple else BuddyInk.copy(alpha = 0.55f),
    fontWeight = if (frame.inApp) FontWeight.SemiBold else FontWeight.Normal,
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    modifier = Modifier.fillMaxWidth().clickable { onFrameClick(frame) }.padding(vertical = 6.dp),
  )
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun BuddyExceptionDetailsPreview() {
  BuddyPreviewSurface { BuddyExceptionDetails(previewExceptionReport, onFrameClick = {}) }
}
