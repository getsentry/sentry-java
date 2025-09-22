package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A check-in for a monitor (CRON). */
public final class CheckIn implements JsonUnknown, JsonSerializable {

  private final @NotNull SentryId checkInId;
  private @NotNull String monitorSlug;
  private @NotNull String status;
  private @Nullable Double duration; // in seconds
  private @Nullable String release;
  private @Nullable String environment;

  private final @NotNull MonitorContexts contexts = new MonitorContexts();
  private @Nullable MonitorConfig monitorConfig;

  private @Nullable Map<String, Object> unknown;

  public CheckIn(final @NotNull String monitorSlug, final @NotNull CheckInStatus status) {
    this(null, monitorSlug, status.apiName());
  }

  public CheckIn(
      final @Nullable SentryId id,
      final @NotNull String monitorSlug,
      final @NotNull CheckInStatus status) {
    this(id, monitorSlug, status.apiName());
  }

  @ApiStatus.Internal
  public CheckIn(
      final @Nullable SentryId checkInId,
      final @NotNull String monitorSlug,
      final @NotNull String status) {
    this.checkInId = checkInId == null ? new SentryId() : checkInId;
    this.monitorSlug = monitorSlug;
    this.status = status;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String CHECK_IN_ID = "check_in_id";
    public static final String MONITOR_SLUG = "monitor_slug";
    public static final String STATUS = "status";
    public static final String DURATION = "duration";
    public static final String RELEASE = "release";
    public static final String ENVIRONMENT = "environment";
    public static final String CONTEXTS = "contexts";
    public static final String MONITOR_CONFIG = "monitor_config";
  }

  public @NotNull SentryId getCheckInId() {
    return checkInId;
  }

  public @NotNull String getMonitorSlug() {
    return monitorSlug;
  }

  public void setMonitorSlug(@NotNull String monitorSlug) {
    this.monitorSlug = monitorSlug;
  }

  public @NotNull String getStatus() {
    return status;
  }

  public void setStatus(@NotNull String status) {
    this.status = status;
  }

  public void setStatus(@NotNull CheckInStatus status) {
    this.status = status.apiName();
  }

  public @Nullable Double getDuration() {
    return duration;
  }

  public void setDuration(@Nullable Double duration) {
    this.duration = duration;
  }

  public @Nullable String getRelease() {
    return release;
  }

  public void setRelease(@Nullable String release) {
    this.release = release;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public void setEnvironment(@Nullable String environment) {
    this.environment = environment;
  }

  public @Nullable MonitorConfig getMonitorConfig() {
    return monitorConfig;
  }

  public void setMonitorConfig(@Nullable MonitorConfig monitorConfig) {
    this.monitorConfig = monitorConfig;
  }

  public @NotNull MonitorContexts getContexts() {
    return contexts;
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
    writer.name(JsonKeys.CHECK_IN_ID);
    checkInId.serialize(writer, logger);
    writer.name(JsonKeys.MONITOR_SLUG).value(monitorSlug);
    writer.name(JsonKeys.STATUS).value(status);
    if (duration != null) {
      writer.name(JsonKeys.DURATION).value(duration);
    }
    if (release != null) {
      writer.name(JsonKeys.RELEASE).value(release);
    }
    if (environment != null) {
      writer.name(JsonKeys.ENVIRONMENT).value(environment);
    }
    if (monitorConfig != null) {
      writer.name(JsonKeys.MONITOR_CONFIG);
      monitorConfig.serialize(writer, logger);
    }
    if (contexts != null) {
      writer.name(JsonKeys.CONTEXTS);
      contexts.serialize(writer, logger);
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

  public static final class Deserializer implements JsonDeserializer<CheckIn> {
    @Override
    public @NotNull CheckIn deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      SentryId sentryId = null;
      MonitorConfig monitorConfig = null;
      String monitorSlug = null;
      String status = null;
      Double duration = null;
      String release = null;
      String environment = null;
      MonitorContexts contexts = null;
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.CHECK_IN_ID:
            sentryId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.MONITOR_SLUG:
            monitorSlug = reader.nextStringOrNull();
            break;
          case JsonKeys.STATUS:
            status = reader.nextStringOrNull();
            break;
          case JsonKeys.DURATION:
            duration = reader.nextDoubleOrNull();
            break;
          case JsonKeys.RELEASE:
            release = reader.nextStringOrNull();
            break;
          case JsonKeys.ENVIRONMENT:
            environment = reader.nextStringOrNull();
            break;
          case JsonKeys.MONITOR_CONFIG:
            monitorConfig = new MonitorConfig.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.CONTEXTS:
            contexts = new MonitorContexts.Deserializer().deserialize(reader, logger);
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

      if (sentryId == null) {
        String message = "Missing required field \"" + JsonKeys.CHECK_IN_ID + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (monitorSlug == null) {
        String message = "Missing required field \"" + JsonKeys.MONITOR_SLUG + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      if (status == null) {
        String message = "Missing required field \"" + JsonKeys.STATUS + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      CheckIn checkIn = new CheckIn(sentryId, monitorSlug, status);
      checkIn.setDuration(duration);
      checkIn.setRelease(release);
      checkIn.setEnvironment(environment);
      checkIn.setMonitorConfig(monitorConfig);
      checkIn.getContexts().putAll(contexts);
      checkIn.setUnknown(unknown);
      return checkIn;
    }
  }
}
