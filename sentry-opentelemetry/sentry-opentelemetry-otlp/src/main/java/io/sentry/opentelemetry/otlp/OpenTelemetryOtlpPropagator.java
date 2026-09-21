package io.sentry.opentelemetry.otlp;

import static io.sentry.SentryTraceHeader.SENTRY_TRACE_HEADER;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.sentry.Baggage;
import io.sentry.BaggageHeader;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryTraceHeader;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.util.PropagationTargetsUtils;
import io.sentry.util.TracingUtils;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OpenTelemetryOtlpPropagator implements TextMapPropagator {

  private static final @NotNull List<String> FIELDS =
      Arrays.asList(SENTRY_TRACE_HEADER, BaggageHeader.BAGGAGE_HEADER);

  public static final @NotNull ContextKey<Baggage> SENTRY_BAGGAGE_KEY =
      ContextKey.named("sentry.baggage");
  private final @NotNull IScopes scopes;

  public OpenTelemetryOtlpPropagator() {
    this(ScopesAdapter.getInstance());
  }

  OpenTelemetryOtlpPropagator(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public Collection<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(final Context context, final C carrier, final TextMapSetter<C> setter) {
    final @NotNull Span otelSpan = Span.fromContext(context);
    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not injecting Sentry tracing information for invalid OpenTelemetry span.");
      return;
    }

    if (!shouldInjectTracingHeaders(otelSpan)) {
      return;
    }

    setter.set(
        carrier,
        SENTRY_TRACE_HEADER,
        otelSpanContext.getTraceId()
            + "-"
            + otelSpanContext.getSpanId()
            + "-"
            + (otelSpanContext.isSampled() ? "1" : "0"));

    final @Nullable Baggage baggage = context.get(SENTRY_BAGGAGE_KEY);
    if (baggage != null) {
      setter.set(carrier, BaggageHeader.BAGGAGE_HEADER, baggage.toHeaderString(null));
    }
  }

  private boolean shouldInjectTracingHeaders(final @NotNull Span otelSpan) {
    final @NotNull SentryOptions options = scopes.getOptions();
    final @Nullable String url = extractUrl(otelSpan, options);

    return url == null
        || PropagationTargetsUtils.contain(options.getTracePropagationTargets(), url);
  }

  private @Nullable String extractUrl(
      final @NotNull Span otelSpan, final @NotNull SentryOptions options) {
    if (!(otelSpan instanceof ReadableSpan)) {
      return null;
    }

    final @NotNull Attributes attributes = ((ReadableSpan) otelSpan).getAttributes();
    final @Nullable String urlFull = attributes.get(UrlAttributes.URL_FULL);
    if (urlFull != null) {
      return urlFull;
    }

    final @Nullable String scheme = attributes.get(UrlAttributes.URL_SCHEME);
    final @Nullable String serverAddress = attributes.get(ServerAttributes.SERVER_ADDRESS);
    final @Nullable Long serverPort = attributes.get(ServerAttributes.SERVER_PORT);
    final @Nullable String path = attributes.get(UrlAttributes.URL_PATH);

    if (scheme == null || serverAddress == null) {
      return null;
    }

    try {
      final @NotNull String pathToUse = path == null ? "" : path;
      if (serverPort == null) {
        return new URL(scheme, serverAddress, pathToUse).toString();
      } else {
        return new URL(scheme, serverAddress, serverPort.intValue(), pathToUse).toString();
      }
    } catch (Throwable t) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Unable to combine URL span attributes into one.", t);
      return null;
    }
  }

  @Override
  public <C> Context extract(
      final Context context, final C carrier, final TextMapGetter<C> getter) {
    final @Nullable String sentryTraceString = getter.get(carrier, SENTRY_TRACE_HEADER);
    if (sentryTraceString == null) {
      return context;
    }

    try {
      SentryTraceHeader sentryTraceHeader = new SentryTraceHeader(sentryTraceString);

      final @Nullable String baggageString = getter.get(carrier, BaggageHeader.BAGGAGE_HEADER);
      final @Nullable Baggage baggage =
          baggageString == null ? null : Baggage.fromHeader(baggageString);
      if (!TracingUtils.shouldContinueTrace(scopes.getOptions(), baggage)) {
        scopes
            .getOptions()
            .getLogger()
            .log(
                SentryLevel.DEBUG, "Not continuing trace due to strict org ID validation failure.");
        return context;
      }
      final @NotNull TraceState traceState = TraceState.getDefault();

      final @NotNull TraceFlags traceFlags =
          Boolean.FALSE.equals(sentryTraceHeader.isSampled())
              ? TraceFlags.getDefault()
              : TraceFlags.getSampled();

      SpanContext otelSpanContext =
          SpanContext.createFromRemoteParent(
              sentryTraceHeader.getTraceId().toString(),
              sentryTraceHeader.getSpanId().toString(),
              traceFlags,
              traceState);

      Span wrappedSpan = Span.wrap(otelSpanContext);

      @NotNull Context modifiedContext = context.with(wrappedSpan);
      if (baggage != null) {
        modifiedContext = modifiedContext.with(SENTRY_BAGGAGE_KEY, baggage);
      }

      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, "Continuing Sentry trace %s", sentryTraceHeader.getTraceId());

      return modifiedContext;
    } catch (InvalidSentryTraceHeaderException e) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Unable to extract Sentry tracing information from invalid header.",
              e);
      return context;
    }
  }
}
