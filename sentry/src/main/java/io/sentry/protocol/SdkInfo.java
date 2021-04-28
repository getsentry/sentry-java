package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds information about the system SDK.
 *
 * <p>This is relevant for iOS and other platforms that have a system SDK. Not to be confused with
 * the client SDK.
 */
public final class SdkInfo implements IUnknownPropertiesConsumer {
  /** The internal name of the SDK. */
  private @Nullable String sdkName;
  /** The major version of the SDK as integer or 0. */
  private @Nullable Integer versionMajor;
  /** The minor version of the SDK as integer or 0. */
  private @Nullable Integer versionMinor;
  /** The patch version of the SDK as integer or 0. */
  private @Nullable Integer versionPatchlevel;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public @Nullable String getSdkName() {
    return sdkName;
  }

  public void setSdkName(final @Nullable String sdkName) {
    this.sdkName = sdkName;
  }

  public @Nullable Integer getVersionMajor() {
    return versionMajor;
  }

  public void setVersionMajor(final @Nullable Integer versionMajor) {
    this.versionMajor = versionMajor;
  }

  public @Nullable Integer getVersionMinor() {
    return versionMinor;
  }

  public void setVersionMinor(final @Nullable Integer versionMinor) {
    this.versionMinor = versionMinor;
  }

  public @Nullable Integer getVersionPatchlevel() {
    return versionPatchlevel;
  }

  public void setVersionPatchlevel(final @Nullable Integer versionPatchlevel) {
    this.versionPatchlevel = versionPatchlevel;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
