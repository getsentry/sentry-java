package io.sentry.android.buddy.model

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class RecommendationStatus(public val value: String) {
  OPEN("OPEN"),
  DISMISSED("DISMISSED"),
}

@ApiStatus.Experimental
public enum class ActionStatus(public val value: String) {
  OPEN("OPEN"),
  EXECUTED("EXECUTED"),
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
