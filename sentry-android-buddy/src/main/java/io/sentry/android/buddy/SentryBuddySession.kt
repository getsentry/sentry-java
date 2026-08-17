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
public data class BuddyAnalysisRequest
public constructor(
  public val recording: BuddyFlowRecording,
  public val recordingJson: String,
  public val flowName: String,
  public val developerNotes: String,
  public val focusAreas: Set<BuddyFocusArea>,
)

@ApiStatus.Experimental
public data class BuddyAnalysisResponse
public constructor(
  public val summary: String,
  public val insights: List<BuddyInsight>,
  public val recommendations: List<BuddyRecommendation>,
)

@ApiStatus.Experimental
public fun interface SentryBuddyAnalyzer {
  public fun analyze(request: BuddyAnalysisRequest): BuddyAnalysisResponse
}

@ApiStatus.Experimental
public object DummySentryBuddyAnalyzer : SentryBuddyAnalyzer {
  override fun analyze(request: BuddyAnalysisRequest): BuddyAnalysisResponse {
    val recording = request.recording
    val flowName = request.flowName.ifBlank { recording.flow.name }
    return BuddyAnalysisResponse(
      summary =
        "$flowName ran for ${formatDuration(recording.summary.durationMs)} across " +
          "${recording.summary.screenCount} screens and ${recording.summary.stepCount} steps.",
      insights =
        listOf(
          BuddyInsight(
            title = "${recording.summary.screenCount.coerceAtLeast(1)} screens captured",
            body = "Buddy saw the app journey and can correlate it with Sentry tags.",
            severity = BuddyRecommendationPriority.MEDIUM,
            elapsedMs = recording.summary.durationMs,
          ),
          BuddyInsight(
            title = "${recording.summary.stepCount} manual steps recorded",
            body = "Add explicit steps around the moments that matter most to this flow.",
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
    )
  }

  private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val millis = durationMs % 1000
    return "$seconds.${millis.toString().padStart(3, '0')}s"
  }

  private fun String.slugifyForSnippet(): String =
    trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), ".").trim('.').ifEmpty { "flow" }
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

  public data class Analyzing public constructor(public val request: BuddyAnalysisRequest) :
    SentryBuddySessionState()

  public data class Insights
  public constructor(
    public val request: BuddyAnalysisRequest,
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
  private val analyzer: SentryBuddyAnalyzer = DummySentryBuddyAnalyzer,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  public var state: SentryBuddySessionState = SentryBuddySessionState.Closed
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
    flowName: String = "Sentry Buddy Session",
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
    } catch (exception: RuntimeException) {
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
    } catch (exception: RuntimeException) {
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
      BuddyAnalysisRequest(
        recording = briefingState.result.recording,
        recordingJson = briefingState.result.recordingJson,
        flowName = briefingState.flowName,
        developerNotes = briefingState.developerNotes,
        focusAreas = briefingState.focusAreas,
      )
    state = SentryBuddySessionState.Analyzing(request)
    try {
      state =
        SentryBuddySessionState.Insights(request = request, response = analyzer.analyze(request))
    } catch (exception: RuntimeException) {
      state =
        SentryBuddySessionState.Error(exception.message ?: "Failed to analyze recording.", state)
    }
  }

  public fun recordAgain() {
    state = SentryBuddySessionState.Intro
  }

  public companion object {
    public val DEFAULT_FOCUS_AREAS: Set<BuddyFocusArea> =
      setOf(BuddyFocusArea.ERRORS_AND_CRASHES, BuddyFocusArea.NETWORK_TIMING)
  }
}
