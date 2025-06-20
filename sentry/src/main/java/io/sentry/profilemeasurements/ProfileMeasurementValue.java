package io.sentry.profilemeasurements;

import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfileMeasurementValue implements JsonUnknown, JsonSerializable {

  private @Nullable Map<String, Object> unknown;
  private double timestamp;
  private @NotNull String relativeStartNs; // timestamp in nanoseconds this frame was started
  private double value; // frame duration in nanoseconds

  @SuppressWarnings("JavaUtilDate")
  public ProfileMeasurementValue() {
    this(0L, 0, 0);
  }

  public ProfileMeasurementValue(
      final @NotNull Long relativeStartNs, final @NotNull Number value, final long nanoTimestamp) {
    this.relativeStartNs = relativeStartNs.toString();
    this.value = value.doubleValue();
    this.timestamp = DateUtils.nanosToSeconds(nanoTimestamp);
  }

  public double getTimestamp() {
    return timestamp;
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
        && value == that.value
        && timestamp == that.timestamp;
  }

  @Override
  public int hashCode() {
    return Objects.hash(unknown, relativeStartNs, value);
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String VALUE = "value";
    public static final String START_NS = "elapsed_since_start_ns";
    public static final String TIMESTAMP = "timestamp";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.VALUE).value(logger, value);
    writer.name(JsonKeys.START_NS).value(logger, relativeStartNs);
    writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private @NotNull BigDecimal doubleToBigDecimal(final @NotNull Double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.DOWN);
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
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
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
          case JsonKeys.TIMESTAMP:
            Double timestamp;
            try {
              timestamp = reader.nextDoubleOrNull();
            } catch (NumberFormatException e) {
              final Date date = reader.nextDateOrNull(logger);
              timestamp = date != null ? DateUtils.dateToSeconds(date) : null;
            }
            if (timestamp != null) {
              data.timestamp = timestamp;
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
