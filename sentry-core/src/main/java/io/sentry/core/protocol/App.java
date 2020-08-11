package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import io.sentry.core.util.CollectionUtils;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class App implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "app";

  private String appIdentifier;
  private Date appStartTime;
  private String deviceAppHash;
  private String buildType;
  private String appName;
  private String appVersion;
  private String appBuild;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getAppIdentifier() {
    return appIdentifier;
  }

  public void setAppIdentifier(String appIdentifier) {
    this.appIdentifier = appIdentifier;
  }

  @SuppressWarnings("JdkObsolete")
  public Date getAppStartTime() {
    final Date appStartTimeRef = appStartTime;
    return appStartTimeRef != null ? (Date) appStartTimeRef.clone() : null;
  }

  public void setAppStartTime(Date appStartTime) {
    this.appStartTime = appStartTime;
  }

  public String getDeviceAppHash() {
    return deviceAppHash;
  }

  public void setDeviceAppHash(String deviceAppHash) {
    this.deviceAppHash = deviceAppHash;
  }

  public String getBuildType() {
    return buildType;
  }

  public void setBuildType(String buildType) {
    this.buildType = buildType;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(String appVersion) {
    this.appVersion = appVersion;
  }

  public String getAppBuild() {
    return appBuild;
  }

  public void setAppBuild(String appBuild) {
    this.appBuild = appBuild;
  }

  @TestOnly
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  /**
   * Clones an App aka deep copy
   *
   * @return the cloned App
   * @throws CloneNotSupportedException if object is not cloneable
   */
  @Override
  public @NotNull App clone() throws CloneNotSupportedException {
    final App clone = (App) super.clone();

    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
