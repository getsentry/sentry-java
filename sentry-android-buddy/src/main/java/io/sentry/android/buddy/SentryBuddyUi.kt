package io.sentry.android.buddy

import android.content.Context
import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
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
  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  var transientRecordingEvent by remember { mutableStateOf<TransientRecordingEvent?>(null) }
  val transientRecordingEventScope = rememberCoroutineScope()
  val analysisScope = rememberCoroutineScope()

  fun syncUiState() {
    state = controller.state
    liveFeed = controller.liveFeed
    healthCheckState = controller.healthCheckState
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
      transientRecordingEventScope.launch { liveFeed = feed }
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
      sentryUiLinks = controller.sentryUiLinks,
      nowMs = nowMs,
      onDispatch = { dispatch(it) },
      onAnalyze = { dispatchAnalysis { analyze() } },
      onResolveRecommendation = { recommendationId ->
        dispatchAnalysis { resolveRecommendation(recommendationId) }
      },
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
  val bubbleColor = if (isRecording) BuddyRed else BuddyPurple
  val attentionItem = liveFeed.latestUnviewedAdverseItem
  val attentionColor = attentionItem?.let { severityColor(it.severity) }
  val showAttentionFlames = !isRecording && attentionItem?.severity == Severity.HIGH
  val showAttentionSparks = !isRecording && attentionItem?.severity == Severity.MEDIUM
  val pulseScale = remember { Animatable(1f) }
  val stopTransition = rememberInfiniteTransition(label = "buddy-floating-stop-button")
  val attentionTransition = rememberInfiniteTransition(label = "buddy-attention-ornaments")
  val stopHaloScale by
    stopTransition.animateFloat(
      initialValue = 1.0f,
      targetValue = 1.14f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "buddy-floating-stop-button-halo-scale",
    )
  val stopHaloAlpha by
    stopTransition.animateFloat(
      initialValue = 0.12f,
      targetValue = 0.28f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "buddy-floating-stop-button-halo-alpha",
    )
  val attentionOrnamentPhase by
    attentionTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "buddy-attention-ornament-phase",
    )
  var bubbleOffset by remember { mutableStateOf<Offset?>(null) }

  LaunchedEffect(attentionItem?.id) {
    if (attentionItem != null) {
      pulseScale.snapTo(1f)
      pulseScale.animateTo(1.28f, animationSpec = tween(durationMillis = 700))
      pulseScale.snapTo(1f)
    }
  }

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
      if (showAttentionFlames) {
        AttentionFlames(
          phase = attentionOrnamentPhase,
          modifier =
            Modifier.size(width = 82.dp, height = 34.dp)
              .align(Alignment.TopCenter)
              .offset(y = (-27).dp),
        )
      } else if (showAttentionSparks) {
        AttentionSparks(
          phase = attentionOrnamentPhase,
          modifier =
            Modifier.size(width = 86.dp, height = 38.dp)
              .align(Alignment.TopCenter)
              .offset(y = (-29).dp),
        )
      }
      if (isRecording) {
        Box(
          modifier =
            Modifier.size(BuddyBubbleSize * stopHaloScale)
              .graphicsLayer { alpha = stopHaloAlpha }
              .background(BuddyRed, CircleShape)
        )
      }
      Box(
        modifier =
          Modifier.size(64.dp)
            .shadow(10.dp, CircleShape)
            .background(bubbleColor, CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .pointerInput(maxWidthPx, maxHeightPx) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                bubbleOffset = ((bubbleOffset ?: resolvedOffset) + dragAmount).constrain()
              }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
      ) {
        if (isRecording) {
          StopIcon(tint = Color.White, modifier = Modifier.size(22.dp))
        } else {
          SentryBuddyGlyph(tint = Color.White, modifier = Modifier.size(30.dp))
        }
      }
      if (attentionColor != null && !isRecording) {
        Box(
          modifier =
            Modifier.size(BuddyBubbleSize * pulseScale.value)
              .border(3.dp, attentionColor.copy(alpha = 0.35f), CircleShape)
        )
        Box(
          modifier =
            Modifier.size(BuddyBubbleSize + 10.dp).border(3.dp, attentionColor, CircleShape)
        )
      }
      if (liveFeed.unviewedAdverseCount > 0 && !isRecording) {
        Text(
          text =
            if (liveFeed.unviewedAdverseCount > 9) "9+"
            else liveFeed.unviewedAdverseCount.toString(),
          modifier =
            Modifier.align(Alignment.TopEnd)
              .offset(x = 6.dp, y = (-6).dp)
              .size(22.dp)
              .background(attentionColor ?: BuddyRed, CircleShape)
              .border(2.dp, Color.White, CircleShape),
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
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
}

@Composable
private fun AttentionFlames(phase: Float, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val pixel = min(size.width / 20f, size.height / 9f)
    val flameWidth = pixel * 18f
    val flameHeight = pixel * 8f
    val originX = (size.width - flameWidth) / 2f
    val baseY = size.height * 0.98f
    val originY = baseY - flameHeight
    val flicker = sin((phase * 2f * PI).toFloat()) > 0f

    fun drawPixel(col: Int, row: Int, color: Color) {
      drawRect(
        color = color,
        topLeft = Offset(originX + col * pixel, originY + row * pixel),
        size = Size(pixel * 0.96f, pixel * 0.96f),
      )
    }

    val orange = Color(0xFFFF6A00)
    val yellow = Color(0xFFFFC400)
    val outerFlame =
      listOf(
        4 to 0,
        10 to 0,
        14 to 0,
        3 to 1,
        4 to 1,
        5 to 1,
        9 to 1,
        10 to 1,
        11 to 1,
        14 to 1,
        2 to 2,
        3 to 2,
        4 to 2,
        5 to 2,
        6 to 2,
        8 to 2,
        9 to 2,
        10 to 2,
        11 to 2,
        12 to 2,
        13 to 2,
        14 to 2,
        15 to 2,
        1 to 3,
        2 to 3,
        3 to 3,
        4 to 3,
        5 to 3,
        6 to 3,
        7 to 3,
        8 to 3,
        9 to 3,
        10 to 3,
        11 to 3,
        12 to 3,
        13 to 3,
        14 to 3,
        15 to 3,
        16 to 3,
        0 to 4,
        1 to 4,
        2 to 4,
        3 to 4,
        4 to 4,
        5 to 4,
        6 to 4,
        7 to 4,
        8 to 4,
        9 to 4,
        10 to 4,
        11 to 4,
        12 to 4,
        13 to 4,
        14 to 4,
        15 to 4,
        16 to 4,
        17 to 4,
        0 to 5,
        1 to 5,
        2 to 5,
        3 to 5,
        4 to 5,
        5 to 5,
        6 to 5,
        7 to 5,
        8 to 5,
        9 to 5,
        10 to 5,
        11 to 5,
        12 to 5,
        13 to 5,
        14 to 5,
        15 to 5,
        16 to 5,
        17 to 5,
        1 to 6,
        2 to 6,
        3 to 6,
        4 to 6,
        5 to 6,
        6 to 6,
        7 to 6,
        8 to 6,
        9 to 6,
        10 to 6,
        11 to 6,
        12 to 6,
        13 to 6,
        14 to 6,
        15 to 6,
        16 to 6,
        2 to 7,
        3 to 7,
        4 to 7,
        5 to 7,
        6 to 7,
        7 to 7,
        8 to 7,
        9 to 7,
        10 to 7,
        11 to 7,
        12 to 7,
        13 to 7,
        14 to 7,
        15 to 7,
      )
    val animatedOuter = if (flicker) outerFlame + listOf(13 to 1, 16 to 2) else outerFlame
    animatedOuter.forEach { (col, row) -> drawPixel(col, row, BuddyRed) }

    listOf(
        4 to 2,
        9 to 2,
        10 to 2,
        13 to 2,
        3 to 3,
        4 to 3,
        5 to 3,
        8 to 3,
        9 to 3,
        10 to 3,
        11 to 3,
        12 to 3,
        13 to 3,
        14 to 3,
        2 to 4,
        3 to 4,
        4 to 4,
        5 to 4,
        6 to 4,
        8 to 4,
        9 to 4,
        10 to 4,
        11 to 4,
        12 to 4,
        13 to 4,
        14 to 4,
        15 to 4,
        2 to 5,
        3 to 5,
        4 to 5,
        5 to 5,
        6 to 5,
        7 to 5,
        8 to 5,
        9 to 5,
        10 to 5,
        11 to 5,
        12 to 5,
        13 to 5,
        14 to 5,
        15 to 5,
        3 to 6,
        4 to 6,
        5 to 6,
        6 to 6,
        7 to 6,
        8 to 6,
        9 to 6,
        10 to 6,
        11 to 6,
        12 to 6,
        13 to 6,
        14 to 6,
      )
      .forEach { (col, row) -> drawPixel(col, row, orange) }

    listOf(
        9 to 3,
        10 to 3,
        4 to 4,
        9 to 4,
        10 to 4,
        11 to 4,
        5 to 5,
        8 to 5,
        9 to 5,
        10 to 5,
        11 to 5,
        8 to 6,
        9 to 6,
        10 to 6,
      )
      .forEach { (col, row) -> drawPixel(col, row, yellow) }
  }
}

