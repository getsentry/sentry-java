package io.sentry.android.buddy.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun BuddyButtonText(text: String, color: Color = Color.Unspecified) {
  Text(
    text = text,
    color = color,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
  )
}
