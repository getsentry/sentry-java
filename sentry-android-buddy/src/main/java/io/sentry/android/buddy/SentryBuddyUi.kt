package io.sentry.android.buddy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
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

  Box(modifier = modifier.fillMaxSize()) {
    content()
    BuddyBubble(
      state = state,
      nowMs = nowMs,
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
  onClick: () -> Unit,
) {
  val isRecording = state is SentryBuddySessionState.Recording
  val bubbleColor = if (isRecording) BuddyRed else BuddyPurple
  val label = if (isRecording) "■" else "△"
  val elapsed =
    if (state is SentryBuddySessionState.Recording) {
      formatElapsed(nowMs - state.startedAtMs)
    } else {
      null
    }

  Column(
    modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier.size(64.dp)
          .shadow(10.dp, CircleShape)
          .background(bubbleColor, CircleShape)
          .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Text(label, color = Color.White, style = MaterialTheme.typography.headlineSmall)
      if (!isRecording) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .size(24.dp)
              .background(BuddyRed, CircleShape)
              .border(2.dp, Color.White, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Text("2", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
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
      Text("△", color = Color.White, style = MaterialTheme.typography.titleLarge)
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
    "Record a session to get a full picture",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = BuddyInk,
  )
  Text(
    "Recommendations catch problems one at a time. A recording captures the screens, steps, " +
      "and Sentry correlation while you use the app, then Buddy turns the trace into guidance.",
    color = BuddyMuted,
    style = MaterialTheme.typography.bodyLarge,
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
  RecommendationPreview()
}

@Composable
private fun RecommendationPreview() {
  Text("Recommendations", style = MaterialTheme.typography.labelLarge, color = BuddyMuted)
  Card(
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = CardDefaults.outlinedCardBorder(),
  ) {
    Column {
      PreviewRow("Unhandled Error on Login", "IllegalStateException thrown once while signing in.")
      HorizontalDivider()
      PreviewRow(
        "Home Has No Screen Transaction",
        "You have opened this screen 4 times with no transaction.",
      )
    }
  }
}

@Composable
private fun PreviewRow(title: String, body: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(modifier = Modifier.size(10.dp).background(BuddyPurple, CircleShape))
    Column {
      Text(title, fontWeight = FontWeight.Bold, color = BuddyInk)
      Text(body, color = BuddyMuted)
      Text("Just now", color = BuddyMuted, fontFamily = FontFamily.Monospace)
    }
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
    "Session Insights",
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
  Column(modifier = Modifier.border(1.dp, BuddyBorder, RoundedCornerShape(12.dp))) {
    state.response.recommendations.forEachIndexed { index, recommendation ->
      RecommendationRow(recommendation)
      if (index != state.response.recommendations.lastIndex) {
        HorizontalDivider()
      }
    }
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

private val BuddyPurple = Color(0xFF7553FF)
private val BuddyRed = Color(0xFFFF003D)
private val BuddyGold = Color(0xFFC47A00)
private val BuddyInk = Color(0xFF171426)
private val BuddyMuted = Color(0xFF6F6B7A)
private val BuddyBorder = Color(0xFFE0DDE6)
private val BuddyCode = Color(0xFFF3F1F6)
