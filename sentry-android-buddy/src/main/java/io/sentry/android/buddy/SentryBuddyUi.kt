package io.sentry.android.buddy

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
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
  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  var isRecordingHelpOpen by remember { mutableStateOf(false) }
  var transientRecordingEvent by remember { mutableStateOf<TransientRecordingEvent?>(null) }
  val transientRecordingEventScope = rememberCoroutineScope()
  val analysisScope = rememberCoroutineScope()

  fun dispatch(action: SentryBuddySessionController.() -> Unit) {
    controller.action()
    state = controller.state
  }

  fun dispatchAnalysis(action: SentryBuddySessionController.() -> Unit) {
    analysisScope.launch {
      withContext(Dispatchers.IO) { controller.action() }
      state = controller.state
    }
  }

  LaunchedEffect(state) {
    if (state is SentryBuddySessionState.Recording) {
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
      nowMs = nowMs,
      maxWidthPx = maxWidthPx,
      maxHeightPx = maxHeightPx,
      bubbleHitBounds = bubbleHitBounds,
      transientEvent = transientRecordingEvent,
      onClick = {
        when (state) {
          SentryBuddySessionState.Closed -> dispatch { open() }
          is SentryBuddySessionState.Recording ->
            dispatch {
              stopRecording()
              briefRecording()
            }
          else -> dispatch { close() }
        }
      },
      onLongClick = {
        if (state is SentryBuddySessionState.Recording) {
          isRecordingHelpOpen = true
        }
      },
      onStopAndAnalyze = {
        isRecordingHelpOpen = false
        dispatch {
          stopRecording()
          briefRecording()
        }
      },
      onDismissRecordingHelp = { isRecordingHelpOpen = false },
      isRecordingHelpOpen = isRecordingHelpOpen,
    )
    BuddySheet(
      state = state,
      onDispatch = { dispatch(it) },
      onAnalyze = { dispatchAnalysis { analyze() } },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.BuddyBubble(
  state: SentryBuddySessionState,
  nowMs: Long,
  maxWidthPx: Float,
  maxHeightPx: Float,
  bubbleHitBounds: BuddyOverlayHitBounds?,
  transientEvent: TransientRecordingEvent?,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onStopAndAnalyze: () -> Unit,
  onDismissRecordingHelp: () -> Unit,
  isRecordingHelpOpen: Boolean,
) {
  val density = LocalDensity.current
  val bubbleSizePx = with(density) { BuddyBubbleSize.toPx() }
  val bubbleMarginPx = with(density) { BuddyBubbleMargin.toPx() }
  val initialTopPx = with(density) { BuddyBubbleInitialTop.toPx() }
  val isRecording = state is SentryBuddySessionState.Recording
  val bubbleColor = if (isRecording) BuddyRed else BuddyPurple
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
    if (isRecording && isRecordingHelpOpen && state is SentryBuddySessionState.Recording) {
      RecordingTooltip(
        state = state,
        nowMs = nowMs,
        onStopAndAnalyze = onStopAndAnalyze,
        onDismiss = onDismissRecordingHelp,
      )
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
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onAnalyze: () -> Unit,
) {
  if (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) {
    return
  }

  ModalBottomSheet(
    onDismissRequest = { onDispatch { close() } },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = Color.White,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when (state) {
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
private fun SentryBuddyGlyph(tint: Color, modifier: Modifier = Modifier) {
  Icon(
    painter = painterResource(id = R.drawable.sentry_buddy_glyph_light),
    contentDescription = null,
    modifier = modifier,
    tint = tint,
  )
}

@Composable
private fun RecordingTooltip(
  state: SentryBuddySessionState.Recording,
  nowMs: Long,
  onStopAndAnalyze: () -> Unit,
  onDismiss: () -> Unit,
) {
  val durationMs = nowMs - state.startedAtMs
  Card(
    modifier = Modifier.width(300.dp).shadow(12.dp, RoundedCornerShape(20.dp)),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = CardDefaults.outlinedCardBorder(),
    shape = RoundedCornerShape(20.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Recording Flow", color = BuddyInk, fontWeight = FontWeight.Bold)
          Text(
            "Everything stays on device",
            color = BuddyMuted,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        TextButton(onClick = onDismiss) { Text("Close") }
      }
      ActiveRecordingCard(durationMs)
      ActiveTimelinePreview(state.intent, durationMs)
      Button(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BuddyRed),
        onClick = onStopAndAnalyze,
      ) {
        BuddyButtonText("Stop and Analyze")
      }
    }
  }
}

@Composable
private fun ActiveRecordingCard(durationMs: Long) {
  Card(
    colors = CardDefaults.cardColors(containerColor = BuddyRed.copy(alpha = 0.10f)),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("●  Recording", color = BuddyRed, fontWeight = FontWeight.Bold)
      Text(
        formatElapsed(durationMs),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = BuddyInk,
      )
    }
  }
}

@Composable
private fun ActiveTimelinePreview(intent: BuddyFlowIntent, durationMs: Long) {
  Text("Live Trace", style = MaterialTheme.typography.labelLarge, color = BuddyMuted)
  TimelineRows(
    items =
      listOf(
        TimelinePreviewItem(0, "recording_started ${intent.name}", BuddyPurple),
        TimelinePreviewItem(durationMs, "recording_in_progress", BuddyRed),
      )
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
private const val ANALYSIS_POLL_INTERVAL_MS = 1000L
private const val ANALYSIS_TIMEOUT_MS = 30_000L

private val BuddyPurple = Color(0xFF7553FF)
private val BuddyRed = Color(0xFFFF003D)
private val BuddyGold = Color(0xFFC47A00)
private val BuddyInk = Color(0xFF171426)
private val BuddyMuted = Color(0xFF6F6B7A)
private val BuddyBorder = Color(0xFFE0DDE6)
private val BuddyCode = Color(0xFFF3F1F6)