@Composable
private fun AttentionSparks(phase: Float, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val sparks =
      listOf(
        Spark(0.22f, 0.66f, BuddyGold, 0.0f),
        Spark(0.38f, 0.36f, BuddyRed.copy(alpha = 0.75f), 0.35f),
        Spark(0.61f, 0.30f, BuddyGold, 0.62f),
        Spark(0.78f, 0.64f, BuddyPurple.copy(alpha = 0.70f), 0.20f),
      )
    sparks.forEach { spark ->
      val wave = sin(((phase + spark.offset) * 2f * PI).toFloat())
      val twinkle = (0.58f + 0.32f * wave).coerceIn(0.35f, 0.95f)
      val center = Offset(size.width * spark.x, size.height * spark.y - wave * 2.2f)
      val radius = 2.6f + twinkle * 2f
      val color = spark.color.copy(alpha = twinkle)
      drawLine(
        color = color,
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = 2.2f,
      )
      drawLine(
        color = color,
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = 2.2f,
      )
      drawCircle(
        spark.color.copy(alpha = 0.35f * twinkle),
        radius = radius * 0.52f,
        center = center,
      )
    }
  }
}

private data class Spark(val x: Float, val y: Float, val color: Color, val offset: Float)

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
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onAnalyze: () -> Unit,
  onResolveRecommendation: (String) -> Unit,
  onRunHealthCheck: () -> Unit,
  onDismissHealthCheck: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  if (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) {
    return
  }
  val maxSheetHeight =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.75f
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
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(max = maxSheetHeight)
          .verticalScroll(rememberScrollState())
          .padding(
            horizontal = if (state is SentryBuddySessionState.LiveFeed) 0.dp else 24.dp,
            vertical = 24.dp,
          ),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when (state) {
        SentryBuddySessionState.LiveFeed ->
          LiveFeedSheet(
            liveFeed,
            healthCheckState,
            sentryUiLinks,
            nowMs,
            onDispatch,
            ::startRecordingAfterSheetExit,
            onRunHealthCheck,
            onDismissHealthCheck,
            onOpenUrl,
          )
        SentryBuddySessionState.Intro -> IntroSheet(::startRecordingAfterSheetExit)
        is SentryBuddySessionState.StoppedSummary -> StoppedSummarySheet(state, onDispatch)
        is SentryBuddySessionState.Briefing -> BriefingSheet(state, onDispatch, onAnalyze)
        is SentryBuddySessionState.Analyzing -> AnalyzingSheet(state)
        is SentryBuddySessionState.Insights ->
          InsightsSheet(state, onDispatch, onResolveRecommendation, onOpenUrl)
        is SentryBuddySessionState.Error -> ErrorSheet(state, onDispatch)
        is SentryBuddySessionState.Recording,
        SentryBuddySessionState.Closed -> Unit
      }
    }
  }
}

