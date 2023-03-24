package io.sentry.android.core;

import static io.sentry.android.core.ActivityLifecycleIntegration.APP_START_COLD;
import static io.sentry.android.core.ActivityLifecycleIntegration.APP_START_WARM;
import static io.sentry.android.core.ActivityLifecycleIntegration.UI_LOAD_OP;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.MeasurementUnit;
import io.sentry.SentryEvent;
import io.sentry.SpanContext;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import io.sentry.util.Objects;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Event Processor responsible for adding Android metrics to transactions */
final class PerformanceAndroidEventProcessor implements EventProcessor {

  private boolean sentStartMeasurement = false;

  private final @NotNull ActivityFramesTracker activityFramesTracker;
  private final @NotNull SentryAndroidOptions options;

  PerformanceAndroidEventProcessor(
      final @NotNull SentryAndroidOptions options,
      final @NotNull ActivityFramesTracker activityFramesTracker) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
    this.activityFramesTracker =
        Objects.requireNonNull(activityFramesTracker, "ActivityFramesTracker is required");
  }

  /**
   * Returns the event itself
   *
   * @param event the SentryEvent the SentryEvent
   * @param hint the Hint the Hint
   * @return returns the event itself
   */
  @Override
  @Nullable
  public SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
    // that's only necessary because on newer versions of Unity, if not overriding this method, it's
    // throwing 'java.lang.AbstractMethodError: abstract method' and the reason is probably
    // compilation mismatch.
    return event;
  }

  @SuppressWarnings("NullAway")
  @Override
  public synchronized @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @NotNull Hint hint) {

    if (!options.isTracingEnabled()) {
      return transaction;
    }

    // the app start measurement is only sent once and only if the transaction has
    // the app.start span, which is automatically created by the SDK.
    if (!sentStartMeasurement && hasAppStartSpan(transaction.getSpans())) {
      final Long appStartUpInterval = AppStartState.getInstance().getAppStartInterval();
      // if appStartUpInterval is null, metrics are not ready to be sent
      if (appStartUpInterval != null) {
        final MeasurementValue value =
            new MeasurementValue(
                (float) appStartUpInterval, MeasurementUnit.Duration.MILLISECOND.apiName());

        final String appStartKey =
            AppStartState.getInstance().isColdStart()
                ? MeasurementValue.KEY_APP_START_COLD
                : MeasurementValue.KEY_APP_START_WARM;

        transaction.getMeasurements().put(appStartKey, value);
        sentStartMeasurement = true;
      }
    }

    final SentryId eventId = transaction.getEventId();
    final SpanContext spanContext = transaction.getContexts().getTrace();

    // only add slow/frozen frames to transactions created by ActivityLifecycleIntegration
    // which have the operation UI_LOAD_OP. If a user-defined (or hybrid SDK) transaction
    // users it, we'll also add the metrics if available
    if (eventId != null
        && spanContext != null
        && spanContext.getOperation().contentEquals(UI_LOAD_OP)) {
      final Map<String, @NotNull MeasurementValue> framesMetrics =
          activityFramesTracker.takeMetrics(eventId);
      if (framesMetrics != null) {
        transaction.getMeasurements().putAll(framesMetrics);
      }
    }

    return transaction;
  }

  private boolean hasAppStartSpan(final @NotNull List<SentrySpan> spans) {
    for (final SentrySpan span : spans) {
      if (span.getOp().contentEquals(APP_START_COLD)
          || span.getOp().contentEquals(APP_START_WARM)) {
        return true;
      }
    }
    return false;
  }
}
