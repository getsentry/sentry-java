package io.sentry.android.buddy.model

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class RecommendationStatus(public val value: String) {
  OPEN("open"),
  DISMISSED("dismissed"),
}

@ApiStatus.Experimental
public enum class ActionStatus(public val value: String) {
  OPEN("open"),
  EXECUTED("executed"),
}

/**
 * How the span of a recommendation compares against production. Only spans have one, and every
 * field is optional, because the model cannot always query all of them.
 *
 * All durations are milliseconds. [duration] is the duration found in the recording, the other
 * values come from production data. [link] opens an explore query on sentry.io that shows that
 * production data.
 */
@ApiStatus.Experimental
public data class PerformanceCharacteristics
@JvmOverloads
public constructor(
  public val spanOp: String? = null,
  public val link: String? = null,
  public val duration: Double? = null,
  public val avg: Double? = null,
  public val p50: Double? = null,
  public val p75: Double? = null,
  public val p90: Double? = null,
  public val p95: Double? = null,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("span_op").value(spanOp)
    writer.name("link").value(link)
    writer.name("duration").value(duration)
    writer.name("avg").value(avg)
    writer.name("p50").value(p50)
    writer.name("p75").value(p75)
    writer.name("p90").value(p90)
    writer.name("p95").value(p95)
    writer.endObject()
  }
}

/**
 * One thing that can be done about a recommendation. The app shows [actionLabel], and executing the
 * action starts the Seer run that carries out [description].
 */
@ApiStatus.Experimental
public data class RecommendationAction
@JvmOverloads
public constructor(
  public val id: String,
  public val actionLabel: String,
  public val actionableForSeer: Boolean,
  /** Detailed instructions on how the action is carried out. It goes into the Seer prompt. */
  public val description: String,
  /** A link to an existing dashboard, a trace, or an explore query. */
  public val link: String? = null,
  public val status: ActionStatus = ActionStatus.OPEN,
  public val seerRunUrl: String? = null,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("id").value(id)
    writer.name("action_label").value(actionLabel)
    writer.name("actionable_for_seer").value(actionableForSeer)
    writer.name("description").value(description)
    writer.name("link").value(link)
    writer.name("status").value(status.value)
    writer.name("seer_run_url").value(seerRunUrl)
    writer.endObject()
  }
}

@ApiStatus.Experimental
public data class Recommendation
@JvmOverloads
public constructor(
  public val id: String,
  public val title: String,
  public val description: String,
  /** A link to docs or additional resources. */
  public val link: String? = null,
  public val severity: Severity = Severity.MEDIUM,
  public val status: RecommendationStatus = RecommendationStatus.OPEN,
  public val actions: List<RecommendationAction> = emptyList(),
  /** How the span of the recommendation compares against production. Only spans have one. */
  public val performanceCharacteristics: PerformanceCharacteristics? = null,
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
    writer.name("actions").value(logger, actions)
    writer.name("performance_characteristics").value(logger, performanceCharacteristics)
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
