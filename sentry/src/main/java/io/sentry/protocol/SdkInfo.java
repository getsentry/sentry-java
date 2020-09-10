package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

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
}
