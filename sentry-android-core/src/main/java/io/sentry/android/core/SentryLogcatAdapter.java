package io.sentry.android.core;

import android.util.Log;
import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class replaces {@link android.util.Log} with its own implementations which creates a {@link
 * io.sentry.Breadcrumb} for each log. It only replaces log functions that meet a minimum level set
 * by the user on the Sentry Android Gradle Plugin.
 */
@ApiStatus.Internal
public final class SentryLogcatAdapter {

  private static void addAsBreadcrumb(
      @Nullable String tag, @NotNull SentryLevel level, @Nullable String msg) {
    addAsBreadcrumb(tag, level, msg, null);
  }

  private static void addAsBreadcrumb(
      @Nullable String tag, @NotNull SentryLevel level, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, level, null, tr);
  }

  private static void addAsBreadcrumb(
      @Nullable final String tag,
      @NotNull final SentryLevel level,
      @Nullable final String msg,
      @Nullable final Throwable tr) {
    Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setCategory("Logcat");
    breadcrumb.setMessage(msg);
    breadcrumb.setLevel(level);
    if (tag != null) {
      breadcrumb.setData("tag", tag);
    }
    if (tr != null && tr.getMessage() != null) {
      breadcrumb.setData("throwable", tr.getMessage());
    }
    Sentry.addBreadcrumb(breadcrumb);
  }

  public static int v(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg);
    return Log.v(tag, msg);
  }

  public static int v(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg, tr);
    return Log.v(tag, msg, tr);
  }

  public static int d(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg);
    return Log.d(tag, msg);
  }

  public static int d(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg, tr);
    return Log.d(tag, msg, tr);
  }

  public static int i(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, SentryLevel.INFO, msg);
    return Log.i(tag, msg);
  }

  public static int i(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.INFO, msg, tr);
    return Log.i(tag, msg, tr);
  }

  public static int w(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, SentryLevel.WARNING, msg);
    return Log.w(tag, msg);
  }

  public static int w(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.WARNING, msg, tr);
    return Log.w(tag, msg, tr);
  }

  public static int w(@Nullable String tag, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.WARNING, tr);
    return Log.w(tag, tr);
  }

  public static int e(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg);
    return Log.e(tag, msg);
  }

  public static int e(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg, tr);
    return Log.e(tag, msg, tr);
  }

  public static int wtf(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg);
    return Log.wtf(tag, msg);
  }

  public static int wtf(@Nullable String tag, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, tr);
    return Log.wtf(tag, tr);
  }

  public static int wtf(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg, tr);
    return Log.wtf(tag, msg, tr);
  }
}
