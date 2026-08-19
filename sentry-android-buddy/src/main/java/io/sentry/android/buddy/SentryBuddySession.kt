package io.sentry.android.buddy

import android.content.Context
import io.sentry.Sentry
import io.sentry.android.buddy.bridge.BuddyFlowRecordingJsonSerializer
import io.sentry.android.buddy.bridge.BuddyHealthCheckCapture
import io.sentry.android.buddy.bridge.DummySentryBuddyFlowAnalysesApi
import io.sentry.android.buddy.bridge.DummySentryBuddyHealthCheckApi
import io.sentry.android.buddy.bridge.DummySentryBuddyOpenUrlApi
import io.sentry.android.buddy.bridge.SentryBuddyFlowAnalysesApi
import io.sentry.android.buddy.bridge.SentryBuddyHealthCheckApi
import io.sentry.android.buddy.bridge.SentryBuddyOpenUrlApi
import io.sentry.android.buddy.model.ActionStatus
import io.sentry.android.buddy.model.AnalysisStatus
import io.sentry.android.buddy.model.BuddyAnalysisResponse
import io.sentry.android.buddy.model.BuddyFlowImportance
import io.sentry.android.buddy.model.BuddyFlowIntent
import io.sentry.android.buddy.model.BuddyFocusArea
import io.sentry.android.buddy.model.BuddyHealthCheckResponse
import io.sentry.android.buddy.model.BuddyHomeRecommendation
import io.sentry.android.buddy.model.BuddyHomeTab
import io.sentry.android.buddy.model.BuddyInsight
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyRecommendationSource
import io.sentry.android.buddy.model.BuddyRecordingResult
import io.sentry.android.buddy.model.BuddySdkConfigSnapshot
import io.sentry.android.buddy.model.BuddySentryUiLinks
import io.sentry.android.buddy.model.BuddyTimelineItem
import io.sentry.android.buddy.model.FlowAnalysisEvent
import io.sentry.android.buddy.model.FlowAnalysisRequest
import io.sentry.android.buddy.model.FlowAnalysisResponse
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationAction
import io.sentry.android.buddy.model.RecommendationStatus
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.model.toHomeRecommendations
import io.sentry.android.buddy.model.withRecommendation
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.ApiStatus

internal sealed class BuddyHealthCheckState {
  data object Hidden : BuddyHealthCheckState()

  data object Running : BuddyHealthCheckState()

  data class Results(val response: BuddyHealthCheckResponse) : BuddyHealthCheckState()

  data class Error(val message: String) : BuddyHealthCheckState()
}

internal data class TransientRecordingEvent(val id: Long, val text: String)

@ApiStatus.Experimental
public interface SentryBuddyRecorderFacade {
  public fun startRecording(intent: BuddyFlowIntent)

  public fun stopRecording(): BuddyRecordingResult
}

@ApiStatus.Experimental
public object RealSentryBuddyRecorderFacade : SentryBuddyRecorderFacade {
  override fun startRecording(intent: BuddyFlowIntent) {
    SentryBuddy.startRecording(intent)
  }

  override fun stopRecording(): BuddyRecordingResult {
    val recording = SentryBuddy.stopRecording()
    return BuddyRecordingResult(
      recording = recording,
      recordingJson = BuddyFlowRecordingJsonSerializer.serialize(recording),
    )
  }
}

@ApiStatus.Experimental
public sealed class SentryBuddySessionState {
  public object Closed : SentryBuddySessionState()

  public object LiveFeed : SentryBuddySessionState()

  public object Intro : SentryBuddySessionState()

  public data class Recording
  public constructor(public val intent: BuddyFlowIntent, public val startedAtMs: Long) :
    SentryBuddySessionState()

  public data class StoppedSummary public constructor(public val result: BuddyRecordingResult) :
    SentryBuddySessionState()

  public data class Briefing
  public constructor(
    public val result: BuddyRecordingResult,
    public val flowName: String,
    public val developerNotes: String,
    public val focusAreas: Set<BuddyFocusArea>,
  ) : SentryBuddySessionState()

  public data class Analyzing
  public constructor(
    public val result: BuddyRecordingResult,
    public val request: FlowAnalysisRequest,
    public val submission: FlowAnalysisResponse,
  ) : SentryBuddySessionState()

