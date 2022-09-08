package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class MeasurementValue implements JsonUnknown, JsonSerializable {

  public static final @NotNull String NONE_UNIT = "none";
  public static final @NotNull String MILLISECOND_UNIT = "millisecond";

  @SuppressWarnings("UnusedVariable")
  private final float value;

  private final @Nullable String unit;

  /** the unknown fields of breadcrumbs, internal usage only */
  private @Nullable Map<String, Object> unknown;

  public MeasurementValue(final float value, final @Nullable String unit) {
    this.value = value;
    this.unit = unit;
  }

  @TestOnly
  public MeasurementValue(
      final float value, final @Nullable String unit, final @Nullable Map<String, Object> unknown) {
    this.value = value;
    this.unit = unit;
    this.unknown = unknown;
  }

  @TestOnly
  public float getValue() {
    return value;
  }

  public @Nullable String getUnit() {
    return unit;
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

  // JsonSerializable

  public static final class JsonKeys {
    public static final String VALUE = "value";
    public static final String UNIT = "unit";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.VALUE).value(value);

    if (unit != null) {
      writer.name(JsonKeys.UNIT).value(unit);
    }

    if (unknown != null) {
      for (final String key : unknown.keySet()) {
        final Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }

    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<MeasurementValue> {
    @Override
    public @NotNull MeasurementValue deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      String unit = null;
      float value = 0;
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.VALUE:
            value = reader.nextFloat();
            break;
          case JsonKeys.UNIT:
            unit = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }

      reader.endObject();
      final MeasurementValue measurement = new MeasurementValue(value, unit);
      measurement.setUnknown(unknown);

      return measurement;
    }
  }
}
