package io.sentry;

import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.protocol.SentryId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents HTTP header "sentry-trace". */
public final class SentryTraceHeader {
  public static final String SENTRY_TRACE_HEADER = "sentry-trace";

  private final @NotNull SentryId traceId;
  private final @NotNull SpanId spanId;
  private final @Nullable Boolean sampled;

  private static final Pattern SENTRY_TRACEPARENT_HEADER_REGEX =
      Pattern.compile(
          "^[ \\t]*(?<traceId>[0-9a-f]{32})-(?<spanId>[0-9a-f]{16})(?<sampled>-[01])?[ \\t]*$",
          Pattern.CASE_INSENSITIVE);

  public SentryTraceHeader(
      final @NotNull SentryId traceId,
      final @NotNull SpanId spanId,
      final @Nullable Boolean sampled) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.sampled = sampled;
  }

  public SentryTraceHeader(final @NotNull String value) throws InvalidSentryTraceHeaderException {
    Matcher matcher = SENTRY_TRACEPARENT_HEADER_REGEX.matcher(value);
    boolean matchesExist = matcher.matches();

    if (!matchesExist || matcher.group("traceId") == null || matcher.group("spanId") == null) {
      throw new InvalidSentryTraceHeaderException(value);
    }

    this.traceId = new SentryId(matcher.group("traceId"));
    this.spanId = new SpanId(matcher.group("spanId"));
    this.sampled =
        matcher.group("sampled") == null ? null : "1".equals(matcher.group("sampled").substring(1));
  }

  public @NotNull String getName() {
    return SENTRY_TRACE_HEADER;
  }

  public @NotNull String getValue() {
    if (sampled != null) {
      return String.format("%s-%s-%s", traceId, spanId, sampled ? "1" : "0");
    } else {
      return String.format("%s-%s", traceId, spanId);
    }
  }

  public @NotNull SentryId getTraceId() {
    return traceId;
  }

  public @NotNull SpanId getSpanId() {
    return spanId;
  }

  public @Nullable Boolean isSampled() {
    return sampled;
  }
}
