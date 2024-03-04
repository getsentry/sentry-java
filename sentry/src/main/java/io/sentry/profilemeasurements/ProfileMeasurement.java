package io.sentry.profilemeasurements;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfileMeasurement implements JsonUnknown, JsonSerializable {

  public static final String ID_FROZEN_FRAME_RENDERS = "frozen_frame_renders";
  public static final String ID_SLOW_FRAME_RENDERS = "slow_frame_renders";
  public static final String ID_SCREEN_FRAME_RATES = "screen_frame_rates";
  public static final String ID_CPU_USAGE = "cpu_usage";
  public static final String ID_MEMORY_FOOTPRINT = "memory_footprint";
  public static final String ID_MEMORY_NATIVE_FOOTPRINT = "memory_native_footprint";
  public static final String ID_UNKNOWN = "unknown";

  public static final String UNIT_HZ = "hz";
  public static final String UNIT_NANOSECONDS = "nanosecond";
  public static final String UNIT_BYTES = "byte";
  public static final String UNIT_PERCENT = "percent";
  public static final String UNIT_UNKNOWN = "unknown";

  private @Nullable Map<String, Object> unknown;
  private @NotNull String unit; // Unit of the value
  private @NotNull Collection<ProfileMeasurementValue> values;

  public ProfileMeasurement() {
    this(UNIT_UNKNOWN, new ArrayList<>());
  }

  public ProfileMeasurement(
      final @NotNull String unit, final @NotNull Collection<ProfileMeasurementValue> values) {
    this.unit = unit;
    this.values = values;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProfileMeasurement that = (ProfileMeasurement) o;
    return Objects.equals(unknown, that.unknown)
        && unit.equals(that.unit)
        && new ArrayList<>(values).equals(new ArrayList<>(that.values));
  }

  @Override
  public int hashCode() {
    return Objects.hash(unknown, unit, values);
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String UNIT = "unit";
    public static final String VALUES = "values";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.UNIT).value(logger, unit);
    writer.name(JsonKeys.VALUES).value(logger, values);
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

  public @NotNull String getUnit() {
    return unit;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public void setUnit(final @NotNull String unit) {
    this.unit = unit;
  }

  public @NotNull Collection<ProfileMeasurementValue> getValues() {
    return values;
  }

  public void setValues(final @NotNull Collection<ProfileMeasurementValue> values) {
    this.values = values;
  }

  public static final class Deserializer implements JsonDeserializer<ProfileMeasurement> {

    @Override
    public @NotNull ProfileMeasurement deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      ProfileMeasurement data = new ProfileMeasurement();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.UNIT:
            String unit = reader.nextStringOrNull();
            if (unit != null) {
              data.unit = unit;
            }
            break;
          case JsonKeys.VALUES:
            List<ProfileMeasurementValue> values =
                reader.nextListOrNull(logger, new ProfileMeasurementValue.Deserializer());
            if (values != null) {
              data.values = values;
            }
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      data.setUnknown(unknown);
      reader.endObject();
      return data;
    }
  }
}
