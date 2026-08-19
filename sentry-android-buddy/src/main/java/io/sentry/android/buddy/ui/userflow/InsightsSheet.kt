package io.sentry.android.buddy.ui.userflow

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.model.BuddySentryUiLinks
import io.sentry.android.buddy.model.FlowAction
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.BuddyRecommendationCard
import io.sentry.android.buddy.ui.common.BuddyRecommendationCardStyle
import io.sentry.android.buddy.ui.common.BuddyRecommendationErrorCard
import io.sentry.android.buddy.ui.common.Icons
import io.sentry.android.buddy.ui.common.MetricCard
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.formatElapsed
import io.sentry.android.buddy.ui.common.isOpen
import io.sentry.android.buddy.ui.common.openLinkLabelFor
import io.sentry.android.buddy.ui.common.seerRunUrl
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyGold
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.common.theme.BuddySweatshirtPink
import io.sentry.android.buddy.ui.common.toActionModels
import io.sentry.android.buddy.ui.common.toCardModel
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewAnalysisResponse
import io.sentry.android.buddy.ui.preview.previewFlowAnalysis
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisRequest
import io.sentry.android.buddy.ui.preview.previewRecordingResult
import io.sentry.android.buddy.ui.preview.previewSentryUiLinks

@Composable
internal fun InsightsSheet(
  state: SentryBuddySessionState.Insights,
  sentryUiLinks: BuddySentryUiLinks,
  recommendationError: String?,
  onExecuteFlowAction: (Context, String) -> Unit,
  onExecuteRecommendationAction: (String, String) -> Unit,
  onDismissRecommendation: (String) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val context = LocalContext.current
  val flowName = state.result.recording.flow.name.ifBlank { "Unnamed flow" }
  val traceLink =
    remember(state.result.recording, sentryUiLinks) {
      sentryUiLinks.linkFor(state.result.recording)
    }
  SheetTitle(
    "Flow insights",
    "$flowName • ${formatElapsed(state.result.recording.summary.durationMs)}",
  )
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    MetricCard(
      state.response.recommendations.size.toString(),
      "Insights",
      Modifier.weight(1f),
      BuddyRed,
      showContainer = false,
    )
    MetricCard(
      state.result.recording.summary.screenCount.toString(),
      "Screens",
      Modifier.weight(1f),
      BuddyPurple,
      showContainer = false,
    )
    MetricCard(
      state.result.recording.summary.spanCount.toString(),
      "Spans",
      Modifier.weight(1f),
      BuddyGold,
      showContainer = false,
    )
  }
  FlowActionRow(
    actions =
      state.analysis.actions.toPermaActionModels(
        context = context,
        recordingJson = state.result.recordingJson,
        onExecuteFlowAction = onExecuteFlowAction,
        onOpenUrl = onOpenUrl,
      )
  )
  Text(
    "Recommendations",
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
  )
  recommendationError?.let { BuddyRecommendationErrorCard(it) }
  if (state.response.recommendations.isEmpty()) {
    Surface(
      modifier = Modifier.fillMaxWidth().border(1.dp, BuddyBorder, RoundedCornerShape(12.dp)),
      color = Color.White,
      shape = RoundedCornerShape(12.dp),
    ) {
      Text(
        text = "No recommendations.",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = BuddyInk,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      state.response.recommendations.forEach { recommendation ->
        key(recommendation.id) {
          val seerRunUrl = recommendation.seerRunUrl()
          val primaryLink = seerRunUrl ?: recommendation.link
          BuddyRecommendationCard(
            model = recommendation.toCardModel(),
            modifier =
              primaryLink?.let { link ->
                Modifier.clickable { onOpenUrl(context, link) }
              } ?: Modifier,
            actions =
              if (recommendation.isOpen()) {
                recommendation.actions.toActionModels(
                  onExecute = { actionId ->
                    onExecuteRecommendationAction(recommendation.id, actionId)
                  },
                  onOpenLink = { link -> onOpenUrl(context, link) },
                )
              } else {
                emptyList()
              },
            onDismiss =
              if (recommendation.isOpen()) {
                { onDismissRecommendation(recommendation.id) }
              } else {
                null
              },
            onOpenLink = primaryLink?.let { link -> { onOpenUrl(context, link) } },
            onOpenPerformanceLink = { link -> onOpenUrl(context, link) },
            openLinkLabel = openLinkLabelFor(seerRunUrl),
            detailsLabel = "Details",
            style = BuddyRecommendationCardStyle.ACTION_INBOX,
          )
        }
      }
    }
  }
  if (traceLink != null) {
    OutlinedButton(
      modifier = Modifier.fillMaxWidth().height(56.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = BuddyPurple),
      onClick = { onOpenUrl(context, traceLink) },
    ) {
      BuddyButtonText("Open flow in Sentry", color = BuddyPurple)
    }
  }
}

