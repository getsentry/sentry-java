package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.bridge.*
import io.sentry.android.buddy.model.*
import java.util.Date
import kotlin.test.Test

class SentryBuddySessionControllerTest {
  @Test
  fun `start recording calls recorder facade and enters recording state`() {
    val recorder = FakeRecorderFacade()
    val controller = SentryBuddySessionController(recorderFacade = recorder, clock = { 42L })

    controller.open()
    controller.startRecording(flowName = "Checkout")

    assertThat(recorder.startedIntent?.name).isEqualTo("Checkout")
    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Recording::class.java)
    val state = controller.state as SentryBuddySessionState.Recording
    assertThat(state.startedAtMs).isEqualTo(42L)
  }

  @Test
  fun `stop recording stores real recording summary`() {
    val recorder = FakeRecorderFacade()
    val controller = SentryBuddySessionController(recorderFacade = recorder)

    controller.startRecording(flowName = "Login")
    controller.stopRecording()

    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.StoppedSummary::class.java)
    val state = controller.state as SentryBuddySessionState.StoppedSummary
    assertThat(state.result.recording.summary.screenCount).isEqualTo(3)
    assertThat(state.result.recordingJson).contains("mobile_flow_recording")
  }

  @Test
  fun `brief recording starts with empty editable flow name`() {
    val controller = SentryBuddySessionController(recorderFacade = FakeRecorderFacade())

    controller.startRecording()
    controller.stopRecording()
    controller.briefRecording()

    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Briefing::class.java)
    val state = controller.state as SentryBuddySessionState.Briefing
    assertThat(state.flowName).isEmpty()
    assertThat(state.result.recording.flow.name).isEmpty()
  }

  @Test
  fun `analyze submits and polls flow analysis`() {
    val recorder = FakeRecorderFacade()
    val flowAnalysesApi = FakeFlowAnalysesApi()
    val controller =
      SentryBuddySessionController(recorderFacade = recorder, flowAnalysesApi = flowAnalysesApi)

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.updateBriefing(
      flowName = "Checkout",
      developerNotes = "Spinner felt slow",
      focusAreas = setOf(BuddyFocusArea.NETWORK_TIMING),
    )
    controller.analyze()

    assertThat(flowAnalysesApi.request?.flowId).isEqualTo("recording-1")
    assertThat(flowAnalysesApi.request?.traceIds).containsExactly("trace-id")
    assertThat(flowAnalysesApi.request?.startTimeMs).isEqualTo(0)
    assertThat(flowAnalysesApi.request?.endTimeMs).isEqualTo(1000)
    assertThat(flowAnalysesApi.request?.dsn).isEqualTo("https://public@example.com/1")
    assertThat(flowAnalysesApi.request?.userAnnotation).contains("Flow: Checkout")
    assertThat(flowAnalysesApi.request?.userAnnotation).contains("Spinner felt slow")
    assertThat(flowAnalysesApi.request?.events?.map { it.type })
      .containsExactly("screen", "recording_stopped")
    assertThat(flowAnalysesApi.submitted).isTrue()
    assertThat(flowAnalysesApi.polledIds).containsExactly("recording-1")
    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Insights::class.java)
    val state = controller.state as SentryBuddySessionState.Insights
    assertThat(state.analysis.status).isEqualTo(AnalysisStatus.COMPLETED)
    assertThat(state.result.recording.flow.name).isEqualTo("Checkout")
    assertThat(controller.homeRecommendations.map { it.id })
      .containsExactly("flow-analysis:recommendation-1")
  }

  @Test
  fun `dummy flow analysis uses generic ready message`() {
    val request =
      FlowAnalysisRequest(
        flowId = "recording-copy-test",
        traceIds = emptyList(),
        startTimeMs = 0,
        endTimeMs = 1,
        dsn = "",
        userAnnotation = "",
        sdk = "test",
        events = emptyList(),
      )

    DummySentryBuddyFlowAnalysesApi.submit(request)
    val response = DummySentryBuddyFlowAnalysesApi.get(request.flowId)

    assertThat(response.title).isEqualTo("Your flow is ready for review.")
  }

  @Test
  fun `skip analysis opens live feed without submitting flow analysis`() {
    val flowAnalysesApi = FakeFlowAnalysesApi()
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = flowAnalysesApi,
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.skipAnalysis()

    assertThat(controller.state).isEqualTo(SentryBuddySessionState.LiveFeed)
    assertThat(flowAnalysesApi.submitted).isFalse()
  }

  @Test
  fun `record again returns to intro state`() {
    val controller = SentryBuddySessionController(recorderFacade = FakeRecorderFacade())

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    controller.recordAgain()

    assertThat(controller.state).isEqualTo(SentryBuddySessionState.Intro)
  }

  @Test
  fun `open latest insights reopens the last seen insights state`() {
    val controller = SentryBuddySessionController(recorderFacade = FakeRecorderFacade())

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    val latestInsights = controller.state as SentryBuddySessionState.Insights

    controller.openLiveFeed()
    controller.openLatestInsights()

    assertThat(controller.hasLatestSeenInsights).isTrue()
    assertThat(controller.state).isEqualTo(latestInsights)
  }

  @Test
  fun `open latest insights refreshes the stored analysis before reopening`() {
    val flowAnalysesApi = FakeFlowAnalysesApi(flowActions = emptyList())
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = flowAnalysesApi,
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    assertThat((controller.state as SentryBuddySessionState.Insights).analysis.actions).isEmpty()

    flowAnalysesApi.flowActions = flowActions()
    controller.openLiveFeed()
    controller.openLatestInsights()

    val reopened = controller.state as SentryBuddySessionState.Insights
    assertThat(reopened.analysis.actions.map { it.id })
      .containsExactly("generate-dashboard", "generate-monitors", "share-recording-json")
      .inOrder()
  }

  @Test
  fun `executing an action updates the insights state`() {
    val flowAnalysesApi = FakeFlowAnalysesApi()
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = flowAnalysesApi,
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    controller.executeRecommendationAction("recommendation-1", "action-1")

    assertThat(flowAnalysesApi.executedActionIds).containsExactly("action-1")
    assertThat(flowAnalysesApi.polledIds).containsExactly("recording-1", "recording-1").inOrder()
    val state = controller.state as SentryBuddySessionState.Insights
    val action = state.analysis.recommendations.single().actions.single()
    assertThat(action.status).isEqualTo(ActionStatus.EXECUTED)
    assertThat(action.seerRunUrl).isEqualTo("https://sentry.io/seer/runs/1")
    assertThat(state.response.recommendations.single().actions.single().status)
      .isEqualTo(ActionStatus.EXECUTED)
    assertThat(controller.homeRecommendations.single().seerRunUrl)
      .isEqualTo("https://sentry.io/seer/runs/1")
    assertThat(controller.homeRecommendations.single().isAttentionDriving).isFalse()
  }

  @Test
  fun `executing a flow action updates the insights state`() {
    val flowAnalysesApi = FakeFlowAnalysesApi()
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = flowAnalysesApi,
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    val link = controller.executeFlowActionAndReturnLink("generate-dashboard")

    assertThat(flowAnalysesApi.executedFlowActionIds).containsExactly("generate-dashboard")
    assertThat(link).isEqualTo("https://sentry.io/seer/runs/generate-dashboard")
    val state = controller.state as SentryBuddySessionState.Insights
    val action = state.analysis.actions.first { it.id == "generate-dashboard" }
    assertThat(action.status).isEqualTo(ActionStatus.EXECUTED)
    assertThat(action.seerRunUrl).isEqualTo("https://sentry.io/seer/runs/generate-dashboard")
  }

  @Test
  fun `home action execution updates aggregate recommendation state`() {
    var nowMs = 100L
    val flowAnalysesApi = FakeFlowAnalysesApi()
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = flowAnalysesApi,
        clock = { nowMs },
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    nowMs = 200L
    controller.executeHomeRecommendationAction("flow-analysis:recommendation-1", "action-1")

    assertThat(flowAnalysesApi.executedActionIds).containsExactly("action-1")
    assertThat(controller.homeRecommendations.single().actions.single().status)
      .isEqualTo(ActionStatus.EXECUTED)
    assertThat(controller.homeRecommendations.single().unread).isFalse()
  }

  @Test
  fun `action execution failure stores inline recommendation error`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi =
          FakeFlowAnalysesApi(actionFailure = IllegalStateException("Execute failed")),
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    controller.executeRecommendationAction("recommendation-1", "action-1")

    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Insights::class.java)
    assertThat(controller.recommendationError).isEqualTo("Execute failed")
    val state = controller.state as SentryBuddySessionState.Insights
    assertThat(state.response.recommendations.single().actions.single().status)
      .isEqualTo(ActionStatus.OPEN)
  }

  @Test
  fun `dismissing a flow recommendation hides it without leaving insights`() {
    val flowAnalysesApi = FakeFlowAnalysesApi()
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = flowAnalysesApi,
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    controller.dismissRecommendation("recommendation-1")

    assertThat(flowAnalysesApi.dismissedRecommendationIds).containsExactly("recommendation-1")
    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Insights::class.java)
    assertThat((controller.state as SentryBuddySessionState.Insights).response.recommendations)
      .isEmpty()
    assertThat(controller.homeRecommendations).isEmpty()
  }

  @Test
  fun `record transient event notifies every recorded event`() {
    val controller = SentryBuddySessionController(recorderFacade = FakeRecorderFacade())
    val events = mutableListOf<TransientRecordingEvent>()
    val removeListener = controller.addTransientRecordingEventListener { events += it }

    controller.startRecording(flowName = "Login")
    controller.recordTransientEvent("Screen: MainActivity")
    controller.recordTransientEvent("Screen: MainActivity")

    removeListener()
    controller.recordTransientEvent("Step: Ignored")

    assertThat(events.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
    assertThat(events.map { it.text })
      .containsExactly(
        "Flow recording started",
        "Screen: MainActivity",
        "Screen: MainActivity",
      )
      .inOrder()
  }

  @Test
  fun `recorder failure enters error state`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(startFailure = IllegalStateException("Already active"))
      )

    controller.open()
    controller.startRecording(flowName = "Login")

    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Error::class.java)
    val state = controller.state as SentryBuddySessionState.Error
    assertThat(state.message).isEqualTo("Already active")
    assertThat(state.previousState).isEqualTo(SentryBuddySessionState.Intro)
  }

  @Test
  fun `flow analysis submit failure enters error state`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = FakeFlowAnalysesApi(submitFailure = IllegalStateException("No response")),
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()

    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Error::class.java)
    assertThat((controller.state as SentryBuddySessionState.Error).message).isEqualTo("No response")
  }

  @Test
  fun `pending flow analysis remains analyzing`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        flowAnalysesApi = FakeFlowAnalysesApi(status = AnalysisStatus.PROCESSING),
      )

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()

    assertThat(controller.state).isInstanceOf(SentryBuddySessionState.Analyzing::class.java)
  }

  @Test
  fun `pending health check from live feed stores results`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        healthCheckApi =
          object : SentryBuddyHealthCheckApi {
            override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse {
              assertThat(request.sdk).isNotEmpty()
              return BuddyHealthCheckResponse(
                recommendations =
                  listOf(
                    Recommendation(
                      id = "tracing-disabled",
                      title = "Turn on tracing",
                      description = "Tracing looks off.",
                      severity = Severity.MEDIUM,
                    )
                  )
              )
            }
          },
      )

    controller.openLiveFeed()
    controller.runPendingHealthCheck()

    assertThat(controller.healthCheckState).isInstanceOf(BuddyHealthCheckState.Results::class.java)
    val state = controller.healthCheckState as BuddyHealthCheckState.Results
    assertThat(state.response.recommendations.single().title).contains("tracing")
    assertThat(controller.homeRecommendations.single().id)
      .isEqualTo("health-check:tracing-disabled")
  }

  @Test
  fun `open live feed remembers last selected tab even with unread recommendations`() {
    val controller = SentryBuddySessionController(recorderFacade = FakeRecorderFacade())

    controller.startRecording(flowName = "Login")
    controller.stopRecording()
    controller.briefRecording()
    controller.analyze()
    controller.selectHomeTab(BuddyHomeTab.RECORD_FLOW)
    controller.close()
    controller.openLiveFeed()

    assertThat(controller.homeRecommendations.any { it.unread }).isTrue()
    assertThat(controller.homeTab).isEqualTo(BuddyHomeTab.RECORD_FLOW)
  }

  @Test
  fun `open live feed selects live feed once for new needs attention item`() {
    val controller = SentryBuddySessionController(recorderFacade = FakeRecorderFacade())

    controller.selectHomeTab(BuddyHomeTab.RECORD_FLOW)
    controller.updateLiveFeed(liveFeedWithUnviewedAttention())
    controller.openLiveFeed()

    assertThat(controller.homeTab).isEqualTo(BuddyHomeTab.LIVE_FEED)
    assertThat(controller.liveFeed.latestUnviewedAdverseItem).isNull()

    controller.close()
    controller.openLiveFeed()

    assertThat(controller.homeTab).isEqualTo(BuddyHomeTab.RECORD_FLOW)
  }

  @Test
  fun `open live feed clears previous health check recommendations before rerunning`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        healthCheckApi =
          object : SentryBuddyHealthCheckApi {
            override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse {
              return BuddyHealthCheckResponse(
                recommendations =
                  listOf(
                    Recommendation(
                      id = "replay-disabled",
                      title = "Consider enabling Session Replay",
                      description = "Replay is off.",
                      severity = Severity.LOW,
                    )
                  )
              )
            }
          },
      )

    controller.openLiveFeed()
    controller.runPendingHealthCheck()
    controller.close()
    controller.openLiveFeed()

    assertThat(controller.homeRecommendations).isEmpty()
    assertThat(controller.healthCheckState).isEqualTo(BuddyHealthCheckState.Hidden)
  }

  @Test
  fun `rerunning a dismissed recommendation makes it active and unread again`() {
    var nowMs = 100L
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        healthCheckApi =
          object : SentryBuddyHealthCheckApi {
            override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse {
              return BuddyHealthCheckResponse(
                recommendations =
                  listOf(
                    Recommendation(
                      id = "replay-disabled",
                      title = "Consider enabling Session Replay",
                      description = "Replay is off.",
                      severity = Severity.LOW,
                    )
                  )
              )
            }
          },
        clock = { nowMs },
      )

    controller.openLiveFeed()
    controller.runPendingHealthCheck()
    controller.dismissHomeRecommendation("health-check:replay-disabled")

    assertThat(controller.homeRecommendations).isEmpty()

    nowMs = 200L
    controller.runHealthCheck()

    assertThat(controller.homeRecommendations.single().status).isEqualTo(RecommendationStatus.OPEN)
    assertThat(controller.homeRecommendations.single().unread).isTrue()
    assertThat(controller.homeRecommendations.single().updatedAtMs).isEqualTo(200L)
  }

  @Test
  fun `health check failure stores inline error state`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        healthCheckApi =
          object : SentryBuddyHealthCheckApi {
            override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse {
              throw IllegalStateException("Bridge offline")
            }
          },
      )

    controller.openLiveFeed()
    controller.runPendingHealthCheck()

    assertThat(controller.healthCheckState).isInstanceOf(BuddyHealthCheckState.Error::class.java)
    assertThat((controller.healthCheckState as BuddyHealthCheckState.Error).message)
      .isEqualTo("Bridge offline")
  }

  @Test
  fun `closing the session hides health check state`() {
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        healthCheckApi =
          object : SentryBuddyHealthCheckApi {
            override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse =
              BuddyHealthCheckResponse(recommendations = emptyList())
          },
      )

    controller.openLiveFeed()
    controller.runPendingHealthCheck()
    controller.close()

    assertThat(controller.healthCheckState).isEqualTo(BuddyHealthCheckState.Hidden)
  }

  @Test
  fun `pending health check runs only once per live feed open`() {
    var checkCalls = 0
    val controller =
      SentryBuddySessionController(
        recorderFacade = FakeRecorderFacade(),
        healthCheckApi =
          object : SentryBuddyHealthCheckApi {
            override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse {
              checkCalls += 1
              return BuddyHealthCheckResponse()
            }
          },
      )

    controller.openLiveFeed()
    controller.runPendingHealthCheck()
    controller.runPendingHealthCheck()

    assertThat(checkCalls).isEqualTo(1)
  }

  private class FakeRecorderFacade(
    private val startFailure: RuntimeException? = null,
    private val stopFailure: RuntimeException? = null,
  ) : SentryBuddyRecorderFacade {
    var startedIntent: BuddyFlowIntent? = null
    val recordingResult =
      BuddyRecordingResult(recording = recording(), recordingJson = recordingJson)

    override fun startRecording(intent: BuddyFlowIntent) {
      startFailure?.let { throw it }
      startedIntent = intent
    }

    override fun stopRecording(): BuddyRecordingResult {
      stopFailure?.let { throw it }
      return recordingResult
    }
  }

  private class FakeFlowAnalysesApi(
    private val submitFailure: RuntimeException? = null,
    private val status: AnalysisStatus = AnalysisStatus.COMPLETED,
    private val actionFailure: RuntimeException? = null,
    var flowActions: List<FlowAction> = flowActions(),
  ) : SentryBuddyFlowAnalysesApi {
    var request: FlowAnalysisRequest? = null
    var submitted = false
    val polledIds = mutableListOf<String>()
    val dismissedRecommendationIds = mutableListOf<String>()
    val executedActionIds = mutableListOf<String>()
    val executedFlowActionIds = mutableListOf<String>()
    private var recommendations =
      listOf(
        Recommendation(
          id = "recommendation-1",
          title = "Add spans around key flow work",
          description = "Use explicit spans to explain the flow.",
          severity = Severity.HIGH,
          actions =
            listOf(
              RecommendationAction(
                id = "action-1",
                actionLabel = "Add the spans",
                actionableForSeer = true,
                description = "Wrap the slowest steps in explicit spans.",
              )
            ),
        )
      )

    override fun submit(request: FlowAnalysisRequest): FlowAnalysisResponse {
      submitFailure?.let { throw it }
      this.request = request
      submitted = true
      return FlowAnalysisResponse(
        flowId = request.flowId,
        status = AnalysisStatus.PROCESSING,
      )
    }

    override fun get(flowId: String): FlowAnalysisResponse {
      polledIds += flowId
      return FlowAnalysisResponse(
        flowId = flowId,
        status = status,
        title = if (status == AnalysisStatus.COMPLETED) "Summary" else null,
        actions = if (status == AnalysisStatus.COMPLETED) flowActions else emptyList(),
        recommendations = if (status == AnalysisStatus.COMPLETED) recommendations else emptyList(),
      )
    }

    override fun dismissRecommendation(flowId: String, recommendationId: String): Recommendation {
      actionFailure?.let { throw it }
      dismissedRecommendationIds += recommendationId
      val dismissed =
        recommendations
          .first { it.id == recommendationId }
          .copy(status = RecommendationStatus.DISMISSED)
      recommendations = recommendations.map { if (it.id == dismissed.id) dismissed else it }
      return dismissed
    }

    override fun executeAction(
      flowId: String,
      recommendationId: String,
      actionId: String,
    ): RecommendationAction {
      actionFailure?.let { throw it }
      executedActionIds += actionId
      val recommendation = recommendations.first { it.id == recommendationId }
      val executed =
        recommendation.actions
          .first { it.id == actionId }
          .copy(
            status = ActionStatus.EXECUTED,
            seerRunUrl = "https://sentry.io/seer/runs/1",
          )
      recommendations = recommendations.map {
        if (it.id != recommendation.id) {
          it
        } else {
          it.copy(
            actions =
              it.actions.map { action ->
                if (action.id == executed.id) executed else action
              }
          )
        }
      }
      return executed
    }

    override fun executeFlowAction(flowId: String, actionId: String): FlowAction {
      actionFailure?.let { throw it }
      executedFlowActionIds += actionId
      val executed =
        flowActions
          .first { it.id == actionId }
          .copy(
            status = ActionStatus.EXECUTED,
            seerRunUrl = "https://sentry.io/seer/runs/$actionId",
          )
      flowActions = flowActions.map { if (it.id == executed.id) executed else it }
      return executed
    }
  }

  private companion object {
    private const val recordingJson = "{\"type\":\"sentry.mobile_flow_recording\"}"

    private fun flowActions(): List<FlowAction> =
      listOf(
        FlowAction(
          id = "generate-dashboard",
          actionLabel = "Dashboard",
          description = "Draft a dashboard from the flow recording.",
          actionableForSeer = true,
        ),
        FlowAction(
          id = "generate-monitors",
          actionLabel = "Monitors",
          description = "Draft monitors from the flow recording.",
          actionableForSeer = true,
        ),
        FlowAction(
          id = "share-recording-json",
          actionLabel = "Share JSON",
          description = "Share the raw flow recording JSON.",
        ),
      )

    private fun recording(): BuddyFlowRecording =
      BuddyFlowRecording(
        flow = BuddyFlowIntent("Login"),
        recording =
          BuddyRecordingMetadata(
            id = "recording-1",
            source = BuddyRecordingMetadata.MANUAL_DEBUG_RECORDING,
            startedAt = Date(0),
            endedAt = Date(1000),
            durationMs = 1000,
          ),
        app = BuddyAppInfo(packageName = "io.sentry.samples.android"),
        device = BuddyDeviceInfo(model = "Pixel"),
        summary =
          BuddyRecordingSummary(
            durationMs = 1000,
            screenCount = 3,
            spanCount = 4,
            breadcrumbCount = 0,
            timelineItemCount = 5,
          ),
        timeline =
          listOf(
            BuddyTimelineItem(
              type = BuddyTimelineItem.Type.SCREEN,
              timestamp = Date(500),
              elapsedMs = 500,
              name = "MainActivity",
            ),
            BuddyTimelineItem(
              type = BuddyTimelineItem.Type.RECORDING_STOPPED,
              timestamp = Date(1000),
              elapsedMs = 1000,
              name = "Login",
            ),
          ),
        sentry =
          BuddySentryCorrelation(
            recordingId = "recording-1",
            dsn = "https://public@example.com/1",
            traceId = "trace-id",
            spanId = "span-id",
            tags = linkedMapOf("sentry.buddy.recording_id" to "recording-1"),
          ),
      )

    private fun liveFeedWithUnviewedAttention(): BuddyLiveFeed {
      val item =
        BuddyLiveFeedItem(
          id = 1L,
          timelineItem =
            BuddyTimelineItem(
              type = BuddyTimelineItem.Type.EVENT,
              timestamp = Date(2000),
              elapsedMs = 2000,
              name = "Crash",
            ),
          category = BuddyLiveFeedItem.Category.ERROR,
          severity = Severity.HIGH,
          adverse = true,
          viewed = false,
        )
      return BuddyLiveFeed(
        items = listOf(item),
        unviewedAdverseCount = 1,
        latestAdverseItem = item,
        latestUnviewedAdverseItem = item,
      )
    }
  }
}
