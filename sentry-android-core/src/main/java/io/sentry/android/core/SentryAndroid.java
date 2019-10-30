package io.sentry.android.core;

import android.content.Context;
import io.sentry.core.Sentry;
import org.jetbrains.annotations.NotNull;

/** Sentry initialization class */
public final class SentryAndroid {

  private SentryAndroid() {}

  /**
   * Sentry initialization method if auto-init is disabled
   *
   * @param context Application. context
   */
  public static void init(@NotNull final Context context) {
    Sentry.init(options -> AndroidOptionsInitializer.init(options, context));
  }
}
