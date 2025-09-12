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
  private @Nullable Long failureIssueThreshold;
  private @Nullable Long recoveryThreshold;
  private @Nullable Map<String, Object> unknown;

  public MonitorConfig(final @NotNull MonitorSchedule schedule) {
    this.schedule = schedule;
    final SentryOptions.Cron defaultCron = ScopesAdapter.getInstance().getOptions().getCron();
    if (defaultCron != null) {
      this.checkinMargin = defaultCron.getDefaultCheckinMargin();
      this.maxRuntime = defaultCron.getDefaultMaxRuntime();
      this.timezone = defaultCron.getDefaultTimezone();
      this.failureIssueThreshold = defaultCron.getDefaultFailureIssueThreshold();
      this.recoveryThreshold = defaultCron.getDefaultRecoveryThreshold();
    }
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

  public @Nullable Long getFailureIssueThreshold() {
    return failureIssueThreshold;
  }

  public void setFailureIssueThreshold(@Nullable Long failureIssueThreshold) {
    this.failureIssueThreshold = failureIssueThreshold;
  }

  public @Nullable Long getRecoveryThreshold() {
    return recoveryThreshold;
  }

  public void setRecoveryThreshold(@Nullable Long recoveryThreshold) {
    this.recoveryThreshold = recoveryThreshold;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String SCHEDULE = "schedule";
    public static final String CHECKIN_MARGIN = "checkin_margin";
    public static final String MAX_RUNTIME = "max_runtime";
    public static final String TIMEZONE = "timezone";
    public static final String FAILURE_ISSUE_THRESHOLD = "failure_issue_threshold";
    public static final String RECOVERY_THRESHOLD = "recovery_threshold";
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
    if (failureIssueThreshold != null) {
      writer.name(JsonKeys.FAILURE_ISSUE_THRESHOLD).value(failureIssueThreshold);
    }
    if (recoveryThreshold != null) {
      writer.name(JsonKeys.RECOVERY_THRESHOLD).value(recoveryThreshold);
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
    public @NotNull MonitorConfig deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      MonitorSchedule schedule = null;
      Long checkinMargin = null;
      Long maxRuntime = null;
      String timezone = null;
      Long failureIssureThreshold = null;
      Long recoveryThreshold = null;
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
          case JsonKeys.FAILURE_ISSUE_THRESHOLD:
            failureIssureThreshold = reader.nextLongOrNull();
            break;
          case JsonKeys.RECOVERY_THRESHOLD:
            recoveryThreshold = reader.nextLongOrNull();
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
      monitorConfig.setFailureIssueThreshold(failureIssureThreshold);
      monitorConfig.setRecoveryThreshold(recoveryThreshold);
      monitorConfig.setUnknown(unknown);
      return monitorConfig;
    }
  }
}
