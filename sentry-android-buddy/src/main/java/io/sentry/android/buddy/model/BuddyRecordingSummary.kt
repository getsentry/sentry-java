package io.sentry.android.buddy.model

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyRecordingSummary
public constructor(
  public val durationMs: Long,
  public val screenCount: Int,
  public val spanCount: Int,
  public val breadcrumbCount: Int,
  public val timelineItemCount: Int,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("durationMs").value(durationMs)
    writer.name("screenCount").value(screenCount.toLong())
    writer.name("spanCount").value(spanCount.toLong())
    writer.name("breadcrumbCount").value(breadcrumbCount.toLong())
    writer.name("timelineItemCount").value(timelineItemCount.toLong())
    writer.endObject()
  }
}
