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

  // Use numbered capture groups for Android API level < 26 compatibility
  private static final Pattern SENTRY_TRACEPARENT_HEADER_REGEX =
      Pattern.compile(
          "^[ \t]*([0-9a-f]{32})-([0-9a-f]{16})(-[01])?[ \t]*$", Pattern.CASE_INSENSITIVE);

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

    if (!matchesExist) {
      throw new InvalidSentryTraceHeaderException(value);
    }

    this.traceId = new SentryId(matcher.group(1));
    this.spanId = new SpanId(matcher.group(2));

    String sampled = matcher.group(3);
    this.sampled = sampled == null ? null : "1".equals(sampled.substring(1));
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
