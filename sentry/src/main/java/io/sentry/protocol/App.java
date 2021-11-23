package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class App implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "app";

  /** Version-independent application identifier, often a dotted bundle ID. */
  private @Nullable String appIdentifier;
  /**
   * Start time of the app.
   *
   * <p>Formatted UTC timestamp when the user started the application.
   */
  private @Nullable Date appStartTime;
  /** Application-specific device identifier. */
  private @Nullable String deviceAppHash;
  /** String identifying the kind of build. For example, `testflight`. */
  private @Nullable String buildType;
  /** Application name as it appears on the platform. */
  private @Nullable String appName;
  /** Application version as it appears on the platform. */
  private @Nullable String appVersion;
  /** Internal build ID as it appears on the platform. */
  private @Nullable String appBuild;

  public App() {}

  App(final @NotNull App app) {
    this.appBuild = app.appBuild;
    this.appIdentifier = app.appIdentifier;
    this.appName = app.appName;
    this.appStartTime = app.appStartTime;
    this.appVersion = app.appVersion;
    this.buildType = app.buildType;
    this.deviceAppHash = app.deviceAppHash;
    this.unknown = CollectionUtils.newConcurrentHashMap(app.unknown);
  }

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public @Nullable String getAppIdentifier() {
    return appIdentifier;
  }

  public void setAppIdentifier(final @Nullable String appIdentifier) {
    this.appIdentifier = appIdentifier;
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public @Nullable Date getAppStartTime() {
    final Date appStartTimeRef = appStartTime;
    return appStartTimeRef != null ? (Date) appStartTimeRef.clone() : null;
  }

  public void setAppStartTime(final @Nullable Date appStartTime) {
    this.appStartTime = appStartTime;
  }

  public @Nullable String getDeviceAppHash() {
    return deviceAppHash;
  }

  public void setDeviceAppHash(final @Nullable String deviceAppHash) {
    this.deviceAppHash = deviceAppHash;
  }

  public @Nullable String getBuildType() {
    return buildType;
  }

  public void setBuildType(final @Nullable String buildType) {
    this.buildType = buildType;
  }

  public @Nullable String getAppName() {
    return appName;
  }

  public void setAppName(final @Nullable String appName) {
    this.appName = appName;
  }

  public @Nullable String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(final @Nullable String appVersion) {
    this.appVersion = appVersion;
  }

  public @Nullable String getAppBuild() {
    return appBuild;
  }

  public void setAppBuild(final @Nullable String appBuild) {
    this.appBuild = appBuild;
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
    public static final String APP_IDENTIFIER = "app_identifier";
    public static final String APP_START_TIME = "app_start_time";
    public static final String DEVICE_APP_HASH = "device_app_hash";
    public static final String BUILD_TYPE = "build_type";
    public static final String APP_NAME = "app_name";
    public static final String APP_VERSION = "app_version";
    public static final String APP_BUILD = "app_build";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (appIdentifier != null) {
      writer.name(JsonKeys.APP_IDENTIFIER).value(appIdentifier);
    }
    if (appStartTime != null) {
      writer.name(JsonKeys.APP_START_TIME).value(logger, appStartTime);
    }
    if (deviceAppHash != null) {
      writer.name(JsonKeys.DEVICE_APP_HASH).value(deviceAppHash);
    }
    if (buildType != null) {
      writer.name(JsonKeys.BUILD_TYPE).value(buildType);
    }
    if (appName != null) {
      writer.name(JsonKeys.APP_NAME).value(appName);
    }
    if (appVersion != null) {
      writer.name(JsonKeys.APP_VERSION).value(appVersion);
    }
    if (appBuild != null) {
      writer.name(JsonKeys.APP_BUILD).value(appBuild);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<App> {
    @Override
    public @NotNull App deserialize(@NotNull JsonObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      App app = new App();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.APP_IDENTIFIER:
            app.appIdentifier = reader.nextStringOrNull();
            break;
          case JsonKeys.APP_START_TIME:
            app.appStartTime = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.DEVICE_APP_HASH:
            app.deviceAppHash = reader.nextStringOrNull();
            break;
          case JsonKeys.BUILD_TYPE:
            app.buildType = reader.nextStringOrNull();
            break;
          case JsonKeys.APP_NAME:
            app.appName = reader.nextStringOrNull();
            break;
          case JsonKeys.APP_VERSION:
            app.appVersion = reader.nextStringOrNull();
            break;
          case JsonKeys.APP_BUILD:
            app.appBuild = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      app.setUnknown(unknown);
      reader.endObject();
      return app;
    }
  }
}
