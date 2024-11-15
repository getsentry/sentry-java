package io.sentry;

import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryTransaction;
import io.sentry.unity.SentryUnityExceptionFactory;
import io.sentry.unity.SentryUnityStackTraceFactory;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnityMainEventProcessor implements EventProcessor, Closeable {
  private static final String PLATFORM_NATIVE = "native";

  private final @NotNull SentryUnityExceptionFactory sentryUnityExceptionFactory;
  private final @NotNull SentryOptions options;

  public UnityMainEventProcessor(final @NotNull SentryOptions options) {
    this.options = options;
    final SentryUnityStackTraceFactory sentryStackTraceFactory =
      new SentryUnityStackTraceFactory(this.options);

    this.sentryUnityExceptionFactory = new SentryUnityExceptionFactory(sentryStackTraceFactory);
  }

  @Override public void close() throws IOException {

  }

  @SuppressWarnings("ThrowableNotThrown")
  @Override public @Nullable SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
    final @Nullable Throwable throwableMechanism = event.getThrowableMechanism();
    if (!(throwableMechanism instanceof ExceptionMechanismException)) {
      return event;
    }
    final @Nullable Throwable throwable = ((ExceptionMechanismException) throwableMechanism).getThrowable();
    final @Nullable String message = throwable.getMessage();
    if (message == null || !(message.trim().startsWith("***") || message.contains("Unity"))) {
      return event;
    }
    //final Mechanism mechanism = ((ExceptionMechanismException) throwableMechanism).getExceptionMechanism();
    //mechanism.setSynthetic(true);

    event.setExceptions(sentryUnityExceptionFactory.getSentryExceptions(throwableMechanism));

    event.setPlatform(PLATFORM_NATIVE);


    return event;
  }

  @Override public @Nullable SentryTransaction process(@NotNull SentryTransaction transaction,
    @NotNull Hint hint) {
    return EventProcessor.super.process(transaction, hint);
  }

  @Override
  public @Nullable SentryReplayEvent process(@NotNull SentryReplayEvent event, @NotNull Hint hint) {
    return EventProcessor.super.process(event, hint);
  }
}
