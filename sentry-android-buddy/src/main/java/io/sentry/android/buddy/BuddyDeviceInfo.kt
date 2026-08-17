package io.sentry.android.buddy

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyDeviceInfo
public constructor(
  public val manufacturer: String? = null,
  public val model: String? = null,
  public val osName: String = "Android",
  public val osVersion: String? = null,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("manufacturer").value(manufacturer)
    writer.name("model").value(model)
    writer.name("osName").value(osName)
    writer.name("osVersion").value(osVersion)
    writer.endObject()
  }
}
