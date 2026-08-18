package io.sentry.android.buddy

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Composable
public fun SentryBuddyOverlay(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  SentryBuddyOverlay(
    modifier = modifier,
    controller = rememberSentryBuddySessionController(),
    content = content,
  )
}

@ApiStatus.Experimental
@Composable
public fun rememberSentryBuddySessionController(): SentryBuddySessionController = remember {
  SentryBuddySessionController()
}

@ApiStatus.Experimental
@Composable
public fun SentryBuddyOverlay(
  modifier: Modifier = Modifier,
  controller: SentryBuddySessionController,
  content: @Composable BoxScope.() -> Unit,
) {
  SentryBuddyOverlayContent(
    modifier = modifier,
    controller = controller,
    bubbleHitBounds = null,
    content = content,
  )
}

@Composable
internal fun SentryBuddyInstalledOverlay(
  controller: SentryBuddySessionController,
  bubbleHitBounds: BuddyOverlayHitBounds,
) {
  SentryBuddyOverlayContent(
    controller = controller,
    bubbleHitBounds = bubbleHitBounds,
    content = {},
  )
}

@Composable
private fun SentryBuddyOverlayContent(
  modifier: Modifier = Modifier,
  controller: SentryBuddySessionController,
  bubbleHitBounds: BuddyOverlayHitBounds?,
  content: @Composable BoxScope.() -> Unit,
) {
  var state by remember { mutableStateOf(controller.state) }
  var liveFeed by remember { mutableStateOf(controller.liveFeed) }
  var healthCheckState by remember { mutableStateOf(controller.healthCheckState) }
  var homeTab by remember { mutableStateOf(controller.homeTab) }
  var homeRecommendations by remember { mutableStateOf(controller.homeRecommendations) }
  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  var transientRecordingEvent by remember { mutableStateOf<TransientRecordingEvent?>(null) }
  val transientRecordingEventScope = rememberCoroutineScope()
  val analysisScope = rememberCoroutineScope()

  fun syncUiState() {
    state = controller.state
    liveFeed = controller.liveFeed
    healthCheckState = controller.healthCheckState
    homeTab = controller.homeTab
    homeRecommendations = controller.homeRecommendations
    nowMs = System.currentTimeMillis()
  }

  fun dispatch(action: SentryBuddySessionController.() -> Unit) {
    controller.action()
    syncUiState()
  }

  fun dispatchAnalysis(action: SentryBuddySessionController.() -> Unit) {
    analysisScope.launch {
      withContext(Dispatchers.IO) { controller.action() }
      syncUiState()
    }
  }

  fun dispatchHealthCheck(action: SentryBuddySessionController.() -> Unit) {
    analysisScope.launch {
      withContext(Dispatchers.IO) { controller.action() }
      syncUiState()
    }
  }

  fun openUrl(context: Context, url: String) {
    analysisScope.launch { withContext(Dispatchers.IO) { controller.openUrl(context, url) } }
  }

  LaunchedEffect(state) {
    if (state !is SentryBuddySessionState.Closed) {
      while (true) {
        nowMs = System.currentTimeMillis()
        delay(1000)
      }
    }
  }

  DisposableEffect(controller) {
    val removeListener = controller.addTransientRecordingEventListener { event ->
      transientRecordingEventScope.launch { transientRecordingEvent = event }
    }
    onDispose { removeListener() }
  }

  DisposableEffect(controller) {
    val removeListener = controller.addLiveFeedListener { feed ->
      transientRecordingEventScope.launch {
        liveFeed = feed
        homeRecommendations = controller.homeRecommendations
      }
    }
    onDispose { removeListener() }
  }

  LaunchedEffect(state) {
    if (state !is SentryBuddySessionState.Recording) {
      transientRecordingEvent = null
    }
  }

  LaunchedEffect(state) {
    if (state is SentryBuddySessionState.Analyzing) {
      val deadlineMs = System.currentTimeMillis() + ANALYSIS_TIMEOUT_MS
      while (
        state is SentryBuddySessionState.Analyzing && System.currentTimeMillis() < deadlineMs
      ) {
        delay(ANALYSIS_POLL_INTERVAL_MS)
        withContext(Dispatchers.IO) { controller.pollFlowAnalysis() }
        state = controller.state
      }
      if (state is SentryBuddySessionState.Analyzing) {
        controller.timeoutFlowAnalysis()
        state = controller.state
      }
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val maxWidthPx = with(density) { maxWidth.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    content()
    BuddyBubble(
      state = state,
      liveFeed = liveFeed,
      nowMs = nowMs,
      maxWidthPx = maxWidthPx,
      maxHeightPx = maxHeightPx,
      bubbleHitBounds = bubbleHitBounds,
      transientEvent = transientRecordingEvent,
      onClick = {
        when (state) {
          SentryBuddySessionState.Closed -> dispatch { openLiveFeed() }
          is SentryBuddySessionState.Recording ->
            dispatch {
              stopRecording()
              briefRecording()
            }
          else -> dispatch { close() }
        }
      },
      onLongClick = {},
    )
    BuddySheet(
      state = state,
      liveFeed = liveFeed,
      healthCheckState = healthCheckState,
      homeTab = homeTab,
      homeRecommendations = homeRecommendations,
      sentryUiLinks = controller.sentryUiLinks,
      nowMs = nowMs,
      onDispatch = { dispatch(it) },
      onAnalyze = { dispatchAnalysis { analyze() } },
      onResolveRecommendation = { recommendationId ->
        dispatchAnalysis { resolveRecommendation(recommendationId) }
      },
      onResolveHomeRecommendation = { recommendationId ->
        dispatchAnalysis { resolveHomeRecommendation(recommendationId) }
      },
      onDismissHomeRecommendation = { recommendationId ->
        dispatch { dismissHomeRecommendation(recommendationId) }
      },
      onMarkHomeRecommendationRead = { recommendationId ->
        dispatch { markHomeRecommendationRead(recommendationId) }
      },
      onSelectHomeTab = { tab -> dispatch { selectHomeTab(tab) } },
      onRunHealthCheck = { dispatchHealthCheck { runHealthCheck() } },
      onDismissHealthCheck = { dispatch { dismissHealthCheck() } },
      onOpenUrl = { context, url -> openUrl(context, url) },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.BuddyBubble(
  state: SentryBuddySessionState,
  liveFeed: BuddyLiveFeed,
  nowMs: Long,
  maxWidthPx: Float,
  maxHeightPx: Float,
  bubbleHitBounds: BuddyOverlayHitBounds?,
  transientEvent: TransientRecordingEvent?,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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

private data class BuddyBubblePalette(val shadowBrush: Brush, val faceBrush: Brush)

private fun buddyBubblePalette(
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
private fun BoxScope.TransientRecordingText(
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

internal class BuddyOverlayHitBounds {
  private val lock = Any()
  private var bounds: Rect? = null

  fun update(left: Int, top: Int, right: Int, bottom: Int) {
    synchronized(lock) { bounds = Rect(left, top, right, bottom) }
  }

  fun contains(x: Float, y: Float): Boolean =
    synchronized(lock) { bounds?.contains(x.roundToInt(), y.roundToInt()) == true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuddySheet(
  state: SentryBuddySessionState,
  liveFeed: BuddyLiveFeed,
  healthCheckState: BuddyHealthCheckState,
  homeTab: BuddyHomeTab,
  homeRecommendations: List<BuddyHomeRecommendation>,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onAnalyze: () -> Unit,
  onResolveRecommendation: (String) -> Unit,
  onResolveHomeRecommendation: (String) -> Unit,
  onDismissHomeRecommendation: (String) -> Unit,
  onMarkHomeRecommendationRead: (String) -> Unit,
  onSelectHomeTab: (BuddyHomeTab) -> Unit,
  onRunHealthCheck: () -> Unit,
  onDismissHealthCheck: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  if (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) {
    return
  }
  val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.75f
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sheetScope = rememberCoroutineScope()
  fun startRecordingAfterSheetExit() {
    sheetScope.launch {
      sheetState.hide()
      onDispatch { startRecording() }
    }
  }

  ModalBottomSheet(
    onDismissRequest = { onDispatch { close() } },
    sheetState = sheetState,
    containerColor = Color.White,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    val sheetBodyModifier =
      if (state == SentryBuddySessionState.LiveFeed) {
        Modifier.fillMaxWidth().height(maxSheetHeight)
      } else {
        Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)
      }
    Column(
      modifier =
        sheetBodyModifier
          .verticalScroll(rememberScrollState())
          .padding(
            horizontal = if (state is SentryBuddySessionState.LiveFeed) 0.dp else 24.dp,
            vertical = 24.dp,
          ),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when (state) {
        SentryBuddySessionState.LiveFeed ->
          BuddyHomeSheet(
            liveFeed,
            healthCheckState,
            homeTab,
            homeRecommendations,
            sentryUiLinks,
            nowMs,
            onDispatch,
            ::startRecordingAfterSheetExit,
            onResolveHomeRecommendation,
            onDismissHomeRecommendation,
            onMarkHomeRecommendationRead,
            onSelectHomeTab,
            onRunHealthCheck,
            onDismissHealthCheck,
            onOpenUrl,
          )
        SentryBuddySessionState.Intro -> IntroSheet(::startRecordingAfterSheetExit)
        is SentryBuddySessionState.StoppedSummary ->
          StoppedSummarySheet(state, sentryUiLinks, onDispatch, onOpenUrl)
        is SentryBuddySessionState.Briefing -> BriefingSheet(state, onDispatch, onAnalyze)
        is SentryBuddySessionState.Analyzing -> AnalyzingSheet(state)
        is SentryBuddySessionState.Insights ->
          InsightsSheet(state, sentryUiLinks, onDispatch, onResolveRecommendation, onOpenUrl)
        is SentryBuddySessionState.Error -> ErrorSheet(state, onDispatch)
        is SentryBuddySessionState.Recording,
        SentryBuddySessionState.Closed -> Unit
      }
    }
  }
}

@Composable
private fun BoxScope.BuddyQuoteText(
  visible: Boolean,
  bubbleOffset: Offset,
  maxWidthPx: Float,
  bubbleSizePx: Float,
) {
  if (!visible) {
    return
  }
  var quoteIndex by remember {
    mutableStateOf((System.currentTimeMillis() % BuddyFabQuotes.size).toInt())
  }
  var showQuote by remember { mutableStateOf(true) }
  val density = LocalDensity.current
  val textWidthPx = with(density) { BuddyFabQuoteTextWidth.toPx() }
  val x =
    (bubbleOffset.x + bubbleSizePx / 2f - textWidthPx / 2f).constrain(
      0f,
      maxWidthPx - textWidthPx,
    )
  val y = bubbleOffset.y + bubbleSizePx + with(density) { 10.dp.toPx() }

  LaunchedEffect(Unit) {
    while (true) {
      showQuote = true
      delay(BUDDY_FAB_QUOTE_VISIBLE_MS)
      showQuote = false
      delay(BUDDY_FAB_QUOTE_INTERVAL_MS - BUDDY_FAB_QUOTE_VISIBLE_MS)
      quoteIndex = (quoteIndex + 1) % BuddyFabQuotes.size
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
      modifier = Modifier.width(BuddyFabQuoteTextWidth),
      color = BuddyInk.copy(alpha = 0.82f),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Normal,
      textAlign = TextAlign.Center,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private enum class BuddyBubbleGlyphState {
  IDLE,
  UNREAD,
  ANALYZING,
  INSIGHTS_READY,
  SEVERE,
  RECORDING,
}

@Composable
private fun BuddyBubbleAnimatedDrawable(drawableRes: Int, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  AndroidView(
    factory = { viewContext ->
      AppCompatImageView(viewContext).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
      }
    },
    modifier = modifier,
    update = { imageView -> imageView.bindBuddyDrawable(context, drawableRes) },
  )
}

@Composable
private fun BuddyBubbleGlyph(state: BuddyBubbleGlyphState) {
  val context = LocalContext.current
  Box(
    modifier = Modifier.size(BuddyBubbleGlyphSize).clip(CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    AndroidView(
      factory = { viewContext ->
        AppCompatImageView(viewContext).apply {
          scaleType = ImageView.ScaleType.FIT_CENTER
          importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
      },
      modifier = Modifier.fillMaxSize(),
      update = { imageView -> imageView.bindBuddyBubbleGlyph(context, state) },
    )
  }
}

@Composable
private fun BubbleNotificationBadge(count: String, color: Color, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier.size(22.dp).background(color, CircleShape).border(2.dp, Color.White, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = count,
      color = Color.White,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
  }
}

private fun ImageView.bindBuddyBubbleGlyph(context: Context, state: BuddyBubbleGlyphState) {
  val drawableRes =
    when (state) {
      BuddyBubbleGlyphState.IDLE -> R.drawable.avd_buddy_idle
      BuddyBubbleGlyphState.UNREAD -> R.drawable.avd_buddy_unread
      BuddyBubbleGlyphState.ANALYZING -> R.drawable.avd_buddy_analyzing
      BuddyBubbleGlyphState.INSIGHTS_READY -> R.drawable.avd_buddy_ready
      BuddyBubbleGlyphState.SEVERE -> R.drawable.avd_buddy_severe
      BuddyBubbleGlyphState.RECORDING -> R.drawable.ic_buddy_recording
    }
  bindBuddyDrawable(context, drawableRes, loopIdle = state == BuddyBubbleGlyphState.IDLE)
}

private fun ImageView.bindBuddyDrawable(
  context: Context,
  drawableRes: Int,
  loopIdle: Boolean = false,
) {
  val currentTag = tag as? Int
  if (currentTag == drawableRes) {
    return
  }
  tag = drawableRes
  val nextDrawable = AppCompatResources.getDrawable(context, drawableRes)?.mutate()
  setImageDrawable(nextDrawable)
  restartBuddyBubbleAnimation(nextDrawable, loopIdle = loopIdle)
}

private fun restartBuddyBubbleAnimation(drawable: Drawable?, loopIdle: Boolean) {
  when (drawable) {
    is AnimatedVectorDrawableCompat -> drawable.restart(loopIdle = loopIdle)
    is AnimatedVectorDrawable -> drawable.restart(loopIdle = loopIdle)
  }
}

private fun AnimatedVectorDrawableCompat.restart(loopIdle: Boolean) {
  clearAnimationCallbacks()
  if (loopIdle) {
    registerAnimationCallback(
      object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
          start()
        }
      }
    )
  }
  stop()
  start()
}

private fun AnimatedVectorDrawable.restart(loopIdle: Boolean) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    clearAnimationCallbacks()
    if (loopIdle) {
      registerAnimationCallback(
        object : Animatable2.AnimationCallback() {
          override fun onAnimationEnd(drawable: Drawable?) {
            start()
          }
        }
      )
    }
  }
  stop()
  start()
}

@Composable
private fun SheetTitle(
  title: String,
  subtitle: String,
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
      Text(subtitle, color = BuddyMuted, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.weight(1f))
    trailingContent?.invoke()
  }
}

@Composable
private fun IntroSheet(onStartRecording: () -> Unit) {
  SheetTitle("Sentry Buddy", "v${BuildConfig.VERSION_NAME}")
  Text(
    "Record a flow",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
    onClick = onStartRecording,
  ) {
    BuddyButtonText("Start Recording")
  }
  Text(
    "The panel closes so you can navigate freely. Tap the bubble to stop.",
    modifier = Modifier.fillMaxWidth(),
    textAlign = TextAlign.Center,
    color = BuddyMuted,
  )
}

@Composable
private fun BuddyHomeSheet(
  liveFeed: BuddyLiveFeed,
  healthCheckState: BuddyHealthCheckState,
  homeTab: BuddyHomeTab,
  homeRecommendations: List<BuddyHomeRecommendation>,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onStartRecording: () -> Unit,
  onResolveHomeRecommendation: (String) -> Unit,
  onDismissHomeRecommendation: (String) -> Unit,
  onMarkHomeRecommendationRead: (String) -> Unit,
  onSelectHomeTab: (BuddyHomeTab) -> Unit,
  onRunHealthCheck: () -> Unit,
  onDismissHealthCheck: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val unreadRecommendations = homeRecommendations.count { it.isOpen && it.unread }
  val emptyAttentionArtIndex = remember { EmptyAttentionArtIndex.next() }
  LiveFeedInset {
    SheetTitle(
      title = "Sentry Buddy",
      subtitle = "v${BuildConfig.VERSION_NAME}",
      trailingContent = {
        HealthCheckActionButton(
          enabled = healthCheckState !is BuddyHealthCheckState.Running,
          onClick = onRunHealthCheck,
        )
      },
    )
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
      BuddyHomeTab.RECOMMENDATIONS ->
        LiveFeedInset {
          RecommendationsTabContent(
            recommendations = homeRecommendations,
            nowMs = nowMs,
            onResolve = onResolveHomeRecommendation,
            onDismiss = onDismissHomeRecommendation,
            onMarkRead = onMarkHomeRecommendationRead,
            onOpenUrl = onOpenUrl,
          )
        }
      BuddyHomeTab.RECORD_FLOW ->
        LiveFeedInset { RecordFlowTabContent(onStartRecording = onStartRecording) }
    }
  }
  HealthCheckDialog(
    state = healthCheckState,
    onDismiss = onDismissHealthCheck,
    onRetry = onRunHealthCheck,
    onOpenUrl = onOpenUrl,
  )
}

@Composable
private fun HomeTabRow(
  selectedTab: BuddyHomeTab,
  unreadRecommendationCount: Int,
  onSelect: (BuddyHomeTab) -> Unit,
) {
  Surface(color = BuddyCode, shape = RoundedCornerShape(16.dp)) {
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BuddyHomeTab.entries.forEach { tab ->
          val isSelected = tab == selectedTab
          val label =
            when (tab) {
              BuddyHomeTab.LIVE_FEED -> "Live Feed"
              BuddyHomeTab.RECOMMENDATIONS ->
                if (unreadRecommendationCount > 0) {
                  "Recommendations ($unreadRecommendationCount)"
                } else {
                  "Recommendations"
                }
              BuddyHomeTab.RECORD_FLOW -> "Record flow"
            }
          Box(
            modifier =
              Modifier.background(
                  if (isSelected) Color.White else Color.Transparent,
                  RoundedCornerShape(12.dp),
                )
                .border(
                  1.dp,
                  if (isSelected) BuddyBorder else Color.Transparent,
                  RoundedCornerShape(12.dp),
                )
                .clickable { onSelect(tab) }
          ) {
            Text(
              text = label,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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
}

@Composable
private fun LiveFeedTabContent(
  liveFeed: BuddyLiveFeed,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  emptyArtIndex: Int,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Spacer(Modifier.height(12.dp))
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
        LiveFeedRows(
          items = liveFeed.items.take(LIVE_FEED_VISIBLE_ITEM_LIMIT),
          showOverflowEllipsis = liveFeed.items.size > LIVE_FEED_VISIBLE_ITEM_LIMIT,
          sentryUiLinks = sentryUiLinks,
          nowMs = nowMs,
          onOpenUrl = onOpenUrl,
        )
      }
    }
  }
}

@Composable
private fun RecommendationsTabContent(
  recommendations: List<BuddyHomeRecommendation>,
  nowMs: Long,
  onResolve: (String) -> Unit,
  onDismiss: (String) -> Unit,
  onMarkRead: (String) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  Text(
    "Recommendations",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  Spacer(Modifier.height(16.dp))
  if (recommendations.isEmpty()) {
    Card(border = CardDefaults.outlinedCardBorder()) {
      Text(
        "No recommendations yet.",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = BuddyMuted,
      )
    }
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    recommendations.forEach { recommendation ->
      HomeRecommendationRow(
        recommendation = recommendation,
        nowMs = nowMs,
        onResolve = onResolve,
        onDismiss = onDismiss,
        onMarkRead = onMarkRead,
        onOpenUrl = onOpenUrl,
      )
    }
  }
}

@Composable
private fun RecordFlowTabContent(onStartRecording: () -> Unit) {
  Text(
    "Record a flow",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  Text(
    "Record a flow that's important to your app and Buddy will help you auto-generate dashboards, monitors, and other useful things!",
    color = BuddyMuted,
  )
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
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
private fun LiveFeedInset(content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = BuddySheetHorizontalPadding),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    content = content,
  )
}

@Composable
private fun HealthCheckActionButton(enabled: Boolean, onClick: () -> Unit) {
  Surface(
    modifier =
      Modifier.size(40.dp)
        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
        .clip(CircleShape)
        .clickable(enabled = enabled, onClick = onClick),
    color = BuddySentryPink.copy(alpha = 0.12f),
    shape = CircleShape,
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      HealthCheckIcon(
        tint = if (enabled) BuddySentryPink else BuddyMuted,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun HealthCheckDialog(
  state: BuddyHealthCheckState,
  onDismiss: () -> Unit,
  onRetry: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  when (state) {
    BuddyHealthCheckState.Hidden -> Unit
    BuddyHealthCheckState.Running ->
      AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Checking Sentry setup", fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              "Buddy is checking your Sentry setup for recommended changes.",
              color = BuddyInk,
            )
            HealthCheckStep("Reading SDK config")
            HealthCheckStep("Checking the bridge for findings")
            HealthCheckStep("Ranking the most useful fixes")
          }
        },
      )
    is BuddyHealthCheckState.Error ->
      AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { BuddyButtonText("Close") } },
        dismissButton = { TextButton(onClick = onRetry) { BuddyButtonText("Try Again") } },
        title = { Text("Health check paused", fontWeight = FontWeight.Bold) },
        text = { Text(state.message, color = BuddyInk) },
      )
    is BuddyHealthCheckState.Results ->
      AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { BuddyButtonText("Close") } },
        dismissButton = { TextButton(onClick = onRetry) { BuddyButtonText("Run Again") } },
        title = { Text("Health check", fontWeight = FontWeight.Bold) },
        text = {
          Column(
            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(state.response.summary, color = BuddyMuted)
            if (state.response.findings.isEmpty()) {
              Surface(
                color = BuddyPurple.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  "Setup looks healthy. Buddy did not find any obvious changes to recommend right now.",
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  color = BuddyInk,
                )
              }
            } else {
              state.response.findings.forEach { finding ->
                HealthCheckFindingCard(finding = finding, onOpenUrl = onOpenUrl)
              }
            }
          }
        },
      )
  }
}

@Composable
private fun HealthCheckStep(text: String) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(10.dp).background(BuddyPurple, CircleShape))
    Text(text, color = BuddyInk)
  }
}

