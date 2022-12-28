package io.sentry.samples.android;

import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.android.core.SentryAndroidOptionsCustomizer;
import org.jetbrains.annotations.NotNull;

public class MyOptionsCustomizer implements SentryAndroidOptionsCustomizer {

  @Override
  public void configure(@NotNull SentryAndroidOptions options) {
    options.setEnvironment("hey there env android");
    options.setBeforeSend(
        ((event, hint) -> {
          event.addBreadcrumb("hello breadcrumb from beforesend");
          return event;
        }));
  }
}
