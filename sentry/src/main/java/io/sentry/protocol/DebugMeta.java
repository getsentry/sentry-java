package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

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
  private SdkInfo sdkInfo;
  /** List of debug information files (debug images). */
  private List<DebugImage> images;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public List<DebugImage> getImages() {
    return images;
  }

  public void setImages(List<DebugImage> images) {
    this.images = images;
  }

  public SdkInfo getSdkInfo() {
    return sdkInfo;
  }

  public void setSdkInfo(SdkInfo sdkInfo) {
    this.sdkInfo = sdkInfo;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