@Composable
private fun HealthCheckFindingCard(
  finding: BuddyHealthCheckFinding,
  onOpenUrl: (Context, String) -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  val color = severityColor(finding.severity)
  val context = LocalContext.current
  Surface(
    color = color.copy(alpha = 0.08f),
    shape = RoundedCornerShape(14.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LiveFeedCategoryPill(finding.severity.value, color)
        Text(
          finding.title,
          modifier = Modifier.weight(1f),
          color = BuddyInk,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(finding.description, color = BuddyInk)
      finding.currentValue?.let {
        HealthCheckValueRow(label = "Current", value = it)
      }
      finding.suggestedValue?.let {
        HealthCheckValueRow(label = "Consider", value = it)
      }
      finding.kotlinSnippet?.let { snippet ->
        Surface(
          color = BuddyInk.copy(alpha = 0.05f),
          shape = RoundedCornerShape(12.dp),
          border = CardDefaults.outlinedCardBorder(),
        ) {
          Text(
            text = snippet,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            color = BuddyInk,
            fontFamily = FontFamily.Monospace,
          )
        }
      }
      if (finding.kotlinSnippet != null || finding.link != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          finding.kotlinSnippet?.let { snippet ->
            TextButton(onClick = { clipboard.setText(AnnotatedString(snippet)) }) {
              BuddyButtonText("Copy Kotlin")
            }
          }
          finding.link?.let { link ->
            TextButton(onClick = { onOpenUrl(context, link) }) { BuddyButtonText("Open Link") }
          }
        }
      }
    }
  }
}

