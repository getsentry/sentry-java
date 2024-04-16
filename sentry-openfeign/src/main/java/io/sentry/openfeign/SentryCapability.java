package io.sentry.openfeign;

import feign.Capability;
import feign.Client;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adds Sentry tracing capability to Feign clients. */
public final class SentryCapability implements Capability {

  private final @NotNull IScopes scopes;
  private final @Nullable SentryFeignClient.BeforeSpanCallback beforeSpan;

  public SentryCapability(
      final @NotNull IScopes scopes,
      final @Nullable SentryFeignClient.BeforeSpanCallback beforeSpan) {
    this.scopes = scopes;
    this.beforeSpan = beforeSpan;
  }

  public SentryCapability(final @Nullable SentryFeignClient.BeforeSpanCallback beforeSpan) {
    this(ScopesAdapter.getInstance(), beforeSpan);
  }

  public SentryCapability() {
    this(ScopesAdapter.getInstance(), null);
  }

  @Override
  public @NotNull Client enrich(final @NotNull Client client) {
    return new SentryFeignClient(client, scopes, beforeSpan);
  }
}
