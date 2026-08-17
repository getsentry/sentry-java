package io.sentry.android.buddy

import java.util.Locale
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class BuddyFocusArea(public val label: String) {
  ERRORS_AND_CRASHES("Errors and crashes"),
  NETWORK_TIMING("Network timing"),
  MISSING_INSTRUMENTATION("Missing instrumentation"),
  FRAME_DROPS_AND_JANK("Frame drops and jank"),
}

@ApiStatus.Experimental
public enum class BuddyRecommendationCategory(public val label: String) {
  INSTRUMENTATION("Instrumentation"),
  PERFORMANCE("Performance"),
  MONITORING("Monitoring"),
  EDUCATION("Education"),
}

@ApiStatus.Experimental
public enum class BuddyRecommendationPriority(public val label: String) {
  HIGH("High"),
  MEDIUM("Medium"),
  LOW("Low"),
}

@ApiStatus.Experimental
public data class BuddyRecommendation
public constructor(
  public val id: String,
  public val title: String,
  public val body: String,
  public val category: BuddyRecommendationCategory,
  public val priority: BuddyRecommendationPriority,
  public val codeSnippet: String? = null,
)

@ApiStatus.Experimental
public data class BuddyInsight
public constructor(
  public val title: String,
  public val body: String,
  public val severity: BuddyRecommendationPriority,
  public val elapsedMs: Long? = null,
)

@ApiStatus.Experimental
public data class BuddyFlowAnalysisSubmitRequest
public constructor(
  public val recording: BuddyFlowRecording,
  public val recordingJson: String,
  public val flowName: String,
  public val developerNotes: String,
  public val focusAreas: Set<BuddyFocusArea>,
)

@ApiStatus.Experimental
public enum class BuddyFlowAnalysisStatus(public val value: String) {
  PENDING("PENDING"),
  RUNNING("RUNNING"),
  COMPLETED("COMPLETED"),
  FAILED("FAILED"),
  EXPIRED("EXPIRED"),
  CANCELLED("CANCELLED"),
}

@ApiStatus.Experimental
public data class BuddyFlowAnalysisSubmission
public constructor(
  public val id: String,
  public val status: BuddyFlowAnalysisStatus = BuddyFlowAnalysisStatus.PENDING,
  public val pollPath: String = "/v1/flow-analyses/$id",
)

@ApiStatus.Experimental
public data class BuddyAnalysisResponse
public constructor(
  public val summary: String,
  public val insights: List<BuddyInsight>,
  public val recommendations: List<BuddyRecommendation>,
  public val recommendationsText: String = "",
)

@ApiStatus.Experimental
public data class BuddyFlowAnalysis
public constructor(
  public val id: String,
  public val status: BuddyFlowAnalysisStatus,
  public val result: BuddyAnalysisResponse? = null,
  public val errorMessage: String? = null,
)

@ApiStatus.Experimental
public data class BuddyRecommendationResolution
public constructor(
  public val flowAnalysisId: String,
  public val recommendationId: String,
  public val resolution: String,
  public val state: String = "resolved",
)

@ApiStatus.Experimental
public interface SentryBuddyFlowAnalysesApi {
  /** Models `POST /v1/flow-analyses`, which should return 202 Accepted with PENDING status. */
  public fun submit(request: BuddyFlowAnalysisSubmitRequest): BuddyFlowAnalysisSubmission

  /** Models `GET /v1/flow-analyses/{flowAnalysisId}`. */
  public fun get(flowAnalysisId: String): BuddyFlowAnalysis

  /** Models `POST /v1/flow-analyses/{flowAnalysisId}/recommendations/{id}/resolve`. */
  public fun resolveRecommendation(
    flowAnalysisId: String,
    recommendationId: String,
    resolution: String,
  ): BuddyRecommendationResolution
}

@ApiStatus.Experimental
public object DummySentryBuddyFlowAnalysesApi : SentryBuddyFlowAnalysesApi {
  private val analyses = mutableMapOf<String, BuddyFlowAnalysis>()

  override fun submit(request: BuddyFlowAnalysisSubmitRequest): BuddyFlowAnalysisSubmission {
    val id = "flow-analysis-${request.recording.recording.id}"
    analyses[id] =
      BuddyFlowAnalysis(
        id = id,
        status = BuddyFlowAnalysisStatus.COMPLETED,
        result = analyze(request),
      )
    return BuddyFlowAnalysisSubmission(id = id, status = BuddyFlowAnalysisStatus.PENDING)
  }

  override fun get(flowAnalysisId: String): BuddyFlowAnalysis {
    return analyses[flowAnalysisId]
      ?: BuddyFlowAnalysis(
        id = flowAnalysisId,
        status = BuddyFlowAnalysisStatus.FAILED,
        errorMessage = "Flow analysis not found.",
      )
  }

  override fun resolveRecommendation(
    flowAnalysisId: String,
    recommendationId: String,
    resolution: String,
  ): BuddyRecommendationResolution =
    BuddyRecommendationResolution(
      flowAnalysisId = flowAnalysisId,
      recommendationId = recommendationId,
      resolution = resolution,
    )