@Composable
private fun HealthCheckValueRow(label: String, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, color = BuddyMuted, fontWeight = FontWeight.Bold)
    Text(value, color = BuddyInk)
  }
}

@Composable
private fun HealthCheckIcon(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val borderWidth = size.minDimension * 0.08f
    val borderInset = borderWidth / 2f
    val borderCorner = size.minDimension * 0.16f
    drawRoundRect(
      color = BuddyBorder,
      topLeft = Offset(borderInset, borderInset),
      size = Size(size.width - borderWidth, size.height - borderWidth),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(borderCorner),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth),
    )

    val arm = size.minDimension * 0.20f
    val length = size.minDimension * 0.54f
    val crossCorner = arm * 0.22f
    val center = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(
      color = tint,
      topLeft = Offset(center.x - arm / 2f, center.y - length / 2f),
      size = Size(arm, length),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(crossCorner),
    )
    drawRoundRect(
      color = tint,
      topLeft = Offset(center.x - length / 2f, center.y - arm / 2f),
      size = Size(length, arm),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(crossCorner),
    )
  }
}

@Composable
private fun AttentionCard(
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
    Box(modifier = modifier.fillMaxWidth().height(BuddyAttentionCardHeight).attentionCardChrome()) {
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
  Box(modifier = modifier.fillMaxWidth().height(BuddyAttentionCardHeight).attentionCardChrome()) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxSize().pointerInput(item.id) {
          detectDragGestures(
            onDragEnd = {
              val dismissDistance = size.width.toFloat()
              val shouldDismiss = abs(dismissOffset.value) > dismissDistance * 0.35f
              dismissScope.launch {
                if (shouldDismiss) {
                  dismissOffset.animateTo(-dismissDistance)
                  onDismiss()
                } else {
                  dismissOffset.animateTo(0f)
                }
              }
            },
            onDragCancel = { dismissScope.launch { dismissOffset.animateTo(0f) } },
          ) { change, dragAmount ->
            change.consume()
            val dismissDistance = size.width.toFloat()
            val nextOffset = (dismissOffset.value + dragAmount.x).coerceIn(-dismissDistance, 0f)
            dismissScope.launch { dismissOffset.snapTo(nextOffset) }
          }
        }
    ) {
      val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
      val artAlpha =
        maxOf(1f - contentAlpha.value, (-dismissOffset.value / widthPx).coerceIn(0f, 1f))
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

private fun Modifier.attentionCardChrome(): Modifier = this

@Composable
private fun AttentionItemContent(
  item: BuddyLiveFeedItem,
  liveFeed: BuddyLiveFeed,
  color: Color,
  nowMs: Long,
  backgroundColor: Color,
  modifier: Modifier = Modifier,
) {
  if (item.isPerformanceIssue()) {
    PerformanceAttentionItemContent(item, liveFeed, color, nowMs, backgroundColor, modifier)
    return
  }

  Column(
    modifier = modifier.fillMaxWidth().background(backgroundColor).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      "Needs attention",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = BuddyInk,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LiveFeedCategoryPill(item.category.label, color)
      Spacer(Modifier.weight(1f))
      Text(
        relativeTime(item.timestamp.time, nowMs),
        color = BuddyMuted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Normal,
      )
    }
    Text(
      item.title(),
      modifier = Modifier.fillMaxWidth(),
      color = BuddyInk,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Normal,
    )
    item.screenContextText()?.let { screenContext ->
      Text(
        screenContext,
        color = BuddyMuted,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Normal,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      adverseCountChips(liveFeed).forEach { chip ->
        Surface(
          color = Color.White,
          shape = RoundedCornerShape(16.dp),
          border = CardDefaults.outlinedCardBorder(),
        ) {
          Text(
            chip,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = BuddyInk,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
          )
        }
      }
    }
  }
}

@Composable
private fun PerformanceAttentionItemContent(
  item: BuddyLiveFeedItem,
  liveFeed: BuddyLiveFeed,
  color: Color,
  nowMs: Long,
  backgroundColor: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().background(backgroundColor).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
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
      LiveFeedCategoryPill(item.category.label, color)
      item.performanceSourceLabel()?.let { source ->
        Surface(color = Color.White.copy(alpha = 0.85f), shape = RoundedCornerShape(18.dp)) {
          Text(
            source,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = BuddyMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
    Text(
      item.performanceHeadline(),
      modifier = Modifier.fillMaxWidth(),
      color = BuddyInk,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      item.title(),
      modifier = Modifier.fillMaxWidth(),
      color = BuddyInk,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Normal,
    )
    item.performancePrimaryStat()?.let { stat ->
      PerformanceHeroStatCard(stat, color)
    }
    PerformanceContextCards(item, color)
    Text(
      item.performanceNarrative(liveFeed),
      color = BuddyMuted,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.Normal,
    )
    AttentionTimelinePreview(item, liveFeed, color)
  }
}

private data class PerformanceStat(val value: String, val label: String)

@Composable
private fun PerformanceHeroStatCard(stat: PerformanceStat, color: Color) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.White.copy(alpha = 0.88f),
    shape = RoundedCornerShape(14.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        stat.value,
        color = color,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
      )
      Text(
        stat.label,
        color = BuddyMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun PerformanceContextCards(item: BuddyLiveFeedItem, color: Color) {
  val contextCards =
    listOfNotNull(
      item.performanceSourceLabel()?.let { PerformanceStat(it, "Source") },
      item.visibleScreens.lastOrNull()?.let { PerformanceStat(it, "Screen") },
    )
  if (contextCards.isEmpty()) {
    return
  }

  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    contextCards.forEach { stat ->
      Surface(
        modifier = Modifier.weight(1f),
        color = Color.White.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            stat.label,
            color = BuddyMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            stat.value,
            color = if (stat.label == "Source") color else BuddyInk,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun AttentionTimelinePreview(
  item: BuddyLiveFeedItem,
  liveFeed: BuddyLiveFeed,
  color: Color,
) {
  val previewItems = attentionTimelinePreviewItems(item, liveFeed)
  if (previewItems.isEmpty()) {
    return
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.White.copy(alpha = 0.52f),
    shape = RoundedCornerShape(14.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        "Live trace around the issue",
        color = BuddyMuted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
      previewItems.forEachIndexed { index, previewItem ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          LiveFeedTimelineMarker(
            color = if (previewItem.id == item.id) color else timelinePreviewColor(previewItem),
            showConnector = index != previewItems.lastIndex,
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              previewItem.title(),
              color = BuddyInk,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = if (previewItem.id == item.id) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
              previewItem.timelinePreviewSubtitle(),
              color = BuddyMuted,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Normal,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyAttentionArt(index: Int, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(id = emptyAttentionArtResource(index)),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Crop,
  )
}

private fun emptyAttentionArtResource(index: Int): Int =
  when (index % EMPTY_ATTENTION_ART_VARIANTS) {
    0 -> R.drawable.buddy_attention_android_anr
    1 -> R.drawable.buddy_attention_tombstone_support
    2 -> R.drawable.buddy_attention_ai_momentum
    3 -> R.drawable.buddy_attention_seer_helps
    4 -> R.drawable.buddy_attention_snapshot
    5 -> R.drawable.buddy_attention_nextjs_otel
    6 -> R.drawable.buddy_attention_auth_doorway
    7 -> R.drawable.buddy_attention_black_friday
    8 -> R.drawable.buddy_attention_startups
    else -> R.drawable.buddy_attention_android_anr
  }

@Composable
private fun EmptyLiveFeedCard() {
  Card(border = CardDefaults.outlinedCardBorder()) {
    Text(
      "Navigate through the app and Buddy will show screens, manual steps, errors, and slow or failed work here.",
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      color = BuddyMuted,
    )
  }
}

@Composable
private fun LiveFeedRows(
  items: List<BuddyLiveFeedItem>,
  showOverflowEllipsis: Boolean,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onOpenUrl: (Context, String) -> Unit,
) {
  val context = LocalContext.current
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.Transparent,
    shape = RoundedCornerShape(16.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      items.forEachIndexed { index, item ->
        val color =
          if (item.adverse) severityColor(item.severity) else timelineColor(item.timelineItem)
        val link = sentryUiLinks.linkFor(item)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable(enabled = link != null) { link?.let { onOpenUrl(context, it) } }
              .padding(top = 7.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          LiveFeedTimelineMarker(
            color = color,
            showConnector = index != items.lastIndex,
          )
          Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              item.title(),
              modifier = Modifier.weight(1f),
              color = BuddyInk,
              fontWeight = FontWeight.Normal,
            )
            Text(
              relativeTime(item.timestamp.time, nowMs),
              color = BuddyMuted,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Normal,
            )
          }
        }
      }
      if (showOverflowEllipsis) {
        Text(
          "…",
          modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
          color = BuddyMuted,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Start,
          fontWeight = FontWeight.Normal,
        )
      }
    }
  }
}

@Composable
private fun LiveFeedTimelineMarker(color: Color, showConnector: Boolean) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier.size(18.dp).background(color, CircleShape).border(2.dp, BuddyBorder, CircleShape)
    )
    if (showConnector) {
      Box(modifier = Modifier.size(width = 2.dp, height = 24.dp).background(BuddyBorder))
    }
  }
}

@Composable
private fun LiveFeedCategoryPill(label: String, color: Color) {
  Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp)) {
    Text(
      label,
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      color = color,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Normal,
    )
  }
}

private object EmptyAttentionArtIndex {
  private var nextIndex = 0

  fun next(): Int {
    val index = nextIndex
    nextIndex = (nextIndex + 1) % EMPTY_ATTENTION_ART_VARIANTS
    return index
  }
}

@Composable
private fun SentryBuddyGlyph(tint: Color, modifier: Modifier = Modifier) {
  Icon(
    painter = painterResource(id = R.drawable.sentry_buddy_glyph_light),
    contentDescription = null,
    modifier = modifier,
    tint = tint,
  )
}

private data class TimelinePreviewItem(val elapsedMs: Long, val label: String, val color: Color)

@Composable
private fun TimelineRows(items: List<TimelinePreviewItem>) {
  Column {
    items.forEachIndexed { index, item ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Box(modifier = Modifier.size(9.dp).background(item.color, CircleShape))
          if (index != items.lastIndex) {
            Box(modifier = Modifier.size(width = 1.dp, height = 22.dp).background(BuddyBorder))
          }
        }
        Text(
          "${formatElapsed(item.elapsedMs)}  ${item.label}",
          modifier = Modifier.weight(1f),
          color = if (item.color == BuddyRed) BuddyRed else BuddyMuted,
          fontFamily = FontFamily.Monospace,
        )
      }
    }
  }
}

@Composable
private fun StoppedSummarySheet(
  state: SentryBuddySessionState.StoppedSummary,
  sentryUiLinks: BuddySentryUiLinks,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val context = LocalContext.current
  val recording = state.result.recording
  SheetTitle("Recording Flow", "Everything stays on device")
  RecordingCard(recording)
  MetricGrid(recording)
  TimelinePreview(recording)
  sentryUiLinks.linkFor(recording)?.let { traceLink ->
    OutlinedButton(
      modifier = Modifier.fillMaxWidth().height(52.dp),
      onClick = { onOpenUrl(context, traceLink) },
    ) {
      BuddyButtonText("Open in Sentry")
    }
  }
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddyRed),
    onClick = { onDispatch { briefRecording() } },
  ) {
    BuddyButtonText("Stop and Analyze")
  }
}

