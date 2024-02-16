package io.sentry.metrics;

import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IMetricsHub {
  /** Captures one or more metrics to be sent to Sentry. */
  void captureMetrics(final @NotNull EncodedMetrics metrics);

  @NotNull
  Map<String, String> getDefaultTagsForMetric();

  /** Captures one or more <see cref="CodeLocations"/> to be sent to Sentry. */
  // void captureCodeLocations(final @NotNull CodeLocations codeLocations);

  /**
   * Starts a child span for the current transaction or, if there is no active transaction, starts a
   * new transaction.
   */
  // @NotNull ISpan startSpan(final @NotNull String operation, final @NotNull String description);

}
