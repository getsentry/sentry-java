package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds information about the system SDK.
 *
 * <p>This is relevant for iOS and other platforms that have a system SDK. Not to be confused with
 * the client SDK.
 */
public final class SdkInfo implements JsonUnknown, JsonSerializable {
  /** The internal name of the SDK. */
  private @Nullable String sdkName;

  /** The major version of the SDK as integer or 0. */
  private @Nullable Integer versionMajor;

  /** The minor version of the SDK as integer or 0. */
  private @Nullable Integer versionMinor;

  /** The patch version of the SDK as integer or 0. */
  private @Nullable Integer versionPatchlevel;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public @Nullable String getSdkName() {
    return sdkName;
  }

  public void setSdkName(final @Nullable String sdkName) {
    this.sdkName = sdkName;
  }

  public @Nullable Integer getVersionMajor() {
    return versionMajor;
  }

  public void setVersionMajor(final @Nullable Integer versionMajor) {
    this.versionMajor = versionMajor;
  }

  public @Nullable Integer getVersionMinor() {
    return versionMinor;
  }

  public void setVersionMinor(final @Nullable Integer versionMinor) {
    this.versionMinor = versionMinor;
  }

  public @Nullable Integer getVersionPatchlevel() {
    return versionPatchlevel;
  }

  public void setVersionPatchlevel(final @Nullable Integer versionPatchlevel) {
    this.versionPatchlevel = versionPatchlevel;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String SDK_NAME = "sdk_name";
    public static final String VERSION_MAJOR = "version_major";
    public static final String VERSION_MINOR = "version_minor";
    public static final String VERSION_PATCHLEVEL = "version_patchlevel";
  }

  // JsonUnknown

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (sdkName != null) {
      writer.name(JsonKeys.SDK_NAME).value(sdkName);
    }
    if (versionMajor != null) {
      writer.name(JsonKeys.VERSION_MAJOR).value(versionMajor);
    }
    if (versionMinor != null) {
      writer.name(JsonKeys.VERSION_MINOR).value(versionMinor);
    }
    if (versionPatchlevel != null) {
      writer.name(JsonKeys.VERSION_PATCHLEVEL).value(versionPatchlevel);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  // JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<SdkInfo> {
    @Override
    public @NotNull SdkInfo deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {

      SdkInfo sdkInfo = new SdkInfo();
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SDK_NAME:
            sdkInfo.sdkName = reader.nextStringOrNull();
            break;
          case JsonKeys.VERSION_MAJOR:
            sdkInfo.versionMajor = reader.nextIntegerOrNull();
            break;
          case JsonKeys.VERSION_MINOR:
            sdkInfo.versionMinor = reader.nextIntegerOrNull();
            break;
          case JsonKeys.VERSION_PATCHLEVEL:
            sdkInfo.versionPatchlevel = reader.nextIntegerOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      sdkInfo.setUnknown(unknown);
      return sdkInfo;
    }
  }
}
