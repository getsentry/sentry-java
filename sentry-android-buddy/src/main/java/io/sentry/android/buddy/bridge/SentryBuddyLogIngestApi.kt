package io.sentry.android.buddy.bridge

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

/**
 * One item an app streams to the buddy logs endpoint. The host keeps [data] opaque, so a new item
 * kind needs no host change — only a new [type].
 */
@ApiStatus.Experimental
public data class BuddyLogItem(
  public val type: String,
  public val timestamp: Long,
  public val data: Map<String, Any?>,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("type").value(type)
    writer.name("timestamp").value(timestamp)
    writer.name("data").value(logger, data)
    writer.endObject()
  }
}

@ApiStatus.Experimental
public interface SentryBuddyLogIngestApi {
  /** Sends a batch of [items] to the host under [sessionId]. Returns the number the host stored. */
  public fun ingest(sessionId: String, items: List<BuddyLogItem>): Int
}

@ApiStatus.Experimental
public object DummySentryBuddyLogIngestApi : SentryBuddyLogIngestApi {
  override fun ingest(sessionId: String, items: List<BuddyLogItem>): Int = 0
}
