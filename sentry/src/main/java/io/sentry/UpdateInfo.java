package io.sentry;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Information about an available app update. */
@ApiStatus.Experimental
public final class UpdateInfo {
  private final @NotNull String id;
  private final @NotNull String buildVersion;
  private final int buildNumber;
  private final @NotNull String downloadUrl;
  private final @NotNull String appName;
  private final @Nullable String createdDate;
  private final @Nullable List<String> installGroups;

  public UpdateInfo(
      final @NotNull String id,
      final @NotNull String buildVersion,
      final int buildNumber,
      final @NotNull String downloadUrl,
      final @NotNull String appName,
      final @Nullable String createdDate,
      final @Nullable List<String> installGroups) {
    this.id = id;
    this.buildVersion = buildVersion;
    this.buildNumber = buildNumber;
    this.downloadUrl = downloadUrl;
    this.appName = appName;
    this.createdDate = createdDate;
    this.installGroups = installGroups;
  }

  public @NotNull String getId() {
    return id;
  }

  public @NotNull String getBuildVersion() {
    return buildVersion;
  }

  public int getBuildNumber() {
    return buildNumber;
  }

  public @NotNull String getDownloadUrl() {
    return downloadUrl;
  }

  public @NotNull String getAppName() {
    return appName;
  }

  public @Nullable String getCreatedDate() {
    return createdDate;
  }

  public @Nullable List<String> getInstallGroups() {
    return installGroups;
  }

  @Override
  public String toString() {
    return "UpdateInfo{"
        + "id='"
        + id
        + '\''
        + ", buildVersion='"
        + buildVersion
        + '\''
        + ", buildNumber="
        + buildNumber
        + ", downloadUrl='"
        + downloadUrl
        + '\''
        + ", appName='"
        + appName
        + '\''
        + ", createdDate='"
        + createdDate
        + '\''
        + ", installGroups="
        + installGroups
        + '}';
  }
}
