package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryRuntime implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "runtime";

  /** Runtime name. */
  private @Nullable String name;

  /** Runtime version string. */
  private @Nullable String version;

  /**
   * Unprocessed runtime info.
   *
   * <p>An unprocessed description string obtained by the runtime. For some well-known runtimes,
   * Sentry will attempt to parse `name` and `version` from this string, if they are not explicitly
   * given.
   */
  private @Nullable String rawDescription;

  public SentryRuntime() {}

  SentryRuntime(final @NotNull SentryRuntime sentryRuntime) {
    this.name = sentryRuntime.name;
    this.version = sentryRuntime.version;
    this.rawDescription = sentryRuntime.rawDescription;
    this.unknown = CollectionUtils.newConcurrentHashMap(sentryRuntime.unknown);
  }

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public @Nullable String getName() {
    return name;
  }

  public void setName(final @Nullable String name) {
    this.name = name;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(final @Nullable String version) {
    this.version = version;
  }

  public @Nullable String getRawDescription() {
    return rawDescription;
  }

  public void setRawDescription(final @Nullable String rawDescription) {
    this.rawDescription = rawDescription;
  }

  // region JsonSerializable

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String VERSION = "version";
    public static final String RAW_DESCRIPTION = "raw_description";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (name != null) {
      writer.name(JsonKeys.NAME).value(name);
    }
    if (version != null) {
      writer.name(JsonKeys.VERSION).value(version);
    }
    if (rawDescription != null) {
      writer.name(JsonKeys.RAW_DESCRIPTION).value(rawDescription);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<SentryRuntime> {
    @Override
    public @NotNull SentryRuntime deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      SentryRuntime runtime = new SentryRuntime();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            runtime.name = reader.nextStringOrNull();
            break;
          case JsonKeys.VERSION:
            runtime.version = reader.nextStringOrNull();
            break;
          case JsonKeys.RAW_DESCRIPTION:
            runtime.rawDescription = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      runtime.setUnknown(unknown);
      reader.endObject();
      return runtime;
    }
  }

  // endregion
}
