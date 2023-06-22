package io.sentry.util;

import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.PropagationContext;
import io.sentry.Scope;
import io.sentry.SentryOptions;
import io.sentry.SentryTraceHeader;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TracingUtils {

  public static void startNewTrace(final @NotNull IHub hub) {
    hub.configureScope(
        scope -> {
          scope.setPropagationContext(new PropagationContext());
        });
  }

  public static @Nullable TracingHeaders traceIfAllowed(
      final @NotNull IHub hub,
      final @NotNull String requestUrl,
      @Nullable List<String> thirdPartyBaggageHeaders,
      final @Nullable ISpan span) {
    final @NotNull SentryOptions sentryOptions = hub.getOptions();
    if (sentryOptions.isTraceSampling() && isAllowedToSendTo(requestUrl, sentryOptions)) {
      return trace(hub, thirdPartyBaggageHeaders, span);
    }

    return null;
  }

  public static @Nullable TracingHeaders trace(
      final @NotNull IHub hub,
      @Nullable List<String> thirdPartyBaggageHeaders,
      final @Nullable ISpan span) {
    final @NotNull SentryOptions sentryOptions = hub.getOptions();

    if (span != null && !span.isNoOp()) {
      return new TracingHeaders(
          span.toSentryTrace(), span.toBaggageHeader(thirdPartyBaggageHeaders));
    } else {
      final @NotNull TracingHeadersHolder returnValue = new TracingHeadersHolder();
      hub.configureScope(
          (scope) -> {
            maybeUpdateBaggage(scope, sentryOptions);
            final @NotNull PropagationContext propagationContext = scope.getPropagationContext();

            final @Nullable Baggage baggage = propagationContext.getBaggage();
            @Nullable BaggageHeader baggageHeader = null;
            if (baggage != null) {
              baggageHeader =
                  BaggageHeader.fromBaggageAndOutgoingHeader(baggage, thirdPartyBaggageHeaders);
            }

            returnValue.headers =
                new TracingHeaders(
                    new SentryTraceHeader(
                        propagationContext.getTraceId(), propagationContext.getSpanId(), null),
                    baggageHeader);
          });
      return returnValue.headers;
    }
  }

  public static void maybeUpdateBaggage(
      final @NotNull Scope scope, final @NotNull SentryOptions sentryOptions) {
    final @NotNull PropagationContext propagationContext = scope.getPropagationContext();
    @Nullable Baggage baggage = propagationContext.getBaggage();
    if (baggage == null) {
      baggage = new Baggage(sentryOptions.getLogger());
      propagationContext.setBaggage(baggage);
    }
    if (baggage.isMutable()) {
      baggage.setValuesFromScope(scope, sentryOptions);
      baggage.freeze();
    }
  }

  private static boolean shouldAttachTracingHeaders(
      final @NotNull String requestUrl, final @NotNull SentryOptions sentryOptions) {
    return PropagationTargetsUtils.contain(sentryOptions.getTracePropagationTargets(), requestUrl);
  }

  private static final class TracingHeadersHolder {
    private @Nullable TracingHeaders headers = null;
  }

  public static final class TracingHeaders {
    private final @NotNull SentryTraceHeader sentryTraceHeader;
    private final @Nullable BaggageHeader baggageHeader;

    public TracingHeaders(
        final @NotNull SentryTraceHeader sentryTraceHeader,
        final @Nullable BaggageHeader baggageHeader) {
      this.sentryTraceHeader = sentryTraceHeader;
      this.baggageHeader = baggageHeader;
    }

    public @NotNull SentryTraceHeader getSentryTraceHeader() {
      return sentryTraceHeader;
    }

    public @Nullable BaggageHeader getBaggageHeader() {
      return baggageHeader;
    }
  }
}
