package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/** An installed and loaded package as part of the Sentry SDK. */
public final class SentryPackage implements IUnknownPropertiesConsumer {
  /** Name of the package. */
  private String name;
  /** Version of the package. */
  private String version;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
