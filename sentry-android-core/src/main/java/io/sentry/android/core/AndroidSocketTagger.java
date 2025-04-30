package io.sentry.android.core;

import android.net.TrafficStats;
import io.sentry.ISocketTagger;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AndroidSocketTagger implements ISocketTagger {

  // just a random number to tag outgoing traffic from the Sentry SDK
  private static final int SENTRY_TAG = 0xF001;

  private static final AndroidSocketTagger instance = new AndroidSocketTagger();

  private AndroidSocketTagger() {}

  public static AndroidSocketTagger getInstance() {
    return instance;
  }

  @Override
  public void tagSockets() {
    TrafficStats.setThreadStatsTag(SENTRY_TAG);
  }

  @Override
  public void untagSockets() {
    TrafficStats.clearThreadStatsTag();
  }
}
