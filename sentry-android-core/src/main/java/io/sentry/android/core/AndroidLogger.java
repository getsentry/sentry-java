package io.sentry.android.core;

import android.util.Log;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AndroidLogger implements ILogger {

  private final @NotNull String tag;

  public AndroidLogger() {
    this("Sentry");
  }

  public AndroidLogger(final @NotNull String tag) {
    this.tag = tag;
  }

  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @NotNull SentryLevel level,
      final @NotNull String message,
      final @Nullable Object... args) {
    if (args == null || args.length == 0) {
      Log.println(toLogcatLevel(level), tag, message);
    } else {
      Log.println(toLogcatLevel(level), tag, String.format(message, args));
    }
  }

  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @NotNull SentryLevel level,
      final @Nullable Throwable throwable,
      final @NotNull String message,
      final @Nullable Object... args) {
    if (args == null || args.length == 0) {
      log(level, message, throwable);
    } else {
      log(level, String.format(message, args), throwable);
    }
  }

  @Override
  public void log(
      final @NotNull SentryLevel level,
      final @NotNull String message,
      final @Nullable Throwable throwable) {

    switch (level) {
      case INFO:
        Log.i(tag, message, throwable);
        break;
      case WARNING:
        Log.w(tag, message, throwable);
        break;
      case ERROR:
        Log.e(tag, message, throwable);
        break;
      case FATAL:
        Log.wtf(tag, message, throwable);
        break;
      case DEBUG:
      default:
        Log.d(tag, message, throwable);
        break;
    }
  }

  @Override
  public boolean isEnabled(@Nullable SentryLevel level) {
    return true;
  }

  private int toLogcatLevel(final @NotNull SentryLevel sentryLevel) {
    switch (sentryLevel) {
      case INFO:
        return Log.INFO;
      case WARNING:
        return Log.WARN;
      case FATAL:
        return Log.ASSERT;
      case DEBUG:
      default:
        return Log.DEBUG;
    }
  }
}
