package io.sentry.android.buddy.ui.userflow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.R
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.model.BuddyFocusArea
import io.sentry.android.buddy.ui.common.BuddyButtonText
import io.sentry.android.buddy.ui.common.BuddyLabelPill
import io.sentry.android.buddy.ui.common.SheetTitle
import io.sentry.android.buddy.ui.common.formatElapsed
import io.sentry.android.buddy.ui.common.theme.BuddyGreen
import io.sentry.android.buddy.ui.common.theme.BuddyInk
import io.sentry.android.buddy.ui.common.theme.BuddyMuted
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.common.theme.BuddyQuickDecisionCard
import io.sentry.android.buddy.ui.common.theme.BuddyQuickDecisionPeek
import io.sentry.android.buddy.ui.common.theme.BuddyQuickDecisionStackHeight
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import io.sentry.android.buddy.ui.preview.previewRecordingResult

@Composable
internal fun BriefingSheet(
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
      colors = ButtonDefaults.buttonColors(containerColor = BuddyGreen),
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

internal data class QuickDecisionCard(
  val id: String,
  val eyebrow: String,
  val title: String,
  val detail: String,
  val options: List<QuickDecisionOption>,
)

internal data class QuickDecisionOption(val value: String, val label: String)

@Composable
internal fun QuickDecisionCardStack(
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
internal fun QuickDecisionThankYouCard() {
  val alpha = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    alpha.snapTo(0f)
    alpha.animateTo(1f, animationSpec = tween(durationMillis = 180))
  }
  Surface(
    modifier =
      Modifier.fillMaxWidth().height(BuddyQuickDecisionStackHeight).graphicsLayer {
        this.alpha = alpha.value
      },
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
internal fun QuickDecisionCardPeek(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    color = BuddyQuickDecisionPeek,
    shape = RoundedCornerShape(20.dp),
    border = CardDefaults.outlinedCardBorder(),
  ) {}
}

@Composable
internal fun QuickDecisionCardView(
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
        BuddyLabelPill(card.eyebrow, BuddyPurple)
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

internal fun demoQuickDecisionCards(): List<QuickDecisionCard> =
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

internal fun List<QuickDecisionCard>.nextUnansweredIndex(answers: Map<String, String>): Int =
  indexOfFirst { answers[it.id] == null }
    .let { index ->
      if (index == -1) size else index
    }

internal fun String.withQuickDecisionAnswers(
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

@Preview(name = "Sheet · briefing", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun BriefingSheetPreview() {
  BuddyPreviewSurface {
    BriefingSheet(
      state =
        SentryBuddySessionState.Briefing(
          result = previewRecordingResult,
          flowName = "Sign in",
          developerNotes = "Sign in feels slow on a cold start.",
          focusAreas = setOf(BuddyFocusArea.NETWORK_TIMING),
        ),
      onDispatch = {},
      onAnalyze = {},
    )
  }
}
