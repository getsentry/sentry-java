package io.sentry.android.buddy

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddySentryCorrelation
public constructor(
  public val recordingId: String,
  public val dsn: String? = null,
  public val traceId: String? = null,
  public val spanId: String? = null,
  public val tags: Map<String, String> = emptyMap(),
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("recordingId").value(recordingId)
    writer.name("dsn").value(dsn)
    writer.name("traceId").value(traceId)
    writer.name("spanId").value(spanId)
    writer.name("tags").value(logger, tags)
    writer.endObject()
  }
}
