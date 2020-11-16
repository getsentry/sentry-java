package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holds information about the system SDK.
 *
 * <p>This is relevant for iOS and other platforms that have a system SDK. Not to be confused with
 * the client SDK.
 */
public final class SdkInfo implements IUnknownPropertiesConsumer {
  /** The internal name of the SDK. */
  private String sdkName;
  /** The major version of the SDK as integer or 0. */
  private Integer versionMajor;
  /** The minor version of the SDK as integer or 0. */
  private Integer versionMinor;
  /** The patch version of the SDK as integer or 0. */
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
