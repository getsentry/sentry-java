package io.sentry.android.buddy.model

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import java.util.Date
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyRecordingMetadata
public constructor(
  public val id: String,
  public val source: String,
  public val startedAt: Date,
  public val endedAt: Date,
  public val durationMs: Long,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("id").value(id)
    writer.name("source").value(source)
    writer.name("startedAt").value(logger, startedAt)
    writer.name("endedAt").value(logger, endedAt)
    writer.name("durationMs").value(durationMs)
    writer.endObject()
  }

  public companion object {
    public const val MANUAL_DEBUG_RECORDING: String = "manual_debug_recording"
  }
}
