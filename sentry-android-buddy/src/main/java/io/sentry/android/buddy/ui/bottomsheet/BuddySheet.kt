package io.sentry.android.buddy.ui.bottomsheet

import android.content.Context
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.BuddyHealthCheckState
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.BuddyHomeTab
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyScreenScanState
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
  screenScanState: BuddyScreenScanState,
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
  onDismissScreenScan: () -> Unit,
  onOpenUrl: (Context, String) -> Unit,
) {
  if (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) {
    return
  }
  if (
    state is SentryBuddySessionState.LiveFeed && screenScanState is BuddyScreenScanState.Scanning
  ) {
    return
  }
  val maxSheetHeight = LocalWindowInfo.current.containerSize.height.dp * 0.75f
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
          if (screenScanState is BuddyScreenScanState.Results) {
            ScreenInstrumentationSheet(
              result = screenScanState.result,
              onDismiss = onDismissScreenScan,
              onShowRecommendations = {
                onDismissScreenScan()
                onSelectHomeTab(BuddyHomeTab.ACTIONS)
              },
            )
          } else {
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
          }

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
