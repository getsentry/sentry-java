package io.sentry.opentelemetry;

import io.opentelemetry.context.ContextKey;
import io.sentry.Baggage;
import io.sentry.IScopes;
import io.sentry.SentryTraceHeader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryOtelKeys {

  public static final @NotNull ContextKey<SentryTraceHeader> SENTRY_TRACE_KEY =
      ContextKey.named("sentry.trace");
  public static final @NotNull ContextKey<Baggage> SENTRY_BAGGAGE_KEY =
      ContextKey.named("sentry.baggage");
  public static final @NotNull ContextKey<IScopes> SENTRY_SCOPES_KEY =
      ContextKey.named("sentry.scopes");
}
