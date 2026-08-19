package io.sentry.android.buddy.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.model.ActionStatus
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationAction
import io.sentry.android.buddy.model.RecommendationStatus
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyCode
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.theme.BuddyRed
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
  val statusLabel: String? = null,
  val timestampLabel: String? = null,
  val unread: Boolean = false,
)

/** One button of the card: an action of the recommendation, with the label the bridge gave it. */
internal data class BuddyRecommendationActionModel(
  val id: String,
  val label: String,
  val onClick: () -> Unit,
)

internal enum class BuddyRecommendationCardStyle {
  FLOW_INSIGHT,
  ACTION_INBOX,
}

private enum class BuddyRecommendationPillEmphasis {
  PRIMARY,
  SECONDARY,
  GHOST,
}

/**
 * A recommendation card. Every button is optional: pass null, or an empty action list, and the
 * button is left out, so the caller decides what a recommendation can do rather than the card
 * guessing.
 */
@Composable
internal fun BuddyRecommendationCard(
  model: BuddyRecommendationCardModel,
  modifier: Modifier = Modifier,
  actions: List<BuddyRecommendationActionModel> = emptyList(),
  onDismiss: (() -> Unit)? = null,
  onOpenLink: (() -> Unit)? = null,
  openLinkLabel: String = "Open Link",
  detailsLabel: String = "Why this matters",
  style: BuddyRecommendationCardStyle = BuddyRecommendationCardStyle.FLOW_INSIGHT,
) {
  val color = severityColor(model.severity)
  val metadataLabels = model.statusLabel?.split(" • ").orEmpty().filter { it.isNotEmpty() }
  val cardColor =
    if (style == BuddyRecommendationCardStyle.ACTION_INBOX) Color.White
    else color.copy(alpha = 0.08f)
  var detailsExpanded by rememberSaveable(model.title, model.description) { mutableStateOf(false) }
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = cardColor,
    shape = RoundedCornerShape(18.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(color))
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (model.timestampLabel != null || metadataLabels.isNotEmpty()) {
          RecommendationMetadataRow(
            timestampLabel = model.timestampLabel,
            labels = metadataLabels,
            severity = model.severity,
          )
        }
        Text(
          model.title,
          modifier = Modifier.fillMaxWidth(),
          color = BuddyInk,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        RecommendationActionRow(
          actions = actions,
          onOpenLink = onOpenLink,
          openLinkLabel = openLinkLabel,
          onDismiss = onDismiss,
        )
        RecommendationDetailsDisclosure(
          label = detailsLabel,
          description = model.description,
          expanded = detailsExpanded,
          onExpandedChange = { detailsExpanded = it },
        )
      }
    }
  }
}

@Composable
private fun RecommendationMetadataRow(
  timestampLabel: String?,
  labels: List<String>,
  severity: Severity,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (timestampLabel != null) {
      Text(
        timestampLabel,
        modifier = Modifier.weight(1f),
        color = BuddyMuted,
        style = MaterialTheme.typography.labelMedium,
      )
    }
    Row(
      modifier = if (timestampLabel == null) Modifier.fillMaxWidth() else Modifier,
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      labels.forEach { label -> RecommendationMetadataPill(label, metadataColor(label, severity)) }
    }
  }
}

@Composable
private fun RecommendationMetadataPill(label: String, color: Color) {
  Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp)) {
    Text(
      label,
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      color = color,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
    )
  }
}

private fun metadataColor(label: String, severity: Severity): Color =
  when (label) {
    severity.value -> severityColor(severity)
    RecommendationStatus.OPEN.value -> BuddyPurple
    else -> BuddyMuted
  }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationActionRow(
  actions: List<BuddyRecommendationActionModel>,
  onOpenLink: (() -> Unit)?,
  openLinkLabel: String,
  onDismiss: (() -> Unit)?,
) {
  if (actions.isEmpty() && onOpenLink == null && onDismiss == null) {
    return
  }
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    actions.forEachIndexed { index, action ->
      RecommendationActionPill(
        label = action.label,
        onClick = action.onClick,
        emphasis =
          if (index == 0) BuddyRecommendationPillEmphasis.PRIMARY
          else BuddyRecommendationPillEmphasis.SECONDARY,
        showExternalIcon = true,
      )
    }
    onOpenLink?.let {
      RecommendationActionPill(
        label = openLinkLabel,
        onClick = it,
        emphasis =
          if (actions.isEmpty()) BuddyRecommendationPillEmphasis.PRIMARY
          else BuddyRecommendationPillEmphasis.SECONDARY,
        showExternalIcon = true,
      )
    }
    onDismiss?.let {
      RecommendationActionPill(
        label = "Dismiss",
        onClick = it,
        emphasis = BuddyRecommendationPillEmphasis.GHOST,
        showExternalIcon = false,
      )
    }
  }
}