@Preview(name = "Sheet · insights", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun InsightsSheetPreview() {
  BuddyPreviewSurface {
    InsightsSheet(
      state =
        SentryBuddySessionState.Insights(
          result = previewRecordingResult,
          request = previewFlowAnalysisRequest,
          analysis = previewFlowAnalysis,
          response = previewAnalysisResponse,
        ),
      sentryUiLinks = previewSentryUiLinks,
      recommendationError = null,
      onExecuteFlowAction = { _, _ -> },
      onExecuteRecommendationAction = { _, _ -> },
      onDismissRecommendation = {},
      onOpenUrl = { _, _ -> },
    )
  }
}

@Composable
private fun FlowActionRow(actions: List<BuddyFlowActionModel>) {
  if (actions.isEmpty()) {
    return
  }
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    actions.forEach { action ->
      FlowActionButton(action = action, modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun FlowActionButton(
  action: BuddyFlowActionModel,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(18.dp)
  val background = BuddySweatshirtPink.copy(alpha = if (action.enabled) 0.12f else 0.05f)
  val contentColor = if (action.enabled) BuddySweatshirtPink else BuddyInk.copy(alpha = 0.36f)
  val border = BorderStroke(1.dp, contentColor.copy(alpha = 0.26f))
  Surface(
    modifier =
      modifier
        .height(42.dp)
        .clip(shape)
        .semantics {
          contentDescription = action.label
          role = Role.Button
          if (!action.enabled) {
            disabled()
          }
        }
        .clickable(enabled = action.enabled, onClick = action.onClick),
    color = background,
    shape = shape,
    border = border,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
      Icon(
        imageVector = action.icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(14.dp),
      )
      Text(
        action.visibleLabel,
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Clip,
      )
    }
  }
}

internal data class BuddyFlowActionModel(
  val label: String,
  val visibleLabel: String,
  val icon: ImageVector,
  val enabled: Boolean,
  val onClick: () -> Unit,
)

internal fun List<FlowAction>.toPermaActionModels(
  context: Context,
  recordingJson: String,
  onExecuteFlowAction: (Context, String) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
): List<BuddyFlowActionModel> =
  map { action ->
      val link = action.seerRunUrl ?: action.link
      when (action.id) {
        FLOW_ACTION_SHARE_RECORDING_JSON ->
          BuddyFlowActionModel(
            action.actionLabel,
            action.permaActionVisibleLabel(),
            action.permaActionIcon(),
            enabled = true,
          ) {
            shareRecordingJson(context, recordingJson)
          }

        else ->
          BuddyFlowActionModel(
            action.actionLabel,
            action.permaActionVisibleLabel(),
            action.permaActionIcon(),
            enabled = link != null || action.actionableForSeer,
          ) {
            if (link != null) {
              onOpenUrl(context, link)
            } else if (action.actionableForSeer) {
              onExecuteFlowAction(context, action.id)
            }
          }
      }
    }
    .take(MAX_FLOW_ACTIONS)

private fun FlowAction.permaActionIcon(): ImageVector =
  when (id) {
    FLOW_ACTION_GENERATE_DASHBOARD -> Icons.dashboard
    FLOW_ACTION_GENERATE_MONITORS -> Icons.monitor
    FLOW_ACTION_SHARE_RECORDING_JSON -> Icons.open_in_new
    else -> Icons.open_in_new
  }

private fun FlowAction.permaActionVisibleLabel(): String =
  when (id) {
    FLOW_ACTION_SHARE_RECORDING_JSON -> "Share"
    else -> actionLabel
  }

private fun shareRecordingJson(context: Context, recordingJson: String) {
  val sendIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, recordingJson)
    }
  val chooser = Intent.createChooser(sendIntent, "Share flow JSON")
  if (context !is Activity) {
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  try {
    context.startActivity(chooser)
  } catch (_: ActivityNotFoundException) {
    // Buddy is a debug overlay. A missing share target should not disrupt the recorded flow.
  }
}

private const val FLOW_ACTION_GENERATE_DASHBOARD = "generate-dashboard"
private const val FLOW_ACTION_GENERATE_MONITORS = "generate-monitors"
private const val FLOW_ACTION_SHARE_RECORDING_JSON = "share-recording-json"
private const val MAX_FLOW_ACTIONS = 3
