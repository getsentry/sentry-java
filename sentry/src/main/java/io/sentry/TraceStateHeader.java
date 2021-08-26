package io.sentry;

import static io.sentry.vendor.Base64.DEFAULT;

import io.sentry.vendor.Base64;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import org.jetbrains.annotations.NotNull;

public final class TraceStateHeader {
  private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

  private static final String TRACE_STATE_HEADER = "tracestate";
  private final @NotNull String value;

  public static @NotNull TraceStateHeader fromTraceState(
      final @NotNull TraceState traceState,
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger) {
    return new TraceStateHeader(toHttpHeaderFriendlyBase64(toJson(traceState, serializer, logger)));
  }

  static @NotNull String toHttpHeaderFriendlyBase64(final @NotNull String input) {
    return toUtf8(stripForbiddenChars(base64(toBytes(input))));
  }

  private static String toUtf8(String toJson) {
    try {
      return URLEncoder.encode(toJson, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return toJson;
    }
  }

  public TraceStateHeader(final @NotNull String value) {
    this.value = value;
  }

  public @NotNull String getName() {
    return TRACE_STATE_HEADER;
  }

  public @NotNull String getValue() {
    return value;
  }

  private static @NotNull String base64(final @NotNull byte[] input) {
    return Base64.encodeToString(input, DEFAULT);
  }

  private static @NotNull String stripForbiddenChars(final @NotNull String input) {
    return input.replace("=", "");
  }

  private static @NotNull String toJson(
      final @NotNull TraceState traceState,
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger) {
    StringWriter stringWriter = new StringWriter();
    try {
      serializer.serialize(traceState, stringWriter);
      return stringWriter.toString();
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Failed to serialize trace state header", e);
      return "{}";
    }
  }

  private static @NotNull byte[] toBytes(final @NotNull String input) {
    return input.getBytes(UTF8_CHARSET);
  }
}
