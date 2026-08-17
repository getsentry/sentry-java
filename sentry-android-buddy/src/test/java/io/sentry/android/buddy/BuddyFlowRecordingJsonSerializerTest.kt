package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlin.test.Test

class BuddyFlowRecordingJsonSerializerTest {
  @Test
  fun `serializes stable recording json shape`() {
    val json = BuddyFlowRecordingJsonSerializer.serialize(recording())

    assertThat(json).contains("\"type\":\"sentry.mobile_flow_recording\"")
    assertThat(json).contains("\"version\":1")
    assertThat(json).contains("\"platform\":\"android\"")
    assertThat(json).contains("\"useCase\":\"onboard_new_flow\"")
    assertThat(json).contains("\"slug\":\"checkout\"")
    assertThat(json).contains("\"importance\":\"business_critical\"")
    assertThat(json).contains("\"type\":\"recording_started\"")
    assertThat(json).contains("\"type\":\"screen\"")
    assertThat(json).contains("\"type\":\"step\"")
    assertThat(json).contains("\"type\":\"recording_stopped\"")
    assertThat(json).contains("\"traceId\":\"trace-id\"")
  }

  private fun recording(): BuddyFlowRecording =
    BuddyFlowRecording(
      flow =
        BuddyFlowIntent(
          name = "Checkout",
          developerGoal = "Make checkout observable",
          importance = BuddyFlowImportance.BUSINESS_CRITICAL,
          data = linkedMapOf("owner" to "payments"),
        ),
      recording =
        BuddyRecordingMetadata(
          id = "recording-id",
          source = BuddyRecordingMetadata.MANUAL_DEBUG_RECORDING,
          startedAt = Date(0),
          endedAt = Date(2000),
          durationMs = 2000,
        ),
      app =
        BuddyAppInfo(
          packageName = "com.example",
          versionName = "1.0",
          versionCode = 1,
          release = "1.0-debug",
          environment = "debug",
        ),
      device = BuddyDeviceInfo(manufacturer = "Google", model = "Pixel", osVersion = "15"),
      summary =
        BuddyRecordingSummary(
          durationMs = 2000,
          screenCount = 1,
          stepCount = 1,
          breadcrumbCount = 0,
          timelineItemCount = 4,
        ),
      timeline =
        listOf(
          BuddyTimelineItem(BuddyTimelineItem.Type.RECORDING_STARTED, Date(0), 0, "Checkout"),
          BuddyTimelineItem(BuddyTimelineItem.Type.SCREEN, Date(1000), 1000, "CheckoutActivity"),
          BuddyTimelineItem(
            BuddyTimelineItem.Type.STEP,
            Date(1500),
            1500,
            "submit payment",
            linkedMapOf("button" to "pay"),
          ),
          BuddyTimelineItem(
            BuddyTimelineItem.Type.RECORDING_STOPPED,
            Date(2000),
            2000,
            "Checkout",
          ),
        ),
      sentry =
        BuddySentryCorrelation(
          recordingId = "recording-id",
          traceId = "trace-id",
          spanId = "span-id",
          tags = linkedMapOf("sentry.buddy.recording_id" to "recording-id"),
        ),
    )
}
