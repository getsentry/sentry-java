package io.sentry.android;

import io.sentry.EventProcessor;
import io.sentry.SentryEvent;

public class DefaultAndroidEventProcessor implements EventProcessor {
  @Override
  public SentryEvent Process(SentryEvent event) {
    // TODO: Do cool android things.
    return event;
  }
}
