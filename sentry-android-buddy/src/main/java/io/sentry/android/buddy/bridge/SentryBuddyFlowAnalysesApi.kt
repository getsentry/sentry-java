package io.sentry.android.buddy.bridge

import io.sentry.android.buddy.model.ActionStatus
import io.sentry.android.buddy.model.AnalysisStatus
import io.sentry.android.buddy.model.FlowAnalysisRequest
import io.sentry.android.buddy.model.FlowAnalysisResponse
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationAction
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
   * Models `POST /v1/flow-analysis/{flowId}/recommendations/{id}/dismiss`, which answers with the
   * dismissed recommendation only.
   */
  public fun dismissRecommendation(flowId: String, recommendationId: String): Recommendation

  /**
   * Models `POST /v1/flow-analysis/{flowId}/recommendations/{id}/actions/{actionId}/execute`, which
   * starts the Seer run and answers with the executed action only.
   */
  public fun executeAction(
    flowId: String,
    recommendationId: String,
    actionId: String,
  ): RecommendationAction
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

  override fun dismissRecommendation(flowId: String, recommendationId: String): Recommendation {
    val analysis = get(flowId)
    val dismissed =
      analysis.recommendations
        .first { it.id == recommendationId }
        .copy(status = RecommendationStatus.DISMISSED)
    analyses[flowId] = analysis.withRecommendation(dismissed)
    return dismissed
  }

  override fun executeAction(
    flowId: String,
    recommendationId: String,
    actionId: String,
  ): RecommendationAction {
    val analysis = get(flowId)
    val recommendation = analysis.recommendations.first { it.id == recommendationId }
    val executed =
      recommendation.actions
        .first { it.id == actionId }
        .copy(
          status = ActionStatus.EXECUTED,
          seerRunUrl = "https://sentry.io/seer/runs/$actionId",
        )
    analyses[flowId] =
      analysis.withRecommendation(
        recommendation.copy(
          actions = recommendation.actions.map { if (it.id == executed.id) executed else it }
        )
      )
    return executed
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
            actions =
              listOf(
                RecommendationAction(
                  id = "add-flow-spans:instrument",
                  actionLabel = "Add the spans",
                  description =
                    "Wrap the slowest steps of this flow in explicit spans so the work shows up " +
                      "in the trace.",
                )
              ),
          ),
          Recommendation(
            id = "set-flow-budget",
            title = "Set an initial duration budget",
            description =
              "Use this recording as the first baseline for future local or CI comparisons.",
            severity = Severity.MEDIUM,
            actions =
              listOf(
                RecommendationAction(
                  id = "set-flow-budget:add-alert",
                  actionLabel = "Create the budget",
                  description =
                    "Add a duration budget for this flow based on the recorded baseline.",
                )
              ),
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
