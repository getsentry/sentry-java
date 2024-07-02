package io.sentry.openfeign;

import feign.Capability;
import feign.Client;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adds Sentry tracing capability to Feign clients. */
public final class SentryCapability implements Capability {

  private final @NotNull IHub hub;
  private final @Nullable SentryFeignClient.BeforeSpanCallback beforeSpan;

  public SentryCapability(
      final @NotNull IHub hub, final @Nullable SentryFeignClient.BeforeSpanCallback beforeSpan) {
    this.hub = hub;
    this.beforeSpan = beforeSpan;
  }

  public SentryCapability(final @Nullable SentryFeignClient.BeforeSpanCallback beforeSpan) {
    this(HubAdapter.getInstance(), beforeSpan);
  }

  public SentryCapability() {
    this(HubAdapter.getInstance(), null);
  }

  @Override
  public @NotNull Client enrich(final @NotNull Client client) {
    return new SentryFeignClient(client, hub, beforeSpan);
  }
}
