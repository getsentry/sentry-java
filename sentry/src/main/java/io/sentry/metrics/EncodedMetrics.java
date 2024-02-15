package io.sentry.metrics;

import java.nio.charset.Charset;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EncodedMetrics {
  @SuppressWarnings({"CharsetObjectCanBeUsed"})
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private final @NotNull String statsd;

  public EncodedMetrics(@NotNull String statsd) {
    this.statsd = statsd;
  }

  public byte[] getStatsd() {
    return statsd.getBytes(UTF8);
  }
}
