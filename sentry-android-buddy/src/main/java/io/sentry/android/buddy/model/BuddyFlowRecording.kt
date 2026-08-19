package io.sentry.android.buddy.model

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyFlowRecording
public constructor(
  public val flow: BuddyFlowIntent,
  public val recording: BuddyRecordingMetadata,
  public val app: BuddyAppInfo,
  public val device: BuddyDeviceInfo,
  public val summary: BuddyRecordingSummary,
  public val timeline: List<BuddyTimelineItem>,
  public val sentry: BuddySentryCorrelation,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("type").value(TYPE)
    writer.name("version").value(VERSION.toLong())
    writer.name("platform").value(PLATFORM)
    writer.name("useCase").value(USE_CASE)
    writer.name("flow").value(logger, flow)
    writer.name("recording").value(logger, recording)
    writer.name("app").value(logger, app)
    writer.name("device").value(logger, device)
    writer.name("summary").value(logger, summary)
    writer.name("timeline").value(logger, timeline)
    writer.name("sentry").value(logger, sentry)
    writer.endObject()
  }

  public companion object {
    public const val TYPE: String = "sentry.mobile_flow_recording"
    public const val VERSION: Int = 1
    public const val PLATFORM: String = "android"
    public const val USE_CASE: String = "onboard_new_flow"
  }
}
