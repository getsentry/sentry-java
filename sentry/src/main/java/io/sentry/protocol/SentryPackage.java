package io.sentry.protocol;

import static io.sentry.SentryLevel.ERROR;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An installed and loaded package as part of the Sentry SDK. */
public final class SentryPackage implements JsonUnknown, JsonSerializable {
  /** Name of the package. */
  private @NotNull String name;
  /** Version of the package. */
  private @NotNull String version;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public SentryPackage(final @NotNull String name, final @NotNull String version) {
    this.name = Objects.requireNonNull(name, "name is required.");
    this.version = Objects.requireNonNull(version, "version is required.");
  }

  public @NotNull String getName() {
    return name;
  }

  public void setName(final @NotNull String name) {
    this.name = Objects.requireNonNull(name, "name is required.");
  }

  public @NotNull String getVersion() {
    return version;
  }

  public void setVersion(final @NotNull String version) {
    this.version = Objects.requireNonNull(version, "version is required.");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SentryPackage that = (SentryPackage) o;
    return java.util.Objects.equals(name, that.name)
        && java.util.Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(name, version);
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String VERSION = "version";
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
    writer.name(JsonKeys.NAME).value(name);
    writer.name(JsonKeys.VERSION).value(version);
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  // JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<SentryPackage> {
    @Override
    public @NotNull SentryPackage deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {

      String name = null;
      String version = null;
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            name = reader.nextString();
            break;
          case JsonKeys.VERSION:
            version = reader.nextString();
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

      if (name == null) {
        String message = "Missing required field \"" + JsonKeys.NAME + "\"";
        Exception exception = new IllegalStateException(message);
        if (logger.isEnabled(ERROR)) {
          logger.log(SentryLevel.ERROR, message, exception);
        }
        throw exception;
      }
      if (version == null) {
        String message = "Missing required field \"" + JsonKeys.VERSION + "\"";
        Exception exception = new IllegalStateException(message);
        if (logger.isEnabled(ERROR)) {
          logger.log(SentryLevel.ERROR, message, exception);
        }
        throw exception;
      }

      SentryPackage sentryPackage = new SentryPackage(name, version);
      sentryPackage.setUnknown(unknown);
      return sentryPackage;
    }
  }
}
