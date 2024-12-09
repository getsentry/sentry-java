package io.sentry.util;

import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.PropagationContext;
import io.sentry.SentryOptions;
import io.sentry.SentryTraceHeader;
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
      return new TracingHeaders(
          span.toSentryTrace(), span.toBaggageHeader(thirdPartyBaggageHeaders));
    } else {
      final @NotNull PropagationContextHolder returnValue = new PropagationContextHolder();
      scopes.configureScope(
          (scope) -> {
            returnValue.propagationContext = maybeUpdateBaggage(scope, sentryOptions);
          });

      if (returnValue.propagationContext != null) {
        final @NotNull PropagationContext propagationContext = returnValue.propagationContext;
        final @Nullable Baggage baggage = propagationContext.getBaggage();
        @Nullable BaggageHeader baggageHeader = null;
        if (baggage != null) {
          baggageHeader =
              BaggageHeader.fromBaggageAndOutgoingHeader(baggage, thirdPartyBaggageHeaders);
        }

        return new TracingHeaders(
            new SentryTraceHeader(
                propagationContext.getTraceId(),
                propagationContext.getSpanId(),
                propagationContext.isSampled()),
            baggageHeader);
      }

      return null;
    }
  }

  public static @NotNull PropagationContext maybeUpdateBaggage(
      final @NotNull IScope scope, final @NotNull SentryOptions sentryOptions) {
    return scope.withPropagationContext(
        propagationContext -> {
          @Nullable Baggage baggage = propagationContext.getBaggage();
          if (baggage == null) {
            baggage = new Baggage(sentryOptions.getLogger());
            propagationContext.setBaggage(baggage);
          }
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

  /** Checks if a transaction is to be ignored. */
  @ApiStatus.Internal
  public static boolean isIgnored(
      final @Nullable List<String> ignoredTransactions, final @Nullable String transactionName) {
    if (transactionName == null) {
      return false;
    }
    if (ignoredTransactions == null || ignoredTransactions.isEmpty()) {
      return false;
    }

    for (final String ignoredSlug : ignoredTransactions) {
      if (ignoredSlug.equalsIgnoreCase(transactionName)) {
        return true;
      }

      try {
        if (transactionName.matches(ignoredSlug)) {
          return true;
        }
      } catch (Throwable t) {
        // ignore invalid regex
      }
    }

    return false;
  }
}
