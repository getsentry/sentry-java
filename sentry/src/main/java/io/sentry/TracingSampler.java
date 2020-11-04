package io.sentry;

import io.sentry.util.Objects;
import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class TracingSampler {
  private final @NotNull SentryOptions options;
  private final @NotNull Random random;

  public TracingSampler(final @NotNull SentryOptions options) {
    this(Objects.requireNonNull(options, "options are required"), new Random());
  }

  @TestOnly
  TracingSampler(final @NotNull SentryOptions options, final @NotNull Random random) {
    this.options = options;
    this.random = random;
  }

  boolean sample(final @Nullable SamplingContext samplingContext) {
    double sampling;
    if (options.getTracesSampler() != null && samplingContext != null) {
      sampling = options.getTracesSampler().sample(samplingContext);
    } else if (options.getTracesSampleRate() != null) {
      sampling = options.getTracesSampleRate();
    } else {
      sampling = 0.0; // transaction is dropped
    }
    return !(sampling < random.nextDouble());
  }
}
