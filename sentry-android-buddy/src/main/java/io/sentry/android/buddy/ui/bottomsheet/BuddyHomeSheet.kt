package io.sentry.android.buddy.ui.bottomsheet

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.BuddyHealthCheckState
import io.sentry.android.buddy.BuildConfig
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.BuddyHomeTab
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddySentryUiLinks
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.BuddyRecommendationCard
import io.sentry.android.buddy.ui.common.BuddyRecommendationCardStyle
import io.sentry.android.buddy.ui.common.BuddyRecommendationErrorCard
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.openLinkLabelFor
import io.sentry.android.buddy.ui.common.theme.BuddyCode
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddySeerSweatshirtPink
import io.sentry.android.buddy.ui.common.theme.BuddySheetHorizontalPadding
import io.sentry.android.buddy.ui.common.theme.LIVE_FEED_VISIBLE_ITEM_LIMIT
import io.sentry.android.buddy.ui.common.timeline.BuddyTimeline
import io.sentry.android.buddy.ui.common.timeline.toTimelineRow
import io.sentry.android.buddy.ui.common.toActionModels
import io.sentry.android.buddy.ui.common.toCardModel
import io.sentry.android.buddy.ui.healthcheck.HealthCheckStatusCard
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.PREVIEW_NOW_MS
import io.sentry.android.buddy.ui.preview.previewEmptyLiveFeed
import io.sentry.android.buddy.ui.preview.previewHomeRecommendation
import io.sentry.android.buddy.ui.preview.previewLiveFeed
import io.sentry.android.buddy.ui.preview.previewSentryUiLinks

@Composable
internal fun BuddyHomeSheet(
  liveFeed: BuddyLiveFeed,
  healthCheckState: BuddyHealthCheckState,
  homeTab: BuddyHomeTab,
  homeRecommendations: List<BuddyHomeRecommendation>,
  recommendationError: String?,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onStartRecording: () -> Unit,
  onExecuteHomeRecommendationAction: (String, String) -> Unit,
  onDismissHomeRecommendation: (String) -> Unit,
  onMarkHomeRecommendationRead: (String) -> Unit,
  onSelectHomeTab: (BuddyHomeTab) -> Unit,
  onRunHealthCheck: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val unreadRecommendations = homeRecommendations.count { it.isAttentionDriving && it.unread }
  val emptyAttentionArtIndex = remember { EmptyAttentionArtIndex.next() }
  LiveFeedInset {
    SheetTitle(title = "Sentry Buddy", subtitle = "v${BuildConfig.VERSION_NAME}")
    HomeTabRow(
      selectedTab = homeTab,
      unreadRecommendationCount = unreadRecommendations,
      onSelect = onSelectHomeTab,
    )
  }
  AnimatedContent(
    targetState = homeTab,
    transitionSpec = {
      val slideDirection = if (targetState.ordinal > initialState.ordinal) 1 else -1
      (slideInHorizontally(
          animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
          initialOffsetX = { fullWidth -> (fullWidth / 5) * slideDirection },
        ) + fadeIn(animationSpec = tween(durationMillis = 180)))
        .togetherWith(
          slideOutHorizontally(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> -(fullWidth / 5) * slideDirection },
          ) + fadeOut(animationSpec = tween(durationMillis = 180))
        )
    },
    label = "buddy-home-tab-content",
  ) { currentTab ->
    when (currentTab) {
      BuddyHomeTab.LIVE_FEED ->
        LiveFeedTabContent(
          liveFeed = liveFeed,
          sentryUiLinks = sentryUiLinks,
          nowMs = nowMs,
          emptyArtIndex = emptyAttentionArtIndex,
          onDispatch = onDispatch,
          onOpenUrl = onOpenUrl,
        )

      BuddyHomeTab.ACTIONS ->
        LiveFeedInset {
          RecommendationsTabContent(
            recommendations = homeRecommendations,
            healthCheckState = healthCheckState,
            recommendationError = recommendationError,
            nowMs = nowMs,
            onExecuteAction = onExecuteHomeRecommendationAction,
            onDismiss = onDismissHomeRecommendation,
            onMarkRead = onMarkHomeRecommendationRead,
            onRetryHealthCheck = onRunHealthCheck,
            onOpenUrl = onOpenUrl,
          )
        }

      BuddyHomeTab.RECORD_FLOW ->
        LiveFeedInset { RecordFlowTabContent(onStartRecording = onStartRecording) }
    }
  }
}

