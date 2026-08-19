package io.sentry.android.buddy.model

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import java.util.Locale
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyFlowIntent
@JvmOverloads
public constructor(
  public val name: String,
  public val developerGoal: String? = null,
  public val importance: BuddyFlowImportance? = null,
  public val data: Map<String, Any?> = emptyMap(),
) : JsonSerializable {
  public val slug: String = slugify(name)

  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("name").value(name)
    writer.name("slug").value(slug)
    writer.name("developerGoal").value(developerGoal)
    writer.name("importance").value(importance?.value)
    writer.name("data").value(logger, data)
    writer.endObject()
  }

  private companion object {
    private fun slugify(value: String): String {
      return value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "flow" }
    }
  }
}
