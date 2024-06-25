package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Browser implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "browser";
  /** Display name of the browser application. */
  private @Nullable String name;
  /** Version string of the browser. */
  private @Nullable String version;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public Browser() {}

  Browser(final @NotNull Browser browser) {
    this.name = browser.name;
    this.version = browser.version;
    this.unknown = CollectionUtils.newConcurrentHashMap(browser.unknown);
  }

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Browser browser = (Browser) o;
    return Objects.equals(name, browser.name) && Objects.equals(version, browser.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, version);
  }

  // region json

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String VERSION = "version";
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
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<Browser> {
    @Override
    public @NotNull Browser deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      Browser browser = new Browser();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            browser.name = reader.nextStringOrNull();
            break;
          case JsonKeys.VERSION:
            browser.version = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      browser.setUnknown(unknown);
      reader.endObject();
      return browser;
    }
  }

  // endregion
}
