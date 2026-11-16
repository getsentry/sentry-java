// Adapted from OpenTelemetry Instrumentation for Java.
// Copyright The OpenTelemetry Authors.
// Licensed under the Apache License, Version 2.0.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/38e68968be30491d74065230e6bf5cfdcf0c13d8/instrumentation-api/src/main/java/io/opentelemetry/instrumentation/api/internal/StringUtils.java

package io.sentry.util.sql.otel;

import org.jetbrains.annotations.NotNull;

/** The {@code StringUtils} methods the vendored grammar and {@link SqlQuery} use. */
final class StringUtils {

  private StringUtils() {}

  /**
   * Returns the longest prefix whose {@link String#length()} does not exceed {@code maxLength}. If
   * truncating at the limit would split a surrogate pair, the pair is omitted.
   */
  static @NotNull String truncate(final @NotNull String value, final int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, truncationLength(value, maxLength));
  }

  /**
   * Truncates {@code value} so that its length does not exceed {@code maxLength}. If the limit
   * would split a surrogate pair, the pair is omitted.
   */
  static void truncate(final @NotNull StringBuilder value, final int maxLength) {
    if (value.length() > maxLength) {
      value.setLength(truncationLength(value, maxLength));
    }
  }

  private static int truncationLength(final @NotNull CharSequence value, final int maxLength) {
    if (maxLength > 0
        && Character.isHighSurrogate(value.charAt(maxLength - 1))
        && Character.isLowSurrogate(value.charAt(maxLength))) {
      return maxLength - 1;
    }
    return maxLength;
  }
}
