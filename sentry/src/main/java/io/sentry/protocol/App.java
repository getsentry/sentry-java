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
import java.util.Date;
import java.util.List;
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
  /** Application permissions in the form of "permission_name" : "granted|not_granted" */
  private @Nullable Map<String, String> permissions;
  /** The list of the visible UI screens * */
  private @Nullable List<String> viewNames;
  /** the app start type */
  private @Nullable String startType;
  /**
   * A flag indicating whether the app is in foreground or not. An app is in foreground when it's
   * visible to the user.
   */
  private @Nullable Boolean inForeground;

  public App() {}

  App(final @NotNull App app) {
    this.appBuild = app.appBuild;
    this.appIdentifier = app.appIdentifier;
    this.appName = app.appName;
    this.appStartTime = app.appStartTime;
    this.appVersion = app.appVersion;
    this.buildType = app.buildType;
    this.deviceAppHash = app.deviceAppHash;
    this.permissions = CollectionUtils.newConcurrentHashMap(app.permissions);
    this.inForeground = app.inForeground;
    this.viewNames = CollectionUtils.newArrayList(app.viewNames);
    this.startType = app.startType;
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

  public @Nullable Map<String, String> getPermissions() {
    return permissions;
  }

  public void setPermissions(@Nullable Map<String, String> permissions) {
    this.permissions = permissions;
  }

  @Nullable
  public Boolean getInForeground() {
    return inForeground;
  }

  public void setInForeground(final @Nullable Boolean inForeground) {
    this.inForeground = inForeground;
  }

  @Nullable
  public List<String> getViewNames() {
    return viewNames;
  }

  public void setViewNames(final @Nullable List<String> viewNames) {
    this.viewNames = viewNames;
  }

  @Nullable
  public String getStartType() {
    return startType;
  }

  public void setStartType(final @Nullable String startType) {
    this.startType = startType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    App app = (App) o;
    return Objects.equals(appIdentifier, app.appIdentifier)
        && Objects.equals(appStartTime, app.appStartTime)
        && Objects.equals(deviceAppHash, app.deviceAppHash)
        && Objects.equals(buildType, app.buildType)
        && Objects.equals(appName, app.appName)
        && Objects.equals(appVersion, app.appVersion)
        && Objects.equals(appBuild, app.appBuild)
        && Objects.equals(permissions, app.permissions)
        && Objects.equals(inForeground, app.inForeground)
        && Objects.equals(viewNames, app.viewNames)
        && Objects.equals(startType, app.startType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        appIdentifier,
        appStartTime,
        deviceAppHash,
        buildType,
        appName,
        appVersion,
        appBuild,
        permissions,
        inForeground,
        viewNames,
        startType);
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
    public static final String APP_PERMISSIONS = "permissions";
    public static final String IN_FOREGROUND = "in_foreground";
    public static final String VIEW_NAMES = "view_names";
    public static final String START_TYPE = "start_type";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
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
    if (permissions != null && !permissions.isEmpty()) {
      writer.name(JsonKeys.APP_PERMISSIONS).value(logger, permissions);
    }
    if (inForeground != null) {
      writer.name(JsonKeys.IN_FOREGROUND).value(inForeground);
    }
    if (viewNames != null) {
      writer.name(JsonKeys.VIEW_NAMES).value(logger, viewNames);
    }
    if (startType != null) {
      writer.name(JsonKeys.START_TYPE).value(startType);
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
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull App deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
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
          case JsonKeys.APP_PERMISSIONS:
            app.permissions =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, String>) reader.nextObjectOrNull());
            break;
          case JsonKeys.IN_FOREGROUND:
            app.inForeground = reader.nextBooleanOrNull();
            break;
          case JsonKeys.VIEW_NAMES:
            final @Nullable List<String> viewNames = (List<String>) reader.nextObjectOrNull();
            if (viewNames != null) {
              app.setViewNames(viewNames);
            }
            break;
          case JsonKeys.START_TYPE:
            app.startType = reader.nextStringOrNull();
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
