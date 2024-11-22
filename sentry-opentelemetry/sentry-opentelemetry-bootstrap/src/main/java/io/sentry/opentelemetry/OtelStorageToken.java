package io.sentry.opentelemetry;

import io.opentelemetry.context.Scope;
import io.sentry.ISentryLifecycleToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class OtelStorageToken implements ISentryLifecycleToken {

  private final @NotNull Scope otelScope;

  OtelStorageToken(final @NotNull Scope otelScope) {
    this.otelScope = otelScope;
  }

  @Override
  public void close() {
    otelScope.close();
  }
}
