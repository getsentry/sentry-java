package io.sentry.android.buddy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationStatus
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.theme.severityColor
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.PREVIEW_NOW_MS
import io.sentry.android.buddy.ui.preview.previewHomeRecommendation
import io.sentry.android.buddy.ui.preview.previewRecommendation

/**
 * What a recommendation looks like once it is ready to draw. Flow analysis, the home list and the
 * health check all reduce their own type to this, so one card renders every one of them.
 */
internal data class BuddyRecommendationCardModel(
  val title: String,
  val description: String,
  val severity: Severity,
  /** Pill text for the header. Null draws a plain severity dot instead. */
  val pillLabel: String? = null,
  val statusLabel: String? = null,
  val timestampLabel: String? = null,
  val unread: Boolean = false,
)

/**
 * A recommendation card. Every action is optional: pass null and the button is left out, so the
 * caller decides what a recommendation can do rather than the card guessing.
 */
@Composable
internal fun BuddyRecommendationCard(
  model: BuddyRecommendationCardModel,
  modifier: Modifier = Modifier,
  onResolve: (() -> Unit)? = null,
  onDismiss: (() -> Unit)? = null,
  onOpenLink: (() -> Unit)? = null,
  openLinkLabel: String = "Open Link",
) {
  val color = severityColor(model.severity)
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = color.copy(alpha = 0.08f),
    shape = RoundedCornerShape(14.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (model.pillLabel != null) {
          BuddyLabelPill(model.pillLabel, color)
        } else {
          Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        }
        Text(
          model.title,
          modifier = Modifier.weight(1f),
          color = BuddyInk,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (model.unread) {
          Box(modifier = Modifier.size(12.dp).background(BuddyPurple, CircleShape))
        }
      }
      Text(model.description, color = BuddyMuted)
      if (model.timestampLabel != null || model.statusLabel != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            model.timestampLabel.orEmpty(),
            color = BuddyMuted,
            style = MaterialTheme.typography.labelMedium,
          )
          model.statusLabel?.let {
            Text(
              it,
              color = BuddyPurple,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
      if (onResolve != null || onDismiss != null || onOpenLink != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          onResolve?.let { OutlinedButton(onClick = it) { BuddyButtonText("Resolve") } }
          onDismiss?.let { TextButton(onClick = it) { BuddyButtonText("Dismiss") } }
          onOpenLink?.let { TextButton(onClick = it) { BuddyButtonText(openLinkLabel) } }
        }
      }
    }
  }
}

/** Flow analysis and health check recommendations. [pillLabel] is null for a plain severity dot. */
internal fun Recommendation.toCardModel(
  pillLabel: String? = null,
  showStatus: Boolean = true,
): BuddyRecommendationCardModel =
  BuddyRecommendationCardModel(
    title = title,
    description = description,
    severity = severity,
    pillLabel = pillLabel,
    statusLabel = if (showStatus) "${severity.value} • ${status.value}" else null,
  )

internal fun BuddyHomeRecommendation.toCardModel(nowMs: Long): BuddyRecommendationCardModel =
  BuddyRecommendationCardModel(
    title = title,
    description = description,
    severity = severity,
    pillLabel = source.label,
    statusLabel = "${severity.value} • ${status.value}",
    timestampLabel = relativeTime(updatedAtMs, nowMs),
    unread = unread && isOpen,
  )

/** Open Seer runs get their own button label, so the reader knows where the link goes. */
internal fun openLinkLabelFor(seerRunUrl: String?): String =
  if (seerRunUrl != null) "Open Seer Run" else "Open Link"

internal fun Recommendation.isResolvableNow(): Boolean =
  resolvable && status == RecommendationStatus.OPEN

@Preview(name = "Recommendation · flow analysis", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardPreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model = previewRecommendation.toCardModel(),
      onResolve = {},
      onOpenLink = {},
    )
  }
}

@Preview(name = "Recommendation · health check", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardHealthCheckPreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model =
        previewRecommendation.toCardModel(
          pillLabel = previewRecommendation.severity.value,
          showStatus = false,
        ),
      onOpenLink = {},
    )
  }
}

@Preview(name = "Recommendation · home list", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardHomePreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model = previewHomeRecommendation.toCardModel(PREVIEW_NOW_MS),
      onResolve = {},
      onDismiss = {},
      onOpenLink = {},
    )
  }
}
