package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class App implements IUnknownPropertiesConsumer {
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

  @TestOnly
  @Nullable
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(@NotNull Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }
}
