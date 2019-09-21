package io.sentry.android;

import android.util.Log;
import io.sentry.ILogger;
import io.sentry.SentryLevel;

class AndroidLogger implements ILogger {

  private static final String tag = "Sentry";

  @Override
  public void log(SentryLevel level, String message, Object... args) {
    Log.println(toLogcatLevel(level), tag, String.format(message, args));
  }

  @Override
  public void log(SentryLevel level, String message, Throwable throwable) {

    switch (level) {
      case Debug:
        Log.d(tag, message, throwable);
        break;
      case Info:
        Log.i(tag, message, throwable);
        break;
      case Warning:
        Log.w(tag, message, throwable);
        break;
      case Error:
        Log.e(tag, message, throwable);
        break;
      case Fatal:
        Log.wtf(tag, message, throwable);
        break;
    }
  }

  SentryLevel toSentryLevel(int logcatLevel) {
    switch (logcatLevel) {
      case Log.VERBOSE:
      case Log.DEBUG:
        return SentryLevel.Debug;
      case Log.INFO:
        return SentryLevel.Info;
      case Log.WARN:
        return SentryLevel.Warning;
      case Log.ERROR:
        return SentryLevel.Error;
      case Log.ASSERT:
      default:
        return SentryLevel.Fatal;
    }
  }

  int toLogcatLevel(SentryLevel sentryLevel) {
    switch (sentryLevel) {
      case Debug:
        return Log.DEBUG;
      case Info:
        return Log.INFO;
      case Warning:
        return Log.WARN;
      case Fatal:
        return Log.ASSERT;
      case Error:
      default:
        return Log.ERROR;
    }
  }
}
