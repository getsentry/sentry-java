package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
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
  ) : SentryBuddyFlowAnalysesApi {
    var request: FlowAnalysisRequest? = null
    var submitted = false
    val polledIds = mutableListOf<String>()

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
      )
    }

    override fun resolveRecommendation(
      flowId: String,
      recommendationId: String,
    ): FlowAnalysisResponse = get(flowId)
  }

  private companion object {
    private const val recordingJson = "{\"type\":\"sentry.mobile_flow_recording\"}"

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
  }
}
