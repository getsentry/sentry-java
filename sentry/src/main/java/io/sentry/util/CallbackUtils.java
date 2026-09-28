package io.sentry.util;

import io.sentry.SentryOptions;
import io.sentry.exception.SentryCallbackError;
import io.sentry.exception.SentryCallbackException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CallbackUtils {
  private CallbackUtils() {}

  /** Marks failures from callbacks that already propagate their exceptions by default. */
  public static void run(final @NotNull SentryOptions options, final @NotNull Runnable callback) {
    try {
      callback.run();
    } catch (Exception | Error e) {
      rethrowIfStrictCallbackMode(options, e);
      throw e;
    }
  }

  /** Call before logging or loss accounting at a user callback boundary. */
  public static void rethrowIfStrictCallbackMode(
      final @NotNull SentryOptions options, final @NotNull Throwable throwable) {
    ExceptionUtils.maybeRethrow(throwable);
    if (options.isStrictCallbackMode()) {
      if (throwable instanceof Error) {
        throw new SentryCallbackError((Error) throwable);
      }
      throw new SentryCallbackException(throwable);
    }
  }

  /** Callback failures must be excluded before invoking any processors or other callbacks. */
  public static boolean isCallbackException(final @Nullable Throwable throwable) {
    return ExceptionUtils.findCallbackException(throwable) != null;
  }
}
