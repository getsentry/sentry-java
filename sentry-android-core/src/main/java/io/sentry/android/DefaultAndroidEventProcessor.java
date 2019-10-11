package io.sentry.android;

import android.content.Context;
import io.sentry.EventProcessor;
import io.sentry.SentryEvent;

public class DefaultAndroidEventProcessor implements EventProcessor {
  Context context;

  public DefaultAndroidEventProcessor(Context context) {
    if (context == null) throw new IllegalArgumentException("The application context is required.");
    this.context = context.getApplicationContext();
  }

  @Override
  public SentryEvent process(SentryEvent event) {
    // TODO: Do cool android things.
    return event;
  }
}