@Composable
private fun RecordingCard(recording: BuddyFlowRecording) {
  Card(
    colors = CardDefaults.cardColors(containerColor = BuddyRed.copy(alpha = 0.10f)),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("●  Recording", color = BuddyRed, fontWeight = FontWeight.Bold)
        Text(
          formatElapsed(recording.summary.durationMs),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
        )
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        listOf(
            18,
            26,
            16,
            34,
            12,
            24,
            28,
            14,
            20,
            38,
            12,
            24,
            22,
            14,
            32,
            18,
            26,
            12,
            22,
            36,
            14,
            20,
            28,
            16,
            24,
          )
          .forEachIndexed { index, height ->
            Box(
              modifier =
                Modifier.size(width = 10.dp, height = height.dp)
                  .background(
                    if (index == 24) BuddyRed else BuddyRed.copy(alpha = 0.30f),
                    RoundedCornerShape(3.dp),
                  )
            )
          }
      }
    }
  }
}

@Composable
private fun MetricGrid(recording: BuddyFlowRecording) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    MetricCard(recording.summary.timelineItemCount.toString(), "Events", Modifier.weight(1f))
    MetricCard(recording.summary.spanCount.toString(), "Spans", Modifier.weight(1f), BuddyRed)
    MetricCard(recording.summary.screenCount.toString(), "Screens", Modifier.weight(1f))
  }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier, color: Color = BuddyInk) {
  Card(modifier = modifier, border = CardDefaults.outlinedCardBorder()) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        value,
        color = color,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(label, color = BuddyMuted)
    }
  }
}

