package io.sentry.android.buddy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import io.sentry.android.buddy.model.RecommendationAction
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
) {
  val color = severityColor(model.severity)
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = color.copy(alpha = 0.08f),
    shape = RoundedCornerShape(16.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          model.title,
          modifier = Modifier.weight(1f),
          color = BuddyInk,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(model.description, color = BuddyMuted)

      Column(modifier = Modifier.offset(x = (-8).dp)) {
        if (actions.isNotEmpty()) {
          actions.forEach { action ->
            TextButton(
              contentPadding = PaddingValues(8.dp),
              onClick = action.onClick,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                BuddyButtonText(action.label)
                Icon(
                  imageVector = Icons.open_in_new,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }

          onOpenLink?.let {
            TextButton(
              contentPadding = PaddingValues(8.dp),
              onClick = it,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                BuddyButtonText(openLinkLabel)
                Icon(
                  imageVector = Icons.open_in_new,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
          onDismiss?.let {
            TextButton(
              contentPadding = PaddingValues(8.dp),
              onClick = it,
            ) {
              BuddyButtonText("Dismiss")
            }
          }
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
): List<BuddyRecommendationActionModel> = map { action ->
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
