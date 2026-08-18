package io.sentry.android.buddy

import android.content.Context
import org.jetbrains.annotations.ApiStatus
import java.util.Locale
import java.util.concurrent.TimeUnit

@ApiStatus.Experimental
public enum class BuddyFocusArea(public val label: String) {
  ERRORS_AND_CRASHES("Errors and crashes"),
  NETWORK_TIMING("Network timing"),
  MISSING_INSTRUMENTATION("Missing instrumentation"),
  FRAME_DROPS_AND_JANK("Frame drops and jank"),
}

@ApiStatus.Experimental
public data class BuddyInsight
public constructor(
  public val title: String,
  public val body: String,
  public val severity: Severity,
  public val elapsedMs: Long? = null,
)

@ApiStatus.Experimental
public data class BuddyAnalysisResponse
public constructor(
  public val summary: String,
  public val insights: List<BuddyInsight>,
  public val recommendations: List<Recommendation>,
)

internal sealed class BuddyHealthCheckState {
  data object Hidden : BuddyHealthCheckState()

  data object Running : BuddyHealthCheckState()

  data class Results(val response: BuddyHealthCheckResponse) : BuddyHealthCheckState()

  data class Error(val message: String) : BuddyHealthCheckState()
}

@ApiStatus.Experimental
public interface SentryBuddyFlowAnalysesApi {
  /** Models `POST /v1/flow-analysis`, which returns 202 Accepted with PROCESSING status. */
  public fun submit(request: FlowAnalysisRequest): FlowAnalysisResponse

  /** Models `GET /v1/flow-analysis/{flowId}`. */
  public fun get(flowId: String): FlowAnalysisResponse

  /**
   * Models `POST /v1/flow-analysis/{flowId}/recommendations/{id}/resolve`, which answers with the
   * resolved recommendation only.
   */
  public fun resolveRecommendation(flowId: String, recommendationId: String): Recommendation
}

@ApiStatus.Experimental
public object DummySentryBuddyFlowAnalysesApi : SentryBuddyFlowAnalysesApi {
  private val analyses = mutableMapOf<String, FlowAnalysisResponse>()

  override fun submit(request: FlowAnalysisRequest): FlowAnalysisResponse {
    analyses[request.flowId] = completedAnalysis(request)
    return FlowAnalysisResponse(flowId = request.flowId, status = AnalysisStatus.PROCESSING)
  }

  override fun get(flowId: String): FlowAnalysisResponse {
    return analyses[flowId]
      ?: FlowAnalysisResponse(
        flowId = flowId,
        status = AnalysisStatus.FAILED,
        error = "Flow analysis not found.",
      )
  }

  override fun resolveRecommendation(flowId: String, recommendationId: String): Recommendation {
    val analysis = get(flowId)
    val resolved =
      analysis.recommendations
        .first { it.id == recommendationId }
        .copy(
          status = RecommendationStatus.RESOLVED,
          seerRunUrl = "https://sentry.io/seer/runs/$recommendationId",
        )
    analyses[flowId] = analysis.withRecommendation(resolved)
    return resolved
  }

  private fun completedAnalysis(request: FlowAnalysisRequest): FlowAnalysisResponse {
    return FlowAnalysisResponse(
      flowId = request.flowId,
      status = AnalysisStatus.COMPLETED,
      title = "Your flow is ready for review.",
      recommendations =
        listOf(
          Recommendation(
            id = "add-flow-spans",
            title = "Add spans around key flow work",
            description =
              "The recording identifies the flow, but explicit spans will make the risky work " +
                "easier to explain in Sentry.",
            severity = Severity.HIGH,
          ),
          Recommendation(
            id = "set-flow-budget",
            title = "Set an initial duration budget",
            description =
              "Use this recording as the first baseline for future local or CI comparisons.",
            severity = Severity.MEDIUM,
          ),
          Recommendation(
            id = "keep-buddy-tags",
            title = "Keep the Buddy correlation tags",
            description =
              "The recording included trace IDs ${request.traceIds.joinToString()} so related " +
                "events and transactions can be found later.",
            severity = Severity.LOW,
          ),
        ),
    )
  }
}

