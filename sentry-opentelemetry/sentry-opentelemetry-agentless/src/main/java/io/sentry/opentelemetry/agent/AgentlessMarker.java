package io.sentry.opentelemetry.agent;

import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.opentelemetry.agentless.BuildConfig;

public final class AgentlessMarker {
  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-opentelemetry-agentless", BuildConfig.VERSION_NAME);
  }
}
