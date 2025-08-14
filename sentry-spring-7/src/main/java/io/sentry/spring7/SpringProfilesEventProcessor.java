package io.sentry.spring7;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryReplayEvent;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.Spring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;

/**
 * Attaches the list of active Spring profiles (an empty list if only the default profile is active)
 * to the {@link io.sentry.TraceContext} associated with the event.
 */
public final class SpringProfilesEventProcessor implements EventProcessor {
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

  private void processInternal(final @NotNull SentryBaseEvent event) {
    @Nullable String[] activeProfiles = environment.getActiveProfiles();
    @NotNull Spring springContext = new Spring();
    springContext.setActiveProfiles(activeProfiles);
    event.getContexts().setSpring(springContext);
  }

  public SpringProfilesEventProcessor(final @NotNull Environment environment) {
    this.environment = environment;
  }
}
