package io.sentry.android.core;

import android.net.TrafficStats;
import io.sentry.ISocketTagger;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AndroidSocketTagger implements ISocketTagger {

  private static final AndroidSocketTagger instance = new AndroidSocketTagger();

  private AndroidSocketTagger() {}

  public static AndroidSocketTagger getInstance() {
    return instance;
  }

  @Override
  public void tagSockets() {
    TrafficStats.setThreadStatsTag(1);
  }

  @Override
  public void untagSockets() {
    TrafficStats.clearThreadStatsTag();
  }
}