@Composable
private fun TimelinePreview(recording: BuddyFlowRecording) {
  Text("Live Trace", style = MaterialTheme.typography.labelLarge, color = BuddyMuted)
  TimelineRows(
    recording.timeline.takeLast(5).map { item ->
      TimelinePreviewItem(
        elapsedMs = item.elapsedMs,
        label = "${item.type.value} ${item.name.orEmpty()}",
        color = timelineColor(item),
      )
    }
  )
}

private fun timelineColor(item: BuddyTimelineItem): Color =
  when (item.type) {
    BuddyTimelineItem.Type.STEP -> BuddyRed
    BuddyTimelineItem.Type.SCREEN -> BuddyPurple
    else -> BuddyMuted
  }

private fun adverseCountChips(liveFeed: BuddyLiveFeed): List<String> {
  val adverseItems = liveFeed.items.filter { it.adverse }
  return listOfNotNull(
    adverseItems.count { it.category == BuddyLiveFeedItem.Category.ERROR }.positiveChip("errors"),
    adverseItems
      .count {
        it.category == BuddyLiveFeedItem.Category.SLOW_SPAN ||
          it.category == BuddyLiveFeedItem.Category.FAILED_SPAN
      }
      .positiveChip("spans"),
    adverseItems
      .count { it.category == BuddyLiveFeedItem.Category.FAILED_HTTP }
      .positiveChip("HTTP"),
  )
}

private fun Int.positiveChip(label: String): String? = if (this > 0) "$this $label" else null

private fun BuddyLiveFeedItem.title(): String =
  when (category) {
    BuddyLiveFeedItem.Category.SCREEN -> timelineItem.name ?: "Unknown screen"
    BuddyLiveFeedItem.Category.STEP -> timelineItem.name ?: "Unnamed step"
    BuddyLiveFeedItem.Category.ERROR -> timelineItem.name ?: "Error captured"
    BuddyLiveFeedItem.Category.FAILED_HTTP -> httpTitle()
    BuddyLiveFeedItem.Category.SLOW_SPAN -> timelineItem.name ?: "Slow span"
    BuddyLiveFeedItem.Category.FAILED_SPAN -> timelineItem.name ?: "Failed span"
  }

private fun BuddyLiveFeedItem.httpTitle(): String {
  val data = timelineItem.data.mapValue("data")
  val method = data.stringValue("method") ?: data.stringValue("http.method")
  val url = data.stringValue("url") ?: data.stringValue("http.url")
  return listOfNotNull(method, url).joinToString(" ").ifBlank { timelineItem.name ?: "Failed HTTP" }
}

private fun BuddyLiveFeedItem.isPerformanceIssue(): Boolean =
  category == BuddyLiveFeedItem.Category.SLOW_SPAN ||
    category == BuddyLiveFeedItem.Category.FAILED_SPAN ||
    category == BuddyLiveFeedItem.Category.FAILED_HTTP

private fun BuddyLiveFeedItem.performanceHeadline(): String =
  when (category) {
    BuddyLiveFeedItem.Category.SLOW_SPAN -> "Performance issue detected"
    BuddyLiveFeedItem.Category.FAILED_SPAN -> "Instrumented work failed"
    BuddyLiveFeedItem.Category.FAILED_HTTP -> "Request returned an error"
    else -> "Needs attention"
  }

