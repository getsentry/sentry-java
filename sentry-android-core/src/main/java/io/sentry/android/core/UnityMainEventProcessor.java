package io.sentry.android.core;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.SentryReplayEvent;
import io.sentry.android.core.unity.SentryUnityExceptionFactory;
import io.sentry.android.core.unity.SentryUnityStackTraceFactory;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryTransaction;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnityMainEventProcessor implements EventProcessor, Closeable {
  private static final String PLATFORM_NATIVE = "native";

  private final @NotNull SentryUnityExceptionFactory sentryUnityExceptionFactory;
  private final @NotNull SentryOptions options;

  public UnityMainEventProcessor(final @NotNull SentryAndroidOptions options) {
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
    List<DebugImage> debugImages = ((SentryAndroidOptions) options).getDebugImagesLoader().loadDebugImages();
    if (debugImages != null && !debugImages.isEmpty()) {
      DebugMeta debugMeta = event.getDebugMeta();

      if (debugMeta == null) {
        debugMeta = new DebugMeta();
      }
      if (debugMeta.getImages() == null) {
        debugMeta.setImages(debugImages);
      } else {
        debugMeta.getImages().addAll(debugImages);
      }

      //event.setDebugMeta(debugMeta);
    }

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