  private fun analyze(request: BuddyFlowAnalysisSubmitRequest): BuddyAnalysisResponse {
    val recording = request.recording
    val flowName = request.flowName.ifBlank { recording.flow.name }
    return BuddyAnalysisResponse(
      summary =
        "Your flow ran for ${formatDuration(recording.summary.durationMs)} across " +
          "${recording.summary.screenCount} ${"screen".pluralize(recording.summary.screenCount)} " +
          "and produced ${recording.summary.spanCount} " +
          "${"span".pluralize(recording.summary.spanCount)}.",
      insights =
        listOf(
          BuddyInsight(
            title = "${recording.summary.screenCount.coerceAtLeast(1)} screens captured",
            body = "Buddy saw the app journey and can correlate it with Sentry tags.",
            severity = BuddyRecommendationPriority.MEDIUM,
            elapsedMs = recording.summary.durationMs,
          ),
          BuddyInsight(
            title = "${recording.summary.spanCount} spans captured",
            body = "Use spans to explain the work that matters most to this flow.",
            severity = BuddyRecommendationPriority.LOW,
          ),
        ),
      recommendations =
        listOf(
          BuddyRecommendation(
            id = "add-flow-spans",
            title = "Add spans around key $flowName work",
            body =
              "The recording identifies the flow, but explicit spans will make the risky work " +
                "easier to explain in Sentry.",
            category = BuddyRecommendationCategory.INSTRUMENTATION,
            priority = BuddyRecommendationPriority.HIGH,
            codeSnippet = "Sentry.getSpan()?.startChild(\"${flowName.slugifyForSnippet()}.step\")",
          ),
          BuddyRecommendation(
            id = "set-flow-budget",
            title = "Set an initial duration budget",
            body = "Use this recording as the first baseline for future local or CI comparisons.",
            category = BuddyRecommendationCategory.PERFORMANCE,
            priority = BuddyRecommendationPriority.MEDIUM,
          ),
          BuddyRecommendation(
            id = "keep-buddy-tags",
            title = "Keep the Buddy correlation tags",
            body =
              "The recorder tagged this run with ${recording.sentry.tags.keys.joinToString()} " +
                "so related events and transactions can be found later.",
            category = BuddyRecommendationCategory.MONITORING,
            priority = BuddyRecommendationPriority.LOW,
          ),
        ),
      recommendationsText =
        "Add spans around the key work in $flowName so the flow is easier to explain in " +
          "Sentry. Use this recording as the initial baseline for duration and keep the " +
          "Buddy correlation tags on related events and transactions.",
    )
  }

  private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val millis = durationMs % 1000
    return "$seconds.${millis.toString().padStart(3, '0')}s"
  }

  private fun String.slugifyForSnippet(): String =
    trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), ".").trim('.').ifEmpty { "flow" }

  private fun String.pluralize(count: Int): String = if (count == 1) this else "${this}s"
}

@ApiStatus.Experimental
public data class BuddyRecordingResult
public constructor(public val recording: BuddyFlowRecording, public val recordingJson: String)

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
    public val request: BuddyFlowAnalysisSubmitRequest,
    public val submission: BuddyFlowAnalysisSubmission,
  ) : SentryBuddySessionState()

  public data class Insights
  public constructor(
    public val request: BuddyFlowAnalysisSubmitRequest,
    public val submission: BuddyFlowAnalysisSubmission,
    public val analysis: BuddyFlowAnalysis,
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
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  public var state: SentryBuddySessionState = SentryBuddySessionState.Closed
    private set

  internal var transientRecordingText: String? = null
    private set

  internal var transientRecordingEventId: Long = 0
    private set

  public fun open() {
    state = SentryBuddySessionState.Intro
  }

  public fun close() {
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
    state =
      SentryBuddySessionState.Briefing(
        result = stoppedState.result,
        flowName = stoppedState.result.recording.flow.name,
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
        flowName = flowName,
        developerNotes = developerNotes,
        focusAreas = focusAreas,
      )
  }

  public fun analyze() {
    val briefingState = state as? SentryBuddySessionState.Briefing ?: return
    val request =
      BuddyFlowAnalysisSubmitRequest(
        recording = briefingState.result.recording,
        recordingJson = briefingState.result.recordingJson,
        flowName = briefingState.flowName,
        developerNotes = briefingState.developerNotes,
        focusAreas = briefingState.focusAreas,
      )
    try {
      val submission = flowAnalysesApi.submit(request)
      state = SentryBuddySessionState.Analyzing(request, submission)
      pollFlowAnalysis()
    } catch (exception: IllegalStateException) {
      state =
        SentryBuddySessionState.Error(exception.message ?: "Failed to submit flow analysis.", state)
    }
  }

  public fun pollFlowAnalysis() {
    val analyzingState = state as? SentryBuddySessionState.Analyzing ?: return
    try {
      val analysis = flowAnalysesApi.get(analyzingState.submission.id)
      when (analysis.status) {
        BuddyFlowAnalysisStatus.COMPLETED -> {
          val response =
            checkNotNull(analysis.result) { "Flow analysis completed without a result." }
          state =
            SentryBuddySessionState.Insights(
              request = analyzingState.request,
              submission = analyzingState.submission,
              analysis = analysis,
              response = response,
            )
        }
        BuddyFlowAnalysisStatus.FAILED ->
          state =
            SentryBuddySessionState.Error(
              analysis.errorMessage ?: "Flow analysis failed.",
              analyzingState,
            )
        BuddyFlowAnalysisStatus.PENDING,
        BuddyFlowAnalysisStatus.RUNNING,
        BuddyFlowAnalysisStatus.EXPIRED,
        BuddyFlowAnalysisStatus.CANCELLED -> Unit
      }
    } catch (exception: IllegalStateException) {
      state =
        SentryBuddySessionState.Error(exception.message ?: "Failed to poll flow analysis.", state)
    }
  }

  public fun recordAgain() {
    state = SentryBuddySessionState.Intro
  }

  internal fun recordTransientEvent(text: String) {
    if (state is SentryBuddySessionState.Recording) {
      transientRecordingText = text
      transientRecordingEventId++
    }
  }

  public companion object {
    public val DEFAULT_FOCUS_AREAS: Set<BuddyFocusArea> =
      setOf(BuddyFocusArea.ERRORS_AND_CRASHES, BuddyFocusArea.NETWORK_TIMING)
  }
}