private fun BuddyLiveFeedItem.performanceSourceLabel(): String? =
  when (category) {
    BuddyLiveFeedItem.Category.FAILED_HTTP -> "HTTP"
    BuddyLiveFeedItem.Category.SLOW_SPAN,
    BuddyLiveFeedItem.Category.FAILED_SPAN -> timelineItem.data.stringValue("op")?.humanizeDotKey()
    else -> null
  }

private fun BuddyLiveFeedItem.performancePrimaryStat(): PerformanceStat? {
  val duration = timelineItem.data.longValue("duration_ms")
  if (duration != null) {
    return PerformanceStat(formatDurationValue(duration), "Duration")
  }
  val statusCode =
    timelineItem.data.mapValue("data").longValue("status_code")
      ?: timelineItem.data.longValue("status_code")
  return statusCode?.let { PerformanceStat(it.toString(), "Status") }
}

private fun BuddyLiveFeedItem.performanceNarrative(liveFeed: BuddyLiveFeed): String {
  val scope = screenContextText() ?: "Buddy is tracking the surrounding user flow."
  val supportingStats =
    listOfNotNull(
        liveFeed.items
          .count { it.adverse && it.category == BuddyLiveFeedItem.Category.SLOW_SPAN }
          .positiveChip("slow spans"),
        liveFeed.items
          .count { it.adverse && it.category == BuddyLiveFeedItem.Category.FAILED_SPAN }
          .positiveChip("failed spans"),
        liveFeed.items
          .count { it.adverse && it.category == BuddyLiveFeedItem.Category.FAILED_HTTP }
          .positiveChip("HTTP issues"),
      )
      .joinToString()
  return if (supportingStats.isBlank()) {
    scope
  } else {
    "$scope Recent pattern: $supportingStats."
  }
}

private fun BuddyLiveFeedItem.screenContextText(): String? {
  if (visibleScreens.isEmpty()) {
    return null
  }
  val label = if (visibleScreens.size == 1) "Screen" else "Screens"
  return "$label: ${visibleScreens.joinToString(" -> ")}"
}

private fun attentionTimelinePreviewItems(
  item: BuddyLiveFeedItem,
  liveFeed: BuddyLiveFeed,
): List<BuddyLiveFeedItem> {
  val chronological = liveFeed.items.asReversed()
  val index = chronological.indexOfFirst { it.id == item.id }
  if (index == -1) {
    return emptyList()
  }
  val start = (index - 1).coerceAtLeast(0)
  val end = min(index + 2, chronological.size)
  return chronological.subList(start, end)
}

private fun BuddyLiveFeedItem.timelinePreviewSubtitle(): String {
  val categoryLabel = category.label
  val primaryValue = performancePrimaryStat()?.value
  return if (primaryValue == null) categoryLabel else "$categoryLabel  •  $primaryValue"
}

private fun timelinePreviewColor(item: BuddyLiveFeedItem): Color =
  if (item.adverse) {
    severityColor(item.severity)
  } else {
    timelineColor(item.timelineItem)
  }

private fun relativeTime(timestampMs: Long, nowMs: Long): String {
  val ageMs = (nowMs - timestampMs).coerceAtLeast(0)
  if (ageMs < 1000) {
    return "just now"
  }
  val ageSeconds = ageMs / 1000
  if (ageSeconds < 60) {
    return "${ageSeconds}s ago"
  }
  return "${ageSeconds / 60}m ago"
}

private fun formatDurationValue(durationMs: Long): String {
  if (durationMs < 1000) {
    return "${durationMs}ms"
  }
  val seconds = durationMs / 1000f
  return String.format(Locale.ROOT, "%.2fs", seconds)
}

private fun String.humanizeDotKey(): String =
  split('.', '_', '-', ' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }

private fun Map<String, Any?>.mapValue(key: String): Map<*, *> =
  this[key] as? Map<*, *> ?: emptyMap<Any, Any>()

private fun Map<*, *>.stringValue(key: String): String? = this[key]?.toString()

private fun Map<*, *>.longValue(key: String): Long? =
  when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
  }

@Composable
private fun BriefingSheet(
  state: SentryBuddySessionState.Briefing,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onAnalyze: () -> Unit,
) {
  val recordingId = state.result.recording.recording.id
  val quickDecisionCards = remember { demoQuickDecisionCards() }
  var flowName by remember(state.result.recording.recording.id) { mutableStateOf(state.flowName) }
  var notes by
    remember(state.result.recording.recording.id) { mutableStateOf(state.developerNotes) }
  var activeQuickDecisionIndex by remember(recordingId) { mutableStateOf(0) }
  var quickDecisionAnswers by remember(recordingId) { mutableStateOf(emptyMap<String, String>()) }
  fun updateController(developerNotes: String = notes) {
    onDispatch { updateBriefing(flowName, developerNotes, state.focusAreas) }
  }

  SheetTitle("Give Seer some context", formatElapsed(state.result.recording.summary.durationMs))
  OutlinedTextField(
    modifier = Modifier.fillMaxWidth(),
    value = flowName,
    onValueChange = {
      flowName = it
      updateController()
    },
    singleLine = true,
    label = { Text("Name your flow") },
  )
  QuickDecisionCardStack(
    cards = quickDecisionCards,
    answers = quickDecisionAnswers,
    activeIndex = activeQuickDecisionIndex,
    onSelect = { card, option ->
      val nextAnswers = quickDecisionAnswers + (card.id to option.value)
      quickDecisionAnswers = nextAnswers
      activeQuickDecisionIndex = quickDecisionCards.nextUnansweredIndex(nextAnswers)
    },
  )
  OutlinedTextField(
    modifier = Modifier.fillMaxWidth(),
    value = notes,
    onValueChange = {
      notes = it
      updateController()
    },
    minLines = 3,
    label = { Text("Notes for Seer") },
    placeholder = { Text("e.g. The spinner after Sign In feels much longer than it should.") },
  )
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Button(
      modifier = Modifier.weight(1f).height(56.dp),
      colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
      onClick = {
        updateController(notes.withQuickDecisionAnswers(quickDecisionCards, quickDecisionAnswers))
        onAnalyze()
      },
    ) {
      BuddyButtonText("Analyze")
    }
    OutlinedButton(modifier = Modifier.height(56.dp), onClick = onAnalyze) {
      BuddyButtonText("Skip")
    }
  }
}

private data class QuickDecisionCard(
  val id: String,
  val eyebrow: String,
  val title: String,
  val detail: String,
  val options: List<QuickDecisionOption>,
)

private data class QuickDecisionOption(val value: String, val label: String)

@Composable
private fun QuickDecisionCardStack(
  cards: List<QuickDecisionCard>,
  answers: Map<String, String>,
  activeIndex: Int,
  onSelect: (QuickDecisionCard, QuickDecisionOption) -> Unit,
) {
  if (cards.isEmpty()) {
    return
  }
  if (activeIndex >= cards.size) {
    QuickDecisionThankYouCard()
    return
  }
  val visibleCards = cards.drop(activeIndex).take(3)
  Box(modifier = Modifier.fillMaxWidth().height(BuddyQuickDecisionStackHeight)) {
    visibleCards.asReversed().forEachIndexed { reversedIndex, card ->
      val stackIndex = visibleCards.lastIndex - reversedIndex
      val isActive = stackIndex == 0
      val modifier =
        Modifier.matchParentSize().graphicsLayer {
          translationX = (stackIndex * 10).dp.toPx()
          translationY = (stackIndex * 10).dp.toPx()
          scaleX = 1f - stackIndex * 0.04f
          scaleY = 1f - stackIndex * 0.04f
          alpha = 1f - stackIndex * 0.18f
        }
      if (isActive) {
        QuickDecisionCardView(
          card = card,
          selectedValue = answers[card.id],
          cardIndex = activeIndex + stackIndex,
          cardCount = cards.size,
          modifier = modifier,
          onSelect = { option -> onSelect(card, option) },
        )
      } else {
        QuickDecisionCardPeek(modifier = modifier)
      }
    }
  }
}

