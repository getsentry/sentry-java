package io.sentry.android.buddy

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class FlowAnalysisEvent
public constructor(
  public val type: String,
  public val timeMs: Long,
  public val data: Map<String, Any?> = emptyMap(),
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("type").value(type)
    writer.name("time_ms").value(timeMs)
    writer.name("data").value(logger, data)
    writer.endObject()
  }
}

@ApiStatus.Experimental
public data class FlowAnalysisRequest
public constructor(
  public val flowId: String,
  public val traceIds: List<String>,
  public val startTimeMs: Long,
  public val endTimeMs: Long,
  public val dsn: String,
  public val userAnnotation: String,
  public val sdkVersion: String,
  public val events: List<FlowAnalysisEvent>,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("flow_id").value(flowId)
    writer.name("trace_ids").value(logger, traceIds)
    writer.name("start_time_ms").value(startTimeMs)
    writer.name("end_time_ms").value(endTimeMs)
    writer.name("dsn").value(dsn)
    writer.name("user_annotation").value(userAnnotation)
    writer.name("sdk_version").value(sdkVersion)
    writer.name("events").value(logger, events)
    writer.endObject()
  }
}

@ApiStatus.Experimental
public enum class AnalysisStatus(public val value: String) {
  PROCESSING("PROCESSING"),
  COMPLETED("COMPLETED"),
  FAILED("FAILED"),
}

@ApiStatus.Experimental
public enum class RecommendationStatus(public val value: String) {
  OPEN("OPEN"),
  RESOLVED("RESOLVED"),
  DISMISSED("DISMISSED"),
  FAILED("FAILED"),
}

@ApiStatus.Experimental
public enum class Severity(public val value: String) {
  LOW("LOW"),
  MEDIUM("MEDIUM"),
  HIGH("HIGH"),
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
  public val status: RecommendationStatus = RecommendationStatus.OPEN,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("id").value(id)
    writer.name("title").value(title)
    writer.name("description").value(description)
    writer.name("link").value(link)
    writer.name("severity").value(severity.value)
    writer.name("status").value(status.value)
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

@ApiStatus.Experimental
public data class FlowAnalysisResponse
@JvmOverloads
public constructor(
  public val flowId: String,
  public val status: AnalysisStatus,
  public val title: String? = null,
  public val recommendations: List<Recommendation> = emptyList(),
  public val issues: List<SentryIssue> = emptyList(),
  public val error: String? = null,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("flow_id").value(flowId)
    writer.name("status").value(status.value)
    writer.name("title").value(title)
    writer.name("recommendations").value(logger, recommendations)
    writer.name("issues").value(logger, issues)
    writer.name("error").value(error)
    writer.endObject()
  }
}
