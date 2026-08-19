package io.sentry.android.buddy.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.R
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.TransientRecordingEvent
import io.sentry.android.buddy.model.BuddyFlowIntent
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.ui.common.constrain
import io.sentry.android.buddy.ui.common.formatElapsed
import io.sentry.android.buddy.ui.common.theme.BuddyAccentBubbleChonk
import io.sentry.android.buddy.ui.common.theme.BuddyAccentBubbleEnd
import io.sentry.android.buddy.ui.common.theme.BuddyAccentBubbleShadow
import io.sentry.android.buddy.ui.common.theme.BuddyAccentBubbleStart
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleFaceLift
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleFaceSize
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleInitialTop
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleMargin
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleSize
import io.sentry.android.buddy.ui.common.theme.BuddyBubbleTouchPadding
import io.sentry.android.buddy.ui.common.theme.BuddyErrorBubbleChonk
import io.sentry.android.buddy.ui.common.theme.BuddyErrorBubbleEnd
import io.sentry.android.buddy.ui.common.theme.BuddyErrorBubbleShadow
import io.sentry.android.buddy.ui.common.theme.BuddyErrorBubbleStart
import io.sentry.android.buddy.ui.common.theme.BuddyFabQuoteEstimatedHeight
import io.sentry.android.buddy.ui.common.theme.BuddyFabQuoteGap
import io.sentry.android.buddy.ui.common.theme.BuddyFabQuoteTextWidth
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyRecordingBubbleChonk
import io.sentry.android.buddy.ui.common.theme.BuddyRecordingBubbleEnd
import io.sentry.android.buddy.ui.common.theme.BuddyRecordingBubbleShadow
import io.sentry.android.buddy.ui.common.theme.BuddyRecordingBubbleStart
import io.sentry.android.buddy.ui.common.theme.BuddyRecordingRingSize
import io.sentry.android.buddy.ui.common.theme.BuddyRed
import io.sentry.android.buddy.ui.common.theme.BuddyTransientTextHeight
import io.sentry.android.buddy.ui.common.theme.BuddyTransientTextWidth
import io.sentry.android.buddy.ui.common.theme.BuddyWarningBubbleChonk
import io.sentry.android.buddy.ui.common.theme.BuddyWarningBubbleEnd
import io.sentry.android.buddy.ui.common.theme.BuddyWarningBubbleShadow
import io.sentry.android.buddy.ui.common.theme.BuddyWarningBubbleStart
import io.sentry.android.buddy.ui.common.theme.severityColor
import io.sentry.android.buddy.ui.preview.PREVIEW_NOW_MS
import io.sentry.android.buddy.ui.preview.previewAnalysisResponse
import io.sentry.android.buddy.ui.preview.previewBusyLiveFeed
import io.sentry.android.buddy.ui.preview.previewEmptyLiveFeed
import io.sentry.android.buddy.ui.preview.previewFlowAnalysis
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisRequest
import io.sentry.android.buddy.ui.preview.previewFlowAnalysisSubmission
import io.sentry.android.buddy.ui.preview.previewRecordingResult
import io.sentry.android.buddy.ui.preview.previewSevereLiveFeed
import io.sentry.android.buddy.ui.preview.previewUnreadLiveFeed
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
internal fun BoxScope.BuddyBubble(
  state: SentryBuddySessionState,
  liveFeed: BuddyLiveFeed,
  nowMs: Long,
  maxWidthPx: Float,
  maxHeightPx: Float,
  bubbleHitBounds: BuddyOverlayHitBounds?,
  transientEvent: TransientRecordingEvent?,
  onClick: () -> Unit,
) {
  val density = LocalDensity.current
  val bubbleSizePx = with(density) { BuddyBubbleSize.toPx() }
  val bubbleMarginPx = with(density) { BuddyBubbleMargin.toPx() }
  val initialTopPx = with(density) { BuddyBubbleInitialTop.toPx() }
  val isRecording = state is SentryBuddySessionState.Recording
  val showQuote = state is SentryBuddySessionState.Closed
  val attentionItem = liveFeed.latestUnviewedAdverseItem
  val attentionColor = attentionItem?.let { severityColor(it.severity) }
  val bubbleGlyphState =
    when {
      isRecording -> BuddyBubbleGlyphState.RECORDING
      state is SentryBuddySessionState.Analyzing -> BuddyBubbleGlyphState.ANALYZING
      state is SentryBuddySessionState.Insights -> BuddyBubbleGlyphState.INSIGHTS_READY
      attentionItem?.severity == Severity.HIGH -> BuddyBubbleGlyphState.SEVERE
      liveFeed.unviewedAdverseCount > 0 -> BuddyBubbleGlyphState.UNREAD
      else -> BuddyBubbleGlyphState.IDLE
    }
  val isSevere = bubbleGlyphState == BuddyBubbleGlyphState.SEVERE
  val badgeText =
    when {
      isRecording -> null
      isSevere -> "!"
      liveFeed.unviewedAdverseCount > 0 ->
        if (liveFeed.unviewedAdverseCount > 9) "9+" else liveFeed.unviewedAdverseCount.toString()

      else -> null
    }
  val bubblePalette =
    buddyBubblePalette(isRecording = isRecording, attentionSeverity = attentionItem?.severity)
  var bubbleOffset by remember { mutableStateOf<Offset?>(null) }

  fun defaultOffset(): Offset =
    Offset(
      x = maxWidthPx - bubbleSizePx - bubbleMarginPx,
      y = initialTopPx,
    )

  fun Offset.constrain(): Offset =
    Offset(
      x = x.constrain(bubbleMarginPx, maxWidthPx - bubbleSizePx - bubbleMarginPx),
      y = y.constrain(bubbleMarginPx, maxHeightPx - bubbleSizePx - bubbleMarginPx),
    )

  LaunchedEffect(maxWidthPx, maxHeightPx) {
    bubbleOffset = (bubbleOffset ?: defaultOffset()).constrain()
  }

  val resolvedOffset = (bubbleOffset ?: defaultOffset()).constrain()
  val transientHeightPx = with(density) { BuddyTransientTextHeight.toPx() }
  val showTransientAbove = resolvedOffset.y > transientHeightPx + bubbleMarginPx
  val elapsed =
    if (state is SentryBuddySessionState.Recording) {
      formatElapsed(nowMs - state.startedAtMs)
    } else {
      null
    }

  Column(
    modifier =
      Modifier.offset { IntOffset(resolvedOffset.x.roundToInt(), resolvedOffset.y.roundToInt()) }
        .onGloballyPositioned { coordinates ->
          val position = coordinates.positionInRoot()
          val touchPaddingPx = with(density) { BuddyBubbleTouchPadding.toPx() }.roundToInt()
          bubbleHitBounds?.update(
            left = position.x.roundToInt() - touchPaddingPx,
            top = position.y.roundToInt() - touchPaddingPx,
            right = position.x.roundToInt() + coordinates.size.width + touchPaddingPx,
            bottom = position.y.roundToInt() + coordinates.size.height + touchPaddingPx,
          )
        },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier.size(64.dp),
      contentAlignment = Alignment.Center,
    ) {
      if (isRecording) {
        BuddyBubbleAnimatedDrawable(
          drawableRes = R.drawable.avd_buddy_recording_ring,
          modifier = Modifier.size(BuddyRecordingRingSize).align(Alignment.Center),
        )
      }
      Box(
        modifier =
          Modifier.size(BuddyBubbleSize)
            .shadow(10.dp, CircleShape)
            .pointerInput(maxWidthPx, maxHeightPx) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                bubbleOffset = ((bubbleOffset ?: resolvedOffset) + dragAmount).constrain()
              }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier.size(BuddyBubbleFaceSize).background(bubblePalette.shadowBrush, CircleShape)
        )
        Box(
          modifier =
            Modifier.size(BuddyBubbleFaceSize)
              .offset(y = BuddyBubbleFaceLift)
              .background(bubblePalette.faceBrush, CircleShape)
              .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
        )
        BuddyBubbleGlyph(state = bubbleGlyphState)
      }
      if (badgeText != null) {
        BubbleNotificationBadge(
          count = badgeText,
          color = if (isSevere) BuddyRed else attentionColor ?: BuddyRed,
          modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
        )
      }
    }
    elapsed?.let {
      Text(
        text = it,
        color = BuddyInk,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
  TransientRecordingText(
    event = transientEvent,
    bubbleOffset = resolvedOffset,
    maxWidthPx = maxWidthPx,
    bubbleSizePx = bubbleSizePx,
    showAbove = showTransientAbove,
  )
  BuddyQuoteText(
    visible = showQuote,
    bubbleOffset = resolvedOffset,
    maxWidthPx = maxWidthPx,
    bubbleSizePx = bubbleSizePx,
  )
}

internal data class BuddyBubblePalette(val shadowBrush: Brush, val faceBrush: Brush)

internal fun buddyBubblePalette(
  isRecording: Boolean,
  attentionSeverity: Severity?,
): BuddyBubblePalette {
  val shadowColors =
    when {
      isRecording -> listOf(BuddyRecordingBubbleChonk, BuddyRecordingBubbleShadow)
      attentionSeverity == Severity.HIGH -> listOf(BuddyErrorBubbleChonk, BuddyErrorBubbleShadow)
      attentionSeverity == Severity.MEDIUM ->
        listOf(BuddyWarningBubbleChonk, BuddyWarningBubbleShadow)

      else -> listOf(BuddyAccentBubbleChonk, BuddyAccentBubbleShadow)
    }
  val faceColors =
    when {
      isRecording -> listOf(BuddyRecordingBubbleStart, BuddyRecordingBubbleEnd)
      attentionSeverity == Severity.HIGH -> listOf(BuddyErrorBubbleStart, BuddyErrorBubbleEnd)
      attentionSeverity == Severity.MEDIUM -> listOf(BuddyWarningBubbleStart, BuddyWarningBubbleEnd)
      else -> listOf(BuddyAccentBubbleStart, BuddyAccentBubbleEnd)
    }
  return BuddyBubblePalette(
    shadowBrush = Brush.linearGradient(colors = shadowColors),
    faceBrush = Brush.linearGradient(colors = faceColors),
  )
}

@Composable
internal fun BoxScope.TransientRecordingText(
  event: TransientRecordingEvent?,
  bubbleOffset: Offset,
  maxWidthPx: Float,
  bubbleSizePx: Float,
  showAbove: Boolean,
) {
  var visible by remember { mutableStateOf(false) }
  var text by remember { mutableStateOf("") }
  val density = LocalDensity.current
  val textWidthPx = with(density) { BuddyTransientTextWidth.toPx() }
  val textHeightPx = with(density) { BuddyTransientTextHeight.toPx() }
  val x =
    (bubbleOffset.x + bubbleSizePx / 2f - textWidthPx / 2f).constrain(
      0f,
      maxWidthPx - textWidthPx,
    )
  val y =
    if (showAbove) {
      bubbleOffset.y - textHeightPx
    } else {
      bubbleOffset.y + bubbleSizePx + with(density) { 8.dp.toPx() }
    }

  LaunchedEffect(event?.id) {
    val currentEvent = event
    if (currentEvent == null) {
      visible = false
      return@LaunchedEffect
    }
    text = currentEvent.text
    visible = false
    delay(40)
    visible = true
    delay(1400)
    visible = false
  }

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
  ) {
    Text(
      text = text,
      modifier = Modifier.width(BuddyTransientTextWidth),
      color = BuddyInk,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
internal fun BoxScope.BuddyQuoteText(
  visible: Boolean,
  bubbleOffset: Offset,
  maxWidthPx: Float,
  bubbleSizePx: Float,
) {
  if (!visible) {
    return
  }
  var quoteIndex by remember {
    mutableStateOf(Random.nextInt(BuddyFabQuotes.size))
  }
  var showQuote by remember { mutableStateOf(true) }
  var quoteHeightPx by remember { mutableStateOf(0f) }
  val density = LocalDensity.current
  val textWidthPx = with(density) { BuddyFabQuoteTextWidth.toPx() }
  val quoteGapPx = with(density) { BuddyFabQuoteGap.toPx() }
  val estimatedQuoteHeightPx = with(density) { BuddyFabQuoteEstimatedHeight.toPx() }
  val quoteSide =
    if (bubbleOffset.x + bubbleSizePx / 2f > maxWidthPx / 2f) {
      BuddyQuoteBubbleSide.LEFT_OF_FAB
    } else {
      BuddyQuoteBubbleSide.RIGHT_OF_FAB
    }
  val x =
    when (quoteSide) {
      BuddyQuoteBubbleSide.LEFT_OF_FAB ->
        (bubbleOffset.x + bubbleSizePx - textWidthPx).constrain(0f, maxWidthPx - textWidthPx)

      BuddyQuoteBubbleSide.RIGHT_OF_FAB -> bubbleOffset.x.constrain(0f, maxWidthPx - textWidthPx)
    }
  val resolvedQuoteHeightPx = if (quoteHeightPx > 0f) quoteHeightPx else estimatedQuoteHeightPx
  val y = (bubbleOffset.y - resolvedQuoteHeightPx - quoteGapPx).coerceAtLeast(0f)

  LaunchedEffect(Unit) {
    while (true) {
      showQuote = true
      delay(BUDDY_FAB_QUOTE_VISIBLE_MS)
      showQuote = false
      delay(BUDDY_FAB_QUOTE_INTERVAL_MS - BUDDY_FAB_QUOTE_VISIBLE_MS)
      quoteIndex = nextRandomBuddyFabQuoteIndex(quoteIndex)
    }
  }

  AnimatedVisibility(
    visible = showQuote,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
  ) {
    Text(
      text = BuddyFabQuotes[quoteIndex],
      modifier =
        Modifier.width(BuddyFabQuoteTextWidth).onGloballyPositioned { coordinates ->
          quoteHeightPx = coordinates.size.height.toFloat()
        },
      color = BuddyInk,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      fontStyle = FontStyle.Italic,
      textAlign = TextAlign.Center,
      maxLines = 4,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

internal enum class BuddyQuoteBubbleSide {
  LEFT_OF_FAB,
  RIGHT_OF_FAB,
}

private fun nextRandomBuddyFabQuoteIndex(currentIndex: Int): Int {
  if (BuddyFabQuotes.size <= 1) {
    return 0
  }
  var nextIndex = currentIndex
  while (nextIndex == currentIndex) {
    nextIndex = Random.nextInt(BuddyFabQuotes.size)
  }
  return nextIndex
}

// The bubble positions itself against the overlay bounds, so previews hand it a fixed-size frame
// and let it settle into the lower-right corner exactly as it does on a real screen.
private val BuddyBubblePreviewWidth = 320.dp
private val BuddyBubblePreviewHeight = 200.dp

@Composable
private fun BuddyBubblePreviewFrame(
  state: SentryBuddySessionState,
  liveFeed: BuddyLiveFeed = previewEmptyLiveFeed,
  transientEvent: TransientRecordingEvent? = null,
) {
  val density = LocalDensity.current
  val widthPx = with(density) { BuddyBubblePreviewWidth.toPx() }
  val heightPx = with(density) { BuddyBubblePreviewHeight.toPx() }
  MaterialTheme {
    Box(
      modifier =
        Modifier.size(width = BuddyBubblePreviewWidth, height = BuddyBubblePreviewHeight)
          .background(Color.White)
    ) {
      BuddyBubble(
        state = state,
        liveFeed = liveFeed,
        nowMs = PREVIEW_NOW_MS,
        maxWidthPx = widthPx,
        maxHeightPx = heightPx,
        bubbleHitBounds = null,
        transientEvent = transientEvent,
        onClick = {},
      )
    }
  }
}

/** Nothing to report: the accent palette plus the rotating quote the bubble shows when idle. */
@Preview(name = "Bubble · idle", showBackground = true)
@Composable
private fun BuddyBubbleIdlePreview() {
  BuddyBubblePreviewFrame(state = SentryBuddySessionState.Closed)
}

@Preview(name = "Bubble · unread signals", showBackground = true)
@Composable
private fun BuddyBubbleUnreadPreview() {
  BuddyBubblePreviewFrame(
    state = SentryBuddySessionState.Closed,
    liveFeed = previewUnreadLiveFeed,
  )
}

@Preview(name = "Bubble · severe", showBackground = true)
@Composable
private fun BuddyBubbleSeverePreview() {
  BuddyBubblePreviewFrame(
    state = SentryBuddySessionState.Closed,
    liveFeed = previewSevereLiveFeed,
  )
}

@Preview(name = "Bubble · badge overflow", showBackground = true)
@Composable
private fun BuddyBubbleBusyPreview() {
  BuddyBubblePreviewFrame(state = SentryBuddySessionState.Closed, liveFeed = previewBusyLiveFeed)
}

/** Recording swaps in the red palette, the pulsing ring and the elapsed timer. */
@Preview(name = "Bubble · recording", showBackground = true)
@Composable
private fun BuddyBubbleRecordingPreview() {
  BuddyBubblePreviewFrame(
    state =
      SentryBuddySessionState.Recording(
        intent = BuddyFlowIntent(name = "Sign in"),
        startedAtMs = PREVIEW_NOW_MS - 92_000,
      ),
    transientEvent = TransientRecordingEvent(id = 1, text = "Screen: LoginActivity"),
  )
}

@Preview(name = "Bubble · analyzing", showBackground = true)
@Composable
private fun BuddyBubbleAnalyzingPreview() {
  BuddyBubblePreviewFrame(
    state =
      SentryBuddySessionState.Analyzing(
        result = previewRecordingResult,
        request = previewFlowAnalysisRequest,
        submission = previewFlowAnalysisSubmission,
      )
  )
}

@Preview(name = "Bubble · insights ready", showBackground = true)
@Composable
private fun BuddyBubbleInsightsPreview() {
  BuddyBubblePreviewFrame(
    state =
      SentryBuddySessionState.Insights(
        result = previewRecordingResult,
        request = previewFlowAnalysisRequest,
        analysis = previewFlowAnalysis,
        response = previewAnalysisResponse,
      )
  )
}
