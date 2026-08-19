package io.sentry.android.buddy.ui.bottomsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.model.BuddyInstrumentationStatus
import io.sentry.android.buddy.model.BuddyScreenInstrumentationItem
import io.sentry.android.buddy.model.BuddyScreenScanResult
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.theme.BuddyGold
import io.sentry.android.buddy.ui.common.theme.BuddyGreen
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewScreenScanResult

@Composable
internal fun ScreenInstrumentationSheet(
  result: BuddyScreenScanResult,
  onDismiss: () -> Unit,
  onShowRecommendations: () -> Unit,
) {
  SheetTitle("Screen scan", result.screenName)
  Text(
    "Seer traced the visible host UI and checked which Sentry signals can explain this screen.",
    color = BuddyMuted,
  )
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = BuddyPurple.copy(alpha = 0.08f),
    shape = RoundedCornerShape(16.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Host bounds traced", color = BuddyInk, fontWeight = FontWeight.Bold)
      Text(result.bounds.size.toString(), color = BuddyPurple, fontWeight = FontWeight.Bold)
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    result.instrumentation.forEach { item -> ScreenInstrumentationRow(item) }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedButton(
      modifier = Modifier.weight(1f).height(52.dp),
      onClick = onDismiss,
    ) {
      BuddyButtonText("Close")
    }
    Button(
      modifier = Modifier.weight(1f).height(52.dp),
      colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
      onClick = onShowRecommendations,
    ) {
      BuddyButtonText("Recommendations", color = Color.White)
    }
  }
}

@Composable
internal fun ScreenInstrumentationRow(item: BuddyScreenInstrumentationItem) {
  val color = instrumentationStatusColor(item.status)
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = color.copy(alpha = 0.08f),
    shape = RoundedCornerShape(14.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.label, color = BuddyInk, fontWeight = FontWeight.Bold)
        Text(item.value, color = BuddyMuted, style = MaterialTheme.typography.bodySmall)
      }
      Text(
        item.status.label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

internal val BuddyInstrumentationStatus.label: String
  get() =
    when (this) {
      BuddyInstrumentationStatus.ENABLED -> "Wired"
      BuddyInstrumentationStatus.WARNING -> "Check"
      BuddyInstrumentationStatus.MISSING -> "Missing"
    }

internal fun instrumentationStatusColor(status: BuddyInstrumentationStatus): Color =
  when (status) {
    BuddyInstrumentationStatus.ENABLED -> BuddyGreen
    BuddyInstrumentationStatus.WARNING -> BuddyGold
    BuddyInstrumentationStatus.MISSING -> BuddyRed
  }

@Preview(name = "Sheet · screen scan", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun ScreenInstrumentationSheetPreview() {
  BuddyPreviewSurface {
    ScreenInstrumentationSheet(
      result = previewScreenScanResult,
      onDismiss = {},
      onShowRecommendations = {},
    )
  }
}