@Composable
private fun RecommendationActionPill(
  label: String,
  onClick: () -> Unit,
  emphasis: BuddyRecommendationPillEmphasis,
  showExternalIcon: Boolean,
) {
  val shape = RoundedCornerShape(22.dp)
  val background =
    when (emphasis) {
      BuddyRecommendationPillEmphasis.PRIMARY -> BuddyPurple
      BuddyRecommendationPillEmphasis.SECONDARY -> BuddyPurple.copy(alpha = 0.10f)
      BuddyRecommendationPillEmphasis.GHOST -> Color.Transparent
    }
  val contentColor =
    when (emphasis) {
      BuddyRecommendationPillEmphasis.PRIMARY -> Color.White
      BuddyRecommendationPillEmphasis.SECONDARY -> BuddyPurple
      BuddyRecommendationPillEmphasis.GHOST -> BuddyMuted
    }
  val border =
    when (emphasis) {
      BuddyRecommendationPillEmphasis.SECONDARY ->
        BorderStroke(1.dp, BuddyPurple.copy(alpha = 0.22f))
      BuddyRecommendationPillEmphasis.GHOST -> BorderStroke(1.dp, BuddyBorder)
      else -> null
    }
  Surface(
    modifier = Modifier.clip(shape).clickable(onClick = onClick),
    color = background,
    shape = shape,
    border = border,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Text(
        label,
        color = contentColor,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
      if (showExternalIcon) {
        Icon(
          imageVector = Icons.open_in_new,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(15.dp),
        )
      }
    }
  }
}

@Composable
private fun RecommendationDetailsDisclosure(
  label: String,
  description: String,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Surface(color = BuddyCode, shape = RoundedCornerShape(12.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          label,
          modifier = Modifier.weight(1f),
          color = BuddyInk,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          if (expanded) "Hide" else "Show",
          color = BuddyPurple,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    AnimatedVisibility(visible = expanded) {
      Text(
        description,
        modifier = Modifier.padding(horizontal = 2.dp),
        color = BuddyMuted,
        style = MaterialTheme.typography.bodyMedium,
      )
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
    statusLabel =
      listOfNotNull(
          pillLabel?.takeIf { showStatus },
          severity.value.takeIf { it.isNotEmpty() },
          status.value.takeIf { it.isNotEmpty() },
        )
        .joinToString(" • "),
  )

internal fun BuddyHomeRecommendation.toCardModel(nowMs: Long): BuddyRecommendationCardModel =
  BuddyRecommendationCardModel(
    title = title,
    description = description,
    severity = severity,
    statusLabel =
      listOfNotNull(
          source.label.takeIf { it.isNotEmpty() },
          severity.value.takeIf { it.isNotEmpty() },
          status.value.takeIf { it.isNotEmpty() },
        )
        .joinToString(" • "),
    timestampLabel = relativeTime(updatedAtMs, nowMs),
    unread = unread && isOpen,
  )

@Composable
internal fun BuddyRecommendationErrorCard(message: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = BuddyRed.copy(alpha = 0.08f),
    shape = RoundedCornerShape(12.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Text(
      message,
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      color = BuddyRed,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
    )
  }
}

/** Open Seer runs get their own button label, so the reader knows where the link goes. */
internal fun openLinkLabelFor(seerRunUrl: String?): String =
  if (seerRunUrl != null) "Open Seer Run" else "Open Link"

/**
 * A link on an action points at something that already exists — a dashboard, a trace, an explore
 * query — so the button opens it rather than asking the bridge to start a Seer run.
 */
internal fun List<RecommendationAction>.toActionModels(
  onExecute: (String) -> Unit,
  onOpenLink: (String) -> Unit,
): List<BuddyRecommendationActionModel> =
  filter { it.status == ActionStatus.OPEN }
    .map { action ->
      BuddyRecommendationActionModel(action.id, action.actionLabel) {
        val link = action.link
        if (link != null) {
          onOpenLink(link)
        } else {
          onExecute(action.id)
        }
      }
    }

/** A dismissed recommendation keeps its card, but offers no buttons any more. */
internal fun Recommendation.isOpen(): Boolean = status == RecommendationStatus.OPEN

/** The newest Seer run of the recommendation, so the card can offer one link to it. */
internal fun Recommendation.seerRunUrl(): String? =
  actions.lastOrNull { it.seerRunUrl != null }?.seerRunUrl

@Preview(name = "Recommendation · flow analysis", showBackground = true, widthDp = 380)
@Composable
private fun BuddyRecommendationCardPreview() {
  BuddyPreviewSurface {
    BuddyRecommendationCard(
      model = previewRecommendation.toCardModel(),
      actions = previewRecommendation.actions.toActionModels(onExecute = {}, onOpenLink = {}),
      onDismiss = {},
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
      actions = previewHomeRecommendation.actions.toActionModels(onExecute = {}, onOpenLink = {}),
      onDismiss = {},
      onOpenLink = {},
    )
  }
}