@Composable
internal fun HomeTabRow(
  selectedTab: BuddyHomeTab,
  unreadRecommendationCount: Int,
  onSelect: (BuddyHomeTab) -> Unit,
) {

  Surface(color = BuddyCode, shape = RoundedCornerShape(16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      BuddyHomeTab.entries.forEach { tab ->
        val isSelected = tab == selectedTab
        val label =
          when (tab) {
            BuddyHomeTab.LIVE_FEED -> "Live Feed"
            BuddyHomeTab.ACTIONS ->
              if (unreadRecommendationCount > 0) {
                "Actions ($unreadRecommendationCount)"
              } else {
                "Actions"
              }

            BuddyHomeTab.RECORD_FLOW -> "Analyze"
          }
        Box(
          modifier =
            Modifier.weight(1f)
              .padding(4.dp)
              .background(
                if (isSelected) Color.White else Color.Transparent,
                RoundedCornerShape(16.dp),
              )
              .clip(RoundedCornerShape(16.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
              ) {
                onSelect(tab)
              },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            textAlign = TextAlign.Center,
            color = if (isSelected) BuddyInk else BuddyMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
internal fun LiveFeedTabContent(
  liveFeed: BuddyLiveFeed,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  emptyArtIndex: Int,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    AttentionCard(
      liveFeed = liveFeed,
      sentryUiLinks = sentryUiLinks,
      nowMs = nowMs,
      emptyArtIndex = emptyArtIndex,
      onDismiss = { onDispatch { dismissLiveFeedAttention() } },
      onOpenUrl = onOpenUrl,
    )
    LiveFeedInset {
      Text(
        "Live feed",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = BuddyInk,
      )
      if (liveFeed.items.isEmpty()) {
        EmptyLiveFeedCard()
      } else {
        val context = LocalContext.current
        BuddyTimeline(
          rows =
            liveFeed.items.take(LIVE_FEED_VISIBLE_ITEM_LIMIT).map { item ->
              item.toTimelineRow(nowMs = nowMs, link = sentryUiLinks.linkFor(item))
            },
          showOverflowEllipsis = liveFeed.items.size > LIVE_FEED_VISIBLE_ITEM_LIMIT,
          onRowClick = { row -> row.link?.let { onOpenUrl(context, it) } },
        )
      }
    }
  }
}

@Composable
internal fun RecommendationsTabContent(
  recommendations: List<BuddyHomeRecommendation>,
  healthCheckState: BuddyHealthCheckState,
  recommendationError: String?,
  nowMs: Long,
  onExecuteAction: (String, String) -> Unit,
  onDismiss: (String) -> Unit,
  onMarkRead: (String) -> Unit,
  onRetryHealthCheck: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  Text(
    "Recommended Actions",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  val context = LocalContext.current
  val showEmptyRecommendationsCard =
    recommendations.isEmpty() &&
      (healthCheckState !is BuddyHealthCheckState.Results ||
        healthCheckState.response.recommendations.isNotEmpty())
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    HealthCheckStatusCard(state = healthCheckState, onRetry = onRetryHealthCheck)
    recommendationError?.let { BuddyRecommendationErrorCard(it) }
    if (showEmptyRecommendationsCard) {
      Card(border = CardDefaults.outlinedCardBorder()) {
        Text(
          "No recommendations yet.",
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          color = BuddyMuted,
        )
      }
    } else {
      recommendations.forEach { recommendation ->
        key(recommendation.id) {
          val primaryLink = recommendation.seerRunUrl ?: recommendation.primaryLink
          BuddyRecommendationCard(
            model = recommendation.toCardModel(nowMs),
            modifier =
              Modifier.clickable {
                onMarkRead(recommendation.id)
                primaryLink?.let { onOpenUrl(context, it) }
              },
            actions =
              if (recommendation.isOpen) {
                recommendation.actions.toActionModels(
                  onExecute = { actionId ->
                    onMarkRead(recommendation.id)
                    onExecuteAction(recommendation.id, actionId)
                  },
                  onOpenLink = { link ->
                    onMarkRead(recommendation.id)
                    onOpenUrl(context, link)
                  },
                )
              } else {
                emptyList()
              },
            onDismiss = if (recommendation.isOpen) ({ onDismiss(recommendation.id) }) else null,
            onOpenLink =
              primaryLink?.let { link ->
                {
                  onMarkRead(recommendation.id)
                  onOpenUrl(context, link)
                }
              },
            openLinkLabel = openLinkLabelFor(recommendation.seerRunUrl),
            detailsLabel = "Details",
            style = BuddyRecommendationCardStyle.ACTION_INBOX,
          )
        }
      }
    }
  }
}

@Composable
internal fun RecordFlowTabContent(onStartRecording: () -> Unit) {
  Text(
    "See what’s happening under the hood.",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  Text(
    "Record a user flow that's important to your app and Buddy will help you auto-generate dashboards, monitors, and other useful things!",
    color = BuddyMuted,
  )
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddySeerSweatshirtPink),
    onClick = onStartRecording,
  ) {
    BuddyButtonText("Start Recording")
  }
  Text(
    "The panel closes so you can navigate freely. Tap the bubble to stop and review the captured flow.",
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center,
    color = BuddyMuted,
  )
}

@Composable
internal fun LiveFeedInset(content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = BuddySheetHorizontalPadding),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    content = content,
  )
}

@Composable
private fun BuddyHomeSheetPreviewFrame(
  homeTab: BuddyHomeTab,
  liveFeed: BuddyLiveFeed = previewLiveFeed,
  homeRecommendations: List<BuddyHomeRecommendation> = listOf(previewHomeRecommendation),
) {
  BuddyPreviewSurface {
    BuddyHomeSheet(
      liveFeed = liveFeed,
      healthCheckState = BuddyHealthCheckState.Hidden,
      homeTab = homeTab,
      homeRecommendations = homeRecommendations,
      recommendationError = null,
      sentryUiLinks = previewSentryUiLinks,
      nowMs = PREVIEW_NOW_MS,
      onDispatch = {},
      onStartRecording = {},
      onExecuteHomeRecommendationAction = { _, _ -> },
      onDismissHomeRecommendation = {},
      onMarkHomeRecommendationRead = {},
      onSelectHomeTab = {},
      onRunHealthCheck = {},
      onOpenUrl = { _, _ -> },
    )
  }
}

@Preview(name = "Home · live feed", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun BuddyHomeSheetLiveFeedPreview() {
  BuddyHomeSheetPreviewFrame(BuddyHomeTab.LIVE_FEED)
}

/** No adverse signals yet, so the attention card falls back to its empty artwork. */
@Preview(name = "Home · live feed empty", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun BuddyHomeSheetLiveFeedEmptyPreview() {
  BuddyHomeSheetPreviewFrame(BuddyHomeTab.LIVE_FEED, liveFeed = previewEmptyLiveFeed)
}

@Preview(name = "Home · recommendations", showBackground = true, widthDp = 380, heightDp = 600)
@Composable
private fun BuddyHomeSheetRecommendationsPreview() {
  BuddyHomeSheetPreviewFrame(BuddyHomeTab.ACTIONS)
}

@Preview(name = "Home · recommendations empty", showBackground = true, widthDp = 380)
@Composable
private fun BuddyHomeSheetRecommendationsEmptyPreview() {
  BuddyHomeSheetPreviewFrame(BuddyHomeTab.ACTIONS, homeRecommendations = emptyList())
}

@Preview(name = "Home · record flow", showBackground = true, widthDp = 380, heightDp = 500)
@Composable
private fun BuddyHomeSheetRecordFlowPreview() {
  BuddyHomeSheetPreviewFrame(BuddyHomeTab.RECORD_FLOW)
}

@Composable
internal fun EmptyLiveFeedCard() {
  Card(border = CardDefaults.outlinedCardBorder()) {
    Text(
      "Navigate through the app and Buddy will show screens, manual steps, errors, and slow or failed work here.",
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      color = BuddyMuted,
    )
  }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun EmptyLiveFeedCardPreview() {
  BuddyPreviewSurface { EmptyLiveFeedCard() }
}
