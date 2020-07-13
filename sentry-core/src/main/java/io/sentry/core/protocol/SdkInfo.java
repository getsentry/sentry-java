package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import io.sentry.core.util.IntegerUtils;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class SdkInfo implements IUnknownPropertiesConsumer {
  private String sdkName;
  private Integer versionMajor;
  private Integer versionMinor;
  private Integer versionPatchlevel;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getSdkName() {
    return sdkName;
  }

  public void setSdkName(String sdkName) {
    this.sdkName = sdkName;
  }

  public Integer getVersionMajor() {
    return versionMajor;
  }

  public void setVersionMajor(Integer versionMajor) {
    this.versionMajor = versionMajor;
  }

  public Integer getVersionMinor() {
    return versionMinor;
  }

  public void setVersionMinor(Integer versionMinor) {
    this.versionMinor = versionMinor;
  }

  public Integer getVersionPatchlevel() {
    return versionPatchlevel;
  }

  public void setVersionPatchlevel(Integer versionPatchlevel) {
    this.versionPatchlevel = versionPatchlevel;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  /**
   * Creates a SdkInfo object based on the given values
   *
   * @param sdkName the SdkName
   * @param sdkVersion the Version String (semver) eg 1.2.3
   * @return the parsed SdkInfo object
   */
  public static @NotNull SdkInfo createSdkInfo(
      final @NotNull String sdkName, final @NotNull String sdkVersion) {
    SdkInfo sdkInfo = new SdkInfo();
    sdkInfo.setSdkName(sdkName);

    String[] version = sdkVersion.split("\\.", -1);
    if (version.length >= 1) {
      sdkInfo.setVersionMajor(IntegerUtils.getNumber(version[0]));

      if (version.length >= 2) {
        sdkInfo.setVersionMinor(IntegerUtils.getNumber(version[1]));

        if (version.length >= 3) {
          String patch = version[2];
          // special casing for debug builds that appends a suffix eg: -SNAPSHOT
          String[] patchAndSnapshot = patch.split("-", -1);
          if (patchAndSnapshot.length >= 1) {
            sdkInfo.setVersionPatchlevel(IntegerUtils.getNumber(patchAndSnapshot[0]));
          }
        }
      }
    }

    return sdkInfo;
  }
}
