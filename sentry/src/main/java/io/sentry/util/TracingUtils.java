package io.sentry.util;

import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.FilterString;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.NoOpLogger;
import io.sentry.PropagationContext;
import io.sentry.SentryOptions;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanContext;
import io.sentry.TracesSamplingDecision;
import io.sentry.W3CTraceparentHeader;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TracingUtils {

  public static void startNewTrace(final @NotNull IScopes scopes) {
    scopes.configureScope(
        scope -> {
          scope.withPropagationContext(
              propagationContext -> {
                scope.setPropagationContext(new PropagationContext());
              });
        });
  }

  public static void setTrace(
      final @NotNull IScopes scopes, final @NotNull PropagationContext propagationContext) {
    scopes.configureScope(
        scope -> {
          scope.withPropagationContext(
              oldPropagationContext -> {
                scope.setPropagationContext(propagationContext);
              });
        });
  }

  public static @Nullable TracingHeaders traceIfAllowed(
      final @NotNull IScopes scopes,
      final @NotNull String requestUrl,
      @Nullable List<String> thirdPartyBaggageHeaders,
      final @Nullable ISpan span) {
    final @NotNull SentryOptions sentryOptions = scopes.getOptions();
    if (sentryOptions.isTraceSampling() && shouldAttachTracingHeaders(requestUrl, sentryOptions)) {
      return trace(scopes, thirdPartyBaggageHeaders, span);
    }

    return null;
  }

  public static @Nullable TracingHeaders trace(
      final @NotNull IScopes scopes,
      @Nullable List<String> thirdPartyBaggageHeaders,
      final @Nullable ISpan span) {
    final @NotNull SentryOptions sentryOptions = scopes.getOptions();

    if (span != null && !span.isNoOp()) {
      final @NotNull SentryTraceHeader sentryTraceHeader = span.toSentryTrace();
      final @Nullable BaggageHeader baggageHeader = span.toBaggageHeader(thirdPartyBaggageHeaders);
      @Nullable W3CTraceparentHeader w3cTraceparentHeader = null;

      if (sentryOptions.isPropagateTraceparent()) {
        final @NotNull SpanContext spanContext = span.getSpanContext();
        w3cTraceparentHeader =
            new W3CTraceparentHeader(
                spanContext.getTraceId(), spanContext.getSpanId(), sentryTraceHeader.isSampled());
      }

      return new TracingHeaders(sentryTraceHeader, baggageHeader, w3cTraceparentHeader);
    } else {
      final @NotNull PropagationContextHolder returnValue = new PropagationContextHolder();
      scopes.configureScope(
          (scope) -> {
            returnValue.propagationContext = maybeUpdateBaggage(scope, sentryOptions);
          });

      if (returnValue.propagationContext != null) {
        final @NotNull PropagationContext propagationContext = returnValue.propagationContext;
        final @NotNull Baggage baggage = propagationContext.getBaggage();
        final @NotNull BaggageHeader baggageHeader =
            BaggageHeader.fromBaggageAndOutgoingHeader(baggage, thirdPartyBaggageHeaders);

        final @NotNull SentryTraceHeader sentryTraceHeader =
            new SentryTraceHeader(
                propagationContext.getTraceId(),
                propagationContext.getSpanId(),
                propagationContext.isSampled());

        @Nullable W3CTraceparentHeader w3cTraceparentHeader = null;
        if (sentryOptions.isPropagateTraceparent()) {
          w3cTraceparentHeader =
              new W3CTraceparentHeader(
                  propagationContext.getTraceId(),
                  propagationContext.getSpanId(),
                  propagationContext.isSampled());
        }

        return new TracingHeaders(sentryTraceHeader, baggageHeader, w3cTraceparentHeader);
      }

      return null;
    }
  }

  public static @NotNull PropagationContext maybeUpdateBaggage(
      final @NotNull IScope scope, final @NotNull SentryOptions sentryOptions) {
    return scope.withPropagationContext(
        propagationContext -> {
          @NotNull Baggage baggage = propagationContext.getBaggage();
          if (baggage.isMutable()) {
            baggage.setValuesFromScope(scope, sentryOptions);
            baggage.freeze();
          }
        });
  }

  private static boolean shouldAttachTracingHeaders(
      final @NotNull String requestUrl, final @NotNull SentryOptions sentryOptions) {
    return PropagationTargetsUtils.contain(sentryOptions.getTracePropagationTargets(), requestUrl);
  }

  private static final class PropagationContextHolder {
    private @Nullable PropagationContext propagationContext = null;
  }

  public static final class TracingHeaders {
    private final @NotNull SentryTraceHeader sentryTraceHeader;
    private final @Nullable BaggageHeader baggageHeader;
    private final @Nullable W3CTraceparentHeader w3cTraceparentHeader;

    public TracingHeaders(
        final @NotNull SentryTraceHeader sentryTraceHeader,
        final @Nullable BaggageHeader baggageHeader) {
      this.sentryTraceHeader = sentryTraceHeader;
      this.baggageHeader = baggageHeader;
      this.w3cTraceparentHeader = null;
    }

    public TracingHeaders(
        final @NotNull SentryTraceHeader sentryTraceHeader,
        final @Nullable BaggageHeader baggageHeader,
        final @Nullable W3CTraceparentHeader w3cTraceparentHeader) {
      this.sentryTraceHeader = sentryTraceHeader;
      this.baggageHeader = baggageHeader;
      this.w3cTraceparentHeader = w3cTraceparentHeader;
    }

    public @NotNull SentryTraceHeader getSentryTraceHeader() {
      return sentryTraceHeader;
    }

    public @Nullable BaggageHeader getBaggageHeader() {
      return baggageHeader;
    }

    public @Nullable W3CTraceparentHeader getW3cTraceparentHeader() {
      return w3cTraceparentHeader;
    }
  }

  /** Checks if a transaction is to be ignored. */
  @ApiStatus.Internal
  public static boolean isIgnored(
      final @Nullable List<FilterString> ignoredTransactions,
      final @Nullable String transactionName) {
    if (transactionName == null) {
      return false;
    }
    if (ignoredTransactions == null || ignoredTransactions.isEmpty()) {
      return false;
    }

    for (final FilterString ignoredTransaction : ignoredTransactions) {
      if (ignoredTransaction.getFilterString().equalsIgnoreCase(transactionName)) {
        return true;
      }
    }

    for (final FilterString ignoredTransaction : ignoredTransactions) {

      try {
        if (ignoredTransaction.matches(transactionName)) {
          return true;
        }
      } catch (Throwable t) {
        // ignore invalid regex
      }
    }

    return false;
  }

  /**
   * Ensures a non null baggage instance is present by creating a new Baggage instance if null is
   * passed in.
   *
   * <p>Also ensures there is a sampleRand value present on the baggage if it is still mutable. If
   * the baggage should be frozen, it also takes care of freezing it.
   *
   * @param incomingBaggage a nullable baggage instance, if null a new one will be created
   * @param decision a TracesSamplingDecision for potentially backfilling sampleRand to match that
   *     decision
   * @return previous baggage instance or a new one
   */
  @ApiStatus.Internal
  public static @NotNull Baggage ensureBaggage(
      final @Nullable Baggage incomingBaggage, final @Nullable TracesSamplingDecision decision) {
    final @Nullable Boolean decisionSampled = decision == null ? null : decision.getSampled();
    final @Nullable Double decisionSampleRate = decision == null ? null : decision.getSampleRate();
    final @Nullable Double decisionSampleRand = decision == null ? null : decision.getSampleRand();

    return ensureBaggage(incomingBaggage, decisionSampled, decisionSampleRate, decisionSampleRand);
  }

  /**
   * Ensures a non null baggage instance is present by creating a new Baggage instance if null is
   * passed in.
   *
   * <p>Also ensures there is a sampleRand value present on the baggage if it is still mutable. If
   * the baggage should be frozen, it also takes care of freezing it.
   *
   * @param incomingBaggage a nullable baggage instance, if null a new one will be created
   * @param decisionSampled sampled decision for potential backfilling
   * @param decisionSampleRate sampleRate for potential backfilling
   * @param decisionSampleRand sampleRand to be used if none in baggage
   * @return previous baggage instance or a new one
   */
  @ApiStatus.Internal
  public static @NotNull Baggage ensureBaggage(
      final @Nullable Baggage incomingBaggage,
      final @Nullable Boolean decisionSampled,
      final @Nullable Double decisionSampleRate,
      final @Nullable Double decisionSampleRand) {
    final @NotNull Baggage baggage =
        incomingBaggage == null ? new Baggage(NoOpLogger.getInstance()) : incomingBaggage;

    if (baggage.getSampleRand() == null) {
      final @Nullable Double baggageSampleRate = baggage.getSampleRate();
      final @Nullable Double sampleRateMaybe =
          baggageSampleRate == null ? decisionSampleRate : baggageSampleRate;
      final @NotNull Double sampleRand =
          SampleRateUtils.backfilledSampleRand(
              decisionSampleRand, sampleRateMaybe, decisionSampled);
      baggage.setSampleRand(sampleRand);
    }
    if (baggage.isMutable()) {
      if (baggage.isShouldFreeze()) {
        baggage.freeze();
      }
    }

    return baggage;
  }
}
