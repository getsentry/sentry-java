package io.sentry.android.buddy.ui.userflow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.BuildConfig
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface

@Composable
internal fun IntroSheet(onStartRecording: () -> Unit) {
  SheetTitle("Sentry Buddy", "v${BuildConfig.VERSION_NAME}")
  Text(
    "Record a flow",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
    onClick = onStartRecording,
  ) {
    BuddyButtonText("Start Recording")
  }
  Text(
    "The panel closes so you can navigate freely. Tap the bubble to stop.",
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center,
    color = BuddyMuted,
  )
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun IntroSheetPreview() {
  BuddyPreviewSurface { IntroSheet(onStartRecording = {}) }
}
