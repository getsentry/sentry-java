package io.sentry;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.INFO;

import io.sentry.cache.EnvelopeCache;
import io.sentry.cache.IEnvelopeCache;
import java.io.File;
import org.jetbrains.annotations.NotNull;

final class MovePreviousSession implements Runnable {

  private final @NotNull SentryOptions options;

  MovePreviousSession(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public void run() {
    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      options.getLogger().log(INFO, "Cache dir is not set, not moving the previous session.");
      return;
    }

    if (!options.isEnableAutoSessionTracking()) {
      options
          .getLogger()
          .log(DEBUG, "Session tracking is disabled, bailing from previous session mover.");
      return;
    }

    final IEnvelopeCache cache = options.getEnvelopeDiskCache();
    if (cache instanceof EnvelopeCache) {
      final File currentSessionFile = EnvelopeCache.getCurrentSessionFile(cacheDirPath);
      final File previousSessionFile = EnvelopeCache.getPreviousSessionFile(cacheDirPath);

      ((EnvelopeCache) cache).movePreviousSession(currentSessionFile, previousSessionFile);

      ((EnvelopeCache) cache).flushPreviousSession();
    }
  }
}
