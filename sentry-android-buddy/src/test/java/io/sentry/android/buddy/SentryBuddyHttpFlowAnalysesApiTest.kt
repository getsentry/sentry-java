package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.bridge.*
import io.sentry.android.buddy.model.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class SentryBuddyHttpFlowAnalysesApiTest {
  private val server = MockWebServer()

  @AfterTest
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `submit posts bridge request shape and parses processing response`() {
    server.enqueue(
      MockResponse().setResponseCode(202).setBody("""{"flow_id":"flow-1","status":"PROCESSING"}""")
    )
    val api = SentryBuddyHttpFlowAnalysesApi(server.url("/").toString())

    val response = api.submit(request())

    assertThat(response.flowId).isEqualTo("flow-1")
    assertThat(response.status).isEqualTo(AnalysisStatus.PROCESSING)
    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.method).isEqualTo("POST")
    assertThat(recordedRequest.path).isEqualTo("/v1/flow-analysis")
    val body = recordedRequest.body.readUtf8()
    assertThat(body).contains("\"timestamp\":123")
    assertThat(body).contains("\"sdk\":\"test-sdk\"")
    assertThat(body).doesNotContain("\"time_ms\":")
    assertThat(body).doesNotContain("\"sdk_version\":")
  }

  @Test
  fun `get parses completed response`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """
          {
            "flow_id": "flow-1",
            "status": "COMPLETED",
            "title": "Checkout flow",
            "actions": [{
              "id": "generate-dashboard",
              "action_label": "Dashboard",
              "description": "Draft a dashboard.",
              "actionable_for_seer": true,
              "status": "OPEN",
              "seer_run_url": null
            }],
            "recommendations": [{
              "id": "rec-1",
              "title": "Add spans",
              "description": "Add spans around checkout.",
              "link": "https://example.com",
              "severity": "HIGH",
              "status": "OPEN",
              "actions": [{
                "id": "act-1",
                "action_label": "Add the spans",
                "description": "Wrap checkout in explicit spans.",
                "status": "OPEN",
                "seer_run_url": null
              }]
            }],
            "issues": [{
              "id": "issue-1",
              "title": "Crash",
              "culprit": "CheckoutActivity",
              "count": 3,
              "level": "error",
              "permalink": "https://sentry.io/issues/1"
            }],
            "enrichment_errors": ["IssueEnrichment: boom"]
          }
          """
            .trimIndent()
        )
    )
    val api = SentryBuddyHttpFlowAnalysesApi(server.url("/").toString())

    val response = api.get("flow-1")

    assertThat(response.status).isEqualTo(AnalysisStatus.COMPLETED)
    assertThat(response.title).isEqualTo("Checkout flow")
    assertThat(response.actions.single().id).isEqualTo("generate-dashboard")
    assertThat(response.actions.single().actionLabel).isEqualTo("Dashboard")
    assertThat(response.actions.single().actionableForSeer).isTrue()
    assertThat(response.recommendations.single().severity).isEqualTo(Severity.HIGH)
    val action = response.recommendations.single().actions.single()
    assertThat(action.id).isEqualTo("act-1")
    assertThat(action.actionLabel).isEqualTo("Add the spans")
    assertThat(action.status).isEqualTo(ActionStatus.OPEN)
    assertThat(response.issues.single().id).isEqualTo("issue-1")
    assertThat(response.enrichmentErrors).containsExactly("IssueEnrichment: boom")
    assertThat(server.takeRequest().path).isEqualTo("/v1/flow-analysis/flow-1")
  }

  @Test
  fun `execute flow action posts to bridge execute endpoint and parses the action`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """
          {
            "id": "generate-dashboard",
            "action_label": "Dashboard",
            "description": "Draft a dashboard.",
            "actionable_for_seer": true,
            "status": "EXECUTED",
            "seer_run_url": "https://sentry.io/seer/runs/1"
          }
          """
            .trimIndent()
        )
    )
    val api = SentryBuddyHttpFlowAnalysesApi(server.url("/").toString())

    val executed = api.executeFlowAction("flow-1", "generate-dashboard")

    assertThat(executed.status).isEqualTo(ActionStatus.EXECUTED)
    assertThat(executed.actionableForSeer).isTrue()
    assertThat(executed.seerRunUrl).isEqualTo("https://sentry.io/seer/runs/1")
    val request = server.takeRequest()
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.path)
      .isEqualTo("/v1/flow-analysis/flow-1/actions/generate-dashboard/execute")
  }

  @Test
  fun `dismiss recommendation posts to bridge dismiss endpoint and parses the recommendation`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """
          {
            "id": "rec-1",
            "title": "Add spans",
            "description": "Add spans around checkout.",
            "status": "DISMISSED"
          }
          """
            .trimIndent()
        )
    )
    val api = SentryBuddyHttpFlowAnalysesApi(server.url("/").toString())

    val dismissed = api.dismissRecommendation("flow-1", "rec-1")

    assertThat(dismissed.status).isEqualTo(RecommendationStatus.DISMISSED)
    val request = server.takeRequest()
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.path).isEqualTo("/v1/flow-analysis/flow-1/recommendations/rec-1/dismiss")
  }

  @Test
  fun `execute action posts to bridge execute endpoint and parses the action`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """
          {
            "id": "act-1",
            "action_label": "Add the spans",
            "description": "Wrap checkout in explicit spans.",
            "status": "EXECUTED",
            "seer_run_url": "https://sentry.io/seer/runs/1"
          }
          """
            .trimIndent()
        )
    )
    val api = SentryBuddyHttpFlowAnalysesApi(server.url("/").toString())

    val executed = api.executeAction("flow-1", "rec-1", "act-1")

    assertThat(executed.status).isEqualTo(ActionStatus.EXECUTED)
    assertThat(executed.seerRunUrl).isEqualTo("https://sentry.io/seer/runs/1")
    val request = server.takeRequest()
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.path)
      .isEqualTo("/v1/flow-analysis/flow-1/recommendations/rec-1/actions/act-1/execute")
  }

  @Test
  fun `http errors include bridge error message`() {
    server.enqueue(
      MockResponse().setResponseCode(400).setBody("""{"error":"dsn must not be blank"}""")
    )
    val api = SentryBuddyHttpFlowAnalysesApi(server.url("/").toString())

    val error = assertFailsWith<IllegalStateException> { api.submit(request()) }

    assertThat(error).hasMessageThat().contains("HTTP 400")
    assertThat(error).hasMessageThat().contains("dsn must not be blank")
  }

  private fun request(): FlowAnalysisRequest =
    FlowAnalysisRequest(
      flowId = "flow-1",
      traceIds = listOf("trace-1"),
      startTimeMs = 1,
      endTimeMs = 2,
      dsn = "https://public@example.com/1",
      userAnnotation = "Flow: Checkout",
      sdk = "test-sdk",
      events =
        listOf(FlowAnalysisEvent(type = "screen", timestamp = 123, data = mapOf("name" to "Main"))),
    )
}
