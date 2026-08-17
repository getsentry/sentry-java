package io.sentry.android.buddy

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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

  fun dispatch(action: SentryBuddySessionController.() -> Unit) {
    controller.action()
    state = controller.state
  }

  LaunchedEffect(state) {
    if (state is SentryBuddySessionState.Recording) {
      while (true) {
        nowMs = System.currentTimeMillis()
        delay(1000)
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
      onClick = {
        when (state) {
          SentryBuddySessionState.Closed -> dispatch { open() }
          is SentryBuddySessionState.Recording -> dispatch { stopRecording() }
          else -> dispatch { close() }
        }
      },
    )
    BuddySheet(state = state, onDispatch = { dispatch(it) })
  }
}

@Composable
private fun BoxScope.BuddyBubble(
  state: SentryBuddySessionState,
  nowMs: Long,
  maxWidthPx: Float,
  maxHeightPx: Float,
  bubbleHitBounds: BuddyOverlayHitBounds?,
  onClick: () -> Unit,
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
          .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      if (isRecording) {
        Text("■", color = Color.White, style = MaterialTheme.typography.headlineSmall)
      } else {
        SentryBuddyGlyph(tint = Color.White, modifier = Modifier.size(30.dp))
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
        is SentryBuddySessionState.Briefing -> BriefingSheet(state, onDispatch)
        is SentryBuddySessionState.Analyzing -> AnalyzingSheet(state)
        is SentryBuddySessionState.Insights -> InsightsSheet(state, onDispatch)
        is SentryBuddySessionState.Error -> ErrorSheet(state, onDispatch)
        SentryBuddySessionState.Closed,
        is SentryBuddySessionState.Recording -> Unit
      }
    }
  }
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
  SheetTitle("Sentry Buddy", "Debug build • v${BuildConfig.VERSION_NAME}")
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
    Text("●  Start Recording", fontWeight = FontWeight.Bold)
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
  Canvas(modifier = modifier) {
    val strokeWidth = size.minDimension * 0.11f
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val left = size.width * 0.10f
    val top = size.height * 0.12f
    val arcSize = size.minDimension * 0.72f
    drawArc(
      color = tint,
      startAngle = -58f,
      sweepAngle = 116f,
      useCenter = false,
      topLeft = Offset(left, top),
      size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
      style = stroke,
    )
    drawArc(
      color = tint,
      startAngle = -58f,
      sweepAngle = 116f,
      useCenter = false,
      topLeft = Offset(left + size.width * 0.18f, top + size.height * 0.18f),
      size = androidx.compose.ui.geometry.Size(arcSize * 0.55f, arcSize * 0.55f),
      style = stroke,
    )
    drawLine(
      color = tint,
      start = Offset(size.width * 0.18f, size.height * 0.86f),
      end = Offset(size.width * 0.82f, size.height * 0.86f),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
  }
}

