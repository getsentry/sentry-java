package io.sentry.android.buddy.model

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class RecommendationStatus(public val value: String) {
  OPEN("OPEN"),
  RESOLVED("RESOLVED"),
  DISMISSED("DISMISSED"),
  FAILED("FAILED"),
}

@ApiStatus.Experimental
public data class Recommendation
@JvmOverloads
public constructor(
  public val id: String,
  public val title: String,
  public val description: String,
  public val link: String? = null,
  public val severity: Severity = Severity.MEDIUM,
  public val resolvable: Boolean = true,
  public val status: RecommendationStatus = RecommendationStatus.OPEN,
  public val seerRunUrl: String? = null,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("id").value(id)
    writer.name("title").value(title)
    writer.name("description").value(description)
    writer.name("link").value(link)
    writer.name("severity").value(severity.value)
    writer.name("resolvable").value(resolvable)
    writer.name("status").value(status.value)
    writer.name("seer_run_url").value(seerRunUrl)
    writer.endObject()
  }
}

@ApiStatus.Experimental
public data class SentryIssue
public constructor(
  public val id: String,
  public val title: String,
  public val culprit: String? = null,
  public val count: Int,
  public val level: String,
  public val permalink: String,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("id").value(id)
    writer.name("title").value(title)
    writer.name("culprit").value(culprit)
    writer.name("count").value(count.toLong())
    writer.name("level").value(level)
    writer.name("permalink").value(permalink)
    writer.endObject()
  }
}