@ApiStatus.Experimental
public data class BuddyRecordingResult
public constructor(public val recording: BuddyFlowRecording, public val recordingJson: String)

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

  private var lastSelectedHomeTab: BuddyHomeTab = BuddyHomeTab.LIVE_FEED

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
    ingestHomeRecommendations(liveFeed.toHomeRecommendations())
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
    updateHomeRecommendation(recommendationId) {
      it.copy(status = RecommendationStatus.DISMISSED, unread = false, updatedAtMs = clock())
    }
  }

  internal fun resolveHomeRecommendation(recommendationId: String) {
    val recommendation = homeRecommendations.firstOrNull { it.id == recommendationId } ?: return
    if (!recommendation.isOpen) {
      return
    }
    if (recommendation.supportsRemoteResolve) {
      val previousState = state
      try {
        val resolved =
          flowAnalysesApi.resolveRecommendation(
            requireNotNull(recommendation.flowId),
            requireNotNull(recommendation.sourceRecommendationId),
          )
        applyResolvedFlowRecommendation(requireNotNull(recommendation.flowId), resolved)
      } catch (exception: IllegalStateException) {
        state =
          SentryBuddySessionState.Error(
            exception.message ?: "Failed to resolve recommendation.",
            previousState,
          )
      }
      return
    }
    updateHomeRecommendation(recommendationId) {
      it.copy(status = RecommendationStatus.RESOLVED, unread = false, updatedAtMs = clock())
    }
  }

  public fun close() {
    dismissHealthCheck()
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
        "Flow analysis timed out after " + TimeUnit.MILLISECONDS.toSeconds(ANALYSIS_TIMEOUT_MS) + " seconds.",
        analyzingState
      )
  }

  public fun pollFlowAnalysis() {
    val analyzingState = state as? SentryBuddySessionState.Analyzing ?: return
    try {
      val analysis = flowAnalysesApi.get(analyzingState.request.flowId)
      when (analysis.status) {
        AnalysisStatus.COMPLETED -> {
          ingestHomeRecommendations(analysis.toHomeRecommendations(clock()))
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

  public fun resolveRecommendation(recommendationId: String) {
    val insightsState = state as? SentryBuddySessionState.Insights ?: return
    try {
      val resolved =
        flowAnalysesApi.resolveRecommendation(insightsState.request.flowId, recommendationId)
      applyResolvedFlowRecommendation(insightsState.request.flowId, resolved)
    } catch (exception: IllegalStateException) {
      state =
        SentryBuddySessionState.Error(
          exception.message ?: "Failed to resolve recommendation.",
          insightsState,
        )
    }
  }

  internal fun runHealthCheck() {
    if (state != SentryBuddySessionState.LiveFeed) {
      return
    }
    healthCheckState = BuddyHealthCheckState.Running
    try {
      val response = healthCheckApi.check(BuddyHealthCheckCapture.captureRequest())
      ingestHomeRecommendations(response.toHomeRecommendations(clock()))
      healthCheckState = BuddyHealthCheckState.Results(response)
    } catch (exception: IllegalStateException) {
      healthCheckState =
        BuddyHealthCheckState.Error(exception.message ?: "Failed to run health check.")
    }
  }

  internal fun dismissHealthCheck() {
    healthCheckState = BuddyHealthCheckState.Hidden
  }

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
          ingestHomeRecommendations(feed.toHomeRecommendations())
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
    recommendations.forEach { upsertHomeRecommendation(it) }
  }

  private fun upsertHomeRecommendation(incoming: BuddyHomeRecommendation) {
    val existing = homeRecommendations.firstOrNull { it.id == incoming.id }
    if (existing != null && !incoming.isMoreRecentThan(existing)) {
      return
    }
    val merged = incoming.copy(unread = incoming.isOpen)
    homeRecommendations = listOf(merged) + homeRecommendations.filterNot { it.id == incoming.id }
  }

  private fun updateHomeRecommendation(
    recommendationId: String,
    transform: (BuddyHomeRecommendation) -> BuddyHomeRecommendation,
  ) {
    val recommendation = homeRecommendations.firstOrNull { it.id == recommendationId } ?: return
    val updated = transform(recommendation)
    homeRecommendations = homeRecommendations.map { if (it.id == recommendationId) updated else it }
  }

  private fun defaultHomeTab(): BuddyHomeTab =
    when {
      liveFeed.latestUnviewedAdverseItem != null -> BuddyHomeTab.LIVE_FEED
      homeRecommendations.any { it.isOpen && it.unread } -> BuddyHomeTab.RECOMMENDATIONS
      else -> lastSelectedHomeTab
    }

  private fun applyResolvedFlowRecommendation(flowId: String, resolved: Recommendation) {
    val nowMs = clock()
    val resolvedAggregate =
      BuddyHomeRecommendation(
        id = "flow-analysis:${resolved.id}",
        source = BuddyRecommendationSource.FLOW_ANALYSIS,
        title = resolved.title,
        description = resolved.description,
        severity = resolved.severity,
        status = resolved.status,
        unread = false,
        updatedAtMs = nowMs,
        primaryLink = resolved.link,
        seerRunUrl = resolved.seerRunUrl,
        flowId = flowId,
        sourceRecommendationId = resolved.id,
      )
    val existing = homeRecommendations.firstOrNull { it.id == resolvedAggregate.id }
    val refreshedAggregate =
      if (existing == null) {
        resolvedAggregate
      } else {
        resolvedAggregate.copy(updatedAtMs = maxOf(nowMs, existing.updatedAtMs + 1))
      }
    homeRecommendations =
      listOf(refreshedAggregate) + homeRecommendations.filterNot { it.id == refreshedAggregate.id }
    val insightsState = state as? SentryBuddySessionState.Insights ?: return
    if (insightsState.request.flowId != flowId) {
      return
    }
    val analysis = insightsState.analysis.withRecommendation(resolved)
    state =
      insightsState.copy(
        analysis = analysis,
        response = analysis.toBuddyAnalysisResponse(insightsState.request),
      )
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
      sdk = "io.sentry.android.buddy@${BuildConfig.VERSION_NAME}",
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
      recommendations = recommendations,
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
