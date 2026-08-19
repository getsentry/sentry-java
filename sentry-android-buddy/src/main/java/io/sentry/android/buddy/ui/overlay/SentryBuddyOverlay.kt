package io.sentry.android.buddy.ui.overlay

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import io.sentry.android.buddy.ANALYSIS_POLL_INTERVAL_MS
import io.sentry.android.buddy.ANALYSIS_TIMEOUT_MS
import io.sentry.android.buddy.SentryBuddySessionController
import io.sentry.android.buddy.SentryBuddySessionState
import io.sentry.android.buddy.TransientRecordingEvent
import io.sentry.android.buddy.model.BuddyHomeTab
import io.sentry.android.buddy.ui.bottomsheet.BuddySheet
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

@ApiStatus.Experimental
@Composable
public fun rememberSentryBuddySessionController(): SentryBuddySessionController = remember {
  SentryBuddySessionController()
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
internal fun SentryBuddyOverlayContent(
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
  var recommendationError by remember { mutableStateOf(controller.recommendationError) }
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
    recommendationError = controller.recommendationError
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

  LaunchedEffect(state) {
    if (state == SentryBuddySessionState.LiveFeed) {
      dispatchHealthCheck { runPendingHealthCheck() }
    }
  }

  LaunchedEffect(state, homeTab) {
    if (state == SentryBuddySessionState.LiveFeed && homeTab == BuddyHomeTab.ACTIONS) {
      dispatchAnalysis { refreshKnownFlowRecommendations() }
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
    )
    BuddySheet(
      state = state,
      liveFeed = liveFeed,
      healthCheckState = healthCheckState,
      homeTab = homeTab,
      homeRecommendations = homeRecommendations,
      recommendationError = recommendationError,
      sentryUiLinks = controller.sentryUiLinks,
      nowMs = nowMs,
      onDispatch = { dispatch(it) },
      onAnalyze = { dispatchAnalysis { analyze() } },
      onExecuteRecommendationAction = { recommendationId, actionId ->
        dispatchAnalysis { executeRecommendationAction(recommendationId, actionId) }
      },
      onDismissRecommendation = { recommendationId ->
        dispatchAnalysis { dismissRecommendation(recommendationId) }
      },
      onExecuteHomeRecommendationAction = { recommendationId, actionId ->
        dispatchAnalysis { executeHomeRecommendationAction(recommendationId, actionId) }
      },
      onDismissHomeRecommendation = { recommendationId ->
        dispatchAnalysis { dismissHomeRecommendation(recommendationId) }
      },
      onMarkHomeRecommendationRead = { recommendationId ->
        dispatch { markHomeRecommendationRead(recommendationId) }
      },
      onSelectHomeTab = { tab -> dispatch { selectHomeTab(tab) } },
      onRunHealthCheck = { dispatchHealthCheck { runHealthCheck() } },
      onOpenUrl = { context, url -> openUrl(context, url) },
    )
  }
}
