package io.sentry.android.buddy.bridge

import io.sentry.android.buddy.model.AnalysisStatus
import io.sentry.android.buddy.model.FlowAnalysisRequest
import io.sentry.android.buddy.model.FlowAnalysisResponse
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationStatus
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.model.withRecommendation
import org.jetbrains.annotations.ApiStatus

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
