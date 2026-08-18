package io.sentry.android.buddy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
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
  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  var transientRecordingEvent by remember { mutableStateOf<TransientRecordingEvent?>(null) }
  val transientRecordingEventScope = rememberCoroutineScope()
  val analysisScope = rememberCoroutineScope()

  fun dispatch(action: SentryBuddySessionController.() -> Unit) {
    controller.action()
    state = controller.state
    liveFeed = controller.liveFeed
    nowMs = System.currentTimeMillis()
  }

  fun dispatchAnalysis(action: SentryBuddySessionController.() -> Unit) {
    analysisScope.launch {
      withContext(Dispatchers.IO) { controller.action() }
      state = controller.state
      liveFeed = controller.liveFeed
      nowMs = System.currentTimeMillis()
    }
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
      sentryUiLinks = controller.sentryUiLinks,
      nowMs = nowMs,
      onDispatch = { dispatch(it) },
      onAnalyze = { dispatchAnalysis { analyze() } },
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
    val baseY = size.height * 0.92f
    val centers = listOf(0.28f, 0.50f, 0.72f)
    centers.forEachIndexed { index, centerFraction ->
      val wave = sin(((phase + index * 0.23f) * 2f * PI).toFloat())
      val centerX = size.width * centerFraction + wave * 2.4f
      val height = size.height * (0.56f + index * 0.08f) + wave * 2f
      val width = size.width * (0.085f + index * 0.012f)
      val tipY = baseY - height
      val outerFlame =
        Path().apply {
          moveTo(centerX, tipY)
          cubicTo(
            centerX - width * 1.35f,
            tipY + height * 0.38f,
            centerX - width,
            baseY,
            centerX,
            baseY,
          )
          cubicTo(
            centerX + width,
            baseY,
            centerX + width * 1.35f,
            tipY + height * 0.38f,
            centerX,
            tipY,
          )
        }
      drawPath(outerFlame, BuddyRed.copy(alpha = 0.86f))

      val innerHeight = height * 0.58f
      val innerWidth = width * 0.52f
      val innerTipY = baseY - innerHeight
      val innerFlame =
        Path().apply {
          moveTo(centerX, innerTipY)
          cubicTo(
            centerX - innerWidth,
            innerTipY + innerHeight * 0.45f,
            centerX - innerWidth * 0.78f,
            baseY,
            centerX,
            baseY,
          )
          cubicTo(
            centerX + innerWidth * 0.78f,
            baseY,
            centerX + innerWidth,
            innerTipY + innerHeight * 0.45f,
            centerX,
            innerTipY,
          )
        }
      drawPath(innerFlame, BuddyGold.copy(alpha = 0.88f))
    }
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
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onAnalyze: () -> Unit,
) {
  if (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) {
    return
  }
  val maxSheetHeight =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.75f

  ModalBottomSheet(
    onDismissRequest = { onDispatch { close() } },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = Color.White,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(max = maxSheetHeight)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when (state) {
        SentryBuddySessionState.LiveFeed ->
          LiveFeedSheet(liveFeed, sentryUiLinks, nowMs, onDispatch)
        SentryBuddySessionState.Intro -> IntroSheet(onDispatch)
        is SentryBuddySessionState.StoppedSummary -> StoppedSummarySheet(state, onDispatch)
        is SentryBuddySessionState.Briefing -> BriefingSheet(state, onDispatch, onAnalyze)
        is SentryBuddySessionState.Analyzing -> AnalyzingSheet(state)
        is SentryBuddySessionState.Insights -> InsightsSheet(state, onDispatch)
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
private fun SheetTitle(title: String, subtitle: String) {
  Row(
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
  }
}

@Composable
private fun IntroSheet(onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit) {
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
    onClick = { onDispatch { startRecording() } },
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
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
) {
  SheetTitle("Sentry Buddy", "Live Feed")
  val emptyAttentionArtIndex = remember { EmptyAttentionArtIndex.next() }
  AttentionCard(
    liveFeed = liveFeed,
    sentryUiLinks = sentryUiLinks,
    nowMs = nowMs,
    emptyArtIndex = emptyAttentionArtIndex,
    onDismiss = { onDispatch { dismissLiveFeedAttention() } },
  )
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
    onClick = { onDispatch { startRecording() } },
  ) {
    BuddyButtonText("Start Recording")
  }
  Text(
    "Live feed",
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  if (liveFeed.items.isEmpty()) {
    EmptyLiveFeedCard()
  } else {
    LiveFeedRows(liveFeed.items.take(LIVE_FEED_VISIBLE_ITEM_LIMIT), sentryUiLinks, nowMs)
  }
}

@Composable
private fun AttentionCard(
  liveFeed: BuddyLiveFeed,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  emptyArtIndex: Int,
  onDismiss: () -> Unit,
) {
  val item = liveFeed.latestUnviewedAdverseItem
  val dismissOffset = remember(item?.id) { Animatable(0f) }
  val headerAlpha =
    if (item == null) {
      0f
    } else {
      (1f - (-dismissOffset.value / ATTENTION_HEADER_FADE_DISTANCE_PX)).coerceIn(0f, 1f)
    }
  Text(
    "Needs attention",
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = BuddyInk.copy(alpha = headerAlpha),
  )

  if (item == null) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = Color.Transparent,
      shape = RoundedCornerShape(16.dp),
      border = CardDefaults.outlinedCardBorder(),
    ) {
      EmptyAttentionArt(
        index = emptyArtIndex,
        modifier = Modifier.fillMaxWidth().height(132.dp).padding(18.dp),
      )
    }
    return
  }

  val color = severityColor(item.severity)
  val context = LocalContext.current
  val link = sentryUiLinks.linkFor(item)
  val dismissScope = rememberCoroutineScope()
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.Transparent,
    shape = RoundedCornerShape(16.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxWidth().heightIn(min = 132.dp).pointerInput(item.id) {
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
          modifier = Modifier.fillMaxSize().padding(18.dp),
        )
      }
      Box(
        modifier =
          Modifier.matchParentSize()
            .offset { IntOffset(dismissOffset.value.roundToInt(), 0) }
            .clickable(enabled = link != null) { link?.let { openSentryLink(context, it) } }
      ) {
        AttentionItemContent(
          item = item,
          liveFeed = liveFeed,
          color = color,
          nowMs = nowMs,
        )
      }
    }
  }
}

