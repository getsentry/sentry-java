package io.sentry;

import io.sentry.util.Platform;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryAutoDateProvider implements SentryDateProvider {

  private final @NotNull SentryDateProvider dateProvider;

  public SentryAutoDateProvider() {
    // Instant is sub-millisecond on every supported JVM, but only millisecond-granular on Android.
    if (Platform.isJvm()) {
      dateProvider = new SentryInstantDateProvider();
    } else {
      dateProvider = new SentryNanotimeDateProvider();
    }
  }

  @Override
  @NotNull
  public SentryDate now() {
    return dateProvider.now();
  }
}
