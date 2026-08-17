package io.sentry.android.buddy

import io.sentry.ILogger
import io.sentry.JsonSerializable
import io.sentry.ObjectWriter
import java.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyAppInfo
public constructor(
  public val packageName: String,
  public val versionName: String? = null,
  public val versionCode: Long? = null,
  public val release: String? = null,
  public val environment: String? = null,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("packageName").value(packageName)
    writer.name("versionName").value(versionName)
    if (versionCode != null) {
      writer.name("versionCode").value(versionCode)
    }
    writer.name("release").value(release)
    writer.name("environment").value(environment)
    writer.endObject()
  }
}
