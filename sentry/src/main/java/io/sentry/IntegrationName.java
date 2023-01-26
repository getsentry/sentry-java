package io.sentry;

import io.sentry.protocol.SdkVersion;
import org.jetbrains.annotations.Nullable;

public interface IntegrationName {
  default String getIntegrationName() {
    return this.getClass()
        .getSimpleName()
        .replace("Sentry", "")
        .replace("Integration", "")
        .replace("Interceptor", "");
  }

  default void addIntegrationToSdkVersion(@Nullable SdkVersion version) {
    if (version != null) {
      version.addIntegration(getIntegrationName());
    }
  }
}