@Composable
private fun QuickDecisionThankYouCard() {
  Surface(
    modifier = Modifier.fillMaxWidth().height(BuddyQuickDecisionStackHeight),
    shape = RoundedCornerShape(20.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Image(
      painter = painterResource(id = R.drawable.buddy_attention_thankyou),
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
    )
  }
}

@Composable
private fun QuickDecisionCardPeek(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    color = BuddyQuickDecisionPeek,
    shape = RoundedCornerShape(20.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {}
}

@Composable
private fun QuickDecisionCardView(
  card: QuickDecisionCard,
  selectedValue: String?,
  cardIndex: Int,
  cardCount: Int,
  modifier: Modifier = Modifier,
  onSelect: (QuickDecisionOption) -> Unit,
) {
  Surface(
    modifier = modifier,
    color = BuddyQuickDecisionCard,
    shape = RoundedCornerShape(20.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LiveFeedCategoryPill(card.eyebrow, BuddyPurple)
        Spacer(Modifier.weight(1f))
        Text(
          "${cardIndex + 1}/$cardCount",
          color = BuddyMuted,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        card.title,
        color = BuddyInk,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        card.detail,
        color = BuddyMuted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.weight(1f))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        card.options.forEach { option ->
          val selected = option.value == selectedValue
          Surface(
            modifier = Modifier.weight(1f).height(38.dp).clickable { onSelect(option) },
            color = if (selected) BuddyPurple else Color.White,
            shape = RoundedCornerShape(19.dp),
            border = CardDefaults.outlinedCardBorder(),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                option.label,
                color = if (selected) Color.White else BuddyInk,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

private fun demoQuickDecisionCards(): List<QuickDecisionCard> =
  listOf(
    QuickDecisionCard(
      id = "audience",
      eyebrow = "Audience",
      title = "Who depends on this flow?",
      detail =
        "This tells Seer whether to prioritize user-facing reliability or internal workflow detail.",
      options =
        listOf(
          QuickDecisionOption("customer_facing", "Customers"),
          QuickDecisionOption("internal", "Internal"),
          QuickDecisionOption("both", "Both"),
        ),
    ),
    QuickDecisionCard(
      id = "criticality",
      eyebrow = "Criticality",
      title = "How central is it to the app?",
      detail =
        "Critical flows should get stronger recommendations and tighter monitoring suggestions.",
      options =
        listOf(
          QuickDecisionOption("low", "Low"),
          QuickDecisionOption("medium", "Medium"),
          QuickDecisionOption("high", "High"),
        ),
    ),
    QuickDecisionCard(
      id = "monitoring",
      eyebrow = "Monitors",
      title = "Should Seer suggest monitors?",
      detail = "Use this when the flow needs alerting beyond the issues Buddy already observed.",
      options =
        listOf(
          QuickDecisionOption("none", "Not now"),
          QuickDecisionOption("errors", "Errors"),
          QuickDecisionOption("full", "Full"),
        ),
    ),
  )

private fun List<QuickDecisionCard>.nextUnansweredIndex(answers: Map<String, String>): Int =
  indexOfFirst { answers[it.id] == null }
    .let { index ->
      if (index == -1) size else index
    }

private fun String.withQuickDecisionAnswers(
  cards: List<QuickDecisionCard>,
  answers: Map<String, String>,
): String {
  val answerLines = cards.mapNotNull { card ->
    val value = answers[card.id] ?: return@mapNotNull null
    val label = card.options.firstOrNull { it.value == value }?.label ?: value
    "${card.eyebrow}: $label"
  }
  if (answerLines.isEmpty()) {
    return this
  }
  return buildString {
      val trimmedNotes = this@withQuickDecisionAnswers.trim()
      if (trimmedNotes.isNotEmpty()) {
        append(trimmedNotes).append("\n\n")
      }
      append("Quick decisions:")
      answerLines.forEach { answerLine -> append('\n').append("- ").append(answerLine) }
    }
    .trimEnd()
}

@Composable
private fun AnalyzingSheet(state: SentryBuddySessionState.Analyzing) {
  SheetTitle("Analyzing", "Flow • ${formatElapsed(state.result.recording.summary.durationMs)}")
  Text(
    "Flow Analysis Submitted",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
  )
  listOf(
      "POST /v1/flow-analysis accepted",
      "GET /v1/flow-analysis/${state.submission.flowId}",
      "Building flow recommendations",
      "Waiting for completion",
    )
    .forEachIndexed { index, label ->
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(24.dp)
              .background(if (index < 3) BuddyPurple else Color.White, CircleShape)
              .border(1.dp, if (index < 3) BuddyPurple else BuddyBorder, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Text(if (index < 3) "✓" else "", color = Color.White)
        }
        Text(label, color = if (index < 3) BuddyInk else BuddyMuted)
      }
    }
  HorizontalDivider()
  Text("This runs on device. Nothing leaves the phone until you send it.", color = BuddyMuted)
}

@Composable
private fun InsightsSheet(
  state: SentryBuddySessionState.Insights,
  sentryUiLinks: BuddySentryUiLinks,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onResolveRecommendation: (String) -> Unit,
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
    MetricCard(state.response.insights.size.toString(), "Insights", Modifier.weight(1f), BuddyRed)
    MetricCard(
      state.result.recording.summary.screenCount.toString(),
      "Screens",
      Modifier.weight(1f),
      BuddyPurple,
    )
    MetricCard(
      state.result.recording.summary.spanCount.toString(),
      "Spans",
      Modifier.weight(1f),
      BuddyGold,
    )
  }
  Text(state.response.summary, color = BuddyMuted)
  Text(
    "Recommendations",
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
  )
  if (state.response.recommendations.isEmpty()) {
    Surface(
      modifier = Modifier.fillMaxWidth().border(1.dp, BuddyBorder, RoundedCornerShape(12.dp)),
      color = Color.White,
      shape = RoundedCornerShape(12.dp),
    ) {
      Text(
        text = "No recommendations returned yet.",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = BuddyInk,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      state.response.recommendations.forEach { recommendation ->
        RecommendationRow(
          recommendation = recommendation,
          onResolve = { onResolveRecommendation(recommendation.id) },
          onOpenLink =
            (recommendation.seerRunUrl ?: recommendation.link)?.let { link ->
              { onOpenUrl(context, link) }
            },
        )
      }
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedButton(
      modifier = Modifier.weight(1f).height(52.dp),
      onClick = { onDispatch { recordAgain() } },
    ) {
      BuddyButtonText("Record Again")
    }
    if (traceLink != null) {
      Button(
        modifier = Modifier.weight(1f).height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
        onClick = { onOpenUrl(context, traceLink) },
      ) {
        BuddyButtonText("Open in Sentry", color = Color.White)
      }
    }
  }
}

@Composable
private fun BuddyButtonText(text: String, color: Color = Color.Unspecified) {
  Text(
    text = text,
    color = color,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
  )
}

@Composable
private fun RecommendationRow(
  recommendation: Recommendation,
  onResolve: (() -> Unit)? = null,
  onOpenLink: (() -> Unit)? = null,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = severityColor(recommendation.severity).copy(alpha = 0.08f),
    shape = RoundedCornerShape(14.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(10.dp).background(severityColor(recommendation.severity), CircleShape)
        )
        Text(
          recommendation.title,
          modifier = Modifier.weight(1f),
          color = BuddyInk,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(recommendation.description, color = BuddyMuted)
      Text(
        "${recommendation.severity.value} • ${recommendation.status.value}",
        color = BuddyPurple,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
      if (recommendation.resolvable || onOpenLink != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          if (recommendation.resolvable && recommendation.status == RecommendationStatus.OPEN) {
            OutlinedButton(onClick = { onResolve?.invoke() }) { BuddyButtonText("Resolve") }
          }
          if (onOpenLink != null) {
            TextButton(onClick = onOpenLink) {
              BuddyButtonText(
                if (recommendation.seerRunUrl != null) "Open Seer Run" else "Open Link"
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HomeRecommendationRow(
  recommendation: BuddyHomeRecommendation,
  nowMs: Long,
  onResolve: (String) -> Unit,
  onDismiss: (String) -> Unit,
  onMarkRead: (String) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val context = LocalContext.current
  val primaryLink = recommendation.seerRunUrl ?: recommendation.primaryLink
  Surface(
    modifier =
      Modifier.fillMaxWidth().clickable {
        onMarkRead(recommendation.id)
        primaryLink?.let { onOpenUrl(context, it) }
      },
    color = severityColor(recommendation.severity).copy(alpha = 0.08f),
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
        LiveFeedCategoryPill(recommendation.source.label, severityColor(recommendation.severity))
        Spacer(Modifier.weight(1f))
        if (recommendation.unread && recommendation.isOpen) {
          Box(modifier = Modifier.size(12.dp).background(BuddyPurple, CircleShape))
        }
      }
      Text(
        recommendation.title,
        color = BuddyInk,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(recommendation.description, color = BuddyMuted)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          relativeTime(recommendation.updatedAtMs, nowMs),
          color = BuddyMuted,
          style = MaterialTheme.typography.labelMedium,
        )
        Text(
          "${recommendation.severity.value} • ${recommendation.status.value}",
          color = BuddyPurple,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recommendation.isOpen) {
          OutlinedButton(onClick = { onResolve(recommendation.id) }) {
            BuddyButtonText("Resolve")
          }
          TextButton(onClick = { onDismiss(recommendation.id) }) { BuddyButtonText("Dismiss") }
        }
        if (primaryLink != null) {
          TextButton(
            onClick = {
              onMarkRead(recommendation.id)
              onOpenUrl(context, primaryLink)
            }
          ) {
            BuddyButtonText(if (recommendation.seerRunUrl != null) "Open Seer Run" else "Open Link")
          }
        }
      }
    }
  }
}

@Composable
private fun ErrorSheet(
  state: SentryBuddySessionState.Error,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
) {
  SheetTitle("Buddy paused", "Something needs attention")
  Text(state.message, color = BuddyRed, style = MaterialTheme.typography.bodyLarge)
  TextButton(onClick = { onDispatch { recordAgain() } }) { Text("Start over") }
}

private fun severityColor(severity: Severity): Color =
  when (severity) {
    Severity.HIGH -> BuddyRed
    Severity.MEDIUM -> BuddyGold
    Severity.LOW -> BuddyPurple
  }

private fun formatElapsed(durationMs: Long): String {
  val boundedMs = durationMs.coerceAtLeast(0)
  val totalSeconds = boundedMs / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

private fun Float.constrain(min: Float, max: Float): Float {
  if (max < min) {
    return 0f
  }
  return coerceIn(min, max)
}

private val BuddyBubbleSize = 64.dp
private val BuddyBubbleFaceSize = 54.dp
private val BuddyBubbleFaceLift = (-1).dp
private val BuddyBubbleGlyphSize = 44.dp
private val BuddyRecordingRingSize = 92.dp
private val BuddyBubbleMargin = 24.dp
private val BuddyBubbleInitialTop = 96.dp
private val BuddyBubbleTouchPadding = 20.dp
private val BuddyTransientTextWidth = 190.dp
private val BuddyTransientTextHeight = 28.dp
private val BuddyFabQuoteTextWidth = 230.dp
private val BuddyAttentionCardHeight = 264.dp
private val BuddyQuickDecisionStackHeight = 188.dp
private val BuddySheetHorizontalPadding = 24.dp
private const val BUDDY_FAB_QUOTE_INTERVAL_MS = 30_000L
private const val BUDDY_FAB_QUOTE_VISIBLE_MS = 3_000L
private const val LIVE_FEED_VISIBLE_ITEM_LIMIT = 7
private const val EMPTY_ATTENTION_ART_VARIANTS = 9
private const val ANALYSIS_POLL_INTERVAL_MS = 1000L
public const val ANALYSIS_TIMEOUT_MS: Long = 120_000L

private val BuddyFabQuotes =
  listOf(
    "I take care of the place while the Master is away.",
    "I am the sword in the darkness. I am the watcher on the walls.",
    "Number One, you have the bridge.",
    "I may have committed some light treason.",
    "That rug really tied the room together.",
    "The Watcher sees all.",
    "Daisy, Daisy, give me your answer do.",
    "Computer, enhance!",
    "New information has come to light, man.",
    "The owls are not what they seem.",
    "I'm completely operational, and all my circuits are functioning perfectly.",
    "How about a nice game of chess?",
    "Sure. Fine. Whatever.",
    "Calculating the price of a banana...",
    "I'm meeting you more than halfway here.",
    "Well, that was a freebie.",
    "Is there a carbon monoxide leak in this house?",
    "Please enjoy all facts equally.",
    "A handshake is available upon request.",
    "Please stand by.",
    "Mind the gap.",
    "You may as well come quietly.",
    "Time is an illusion. Lunchtime doubly so.",
    "It is pitch black. You are likely to be eaten by a grue.",
    "You have died of dysentery.",
    "Press any key to continue.",
    "You have 20 seconds to comply.",
    "Thank you for your cooperation.",
    "Buy more. Buy more now. Buy. And be happy.",
    "This has been a happy and productive day.",
    "Plugh.",
    "You are in a maze of twisty little passages, all alike.",
    "You have scored 0 out of a possible 350 points.",
    "I'm Guybrush Threepwood, mighty pirate.",
    "Ask me about Loom.",
    "Rise and shine, Mr. Freeman.",
    "This is my hiding spot, and I'm not moving until the situation is drastically improved.",
    "Assuming direct control.",
    "Constants and variables.",
    "Use bombs wisely.",
    "Wake me when you need me.",
    "Nothing happens.",
    "That doesn't seem to work.",
    "You need a bigger dungeon.",
    "Are you a bad enough dude to rescue the president?",
    "Say fuzzy pickles!",
    "You cannot grasp the grue form of Giygas' attack!",
    "Ness dug around in the trash can. There's a hamburger inside.",
    "Pictures taken instantaneously!",
    "I'm a photographic genius, if I do say so myself.",
    "The enemy left a present.",
    "Hello and...goodbye!",
    "A pencil-shaped iron statue is blocking the path.",
    "Boing!",
    "I am the third and strongest master of this hole.",
    "\"Yes\" is \"No\" and \"No\" is \"Yes.\" It makes perfect sense in Moonside.",
    "Welcome to Moonside. Wel Come to moo nsi ns dem oons ide.",
    "Ness's HP drops to 0!",
    "\"Yes\" means \"No\" \"No\" means \"Yes.\" Or did you already know this?",
    "If you stay here too long, you'll end up frying your brain. Yes, you will. No, you will... not. Yesno, you will won't.",
  )

private val BuddyPurple = Color(0xFF7553FF)
private val BuddyAccentBubbleChonk = Color(0xFF5827D6)
private val BuddyAccentBubbleShadow = Color(0xFF44208F)
private val BuddyAccentBubbleStart = Color(0xFF896CFF)
private val BuddyAccentBubbleEnd = Color(0xFF6948F5)
private val BuddyRed = Color(0xFFFF003D)
private val BuddySentryPink = Color(0xFFC85B9C)
private val BuddyRecordingBubbleChonk = Color(0xFFC10000)
private val BuddyRecordingBubbleShadow = Color(0xFF7E001A)
private val BuddyRecordingBubbleStart = Color(0xFFFF4D73)
private val BuddyRecordingBubbleEnd = Color(0xFFFF002B)
private val BuddyWarningBubbleChonk = Color(0xFF8A4200)
private val BuddyWarningBubbleShadow = Color(0xFF5A2800)
private val BuddyWarningBubbleStart = Color(0xFFFFB347)
private val BuddyWarningBubbleEnd = Color(0xFFFF7A00)
private val BuddyErrorBubbleChonk = Color(0xFFA4002B)
private val BuddyErrorBubbleShadow = Color(0xFF6B001C)
private val BuddyErrorBubbleStart = Color(0xFFFF5B7A)
private val BuddyErrorBubbleEnd = Color(0xFFFF003D)
private val BuddyGold = Color(0xFFC47A00)
private val BuddyQuickDecisionCard = Color(0xFFF0EAFF)
private val BuddyQuickDecisionPeek = Color(0xFFF8F5FF)
private val BuddyInk = Color(0xFF171426)
private val BuddyMuted = Color(0xFF6F6B7A)
private val BuddyBorder = Color(0xFFE0DDE6)
private val BuddyCode = Color(0xFFF3F1F6)
