package io.sentry;

import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class MonitorSchedule implements JsonUnknown, JsonSerializable {

  public static @NotNull MonitorSchedule crontab(final @NotNull String value) {
    return new MonitorSchedule(MonitorScheduleType.CRONTAB.apiName(), value, null);
  }

  public static @NotNull MonitorSchedule interval(
      final @NotNull Integer value, final @NotNull MonitorScheduleUnit unit) {
    return new MonitorSchedule(
        MonitorScheduleType.INTERVAL.apiName(), value.toString(), unit.apiName());
  }

  /** crontab | interval */
  private @NotNull String type;

  private @NotNull String value;

  /** only required for type=interval */
  private @Nullable String unit;

  private @Nullable Map<String, Object> unknown;

  @ApiStatus.Internal
  public MonitorSchedule(
      final @NotNull String type, final @NotNull String value, final @Nullable String unit) {
    this.type = type;
    this.value = value;
    this.unit = unit;
  }

  public @NotNull String getType() {
    return type;
  }

  public void setType(final @NotNull String type) {
    this.type = type;
  }

  public @NotNull String getValue() {
    return value;
  }

  public void setValue(final @NotNull String value) {
    this.value = value;
  }

  public void setValue(final @NotNull Integer value) {
    this.value = value.toString();
  }

  public @Nullable String getUnit() {
    return unit;
  }

  public void setUnit(final @Nullable String unit) {
    this.unit = unit;
  }

  public void setUnit(final @Nullable MonitorScheduleUnit unit) {
    this.unit = unit == null ? null : unit.apiName();
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String VALUE = "value";
    public static final String UNIT = "unit";
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
    writer.name(JsonKeys.TYPE).value(type);
    if (MonitorScheduleType.INTERVAL.apiName().equalsIgnoreCase(type)) {
      try {
        writer.name(JsonKeys.VALUE).value(Integer.valueOf(value));
      } catch (Throwable t) {
        logger.log(SentryLevel.ERROR, "Unable to serialize monitor schedule value: %s", value);
      }
    } else {
      writer.name(JsonKeys.VALUE).value(value);
    }
    if (unit != null) {
      writer.name(JsonKeys.UNIT).value(unit);
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

  public static final class Deserializer implements JsonDeserializer<MonitorSchedule> {
    @Override
    public @NotNull MonitorSchedule deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      String type = null;
      String value = null;
      String unit = null;
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TYPE:
            type = reader.nextStringOrNull();
            break;
          case JsonKeys.VALUE:
            value = reader.nextStringOrNull();
            break;
          case JsonKeys.UNIT:
            unit = reader.nextStringOrNull();
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

      if (type == null) {
        String message = "Missing required field \"" + JsonKeys.TYPE + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (value == null) {
        String message = "Missing required field \"" + JsonKeys.VALUE + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      MonitorSchedule monitorSchedule = new MonitorSchedule(type, value, unit);
      monitorSchedule.setUnknown(unknown);
      return monitorSchedule;
    }
  }
}
