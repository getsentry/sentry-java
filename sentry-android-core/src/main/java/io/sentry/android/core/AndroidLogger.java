package io.sentry.android.core;

import android.util.Log;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.Nullable;

final class AndroidLogger implements ILogger {

  private static final String tag = "Sentry";

  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @Nullable SentryLevel level,
      final @Nullable String message,
      final @Nullable Object... args) {
    Log.println(toLogcatLevel(level), tag, String.format(message, args));
  }

  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(SentryLevel level, Throwable throwable, String message, Object... args) {
    log(level, String.format(message, args), throwable);
  }

  @Override
  public void log(SentryLevel level, String message, Throwable throwable) {

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

  private int toLogcatLevel(SentryLevel sentryLevel) {
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
