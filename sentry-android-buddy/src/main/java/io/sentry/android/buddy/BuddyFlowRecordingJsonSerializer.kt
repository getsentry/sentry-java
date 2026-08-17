package io.sentry.android.buddy

import io.sentry.JsonSerializer
import io.sentry.SentryOptions
import java.io.StringWriter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public object BuddyFlowRecordingJsonSerializer {
  @JvmStatic
  public fun serialize(recording: BuddyFlowRecording): String {
    val writer = StringWriter()
    JsonSerializer(SentryOptions()).serialize(recording, writer)
    return writer.toString()
  }
}
