package io.sentry.opentelemetry.agent;

import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.opentelemetry.agentless.spring.BuildConfig;

public final class AgentlessSpringMarker {
  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage(
            "maven:io.sentry:sentry-opentelemetry-agentless-spring", BuildConfig.VERSION_NAME);
  }
}
