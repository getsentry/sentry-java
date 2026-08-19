package io.sentry.android.buddy.ui.bottomsheet

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.model.BuddySentryUiLinks
import io.sentry.android.buddy.ui.common.BuddyLabelPill
import io.sentry.android.buddy.ui.common.relativeTime
import io.sentry.android.buddy.ui.common.theme.BuddyAttentionCardHeight
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.severityColor
import io.sentry.android.buddy.ui.common.timeline.BuddyTimeline
import io.sentry.android.buddy.ui.common.timeline.title
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.PREVIEW_NOW_MS
import io.sentry.android.buddy.ui.preview.previewLiveFeed
import io.sentry.android.buddy.ui.preview.previewSentryUiLinks
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun AttentionCard(
  modifier: Modifier = Modifier,
  liveFeed: BuddyLiveFeed,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  emptyArtIndex: Int,
  onDismiss: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val item = liveFeed.latestUnviewedAdverseItem
  val dismissOffset = remember(item?.id) { Animatable(0f) }

  if (item == null) {
    Box(modifier = modifier.fillMaxWidth().height(BuddyAttentionCardHeight)) {
      EmptyAttentionArt(
        index = emptyArtIndex,
        modifier = Modifier.fillMaxSize(),
      )
    }
    return
  }

  val color = severityColor(item.severity)
  val context = LocalContext.current
  val link = sentryUiLinks.linkFor(item)
  val dismissScope = rememberCoroutineScope()
  val contentAlpha = remember(item.id) { Animatable(0f) }
  LaunchedEffect(item.id) {
    contentAlpha.snapTo(0f)
    contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = 260))
  }
  Box(modifier = modifier.fillMaxWidth().height(BuddyAttentionCardHeight)) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxSize().pointerInput(item.id) {
          detectDragGestures(
            onDragEnd = {
              val dismissDistance = size.width.toFloat()
              val shouldDismiss = abs(dismissOffset.value) > dismissDistance * 0.35f
              dismissScope.launch {
                if (shouldDismiss) {
                  coroutineScope {
                    launch {
                      dismissOffset.animateTo(
                        -dismissDistance,
                        animationSpec = tween(durationMillis = 280),
                      )
                    }
                    launch {
                      contentAlpha.animateTo(0f, animationSpec = tween(durationMillis = 440))
                    }
                  }
                  onDismiss()
                } else {
                  contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = 180))
                  dismissOffset.animateTo(0f)
                }
              }
            },
            onDragCancel = {
              dismissScope.launch {
                contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = 180))
                dismissOffset.animateTo(0f)
              }
            },
          ) { change, dragAmount ->
            change.consume()
            val dismissDistance = size.width.toFloat()
            val nextOffset = (dismissOffset.value + dragAmount.x).coerceIn(-dismissDistance, 0f)
            dismissScope.launch { dismissOffset.snapTo(nextOffset) }
          }
        }
    ) {
      val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
      val swipeProgress = (-dismissOffset.value / widthPx).coerceIn(0f, 1f)
      val artAlpha =
        if (swipeProgress > 0f) {
          ((swipeProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        } else {
          1f - contentAlpha.value
        }
      Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = artAlpha }) {
        EmptyAttentionArt(
          index = emptyArtIndex,
          modifier = Modifier.fillMaxSize(),
        )
      }
      Box(
        modifier =
          Modifier.matchParentSize()
            .graphicsLayer { alpha = contentAlpha.value }
            .offset { IntOffset(dismissOffset.value.roundToInt(), 0) }
            .clickable(enabled = link != null) { link?.let { onOpenUrl(context, it) } }
      ) {
        AttentionItemContent(
          item = item,
          liveFeed = liveFeed,
          color = color,
          nowMs = nowMs,
          backgroundColor = color.copy(alpha = 0.10f),
          modifier = Modifier.matchParentSize(),
        )
      }
    }
  }
}

@Composable
internal fun AttentionItemContent(
  item: BuddyLiveFeedItem,
  liveFeed: BuddyLiveFeed,
  color: Color,
  nowMs: Long,
  backgroundColor: Color,
  modifier: Modifier = Modifier,
) {
  // Performance issues earn the extra detail: a headline, a hero stat and the surrounding trace.
  // Everything else keeps the compact shape.
  val performance = item.isPerformanceIssue()
  AttentionCardBackground(modifier = modifier, backgroundColor = backgroundColor) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(if (performance) 12.dp else 10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "Needs attention",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = BuddyInk,
        )
        Text(
          relativeTime(item.timestamp.time, nowMs),
          color = BuddyMuted,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Normal,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BuddyLabelPill(item.category.label, color)
        if (performance) {
          item.performanceSourceLabel()?.let { AttentionSourcePill(it) }
        }
      }
      if (performance) {
        Text(
          item.performanceHeadline(),
          modifier = Modifier.fillMaxWidth(),
          color = BuddyInk,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        item.title(),
        modifier = Modifier.fillMaxWidth(),
        color = BuddyInk,
        style =
          if (performance) MaterialTheme.typography.bodyLarge
          else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
      )
      if (performance) {
        item.performancePrimaryStat()?.let { stat -> PerformanceHeroStatCard(stat, color) }
        PerformanceContextCards(item, color)
        Text(
          item.performanceNarrative(liveFeed),
          color = BuddyMuted,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Normal,
        )
        AttentionTimeline(item, liveFeed, nowMs)
      } else {
        item.screenContextText()?.let { screenContext ->
          Text(
            screenContext,
            color = BuddyMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
          )
        }
        AttentionCountChips(liveFeed)
      }
    }
  }
}

@Composable
internal fun AttentionCardBackground(
  modifier: Modifier = Modifier,
  backgroundColor: Color,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxWidth().background(backgroundColor).clipToAttentionCard()) {
    content()
  }
}

internal fun Modifier.clipToAttentionCard(): Modifier = this.clip(RoundedCornerShape(20.dp))

@Composable
internal fun AttentionTimeline(item: BuddyLiveFeedItem, liveFeed: BuddyLiveFeed, nowMs: Long) {
  val rows = attentionTimelineRows(item, liveFeed.items, nowMs)
  if (rows.isEmpty()) {
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      "Live trace around the issue",
      color = BuddyMuted,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
    )
    BuddyTimeline(rows)
  }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun AttentionCardPreview() {
  BuddyPreviewSurface {
    AttentionCard(
      liveFeed = previewLiveFeed,
      sentryUiLinks = previewSentryUiLinks,
      nowMs = PREVIEW_NOW_MS,
      emptyArtIndex = 0,
      onDismiss = {},
      onOpenUrl = { _, _ -> },
    )
  }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun AttentionTimelinePreview() {
  BuddyPreviewSurface {
    AttentionTimeline(previewLiveFeed.items.first(), previewLiveFeed, PREVIEW_NOW_MS)
  }
}
