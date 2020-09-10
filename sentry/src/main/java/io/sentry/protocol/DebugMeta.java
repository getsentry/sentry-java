package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

public final class DebugMeta implements IUnknownPropertiesConsumer {
  private SdkInfo sdkInfo;
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
