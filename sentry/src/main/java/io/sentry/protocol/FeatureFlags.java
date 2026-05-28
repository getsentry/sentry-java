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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FeatureFlags implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "flags";

  private @NotNull List<FeatureFlag> values;

  public FeatureFlags() {
    this.values = new ArrayList<>();
  }

  FeatureFlags(final @NotNull FeatureFlags featureFlags) {
    this.values = featureFlags.values;
    this.unknown = CollectionUtils.newConcurrentHashMap(featureFlags.unknown);
  }

  public FeatureFlags(final @NotNull List<FeatureFlag> values) {
    this.values = values;
  }

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  @NotNull
  public List<FeatureFlag> getValues() {
    return values;
  }

  public void setValues(final @NotNull List<FeatureFlag> values) {
    this.values = values;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FeatureFlags flags = (FeatureFlags) o;
    return Objects.equals(values, flags.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(values);
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
    public static final String VALUES = "values";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();

    writer.name(JsonKeys.VALUES).value(logger, values);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<FeatureFlags> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull FeatureFlags deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      @Nullable List<FeatureFlag> values = null;
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.VALUES:
            values = reader.nextListOrNull(logger, new FeatureFlag.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      if (values == null) {
        values = new ArrayList<>();
      }
      FeatureFlags flags = new FeatureFlags(values);
      flags.setUnknown(unknown);
      reader.endObject();
      return flags;
    }
  }
}
