package io.sentry;

public interface IntegrationName {
  default String getIntegrationName() {
    return this.getClass()
        .getSimpleName()
        .replace("Sentry", "")
        .replace("Integration", "")
        .replace("Interceptor", "");
  }

  default void addIntegrationToSdkVersion() {
    SentryIntegrationPackageStorage.getInstance().addIntegration(getIntegrationName());
  }
}
