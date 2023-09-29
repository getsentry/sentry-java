package io.sentry;

import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MonitorConfig implements JsonUnknown, JsonSerializable {

  private @NotNull MonitorSchedule schedule;
  private @Nullable Long checkinMargin;
  private @Nullable Long maxRuntime;
  private @Nullable String timezone;

  private @Nullable Map<String, Object> unknown;

  public MonitorConfig(final @NotNull MonitorSchedule schedule) {
    this.schedule = schedule;
  }

  public @NotNull MonitorSchedule getSchedule() {
    return schedule;
  }

  public void setSchedule(@NotNull MonitorSchedule schedule) {
    this.schedule = schedule;
  }

  public @Nullable Long getCheckinMargin() {
    return checkinMargin;
  }

  public void setCheckinMargin(@Nullable Long checkinMargin) {
    this.checkinMargin = checkinMargin;
  }

  public @Nullable Long getMaxRuntime() {
    return maxRuntime;
  }

  public void setMaxRuntime(@Nullable Long maxRuntime) {
    this.maxRuntime = maxRuntime;
  }

  public @Nullable String getTimezone() {
    return timezone;
  }

  public void setTimezone(@Nullable String timezone) {
    this.timezone = timezone;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String SCHEDULE = "schedule";
    public static final String CHECKIN_MARGIN = "checkin_margin";
    public static final String MAX_RUNTIME = "max_runtime";
    public static final String TIMEZONE = "timezone";
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
    writer.name(JsonKeys.SCHEDULE);
    schedule.serialize(writer, logger);
    if (checkinMargin != null) {
      writer.name(JsonKeys.CHECKIN_MARGIN).value(checkinMargin);
    }
    if (maxRuntime != null) {
      writer.name(JsonKeys.MAX_RUNTIME).value(maxRuntime);
    }
    if (timezone != null) {
      writer.name(JsonKeys.TIMEZONE).value(timezone);
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

  public static final class Deserializer implements JsonDeserializer<MonitorConfig> {
    @Override
    public @NotNull MonitorConfig deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      MonitorSchedule schedule = null;
      Long checkinMargin = null;
      Long maxRuntime = null;
      String timezone = null;
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SCHEDULE:
            schedule = new MonitorSchedule.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.CHECKIN_MARGIN:
            checkinMargin = reader.nextLongOrNull();
            break;
          case JsonKeys.MAX_RUNTIME:
            maxRuntime = reader.nextLongOrNull();
            break;
          case JsonKeys.TIMEZONE:
            timezone = reader.nextStringOrNull();
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

      if (schedule == null) {
        String message = "Missing required field \"" + JsonKeys.SCHEDULE + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      MonitorConfig monitorConfig = new MonitorConfig(schedule);
      monitorConfig.setCheckinMargin(checkinMargin);
      monitorConfig.setMaxRuntime(maxRuntime);
      monitorConfig.setTimezone(timezone);
      monitorConfig.setUnknown(unknown);
      return monitorConfig;
    }
  }
}
