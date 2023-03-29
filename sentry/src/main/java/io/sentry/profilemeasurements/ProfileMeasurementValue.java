package io.sentry.profilemeasurements;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfileMeasurementValue implements JsonUnknown, JsonSerializable {

  private @Nullable Map<String, Object> unknown;
  private @NotNull String relativeStartNs; // timestamp in nanoseconds this frame was started
  private double value; // frame duration in nanoseconds

  public ProfileMeasurementValue() {
    this(0L, 0);
  }

  public ProfileMeasurementValue(final @NotNull Long relativeStartNs, final @NotNull Number value) {
    this.relativeStartNs = relativeStartNs.toString();
    this.value = value.doubleValue();
  }

  public double getValue() {
    return value;
  }

  public @NotNull String getRelativeStartNs() {
    return relativeStartNs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProfileMeasurementValue that = (ProfileMeasurementValue) o;
    return Objects.equals(unknown, that.unknown)
        && relativeStartNs.equals(that.relativeStartNs)
        && value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(unknown, relativeStartNs, value);
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String VALUE = "value";
    public static final String START_NS = "elapsed_since_start_ns";
  }

  @Override
  public void serialize(final @NotNull JsonObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.VALUE).value(logger, value);
    writer.name(JsonKeys.START_NS).value(logger, relativeStartNs);
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
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<ProfileMeasurementValue> {

    @Override
    public @NotNull ProfileMeasurementValue deserialize(
        final @NotNull JsonObjectReader reader, final @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      ProfileMeasurementValue data = new ProfileMeasurementValue();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.VALUE:
            Double value = reader.nextDoubleOrNull();
            if (value != null) {
              data.value = value;
            }
            break;
          case JsonKeys.START_NS:
            String startNs = reader.nextStringOrNull();
            if (startNs != null) {
              data.relativeStartNs = startNs;
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
