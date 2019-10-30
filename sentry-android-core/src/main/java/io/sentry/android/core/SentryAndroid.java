package io.sentry.android.core;

import android.content.Context;
import io.sentry.core.Sentry;
import io.sentry.core.util.NonNull;

/** Sentry initialization class */
public final class SentryAndroid {

  private SentryAndroid() {}

  /**
   * Sentry initialization method if auto-init is disabled
   *
   * @param context Application. context
   */
  public static void init(@NonNull final Context context) {
    Sentry.init(options -> AndroidOptionsInitializer.init(options, context));
  }
}
