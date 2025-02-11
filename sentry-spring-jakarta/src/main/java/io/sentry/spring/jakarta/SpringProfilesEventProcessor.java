package io.sentry.spring.jakarta;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryReplayEvent;
import io.sentry.SpanContext;
import io.sentry.protocol.SentryTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;

/**
 * Attaches the list of active Spring profiles (an empty list if only the default profile is active)
 * to the {@link io.sentry.TraceContext} associated with the event.
 */
public final class SpringProfilesEventProcessor implements EventProcessor {
  private static final @NotNull String ACTIVE_PROFILES_TRACE_CONTEXT_KEY = "spring.active_profiles";

  private final @NotNull Environment environment;

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    processInternal(event);
    return event;
  }

  @Override
  public @NotNull SentryTransaction process(
      final @NotNull SentryTransaction transaction, final @NotNull Hint hint) {
    processInternal(transaction);
    return transaction;
  }

  @Override
  public @NotNull SentryReplayEvent process(
      final @NotNull SentryReplayEvent event, final @NotNull Hint hint) {
    processInternal(event);
    return event;
  }

  public void processInternal(final @NotNull SentryBaseEvent event) {
    @Nullable SpanContext trace = event.getContexts().getTrace();
    if (trace != null) {
      @Nullable String[] activeProfiles = environment.getActiveProfiles();
      if (activeProfiles == null) {
        activeProfiles = new String[0];
      }
      trace.setData(ACTIVE_PROFILES_TRACE_CONTEXT_KEY, activeProfiles);
    }
  }

  public SpringProfilesEventProcessor(final @NotNull Environment environment) {
    this.environment = environment;
  }
}
