package io.sentry.android.core;

import android.util.Log;
import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This class replaces {@link android.util.Log} with its own implementations which creates a {@link
 * io.sentry.Breadcrumb} for each log. It only replaces log functions that meet a minimum level set
 * by the user on the Sentry Android Gradle Plugin.
 */
@ApiStatus.Internal
public final class SentryLogcatAdapter {

  private static void addAsBreadcrumb(@NotNull String tag, @NotNull SentryLevel level, String msg) {
    addAsBreadcrumb(tag, level, msg, null);
  }

  private static void addAsBreadcrumb(
      @NotNull String tag, @NotNull SentryLevel level, Throwable tr) {
    addAsBreadcrumb(tag, level, null, tr);
  }

  private static void addAsBreadcrumb(
      @NotNull String tag, @NotNull SentryLevel level, String msg, Throwable tr) {
    Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setCategory("log");
    breadcrumb.setMessage(msg);
    breadcrumb.setLevel(level);
    breadcrumb.setData("tag", tag);
    if (tr != null) {
      breadcrumb.setData("throwable", tr.getMessage());
    }
    Sentry.addBreadcrumb(breadcrumb);
  }

  public static int v(String tag, @NotNull String msg) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg);
    return Log.v(tag, msg);
  }

  public static int v(String tag, String msg, Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg, tr);
    return Log.v(tag, msg, tr);
  }

  public static int d(String tag, @NotNull String msg) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg);
    return Log.d(tag, msg);
  }

  public static int d(String tag, String msg, Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.DEBUG, msg, tr);
    return Log.d(tag, msg, tr);
  }

  public static int i(String tag, @NotNull String msg) {
    addAsBreadcrumb(tag, SentryLevel.INFO, msg);
    return Log.i(tag, msg);
  }

  public static int i(String tag, String msg, Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.INFO, msg, tr);
    return Log.i(tag, msg, tr);
  }

  public static int w(String tag, @NotNull String msg) {
    addAsBreadcrumb(tag, SentryLevel.WARNING, msg);
    return Log.w(tag, msg);
  }

  public static int w(String tag, String msg, Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.WARNING, msg, tr);
    return Log.w(tag, msg, tr);
  }

  public static int w(String tag, Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.WARNING, tr);
    return Log.w(tag, tr);
  }

  public static int e(String tag, @NotNull String msg) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg);
    return Log.e(tag, msg);
  }

  public static int e(String tag, String msg, Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg, tr);
    return Log.e(tag, msg, tr);
  }

  public static int wtf(String tag, String msg) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg);
    return Log.wtf(tag, msg);
  }

  public static int wtf(String tag, @NotNull Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, tr);
    return Log.wtf(tag, tr);
  }

  public static int wtf(String tag, String msg, Throwable tr) {
    addAsBreadcrumb(tag, SentryLevel.ERROR, msg, tr);
    return Log.wtf(tag, msg, tr);
  }
}