@Composable
private fun StopIcon(tint: Color, modifier: Modifier = Modifier) {
  Box(modifier = modifier.background(tint, RoundedCornerShape(4.dp)))
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
private fun LiveFeedSheet(
  liveFeed: BuddyLiveFeed,
  healthCheckState: BuddyHealthCheckState,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onStartRecording: () -> Unit,
  onRunHealthCheck: () -> Unit,
  onDismissHealthCheck: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  LiveFeedInset {
    SheetTitle(
      title = "Sentry Buddy",
      subtitle = "Live Feed",
      trailingContent = {
        HealthCheckActionButton(
          enabled = healthCheckState !is BuddyHealthCheckState.Running,
          onClick = onRunHealthCheck,
        )
      },
    )
  }
  val emptyAttentionArtIndex = remember { EmptyAttentionArtIndex.next() }
  LiveFeedInset { Spacer(Modifier.height(12.dp)) }
  AttentionCard(
    liveFeed = liveFeed,
    sentryUiLinks = sentryUiLinks,
    nowMs = nowMs,
    emptyArtIndex = emptyAttentionArtIndex,
    onDismiss = { onDispatch { dismissLiveFeedAttention() } },
    onOpenUrl = onOpenUrl,
  )
  LiveFeedInset {
    Spacer(Modifier.height(12.dp))
    Button(
      modifier = Modifier.fillMaxWidth().height(56.dp),
      colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
      onClick = onStartRecording,
    ) {
      BuddyButtonText("Start Recording")
    }
    Spacer(Modifier.height(12.dp))
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
  HealthCheckDialog(
    state = healthCheckState,
    onDismiss = onDismissHealthCheck,
    onRetry = onRunHealthCheck,
    onOpenUrl = onOpenUrl,
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
  Box(
    modifier =
      Modifier.size(40.dp)
        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
        .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    HealthCheckIcon(
      tint = if (enabled) BuddyPurple else BuddyMuted,
      modifier = Modifier.size(18.dp),
    )
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
      finding.link?.let { link ->
        TextButton(onClick = { onOpenUrl(context, link) }) { BuddyButtonText("Open Link") }
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
    val strokeWidth = size.minDimension * 0.10f
    val sheetWidth = size.width * 0.62f
    val sheetHeight = size.height * 0.80f
    val left = size.width * 0.08f
    val top = size.height * 0.10f
    drawRoundRect(
      color = tint,
      topLeft = Offset(left, top),
      size = Size(sheetWidth, sheetHeight),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
    )
    drawLine(
      color = tint,
      start = Offset(left + strokeWidth, top + size.height * 0.22f),
      end = Offset(left + sheetWidth - strokeWidth, top + size.height * 0.22f),
      strokeWidth = strokeWidth,
      pathEffect = PathEffect.cornerPathEffect(strokeWidth),
    )
    drawLine(
      color = tint,
      start = Offset(left + strokeWidth, top + size.height * 0.38f),
      end = Offset(left + sheetWidth * 0.72f, top + size.height * 0.38f),
      strokeWidth = strokeWidth,
      pathEffect = PathEffect.cornerPathEffect(strokeWidth),
    )
    drawCircle(
      color = Color.White,
      radius = size.width * 0.22f,
      center = Offset(size.width * 0.78f, size.height * 0.73f),
    )
    drawCircle(
      color = tint,
      radius = size.width * 0.22f,
      center = Offset(size.width * 0.78f, size.height * 0.73f),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
    )
    drawLine(
      color = tint,
      start = Offset(size.width * 0.69f, size.height * 0.73f),
      end = Offset(size.width * 0.76f, size.height * 0.80f),
      strokeWidth = strokeWidth,
      pathEffect = PathEffect.cornerPathEffect(strokeWidth),
    )
    drawLine(
      color = tint,
      start = Offset(size.width * 0.76f, size.height * 0.80f),
      end = Offset(size.width * 0.88f, size.height * 0.64f),
      strokeWidth = strokeWidth,
      pathEffect = PathEffect.cornerPathEffect(strokeWidth),
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
      Box(
        modifier =
          Modifier.matchParentSize().offset {
            IntOffset((dismissOffset.value + widthPx).roundToInt(), 0)
          }
      ) {
        EmptyAttentionArt(
          index = emptyArtIndex,
          modifier = Modifier.fillMaxSize(),
        )
      }
      Box(
        modifier =
          Modifier.matchParentSize()
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

private fun Modifier.attentionCardChrome(): Modifier = drawBehind {
  val stroke = 1.dp.toPx()
  val inset = stroke / 2f
  drawLine(
    color = BuddyBorder,
    start = Offset(0f, inset),
    end = Offset(size.width, inset),
    strokeWidth = stroke,
  )
  drawLine(
    color = BuddyBorder,
    start = Offset(0f, size.height - inset),
    end = Offset(size.width, size.height - inset),
    strokeWidth = stroke,
  )
}

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
    else -> R.drawable.buddy_attention_thankyou
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
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
) {
  val recording = state.result.recording
  SheetTitle("Recording Flow", "Everything stays on device")
  RecordingCard(recording)
  MetricGrid(recording)
  TimelinePreview(recording)
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
  var flowName by remember(state.result.recording.recording.id) { mutableStateOf(state.flowName) }
  var notes by
    remember(state.result.recording.recording.id) { mutableStateOf(state.developerNotes) }
  fun updateController() {
    onDispatch { updateBriefing(flowName, notes, state.focusAreas) }
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
        updateController()
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
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onResolveRecommendation: (String) -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  var isJsonDialogOpen by remember { mutableStateOf(false) }
  val flowName = state.result.recording.flow.name.ifBlank { "Unnamed flow" }
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
          onOpenLink = recommendation.link?.let { link -> { onOpenUrl(context, link) } },
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
    Surface(
      modifier =
        Modifier.weight(1f)
          .height(52.dp)
          .combinedClickable(
            onClick = { clipboard.setText(AnnotatedString(state.result.recordingJson)) },
            onLongClick = { isJsonDialogOpen = true },
          ),
      color = BuddyPurple,
      shape = RoundedCornerShape(28.dp),
    ) {
      Box(contentAlignment = Alignment.Center) { BuddyButtonText("Copy JSON", color = Color.White) }
    }
  }
  if (isJsonDialogOpen) {
    AlertDialog(
      onDismissRequest = { isJsonDialogOpen = false },
      confirmButton = {
        TextButton(onClick = { isJsonDialogOpen = false }) { BuddyButtonText("Close") }
      },
      title = { Text("Recording JSON", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = prettyPrintJson(state.result.recordingJson),
          modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
          fontFamily = FontFamily.Monospace,
          color = BuddyInk,
        )
      },
    )
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

private fun prettyPrintJson(value: String): String {
  val result = StringBuilder(value.length * 2)
  var indent = 0
  var inString = false
  var escaping = false

  value.forEach { char ->
    when {
      escaping -> {
        result.append(char)
        escaping = false
      }
      char == '\\' && inString -> {
        result.append(char)
        escaping = true
      }
      char == '"' -> {
        result.append(char)
        inString = !inString
      }
      inString -> result.append(char)
      char == '{' || char == '[' -> {
        result.append(char)
        indent++
        appendJsonNewLine(result, indent)
      }
      char == '}' || char == ']' -> {
        indent = (indent - 1).coerceAtLeast(0)
        appendJsonNewLine(result, indent)
        result.append(char)
      }
      char == ',' -> {
        result.append(char)
        appendJsonNewLine(result, indent)
      }
      char == ':' -> result.append(": ")
      !char.isWhitespace() -> result.append(char)
    }
  }

  return result.toString()
}

private fun appendJsonNewLine(result: StringBuilder, indent: Int) {
  result.append('\n')
  repeat(indent) { result.append("  ") }
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
            TextButton(onClick = onOpenLink) { BuddyButtonText("Open Link") }
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
private val BuddyBubbleMargin = 24.dp
private val BuddyBubbleInitialTop = 96.dp
private val BuddyBubbleTouchPadding = 20.dp
private val BuddyTransientTextWidth = 190.dp
private val BuddyTransientTextHeight = 28.dp
private val BuddyAttentionCardHeight = 264.dp
private val BuddySheetHorizontalPadding = 24.dp
private const val LIVE_FEED_VISIBLE_ITEM_LIMIT = 7
private const val EMPTY_ATTENTION_ART_VARIANTS = 10
private const val ANALYSIS_POLL_INTERVAL_MS = 1000L
private const val ANALYSIS_TIMEOUT_MS = 30_000L

private val BuddyPurple = Color(0xFF7553FF)
private val BuddyRed = Color(0xFFFF003D)
private val BuddyGold = Color(0xFFC47A00)
private val BuddyInk = Color(0xFF171426)
private val BuddyMuted = Color(0xFF6F6B7A)
private val BuddyBorder = Color(0xFFE0DDE6)
private val BuddyCode = Color(0xFFF3F1F6)