@Composable
private fun StoppedSummarySheet(
  state: SentryBuddySessionState.StoppedSummary,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
) {
  val recording = state.result.recording
  val clipboard = LocalClipboardManager.current
  SheetTitle("Recording Session", "Everything stayed on device")
  RecordingCard(recording)
  MetricGrid(recording)
  TimelinePreview(recording)
  Button(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    colors = ButtonDefaults.buttonColors(containerColor = BuddyRed),
    onClick = { onDispatch { briefRecording() } },
  ) {
    Text("■  Stop and Analyze", fontWeight = FontWeight.Bold)
  }
  OutlinedButton(
    modifier = Modifier.fillMaxWidth(),
    onClick = { clipboard.setText(AnnotatedString(state.result.recordingJson)) },
  ) {
    Text("Copy Recording JSON")
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
    MetricCard(recording.summary.stepCount.toString(), "Steps", Modifier.weight(1f), BuddyRed)
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
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    recording.timeline.takeLast(5).forEach { item ->
      Text(
        "${formatElapsed(item.elapsedMs)}  ${item.type.value} ${item.name.orEmpty()}",
        color = if (item.type == BuddyTimelineItem.Type.STEP) BuddyRed else BuddyMuted,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BriefingSheet(
  state: SentryBuddySessionState.Briefing,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
) {
  var flowName by remember(state.result.recording.recording.id) { mutableStateOf(state.flowName) }
  var notes by
    remember(state.result.recording.recording.id) { mutableStateOf(state.developerNotes) }
  var focusAreas by
    remember(state.result.recording.recording.id) { mutableStateOf(state.focusAreas) }
  fun updateController() {
    onDispatch { updateBriefing(flowName, notes, focusAreas) }
  }

  SheetTitle("Brief Seer", "Session • ${formatElapsed(state.result.recording.summary.durationMs)}")
  Text(
    "What were you doing?",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
  )
  Text(
    "Seer has the trace. Tell it what mattered and it will weight the analysis toward that.",
    color = BuddyMuted,
  )
  Text("The flow you recorded", fontWeight = FontWeight.Bold, color = BuddyMuted)
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    listOf("Login", "Sign-up", "Checkout", "Feed scroll", "Cold start", "Settings").forEach { label
      ->
      SelectablePill(label = label, selected = flowName == label) {
        flowName = label
        updateController()
      }
    }
  }
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
  Text("Where to look first", fontWeight = FontWeight.Bold, color = BuddyMuted)
  Column(modifier = Modifier.border(1.dp, BuddyBorder, RoundedCornerShape(12.dp))) {
    BuddyFocusArea.entries.forEach { focusArea ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              focusAreas =
                if (focusAreas.contains(focusArea)) focusAreas - focusArea
                else focusAreas + focusArea
              updateController()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Checkbox(checked = focusAreas.contains(focusArea), onCheckedChange = null)
        Text(focusArea.label, modifier = Modifier.weight(1f))
      }
      if (focusArea != BuddyFocusArea.entries.last()) {
        HorizontalDivider()
      }
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Button(
      modifier = Modifier.weight(1f).height(56.dp),
      colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
      onClick = {
        onDispatch {
          updateBriefing(flowName, notes, focusAreas)
          analyze()
        }
      },
    ) {
      Text("Analyze", fontWeight = FontWeight.Bold)
    }
    OutlinedButton(modifier = Modifier.height(56.dp), onClick = { onDispatch { analyze() } }) {
      Text("Skip")
    }
  }
}

@Composable
private fun SelectablePill(label: String, selected: Boolean, onClick: () -> Unit) {
  val borderColor = if (selected) BuddyPurple else BuddyBorder
  val backgroundColor = if (selected) BuddyPurple.copy(alpha = 0.10f) else Color.White
  Text(
    text = label,
    modifier =
      Modifier.border(1.dp, borderColor, RoundedCornerShape(50))
        .background(backgroundColor, RoundedCornerShape(50))
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    color = if (selected) BuddyPurple else BuddyInk,
    fontWeight = FontWeight.Bold,
  )
}

@Composable
private fun AnalyzingSheet(state: SentryBuddySessionState.Analyzing) {
  SheetTitle("Analyzing", "Session • ${formatElapsed(state.request.recording.summary.durationMs)}")
  Text(
    "Flow Analysis Submitted",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
  )
  listOf(
      "POST /v1/flow-analyses accepted",
      "GET /v1/flow-analyses/${state.submission.id}",
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
  SheetTitle(
    "Flow insights",
    "Session • ${formatElapsed(state.request.recording.summary.durationMs)}",
  )
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    MetricCard(state.response.insights.size.toString(), "Insights", Modifier.weight(1f), BuddyRed)
    MetricCard(
      state.request.recording.summary.stepCount.toString(),
      "Steps",
      Modifier.weight(1f),
      BuddyGold,
    )
    MetricCard(
      state.request.recording.summary.screenCount.toString(),
      "Screens",
      Modifier.weight(1f),
      BuddyPurple,
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
      Text("Record Again", fontWeight = FontWeight.Bold)
    }
    Button(
      modifier = Modifier.weight(1f).height(52.dp),
      colors = ButtonDefaults.buttonColors(containerColor = BuddyPurple),
      onClick = { clipboard.setText(AnnotatedString(state.request.recordingJson)) },
    ) {
      Text("Copy JSON", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun RecommendationRow(recommendation: BuddyRecommendation) {
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
          Modifier.size(10.dp).background(priorityColor(recommendation.priority), CircleShape)
      )
      Text(recommendation.title, color = BuddyInk, fontWeight = FontWeight.Bold)
    }
    Text(recommendation.body, color = BuddyMuted)
    Text(
      "${recommendation.category.label} • ${recommendation.priority.label}",
      color = BuddyPurple,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
    )
    recommendation.codeSnippet?.let {
      Surface(color = BuddyCode, shape = RoundedCornerShape(8.dp)) {
        Text(
          text = it,
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          fontFamily = FontFamily.Monospace,
          color = BuddyInk,
        )
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

private fun priorityColor(priority: BuddyRecommendationPriority): Color =
  when (priority) {
    BuddyRecommendationPriority.HIGH -> BuddyRed
    BuddyRecommendationPriority.MEDIUM -> BuddyGold
    BuddyRecommendationPriority.LOW -> BuddyPurple
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

private val BuddyPurple = Color(0xFF7553FF)
private val BuddyRed = Color(0xFFFF003D)
private val BuddyGold = Color(0xFFC47A00)
private val BuddyInk = Color(0xFF171426)
private val BuddyMuted = Color(0xFF6F6B7A)
private val BuddyBorder = Color(0xFFE0DDE6)
private val BuddyCode = Color(0xFFF3F1F6)
