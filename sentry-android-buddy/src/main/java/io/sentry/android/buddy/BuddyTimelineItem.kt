package io.sentry.android.buddy

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import java.util.Date
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyTimelineItem
@JvmOverloads
public constructor(
  public val type: Type,
  public val timestamp: Date,
  public val elapsedMs: Long,
  public val name: String? = null,
  public val data: Map<String, Any?> = emptyMap(),
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("type").value(type.value)
    writer.name("timestamp").value(logger, timestamp)
    writer.name("elapsedMs").value(elapsedMs)
    writer.name("name").value(name)
    writer.name("data").value(logger, data)
    writer.endObject()
  }

  @ApiStatus.Experimental
  public enum class Type(public val value: String) {
    RECORDING_STARTED("recording_started"),
    SCREEN("screen"),
    STEP("step"),
    BREADCRUMB("breadcrumb"),
    RECORDING_STOPPED("recording_stopped"),
  }
}
