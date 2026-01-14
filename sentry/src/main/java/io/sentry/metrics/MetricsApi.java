package io.sentry.metrics;

import io.sentry.Scopes;
import org.jetbrains.annotations.NotNull;

public final class MetricsApi implements IMetricsApi {

  private final @NotNull Scopes scopes;

  public MetricsApi(final @NotNull Scopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void count(@NotNull String name) {
    scopes.getOptions();
  }
}
