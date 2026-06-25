package io.sentry.android.core;

import io.sentry.OptionsContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Direct OptionsContainer for SentryAndroidOptions that avoids reflective
 * getDeclaredConstructor().newInstance() on the Android startup path.
 */
final class SentryAndroidOptionsContainer extends OptionsContainer<SentryAndroidOptions> {

  @Override
  public @NotNull SentryAndroidOptions createInstance() {
    return new SentryAndroidOptions();
  }
}
