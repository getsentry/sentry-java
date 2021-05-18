package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debugging and processing meta information.
 *
 * <p>The debug meta interface carries debug information for processing errors and crash reports.
 * Sentry amends the information in this interface.
 *
 * <p>Example (look at field types to see more detail):
 *
 * <p>```json { "debug_meta": { "images": [], "sdk_info": { "sdk_name": "iOS", "version_major": 10,
 * "version_minor": 3, "version_patchlevel": 0 } } } ```
 */
public final class DebugMeta implements IUnknownPropertiesConsumer {
  /** Information about the system SDK (e.g. iOS SDK). */
  private @Nullable SdkInfo sdkInfo;
  /** List of debug information files (debug images). */
  private @Nullable List<DebugImage> images;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public @Nullable List<DebugImage> getImages() {
    return images;
  }

  public void setImages(final @Nullable List<DebugImage> images) {
    this.images = images != null ? new ArrayList<>(images) : null;
  }

  public @Nullable SdkInfo getSdkInfo() {
    return sdkInfo;
  }

  public void setSdkInfo(final @Nullable SdkInfo sdkInfo) {
    this.sdkInfo = sdkInfo;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