  public data class Insights
  public constructor(
    public val result: BuddyRecordingResult,
    public val request: FlowAnalysisRequest,
    public val analysis: FlowAnalysisResponse,
    public val response: BuddyAnalysisResponse,
  ) : SentryBuddySessionState()

  public data class Error
  public constructor(
    public val message: String,
    public val previousState: SentryBuddySessionState,
  ) : SentryBuddySessionState()
}

@ApiStatus.Experimental
public class SentryBuddySessionController
@JvmOverloads
public constructor(
  private val recorderFacade: SentryBuddyRecorderFacade = RealSentryBuddyRecorderFacade,
  private val flowAnalysesApi: SentryBuddyFlowAnalysesApi = DummySentryBuddyFlowAnalysesApi,
  private val healthCheckApi: SentryBuddyHealthCheckApi = DummySentryBuddyHealthCheckApi,
  private val openUrlApi: SentryBuddyOpenUrlApi = DummySentryBuddyOpenUrlApi,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  public var state: SentryBuddySessionState = SentryBuddySessionState.Closed
    private set

  internal var liveFeed: BuddyLiveFeed = safeLiveFeed()
    private set

  internal var sentryUiLinks: BuddySentryUiLinks = BuddySentryUiLinks()

  internal var healthCheckState: BuddyHealthCheckState = BuddyHealthCheckState.Hidden
    private set

  internal var homeTab: BuddyHomeTab = BuddyHomeTab.LIVE_FEED
    private set

  internal var homeRecommendations: List<BuddyHomeRecommendation> = emptyList()
    private set

  internal var recommendationError: String? = null
    private set

  private var lastSelectedHomeTab: BuddyHomeTab = BuddyHomeTab.LIVE_FEED
  private var hasPendingHealthCheck: Boolean = false
  private val knownFlowIds = linkedSetOf<String>()

  private val transientRecordingEventLock: Any = Any()
  private val transientRecordingEventListeners = mutableListOf<(TransientRecordingEvent) -> Unit>()
  private var transientRecordingEventId: Long = 0

  public fun open() {
    dismissHealthCheck()
    state = SentryBuddySessionState.Intro
  }

  internal fun openLiveFeed() {
    dismissHealthCheck()
    liveFeed = safeLiveFeed()
    clearHealthCheckRecommendations()
    ingestHomeRecommendations(liveFeed.toHomeRecommendations(sentryUiLinks))
    hasPendingHealthCheck = true
    homeTab = defaultHomeTab()
    state = SentryBuddySessionState.LiveFeed
  }

  internal fun dismissLiveFeedAttention() {
    liveFeed = safeMarkLiveFeedSeen()
  }

  internal fun selectHomeTab(tab: BuddyHomeTab) {
    homeTab = tab
    lastSelectedHomeTab = tab
  }

  internal fun markHomeRecommendationRead(recommendationId: String) {
    updateHomeRecommendation(recommendationId) { it.copy(unread = false) }
  }

  internal fun dismissHomeRecommendation(recommendationId: String) {
    val recommendation = homeRecommendations.firstOrNull { it.id == recommendationId } ?: return
    if (!recommendation.isOpen) {
      return
    }
    if (recommendation.supportsRemoteActions) {
      val flowId = requireNotNull(recommendation.flowId)
      try {
        clearRecommendationError()
        val dismissed =
          flowAnalysesApi.dismissRecommendation(
            flowId,
            requireNotNull(recommendation.sourceRecommendationId),
          )
        applyFlowRecommendation(flowId, dismissed)
      } catch (exception: IllegalStateException) {
        setRecommendationError(exception.message ?: "Failed to dismiss recommendation.")
      }
      return
    }
    removeHomeRecommendation(recommendationId)
  }

  internal fun executeHomeRecommendationAction(recommendationId: String, actionId: String) {
    val recommendation = homeRecommendations.firstOrNull { it.id == recommendationId } ?: return
    if (!recommendation.isOpen) {
      return
    }
    if (recommendation.supportsRemoteActions) {
      val flowId = requireNotNull(recommendation.flowId)
      try {
        clearRecommendationError()
        val executed =
          flowAnalysesApi.executeAction(
            flowId,
            requireNotNull(recommendation.sourceRecommendationId),
            actionId,
          )
        applyFlowAction(flowId, requireNotNull(recommendation.sourceRecommendationId), executed)
        refreshFlowAnalysisOrStoreError(flowId)
      } catch (exception: IllegalStateException) {
        setRecommendationError(exception.message ?: "Failed to execute the action.")
      }
      return
    }
    // A recommendation that no flow analysis owns has no Seer run to start, so the action is only
    // marked off locally.
    updateHomeRecommendation(recommendationId) {
      it.copy(
        actions = it.actions.markExecuted(actionId),
        unread = false,
        updatedAtMs = clock(),
      )
    }
  }

  public fun close() {
    dismissHealthCheck()
    hasPendingHealthCheck = false
    if (state !is SentryBuddySessionState.Recording) {
      state = SentryBuddySessionState.Closed
    }
  }

  @JvmOverloads
  public fun startRecording(
    flowName: String = "Sentry Buddy Flow",
    developerGoal: String = "Understand how this app flow maps to Sentry telemetry.",
    importance: BuddyFlowImportance = BuddyFlowImportance.BUSINESS_CRITICAL,
    focusAreas: Set<BuddyFocusArea> = DEFAULT_FOCUS_AREAS,
  ) {
    val previousState = state
    try {
      dismissHealthCheck()
      val intent =
        BuddyFlowIntent(
          name = flowName,
          developerGoal = developerGoal,
          importance = importance,
          data = linkedMapOf("focusAreas" to focusAreas.map { it.name.lowercase(Locale.ROOT) }),
        )
      recorderFacade.startRecording(intent)
      state = SentryBuddySessionState.Recording(intent = intent, startedAtMs = clock())
      recordTransientEvent("Flow recording started")
    } catch (exception: IllegalStateException) {
      state =
        SentryBuddySessionState.Error(
          exception.message ?: "Failed to start recording.",
          previousState,
        )
    }
  }

  public fun stopRecording() {
    val previousState = state
    try {
      dismissHealthCheck()
      state = SentryBuddySessionState.StoppedSummary(recorderFacade.stopRecording())
    } catch (exception: IllegalStateException) {
      state =
        SentryBuddySessionState.Error(
          exception.message ?: "Failed to stop recording.",
          previousState,
        )
    }
  }

  public fun briefRecording() {
    val stoppedState = state as? SentryBuddySessionState.StoppedSummary ?: return
    dismissHealthCheck()
    state =
      SentryBuddySessionState.Briefing(
        result = stoppedState.result.withFlowName(""),
        flowName = "",
        developerNotes = "",
        focusAreas = DEFAULT_FOCUS_AREAS,
      )
  }

  public fun updateBriefing(
    flowName: String,
    developerNotes: String,
    focusAreas: Set<BuddyFocusArea>,
  ) {
    val briefingState = state as? SentryBuddySessionState.Briefing ?: return
    state =
      briefingState.copy(
        result = briefingState.result.withFlowName(flowName),
        flowName = flowName,
        developerNotes = developerNotes,
        focusAreas = focusAreas,
      )
  }

  public fun analyze() {
    val briefingState = state as? SentryBuddySessionState.Briefing ?: return
    dismissHealthCheck()
    val request = buildFlowAnalysisRequest(briefingState)
    if (request.dsn.isBlank()) {
      state =
        SentryBuddySessionState.Error(
          "Flow analysis requires Sentry to be configured with a DSN.",
          briefingState,
        )
      return
    }
    try {
      val submission = flowAnalysesApi.submit(request)
      rememberKnownFlow(request.flowId)
      state = SentryBuddySessionState.Analyzing(briefingState.result, request, submission)
      pollFlowAnalysis()
    } catch (exception: IllegalStateException) {
      state =
        SentryBuddySessionState.Error(exception.message ?: "Failed to submit flow analysis.", state)
    }
  }

  internal fun timeoutFlowAnalysis() {
    val analyzingState = state as? SentryBuddySessionState.Analyzing ?: return
    state =
      SentryBuddySessionState.Error(
        "Flow analysis timed out after " +
          TimeUnit.MILLISECONDS.toSeconds(ANALYSIS_TIMEOUT_MS) +
          " seconds.",
        analyzingState,
      )
  }

  public fun pollFlowAnalysis() {
    val analyzingState = state as? SentryBuddySessionState.Analyzing ?: return
    try {
      val analysis = flowAnalysesApi.get(analyzingState.request.flowId)
      when (analysis.status) {
        AnalysisStatus.COMPLETED -> {
          applyFlowAnalysis(analysis)
          state =
            SentryBuddySessionState.Insights(
              result = analyzingState.result,
              request = analyzingState.request,
              analysis = analysis,
              response = analysis.toBuddyAnalysisResponse(analyzingState.request),
            )
        }

        AnalysisStatus.FAILED ->
          state =
            SentryBuddySessionState.Error(
              analysis.error ?: "Flow analysis failed.",
              analyzingState,
            )

        AnalysisStatus.PROCESSING -> Unit
      }
    } catch (exception: IllegalStateException) {
      state =
        SentryBuddySessionState.Error(exception.message ?: "Failed to poll flow analysis.", state)
    }
  }

  public fun recordAgain() {
    dismissHealthCheck()
    state = SentryBuddySessionState.Intro
  }

  public fun dismissRecommendation(recommendationId: String) {
    val insightsState = state as? SentryBuddySessionState.Insights ?: return
    try {
      clearRecommendationError()
      val dismissed =
        flowAnalysesApi.dismissRecommendation(insightsState.request.flowId, recommendationId)
      applyFlowRecommendation(insightsState.request.flowId, dismissed)
    } catch (exception: IllegalStateException) {
      setRecommendationError(exception.message ?: "Failed to dismiss recommendation.")
    }
  }

  public fun executeRecommendationAction(recommendationId: String, actionId: String) {
    val insightsState = state as? SentryBuddySessionState.Insights ?: return
    try {
      clearRecommendationError()
      val executed =
        flowAnalysesApi.executeAction(insightsState.request.flowId, recommendationId, actionId)
      applyFlowAction(insightsState.request.flowId, recommendationId, executed)
      refreshFlowAnalysisOrStoreError(insightsState.request.flowId)
    } catch (exception: IllegalStateException) {
      setRecommendationError(exception.message ?: "Failed to execute the action.")
    }
  }

  internal fun refreshKnownFlowRecommendations() {
    knownFlowIds.toList().forEach { flowId -> refreshFlowAnalysisOrStoreError(flowId) }
  }

  internal fun runPendingHealthCheck() {
    if (!hasPendingHealthCheck) {
      return
    }
    hasPendingHealthCheck = false
    runHealthCheck()
  }

  internal fun runHealthCheck() {
    if (state != SentryBuddySessionState.LiveFeed) {
      return
    }
    healthCheckState = BuddyHealthCheckState.Running
    try {
      val response = healthCheckApi.check(BuddyHealthCheckCapture.captureRequest())
      replaceHealthCheckRecommendations(response.toHomeRecommendations(clock()))
      healthCheckState = BuddyHealthCheckState.Results(response)
    } catch (exception: IllegalStateException) {
      healthCheckState =
        BuddyHealthCheckState.Error(exception.message ?: "Failed to run health check.")
    }
  }

  internal fun dismissHealthCheck() {
    healthCheckState = BuddyHealthCheckState.Hidden
  }

  private fun BuddySdkConfigSnapshot.hasTracing(): Boolean =
    tracesSampleRate != null || hasTracesSampler

  private fun BuddySdkConfigSnapshot.hasReplay(): Boolean =
    sessionReplayEnabled || sessionReplayOnErrorEnabled

  public fun openUrl(context: Context, url: String) {
    try {
      openUrlApi.open(context, url)
    } catch (_: IllegalStateException) {
      // A debug overlay should not disrupt the current session when the bridge is unreachable.
    }
  }

  internal fun recordTransientEvent(text: String) {
    val event: TransientRecordingEvent
    val listeners: List<(TransientRecordingEvent) -> Unit>
    synchronized(transientRecordingEventLock) {
      if (state !is SentryBuddySessionState.Recording) {
        return
      }
      transientRecordingEventId++
      event = TransientRecordingEvent(transientRecordingEventId, text)
      listeners = transientRecordingEventListeners.toList()
    }
    listeners.forEach { it(event) }
  }

  internal fun addTransientRecordingEventListener(
    listener: (TransientRecordingEvent) -> Unit
  ): () -> Unit {
    synchronized(transientRecordingEventLock) { transientRecordingEventListeners += listener }
    return {
      synchronized(transientRecordingEventLock) { transientRecordingEventListeners -= listener }
    }
  }

  internal fun addLiveFeedListener(listener: (BuddyLiveFeed) -> Unit): () -> Unit {
    val noOp: () -> Unit = {}
    return try {
      if (recorderFacade !== RealSentryBuddyRecorderFacade) {
        listener(BuddyLiveFeed())
        noOp
      } else {
        SentryBuddy.addLiveFeedListener { feed ->
          liveFeed = feed
          ingestHomeRecommendations(feed.toHomeRecommendations(sentryUiLinks))
          listener(feed)
        }
      }
    } catch (_: IllegalStateException) {
      listener(BuddyLiveFeed())
      noOp
    }
  }

  private fun safeLiveFeed(): BuddyLiveFeed =
    try {
      if (recorderFacade === RealSentryBuddyRecorderFacade) {
        SentryBuddy.liveFeedSnapshot()
      } else {
        BuddyLiveFeed()
      }
    } catch (_: IllegalStateException) {
      BuddyLiveFeed()
    }

  private fun safeMarkLiveFeedSeen(): BuddyLiveFeed =
    try {
      if (recorderFacade === RealSentryBuddyRecorderFacade) {
        SentryBuddy.markLiveFeedSeen()
      } else {
        BuddyLiveFeed()
      }
    } catch (_: IllegalStateException) {
      BuddyLiveFeed()
    }

  private fun ingestHomeRecommendations(recommendations: List<BuddyHomeRecommendation>) {
    recommendations
      .filter { it.status != RecommendationStatus.DISMISSED }
      .forEach {
        upsertHomeRecommendation(it)
      }
  }

  private fun clearHealthCheckRecommendations() {
    homeRecommendations = homeRecommendations.filterNot {
      it.source == BuddyRecommendationSource.HEALTH_CHECK
    }
  }

  private fun replaceHealthCheckRecommendations(recommendations: List<BuddyHomeRecommendation>) {
    clearHealthCheckRecommendations()
    ingestHomeRecommendations(recommendations)
  }

  private fun upsertHomeRecommendation(incoming: BuddyHomeRecommendation) {
    val existing = homeRecommendations.firstOrNull { it.id == incoming.id }
    if (existing != null && !incoming.isMoreRecentThan(existing)) {
      return
    }
    val merged = incoming.copy(unread = incoming.isAttentionDriving)
    homeRecommendations =
      (listOf(merged) + homeRecommendations.filterNot { it.id == incoming.id }).sortedBy {
        it.updatedAtMs
      }
  }

  private fun updateHomeRecommendation(
    recommendationId: String,
    transform: (BuddyHomeRecommendation) -> BuddyHomeRecommendation,
  ) {
    val recommendation = homeRecommendations.firstOrNull { it.id == recommendationId } ?: return
    val updated = transform(recommendation)
    homeRecommendations =
      homeRecommendations
        .map { if (it.id == recommendationId) updated else it }
        .sortedBy { it.updatedAtMs }
  }

  private fun removeHomeRecommendation(recommendationId: String) {
    homeRecommendations = homeRecommendations.filterNot { it.id == recommendationId }
  }

  private fun defaultHomeTab(): BuddyHomeTab =
    when {
      liveFeed.latestUnviewedAdverseItem != null -> BuddyHomeTab.LIVE_FEED
      homeRecommendations.any { it.isAttentionDriving && it.unread } -> BuddyHomeTab.ACTIONS
      else -> lastSelectedHomeTab
    }

  private fun setRecommendationError(message: String) {
    recommendationError = message
  }

  private fun clearRecommendationError() {
    recommendationError = null
  }

  private fun rememberKnownFlow(flowId: String) {
    knownFlowIds += flowId
  }

  private fun refreshFlowAnalysisOrStoreError(flowId: String) {
    try {
      refreshFlowAnalysis(flowId)
    } catch (exception: IllegalStateException) {
      setRecommendationError(exception.message ?: "Failed to refresh recommendations.")
    }
  }

  private fun refreshFlowAnalysis(flowId: String) {
    val analysis = flowAnalysesApi.get(flowId)
    when (analysis.status) {
      AnalysisStatus.COMPLETED -> applyFlowAnalysis(analysis)
      AnalysisStatus.FAILED -> setRecommendationError(analysis.error ?: "Flow analysis failed.")
      AnalysisStatus.PROCESSING -> Unit
    }
  }

  private fun applyFlowAnalysis(analysis: FlowAnalysisResponse) {
    rememberKnownFlow(analysis.flowId)
    homeRecommendations = homeRecommendations.filterNot { it.flowId == analysis.flowId }
    ingestHomeRecommendations(analysis.toHomeRecommendations(clock()))
    val insightsState = state as? SentryBuddySessionState.Insights ?: return
    if (insightsState.request.flowId != analysis.flowId) {
      return
    }
    state =
      insightsState.copy(
        analysis = analysis,
        response = analysis.toBuddyAnalysisResponse(insightsState.request),
      )
  }

  /**
   * The dismiss and execute endpoints answer with one recommendation or one action, so the answer
   * is folded back into both the home list and the open insights sheet.
   */
  private fun applyFlowRecommendation(flowId: String, updated: Recommendation) {
    rememberKnownFlow(flowId)
    val nowMs = clock()
    if (updated.status == RecommendationStatus.DISMISSED) {
      removeHomeRecommendation("flow-analysis:${updated.id}")
      val insightsState = state as? SentryBuddySessionState.Insights ?: return
      if (insightsState.request.flowId != flowId) {
        return
      }
      val analysis = insightsState.analysis.withRecommendation(updated)
      state =
        insightsState.copy(
          analysis = analysis,
          response = analysis.toBuddyAnalysisResponse(insightsState.request),
        )
      return
    }
    val aggregate =
      BuddyHomeRecommendation(
        id = "flow-analysis:${updated.id}",
        source = BuddyRecommendationSource.FLOW_ANALYSIS,
        title = updated.title,
        description = updated.description,
        severity = updated.severity,
        status = updated.status,
        unread = false,
        updatedAtMs = nowMs,
        primaryLink = updated.link,
        actions = updated.actions,
        flowId = flowId,
        sourceRecommendationId = updated.id,
      )
    val existing = homeRecommendations.firstOrNull { it.id == aggregate.id }
    val refreshedAggregate =
      if (existing == null) {
        aggregate
      } else {
        aggregate.copy(updatedAtMs = maxOf(nowMs, existing.updatedAtMs + 1))
      }
    homeRecommendations =
      (listOf(refreshedAggregate) +
          homeRecommendations.filterNot { it.id == refreshedAggregate.id })
        .sortedBy { it.updatedAtMs }
    val insightsState = state as? SentryBuddySessionState.Insights ?: return
    if (insightsState.request.flowId != flowId) {
      return
    }
    val analysis = insightsState.analysis.withRecommendation(updated)
    state =
      insightsState.copy(
        analysis = analysis,
        response = analysis.toBuddyAnalysisResponse(insightsState.request),
      )
  }

  private fun applyFlowAction(
    flowId: String,
    recommendationId: String,
    executed: RecommendationAction,
  ) {
    rememberKnownFlow(flowId)
    val recommendation = currentFlowRecommendation(flowId, recommendationId)
    if (recommendation != null) {
      applyFlowRecommendation(
        flowId,
        recommendation.copy(actions = recommendation.actions.replacing(executed)),
      )
      return
    }
    // The insights sheet is not open, so the home entry is the only copy that has to learn about
    // the started Seer run.
    updateHomeRecommendation("flow-analysis:$recommendationId") {
      it.copy(actions = it.actions.replacing(executed), unread = false, updatedAtMs = clock())
    }
  }

  private fun currentFlowRecommendation(flowId: String, recommendationId: String): Recommendation? {
    val insightsState = state as? SentryBuddySessionState.Insights
    if (insightsState != null && insightsState.request.flowId == flowId) {
      return insightsState.analysis.recommendations.firstOrNull { it.id == recommendationId }
    }
    return null
  }

  private fun BuddyHomeRecommendation.isMoreRecentThan(existing: BuddyHomeRecommendation): Boolean {
    return updatedAtMs > existing.updatedAtMs
  }

  private fun buildFlowAnalysisRequest(
    state: SentryBuddySessionState.Briefing
  ): FlowAnalysisRequest {
    val recording = state.result.recording
    return FlowAnalysisRequest(
      flowId = recording.recording.id,
      traceIds = listOfNotNull(recording.sentry.traceId),
      startTimeMs = recording.recording.startedAt.time,
      endTimeMs = recording.recording.endedAt.time,
      dsn = recording.sentry.dsn.orEmpty(),
      userAnnotation = state.userAnnotation(),
      sdk = Sentry.getCurrentScopes().options.sdkIdentifier(),
      events = recording.timeline.map { it.toFlowAnalysisEvent() },
    )
  }

  private fun SentryBuddySessionState.Briefing.userAnnotation(): String = buildString {
    append("Flow: ").append(flowName)
    if (developerNotes.isNotBlank()) {
      append('\n').append(developerNotes)
    }
    if (focusAreas.isNotEmpty()) {
      append('\n').append("Focus areas: ").append(focusAreas.joinToString { it.label })
    }
  }

  private fun BuddyRecordingResult.withFlowName(flowName: String): BuddyRecordingResult {
    val updatedRecording = recording.copy(flow = recording.flow.copy(name = flowName))
    return copy(
      recording = updatedRecording,
      recordingJson = BuddyFlowRecordingJsonSerializer.serialize(updatedRecording),
    )
  }

  private fun BuddyTimelineItem.toFlowAnalysisEvent(): FlowAnalysisEvent =
    FlowAnalysisEvent(
      type = type.value,
      timestamp = timestamp.time,
      data = dataWithCommonFields(),
    )

  private fun BuddyTimelineItem.dataWithCommonFields(): Map<String, Any?> =
    linkedMapOf<String, Any?>("elapsed_ms" to elapsedMs).apply {
      name?.let { put("name", it) }
      putAll(data)
    }

  private fun FlowAnalysisResponse.toBuddyAnalysisResponse(
    request: FlowAnalysisRequest
  ): BuddyAnalysisResponse {
    val durationMs = request.endTimeMs - request.startTimeMs
    val screenCount = request.events.count { it.type == BuddyTimelineItem.Type.SCREEN.value }
    val spanCount = request.events.count { it.type == "span" }
    return BuddyAnalysisResponse(
      summary =
        title
          ?: "Your flow ran for ${formatDuration(durationMs)} across " +
            "$screenCount ${"screen".pluralize(screenCount)} and produced " +
            "$spanCount ${"span".pluralize(spanCount)}.",
      insights =
        listOf(
          BuddyInsight(
            title = "${screenCount.coerceAtLeast(1)} screens captured",
            body = "Buddy saw the app journey and can correlate it with Sentry tags.",
            severity = Severity.MEDIUM,
            elapsedMs = durationMs,
          ),
          BuddyInsight(
            title = "$spanCount spans captured",
            body = "Use spans to explain the work that matters most to this flow.",
            severity = Severity.LOW,
          ),
        ),
      recommendations = recommendations.filterNot { it.status == RecommendationStatus.DISMISSED },
    )
  }

  private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val millis = durationMs % 1000
    return "$seconds.${millis.toString().padStart(3, '0')}s"
  }

  private fun String.pluralize(count: Int): String = if (count == 1) this else "${this}s"

  public companion object {
    public val DEFAULT_FOCUS_AREAS: Set<BuddyFocusArea> =
      setOf(BuddyFocusArea.ERRORS_AND_CRASHES, BuddyFocusArea.NETWORK_TIMING)
  }
}

internal const val ANALYSIS_POLL_INTERVAL_MS = 1000L

internal const val ANALYSIS_TIMEOUT_MS = 120_000L

private fun io.sentry.SentryOptions.sdkIdentifier(): String {
  val sdkVersion = sdkVersion
  if (sdkVersion != null) {
    return "${sdkVersion.name}@${sdkVersion.version}"
  }
  return sentryClientName?.takeIf { it.isNotBlank() } ?: "unknown"
}

private fun List<RecommendationAction>.replacing(
  action: RecommendationAction
): List<RecommendationAction> = map { if (it.id == action.id) action else it }

private fun List<RecommendationAction>.markExecuted(actionId: String): List<RecommendationAction> =
  map {
    if (it.id == actionId) it.copy(status = ActionStatus.EXECUTED) else it
  }