@Composable
private fun AttentionItemContent(
  item: BuddyLiveFeedItem,
  liveFeed: BuddyLiveFeed,
  color: Color,
  nowMs: Long,
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
      LiveFeedCategoryPill(item.category.label, color)
      Text(
        item.title(),
        modifier = Modifier.weight(1f),
        color = BuddyInk,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
      )
      Text(
        relativeTime(item.timestamp.time, nowMs),
        color = BuddyMuted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Normal,
      )
    }
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
private fun EmptyAttentionArt(index: Int, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val variant = index % EMPTY_ATTENTION_ART_VARIANTS
    val palette =
      when (variant % 5) {
        0 -> listOf(BuddyPurple, BuddyRed, BuddyGold)
        1 -> listOf(BuddyRed, BuddyPurple, BuddyMuted)
        2 -> listOf(BuddyGold, BuddyPurple, BuddyRed)
        3 -> listOf(BuddyPurple, BuddyMuted, BuddyGold)
        else -> listOf(BuddyMuted, BuddyRed, BuddyPurple)
      }
    val w = size.width
    val h = size.height
    val center = Offset(w * (0.46f + (variant % 3) * 0.04f), h * 0.50f)
    drawCircle(palette[0].copy(alpha = 0.12f), radius = h * 0.46f, center = center)
    drawCircle(
      palette[1].copy(alpha = 0.16f),
      radius = h * 0.26f,
      center = Offset(w * 0.68f, h * 0.34f),
    )
    drawCircle(
      palette[2].copy(alpha = 0.18f),
      radius = h * 0.18f,
      center = Offset(w * 0.28f, h * 0.72f),
    )
    val glyph =
      Path().apply {
        moveTo(w * 0.45f, h * 0.18f)
        lineTo(w * 0.27f, h * 0.74f)
        lineTo(w * 0.72f, h * 0.74f)
        close()
      }
    drawPath(glyph, palette[0].copy(alpha = 0.20f))
    drawLine(palette[0], Offset(w * 0.38f, h * 0.58f), Offset(w * 0.58f, h * 0.58f), 5f)
    drawLine(palette[1], Offset(w * 0.42f, h * 0.46f), Offset(w * 0.62f, h * 0.46f), 4f)
    drawLine(palette[2], Offset(w * 0.46f, h * 0.34f), Offset(w * 0.66f, h * 0.34f), 3f)
  }
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
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
) {
  val context = LocalContext.current
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.Transparent,
    shape = RoundedCornerShape(16.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      items.forEach { item ->
        val color =
          if (item.adverse) severityColor(item.severity) else timelineColor(item.timelineItem)
        val link = sentryUiLinks.linkFor(item)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable(enabled = link != null) { link?.let { openSentryLink(context, it) } }
              .padding(vertical = 7.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LiveFeedCategoryPill(item.category.label, color)
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

private fun BuddyLiveFeedItem.screenContextText(): String? {
  if (visibleScreens.isEmpty()) {
    return null
  }
  val label = if (visibleScreens.size == 1) "Screen" else "Screens"
  return "$label: ${visibleScreens.joinToString(" -> ")}"
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

private fun openSentryLink(context: Context, link: String) {
  try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
  } catch (_: ActivityNotFoundException) {
    // A debug overlay should not crash the app when no browser can handle the link.
  }
}

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
) {
  val clipboard = LocalClipboardManager.current
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
  Surface(
    modifier = Modifier.fillMaxWidth().border(1.dp, BuddyBorder, RoundedCornerShape(12.dp)),
    color = Color.White,
    shape = RoundedCornerShape(12.dp),
  ) {
    Text(
      text = state.response.recommendationsText.ifBlank { "No recommendations returned yet." },
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      color = BuddyInk,
      style = MaterialTheme.typography.bodyMedium,
    )
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
private fun RecommendationRow(recommendation: Recommendation) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(10.dp).background(severityColor(recommendation.severity), CircleShape)
      )
      Text(recommendation.title, color = BuddyInk, fontWeight = FontWeight.Bold)
    }
    Text(recommendation.description, color = BuddyMuted)
    Text(
      "${recommendation.severity.value} • ${recommendation.status.value}",
      color = BuddyPurple,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
    )
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
private const val LIVE_FEED_VISIBLE_ITEM_LIMIT = 7
private const val EMPTY_ATTENTION_ART_VARIANTS = 10
private const val ATTENTION_HEADER_FADE_DISTANCE_PX = 180f
private const val ANALYSIS_POLL_INTERVAL_MS = 1000L
private const val ANALYSIS_TIMEOUT_MS = 30_000L

private val BuddyPurple = Color(0xFF7553FF)
private val BuddyRed = Color(0xFFFF003D)
private val BuddyGold = Color(0xFFC47A00)
private val BuddyInk = Color(0xFF171426)
private val BuddyMuted = Color(0xFF6F6B7A)
private val BuddyBorder = Color(0xFFE0DDE6)
private val BuddyCode = Color(0xFFF3F1F6)
