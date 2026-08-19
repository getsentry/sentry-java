package io.sentry.android.buddy.ui.bottomsheet

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.BuddyHealthCheckState
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.BuddyHomeTab
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddySentryUiLinks
import io.sentry.android.buddy.ui.userflow.AnalyzingSheet
import io.sentry.android.buddy.ui.userflow.BriefingSheet
import io.sentry.android.buddy.ui.userflow.ErrorSheet
import io.sentry.android.buddy.ui.userflow.InsightsSheet
import io.sentry.android.buddy.ui.userflow.IntroSheet
import io.sentry.android.buddy.ui.userflow.StoppedSummarySheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuddySheet(
  state: SentryBuddySessionState,
  liveFeed: BuddyLiveFeed,
  healthCheckState: BuddyHealthCheckState,
  homeTab: BuddyHomeTab,
  homeRecommendations: List<BuddyHomeRecommendation>,
  recommendationError: String?,
  sentryUiLinks: BuddySentryUiLinks,
  nowMs: Long,
  onDispatch: (SentryBuddySessionController.() -> Unit) -> Unit,
  onAnalyze: () -> Unit,
  onExecuteRecommendationAction: (String, String) -> Unit,
  onDismissRecommendation: (String) -> Unit,
  onExecuteHomeRecommendationAction: (String, String) -> Unit,
  onDismissHomeRecommendation: (String) -> Unit,
  onMarkHomeRecommendationRead: (String) -> Unit,
  onSelectHomeTab: (BuddyHomeTab) -> Unit,
  onRunHealthCheck: () -> Unit,
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
    AnimatedContent(
      targetState = state,
      transitionSpec = {
        if (
          initialState is SentryBuddySessionState.Analyzing &&
            targetState is SentryBuddySessionState.Insights
        ) {
          ContentTransform(
            targetContentEnter =
              fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 80)) +
                slideInVertically(
                  animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                  initialOffsetY = { fullHeight -> fullHeight / 10 },
                ),
            initialContentExit =
              fadeOut(animationSpec = tween(durationMillis = 180)) +
                slideOutVertically(
                  animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                  targetOffsetY = { fullHeight -> -(fullHeight / 20) },
                ),
          )
        } else {
          ContentTransform(
            targetContentEnter = fadeIn(animationSpec = tween(durationMillis = 180)),
            initialContentExit = fadeOut(animationSpec = tween(durationMillis = 120)),
          )
        }
      },
      label = "buddy-sheet-content",
    ) {
      val sheetBodyModifier =
        if (it == SentryBuddySessionState.LiveFeed) {
          Modifier.fillMaxWidth().height(maxSheetHeight)
        } else {
          Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)
        }
      Column(
        modifier =
          sheetBodyModifier
            .verticalScroll(rememberScrollState())
            .padding(
              horizontal = if (it is SentryBuddySessionState.LiveFeed) 0.dp else 24.dp,
              vertical = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        when (it) {
          SentryBuddySessionState.LiveFeed ->
            BuddyHomeSheet(
              liveFeed,
              healthCheckState,
              homeTab,
              homeRecommendations,
              recommendationError,
              sentryUiLinks,
              nowMs,
              onDispatch,
              ::startRecordingAfterSheetExit,
              onExecuteHomeRecommendationAction,
              onDismissHomeRecommendation,
              onMarkHomeRecommendationRead,
              onSelectHomeTab,
              onRunHealthCheck,
              onOpenUrl,
            )

          SentryBuddySessionState.Intro -> IntroSheet(::startRecordingAfterSheetExit)
          is SentryBuddySessionState.StoppedSummary ->
            StoppedSummarySheet(it, sentryUiLinks, onDispatch, onOpenUrl)

          is SentryBuddySessionState.Briefing -> BriefingSheet(it, onDispatch, onAnalyze)
          is SentryBuddySessionState.Analyzing -> AnalyzingSheet(it)
          is SentryBuddySessionState.Insights ->
            InsightsSheet(
              it,
              sentryUiLinks,
              recommendationError,
              onDispatch,
              onExecuteRecommendationAction,
              onDismissRecommendation,
              onOpenUrl,
            )

          is SentryBuddySessionState.Error -> ErrorSheet(it, onDispatch)
          is SentryBuddySessionState.Recording,
          SentryBuddySessionState.Closed -> Unit
        }
      }
    }
  }
}
