package io.sentry.android.buddy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple

@Composable
internal fun SheetTitle(
  title: String,
  subtitle: String,
  subtitleContent: (@Composable () -> Unit)? = null,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier.size(44.dp).background(BuddyPurple, RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
    ) {
      SentryBuddyGlyph(tint = Color.White, modifier = Modifier.size(26.dp))
    }
    Column {
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      if (subtitleContent != null) {
        subtitleContent()
      } else {
        Text(subtitle, color = BuddyMuted, style = MaterialTheme.typography.bodyMedium)
      }
    }
    Spacer(Modifier.weight(1f))
    trailingContent?.invoke()
  }
}
