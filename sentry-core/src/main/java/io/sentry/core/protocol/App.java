package io.sentry.core.protocol;

import java.util.Date;

public class App {
  static final String TYPE = "app";

  private String appIdentifier;
  private Date appStartTime;
  private String deviceAppHash;
  private String buildType;
  private String appName;
  private String appVersion;
  private String appBuild;

  public String getAppIdentifier() {
    return appIdentifier;
  }

  public void setAppIdentifier(String appIdentifier) {
    this.appIdentifier = appIdentifier;
  }

  public Date getAppStartTime() {
    return appStartTime;
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
}
