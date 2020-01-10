package io.sentry.android.core;

import android.util.Log;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;

final class AndroidLogger implements ILogger {

  private static final String tag = "Sentry";

  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(SentryLevel level, String message, Object... args) {
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
      case DEBUG:
        Log.d(tag, message, throwable);
        break;
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
    }
  }

  SentryLevel toSentryLevel(int logcatLevel) {
    switch (logcatLevel) {
      case Log.VERBOSE:
      case Log.DEBUG:
        return SentryLevel.DEBUG;
      case Log.INFO:
        return SentryLevel.INFO;
      case Log.WARN:
        return SentryLevel.WARNING;
      case Log.ERROR:
        return SentryLevel.ERROR;
      case Log.ASSERT:
      default:
        return SentryLevel.FATAL;
    }
  }

  private int toLogcatLevel(SentryLevel sentryLevel) {
    switch (sentryLevel) {
      case DEBUG:
        return Log.DEBUG;
      case INFO:
        return Log.INFO;
      case WARNING:
        return Log.WARN;
      case FATAL:
        return Log.ASSERT;
      case ERROR:
      default:
        return Log.ERROR;
    }
  }
}
